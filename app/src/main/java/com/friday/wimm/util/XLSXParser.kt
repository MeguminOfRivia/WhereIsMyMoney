package com.friday.wimm.util

import com.friday.wimm.data.model.Transaction
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.DateUtil
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object XLSXParser {
    fun parse(inputStream: InputStream): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        // 找到表头行（包含"交易时间"的行）
        var headerRowIndex = -1
        var headerMap = mutableMapOf<String, Int>()

        for (i in 0..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val firstCell = row.getCell(0)?.toString() ?: continue
            if (firstCell.contains("交易时间")) {
                headerRowIndex = i
                // 解析表头
                for (j in 0 until row.lastCellNum) {
                    val headerText = row.getCell(j)?.toString()?.trim() ?: ""
                    if (headerText.isNotEmpty()) {
                        headerMap[headerText] = j
                    }
                }
                break
            }
        }

        if (headerRowIndex == -1 || headerMap.isEmpty()) {
            workbook.close()
            return transactions
        }

        // 获取列索引 - 精确匹配优先
        val timeCol = findColumn(headerMap, "交易时间")
        val merchantCol = findColumn(headerMap, "交易对方", "对方")
        val amountCol = findColumn(headerMap, "金额(元)", "金额")
        val typeCol = findColumn(headerMap, "收/支", "收/支 ")
        val noteCol = findColumn(headerMap, "备注", "备注 ")
        val productCol = findColumn(headerMap, "商品", "商品 ")
        val transactionNoCol = findColumn(headerMap, "交易单号")

        // 解析数据行
        for (i in (headerRowIndex + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue

            try {
                // 获取金额
                val amountCell = if (amountCol >= 0) row.getCell(amountCol) else null
                val amount = getNumericValue(amountCell)
                if (amount == null || amount == 0.0) continue

                // 获取收/支类型
                val typeCell = if (typeCol >= 0) row.getCell(typeCol) else null
                val typeStr = typeCell?.toString()?.trim() ?: ""
                // 跳过不计收支（"/"）的交易，避免转账等被错误计入
                if (typeStr == "/" || typeStr.isEmpty()) continue

                // 精确匹配收/支类型
                val type = when (typeStr) {
                    "收入" -> "income"
                    "支出" -> "expense"
                    else -> {
                        // 模糊匹配（兼容其他格式）
                        when {
                            typeStr.contains("收入") -> "income"
                            typeStr.contains("支出") -> "expense"
                            else -> continue // 无法识别，跳过
                        }
                    }
                }

                // 获取交易对方
                val merchantCell = if (merchantCol >= 0) row.getCell(merchantCol) else null
                val merchant = merchantCell?.toString()?.trim() ?: ""

                // 获取备注
                val noteCell = if (noteCol >= 0) row.getCell(noteCol) else null
                val note = noteCell?.toString()?.trim() ?: ""

                // 获取商品
                val productCell = if (productCol >= 0) row.getCell(productCol) else null
                val product = productCell?.toString()?.trim() ?: ""

                // 获取交易单号（用于去重）
                val transactionNoCell = if (transactionNoCol >= 0) row.getCell(transactionNoCol) else null
                val transactionNo = transactionNoCell?.toString()?.trim() ?: ""

                // 获取时间
                val timeCell = if (timeCol >= 0) row.getCell(timeCol) else null
                val timestamp = parseTimestamp(timeCell)

                // 跳过汇总行（收款方为空或包含"合计"字样）
                val finalMerchant = merchant.ifEmpty { product.ifEmpty { "未知" } }
                if (finalMerchant.contains("合计") || finalMerchant.contains("总计")) continue

                transactions.add(
                    Transaction(
                        amount = amount,
                        merchant = finalMerchant,
                        source = "xlsx",
                        timestamp = timestamp,
                        note = note.ifEmpty { product },
                        type = type,
                        transactionNo = transactionNo.ifEmpty { null }
                    )
                )
            } catch (e: Exception) {
                // 跳过解析失败的行
            }
        }

        workbook.close()
        return transactions
    }

    private fun findColumn(headerMap: Map<String, Int>, vararg names: String): Int {
        // 优先精确匹配
        for (name in names) {
            headerMap[name]?.let { return it }
        }
        // 模糊匹配：只匹配以关键字开头的列（避免误匹配）
        for (name in names) {
            for ((key, value) in headerMap) {
                if (key.startsWith(name) || key.contains(name)) return value
            }
        }
        return -1
    }

    private fun getNumericValue(cell: org.apache.poi.ss.usermodel.Cell?): Double? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> cell.stringCellValue.trim().toDoubleOrNull()
            CellType.FORMULA -> try { cell.numericCellValue } catch (e: Exception) { null }
            else -> null
        }
    }

    private fun parseTimestamp(cell: org.apache.poi.ss.usermodel.Cell?): Long {
        if (cell == null) return System.currentTimeMillis()
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue.time
                    } else {
                        System.currentTimeMillis()
                    }
                }
                CellType.STRING -> {
                    val dateStr = cell.stringCellValue.trim()
                    // 尝试多种日期格式
                    val formats = listOf(
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy/MM/dd HH:mm:ss",
                        "yyyy-MM-dd HH:mm",
                        "yyyy/MM/dd HH:mm"
                    )
                    for (format in formats) {
                        try {
                            val sdf = SimpleDateFormat(format, Locale.CHINA)
                            val date = sdf.parse(dateStr)
                            if (date != null) return date.time
                        } catch (_: Exception) {}
                    }
                    System.currentTimeMillis()
                }
                else -> System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
