package com.friday.wimm.util

import com.friday.wimm.data.model.Transaction
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV 账单解析，主要面向支付宝导出的 `.csv`（GBK 编码、带表头、字段带引号）。
 *
 * 老版本只认「金额,收款方」两列、时间一律填当前时间，导进来的账全是今天的，
 * 现在按表头找列，认不出来才退回老格式。
 *
 * 几条硬规则：
 *  - 「收/支」是「不计收支」或「/」的行直接跳过（自己转自己不算花钱）；
 *  - 交易关闭/失败的行跳过；
 *  - 交易单号重复时自动加 `#2` 后缀，保证两笔真实交易不会因为单号相同被数据库去重吃掉。
 */
object CSVParser {

    fun parse(inputStream: InputStream): List<Transaction> {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return emptyList()
        val text = decode(bytes)
        val rows = parseRows(text)
        if (rows.isEmpty()) return emptyList()

        val headerIndex = rows.indexOfFirst { row ->
            row.any {
                it.contains("交易时间") || it.contains("交易创建时间") ||
                    it.contains("付款时间") || it.contains("创建时间")
            }
        }
        if (headerIndex < 0) return parseSimpleRows(rows)

        val header = rows[headerIndex]
        val timeCol = findColumn(header, "交易时间", "交易创建时间", "付款时间", "创建时间")
        val amountCol = findColumn(header, "金额（元）", "金额(元)", "金额")
        val merchantCol = findColumn(header, "交易对方", "对方", "商户名称")
        val productCol = findColumn(header, "商品说明", "商品名称", "商品")
        val typeCol = findColumn(header, "收/支", "收支", "资金方向")
        val noteCol = findColumn(header, "备注")
        val noCol = findColumn(header, "交易订单号", "交易单号", "交易号", "流水号")
        val statusCol = findColumn(header, "交易状态", "当前状态")

        val source = detectSource(text)
        val result = mutableListOf<Transaction>()
        val seenNumbers = mutableMapOf<String, Int>()

        for (i in (headerIndex + 1) until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            val status = row.getOrNull(statusCol)?.trim().orEmpty()
            if (status.contains("关闭") || status.contains("失败")) continue

            val direction = row.getOrNull(typeCol)?.trim().orEmpty()
            val type = when {
                direction.contains("支出") -> "expense"
                direction.contains("收入") -> "income"
                // 「不计收支」/「/」/ 空：转账、还款之类，不进收支
                else -> continue
            }

            val amountCents = parseAmountCents(row.getOrNull(amountCol) ?: "") ?: continue
            val timestamp = parseTimestamp(row.getOrNull(timeCol) ?: "") ?: continue

            val merchantRaw = row.getOrNull(merchantCol)?.trim().orEmpty()
            val product = row.getOrNull(productCol)?.trim().orEmpty()
            val merchant = when {
                merchantRaw.isEmpty() || merchantRaw == "/" -> product.ifEmpty { "未知" }
                else -> merchantRaw
            }
            if (merchant.contains("合计") || merchant.contains("总计")) continue

            val noteRaw = row.getOrNull(noteCol)?.trim().orEmpty()
            val note = when {
                noteRaw.isNotEmpty() -> noteRaw
                product.isNotEmpty() && product != merchant -> product
                else -> ""
            }

            val rawNo = row.getOrNull(noCol)?.trim().orEmpty()
            val transactionNo = if (rawNo.isEmpty() || rawNo == "/") {
                null
            } else {
                val n = (seenNumbers[rawNo] ?: 0) + 1
                seenNumbers[rawNo] = n
                if (n == 1) rawNo else "$rawNo#$n"
            }

            result.add(
                Transaction(
                    amount = Money.fromCents(amountCents),
                    merchant = merchant,
                    source = source,
                    timestamp = timestamp,
                    note = note,
                    type = type,
                    transactionNo = transactionNo,
                    dataSource = "file"
                )
            )
        }
        return result
    }

    // ── 解析辅助 ────────────────────────────────────────────

    /** GBK / UTF-8 / 带 BOM 的 UTF-8 都认；先按 UTF-8 严格解码，失败再当 GB18030 */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
        return utf8 ?: String(bytes, Charset.forName("GB18030"))
    }

    /** 处理引号、逗号、换行的 CSV 切分 */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    row.add(field.toString())
                    field.clear()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    private fun findColumn(header: List<String>, vararg names: String): Int {
        names.forEach { name ->
            val exact = header.indexOfFirst { it.trim() == name }
            if (exact >= 0) return exact
        }
        names.forEach { name ->
            val fuzzy = header.indexOfFirst { it.trim().contains(name) }
            if (fuzzy >= 0) return fuzzy
        }
        return -1
    }

    private fun parseAmountCents(raw: String): Long? {
        val cleaned = raw.trim()
            .replace("¥", "").replace("￥", "")
            .replace(",", "").replace("，", "")
            .replace("元", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value == 0.0 || value.isNaN() || value.isInfinite()) return null
        return Math.round(kotlin.math.abs(value) * 100.0)
    }

    private fun parseTimestamp(raw: String): Long? {
        val cleaned = raw.trim().replace("/", "-")
        if (cleaned.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            runCatching {
                LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern, Locale.CHINA))
                    .atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }
        }
        runCatching {
            LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA))
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        return null
    }

    private fun detectSource(text: String): String {
        val head = text.take(4000)
        return when {
            head.contains("微信") -> "wechat"
            head.contains("支付宝") || head.contains("alipay", ignoreCase = true) -> "alipay"
            else -> "csv"
        }
    }

    /** 没有表头的老格式：每行「金额,收款方」 */
    private fun parseSimpleRows(rows: List<List<String>>): List<Transaction> {
        val result = mutableListOf<Transaction>()
        val now = System.currentTimeMillis()
        rows.forEachIndexed { index, row ->
            if (row.size < 2) return@forEachIndexed
            val amount = row[0].trim().toDoubleOrNull() ?: return@forEachIndexed
            val merchant = row[1].trim()
            if (amount <= 0.0 || merchant.isEmpty()) return@forEachIndexed
            result.add(
                Transaction(
                    amount = amount,
                    merchant = merchant,
                    // 每条错开一秒，避免同一批老数据因为时间完全相同被当成重复
                    source = "csv",
                    timestamp = now + index * 1000L
                )
            )
        }
        return result
    }
}
