package com.friday.wimm.data.repository

import com.friday.wimm.data.database.DatabaseHelper
import com.friday.wimm.data.database.ImportRecord
import com.friday.wimm.data.database.InsertResult
import com.friday.wimm.data.database.MerchantTotal
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionRepository(private val dbHelper: DatabaseHelper) {

    suspend fun insert(transaction: Transaction): Long = withContext(Dispatchers.IO) {
        dbHelper.insertTransaction(transaction)
    }

    /** 带指纹去重的插入：同一笔交易重复到达时不会写入第二条 */
    suspend fun insertDedup(transaction: Transaction): InsertResult = withContext(Dispatchers.IO) {
        dbHelper.insertTransactionDedup(transaction)
    }

    suspend fun updateAmount(id: Long, amount: Double) = withContext(Dispatchers.IO) {
        dbHelper.updateTransactionAmount(id, amount)
    }

    suspend fun insertAll(transactions: List<Transaction>, fileId: Long = 0): Int = withContext(Dispatchers.IO) {
        dbHelper.insertTransactions(transactions, fileId)
    }

    suspend fun getAllTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        dbHelper.getAllTransactions()
    }

    suspend fun getTransactionsByTimeRange(startTime: Long, endTime: Long): List<Transaction> =
        withContext(Dispatchers.IO) {
            dbHelper.getTransactionsByTimeRange(startTime, endTime)
        }

    suspend fun getMerchantTotals(): List<MerchantTotal> = withContext(Dispatchers.IO) {
        dbHelper.getMerchantTotals()
    }

    suspend fun getTotalAmount(): Pair<Double, Double> = withContext(Dispatchers.IO) {
        dbHelper.getTotalAmount()
    }

    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        dbHelper.clearAllTransactions()
    }

    suspend fun deleteFileAnalysis(fileId: Long) = withContext(Dispatchers.IO) {
        dbHelper.deleteFileAnalysis(fileId)
    }

    suspend fun deleteAllFileAnalysis() = withContext(Dispatchers.IO) {
        dbHelper.deleteAllFileAnalysis()
    }

    suspend fun getTransactionsByFileId(fileId: Long): List<Transaction> = withContext(Dispatchers.IO) {
        dbHelper.getTransactionsByFileId(fileId)
    }

    suspend fun getPendingTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        dbHelper.getPendingTransactions()
    }

    suspend fun getPendingTransactionsByCard(card: Card): List<Transaction> = withContext(Dispatchers.IO) {
        dbHelper.getPendingTransactionsByCard(card)
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.deleteTransaction(id)
    }

    suspend fun updateTransaction(id: Long, amount: Double? = null, merchant: String? = null) = withContext(Dispatchers.IO) {
        dbHelper.updateTransaction(id, amount, merchant)
    }

    suspend fun getTransactionsByMerchant(merchant: String): List<Transaction> = withContext(Dispatchers.IO) {
        dbHelper.getTransactionsByMerchant(merchant)
    }

    // 导入记录管理
    suspend fun addImportRecord(fileName: String, startTime: Long, endTime: Long, count: Int): Long =
        withContext(Dispatchers.IO) {
            dbHelper.addImportRecord(fileName, startTime, endTime, count)
        }

    suspend fun getAllImportRecords(): List<ImportRecord> = withContext(Dispatchers.IO) {
        dbHelper.getAllImportRecords()
    }

    suspend fun checkTimeRangeOverlap(startTime: Long, endTime: Long): List<ImportRecord> =
        withContext(Dispatchers.IO) {
            dbHelper.checkTimeRangeOverlap(startTime, endTime)
        }
}
