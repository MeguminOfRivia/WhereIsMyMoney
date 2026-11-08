package com.friday.wimm.ui.import_screen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.data.repository.TransactionRepository
import com.friday.wimm.util.CSVParser
import com.friday.wimm.util.MerchantNames
import com.friday.wimm.util.Money
import com.friday.wimm.util.XLSXParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 账单导入。
 *
 * 导入不只是「塞进数据库」，还要做三件事：
 *  1. 按账单来源（微信/支付宝）把每条流水挂到对应账户上；
 *  2. 能认出收款方的，自动分到对应大类（规则 > 兜底「未分类」）；
 *  3. 和之前自动记下的通知记录合并——同一笔钱不该出现两次。
 */
class ImportViewModel(
    private val app: MyApplication,
    private val repository: TransactionRepository
) : ViewModel() {
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private var lastImportedFileName: String? = null

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = getFileName(context, uri)

                    // 检查是否为同一文件
                    if (fileName == lastImportedFileName) {
                        _importState.value = ImportState.Error("刚刚已导入过此文件，请勿重复导入")
                        return@launch
                    }

                    // Apache POI 解析大文件非常重，必须离开主线程，否则界面会卡死/ANR
                    val transactions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                            XLSXParser.parse(inputStream)
                        } else {
                            CSVParser.parse(inputStream)
                        }
                    }
                    inputStream.close()

                    if (transactions.isEmpty()) {
                        _importState.value = ImportState.Error("未解析到任何交易记录")
                        return@launch
                    }

                    // 计算文件时间范围
                    val minTime = transactions.minOf { it.timestamp }
                    val maxTime = transactions.maxOf { it.timestamp }

                    // 检查时间范围重叠
                    val overlapRecords = repository.checkTimeRangeOverlap(minTime, maxTime)

                    // 先创建导入记录，获取 fileId
                    val fileId = repository.addImportRecord(fileName, minTime, maxTime, transactions.size)

                    // 归属账户 + 自动分类 + 与通知记录合并
                    val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        prepareTransactions(transactions)
                    }

                    val insertedCount = repository.insertAll(prepared.toInsert, fileId)

                    val expenseCount = prepared.toInsert.count { it.type == "expense" }
                    val incomeCount = prepared.toInsert.count { it.type == "income" }
                    val totalExpense = prepared.toInsert.filter { it.type == "expense" }.sumOf { it.amount }
                    val totalIncome = prepared.toInsert.filter { it.type == "income" }.sumOf { it.amount }

                    lastImportedFileName = fileName

                    _importState.value = ImportState.Success(
                        count = insertedCount,
                        totalCount = transactions.size,
                        expenseCount = expenseCount,
                        incomeCount = incomeCount,
                        totalExpense = totalExpense,
                        totalIncome = totalIncome,
                        fileName = fileName,
                        overlapCount = overlapRecords.size,
                        mergedCount = prepared.mergedCount
                    )
                } else {
                    _importState.value = ImportState.Error("无法读取文件")
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "导入失败")
            }
        }
    }

    private class Prepared(val toInsert: List<Transaction>, val mergedCount: Int)

    private fun prepareTransactions(source: List<Transaction>): Prepared {
        val financeDao = app.financeDao
        val ledgerDao = app.ledgerDao
        val toInsert = mutableListOf<Transaction>()
        var merged = 0

        source.forEach { tx ->
            val accountId = if (tx.accountId > 0) {
                tx.accountId
            } else {
                financeDao.getDefaultAccountIdForSource(tx.source)
            }
            val categoryId = tx.categoryId
                ?: financeDao.resolveCategoryIdForMerchant(tx.merchant)
                ?: financeDao.getFallbackCategoryId(tx.type)

            // 通知已经记过同一笔？补全信息，不再插入第二条
            val candidate = ledgerDao.findMergeCandidate(
                amountCents = Money.toCents(tx.amount),
                type = tx.type,
                timestamp = tx.timestamp
            )
            if (candidate != null) {
                merged++
                if (MerchantNames.isGeneric(candidate.merchant) &&
                    !MerchantNames.isGeneric(tx.merchant)
                ) {
                    ledgerDao.updateEntry(candidate.id, merchant = tx.merchant)
                }
                ledgerDao.attachTransactionNo(candidate.id, tx.transactionNo)
                ledgerDao.applyAccountIfUnset(candidate.id, accountId)
                ledgerDao.applyCategoryIfUnset(candidate.id, categoryId)
            } else {
                toInsert.add(tx.copy(accountId = accountId, categoryId = categoryId))
            }
        }
        return Prepared(toInsert, merged)
    }

    fun clearAllData() {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                repository.deleteAllFileAnalysis()
                lastImportedFileName = null
                _importState.value = ImportState.Cleared
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "清空失败")
            }
        }
    }

    fun deleteFileAnalysis(fileId: Long) {
        viewModelScope.launch {
            repository.deleteFileAnalysis(fileId)
        }
    }

    suspend fun deleteImportRecord(fileId: Long) {
        repository.deleteFileAnalysis(fileId)
    }

    suspend fun getImportRecords() = repository.getAllImportRecords()

    suspend fun getTransactionsByFileId(fileId: Long) = repository.getTransactionsByFileId(fileId)

    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = ""
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex) ?: ""
                    }
                }
            }
        }
        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment ?: ""
        }
        return fileName
    }

    sealed class ImportState {
        object Idle : ImportState()
        object Loading : ImportState()
        object Cleared : ImportState()
        data class Success(
            val count: Int,
            val totalCount: Int = 0,
            val expenseCount: Int = 0,
            val incomeCount: Int = 0,
            val totalExpense: Double = 0.0,
            val totalIncome: Double = 0.0,
            val fileName: String = "",
            val overlapCount: Int = 0,  // 与已有数据重叠的导入记录数
            val mergedCount: Int = 0    // 与自动记录合并的条数
        ) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    class Factory(private val app: MyApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportViewModel(app, app.transactionRepository) as T
        }
    }
}
