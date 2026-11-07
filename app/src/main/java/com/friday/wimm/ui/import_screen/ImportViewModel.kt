package com.friday.wimm.ui.import_screen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.friday.wimm.data.repository.TransactionRepository
import com.friday.wimm.util.CSVParser
import com.friday.wimm.util.XLSXParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImportViewModel(private val repository: TransactionRepository) : ViewModel() {
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

                    // 先创建导入记录，获取fileId
                    val fileId = repository.addImportRecord(fileName, minTime, maxTime, transactions.size)

                    val insertedCount = repository.insertAll(transactions, fileId)

                    val expenseCount = transactions.count { it.type == "expense" }
                    val incomeCount = transactions.count { it.type == "income" }
                    val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
                    val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }

                    lastImportedFileName = fileName

                    _importState.value = ImportState.Success(
                        count = insertedCount,
                        totalCount = transactions.size,
                        expenseCount = expenseCount,
                        incomeCount = incomeCount,
                        totalExpense = totalExpense,
                        totalIncome = totalIncome,
                        fileName = fileName,
                        overlapCount = overlapRecords.size
                    )
                } else {
                    _importState.value = ImportState.Error("无法读取文件")
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "导入失败")
            }
        }
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
            val overlapCount: Int = 0  // 与已有数据重叠的导入记录数
        ) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportViewModel(repository) as T
        }
    }
}
