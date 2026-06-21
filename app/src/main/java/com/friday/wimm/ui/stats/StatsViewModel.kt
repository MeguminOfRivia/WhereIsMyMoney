package com.friday.wimm.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.friday.wimm.data.database.MerchantTotal
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.data.repository.CategoryRepository
import com.friday.wimm.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    private val _merchantTotals = MutableStateFlow<List<MerchantTotal>>(emptyList())
    val merchantTotals: StateFlow<List<MerchantTotal>> = _merchantTotals

    private val _allTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val allTransactions: StateFlow<List<Transaction>> = _allTransactions

    private val _totalExpense = MutableStateFlow(0.0)
    val totalExpense: StateFlow<Double> = _totalExpense

    private val _totalIncome = MutableStateFlow(0.0)
    val totalIncome: StateFlow<Double> = _totalIncome

    private val _expenseCount = MutableStateFlow(0)
    val expenseCount: StateFlow<Int> = _expenseCount

    private val _incomeCount = MutableStateFlow(0)
    val incomeCount: StateFlow<Int> = _incomeCount

    // 筛选后的交易记录
    private val _filteredTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val filteredTransactions: StateFlow<List<Transaction>> = _filteredTransactions

    // 当前时间范围显示文本
    private val _timeRangeText = MutableStateFlow("全部")
    val timeRangeText: StateFlow<String> = _timeRangeText

    // 均值统计
    private val _averages = MutableStateFlow(Averages(0.0, 0.0, 0.0, ""))
    val averages: StateFlow<Averages> = _averages

    // 排序方式：0=金额，1=时间
    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode

    // 排序方向：false=降序，true=升序
    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending

    // 刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Toast消息
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    init {
        loadData()
    }

    fun setSortMode(mode: Int) {
        _sortMode.value = mode
        updateStats(_filteredTransactions.value)
    }

    fun toggleSortDirection() {
        _sortAscending.value = !_sortAscending.value
        updateStats(_filteredTransactions.value)
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun loadData() {
        viewModelScope.launch {
            val allTransactions = transactionRepository.getAllTransactions()
                .filter { it.dataSource == "notification" } // 只显示通知监听数据
            _allTransactions.value = allTransactions
            applyTimeFilter()
        }
    }

    fun setTimeRange(startTime: Long, endTime: Long) {
        viewModelScope.launch {
            applyTimeRange(startTime, endTime)
        }
    }

    private fun applyTimeFilter() {
        // 默认显示最长时间段：从最早到今天
        if (_allTransactions.value.isEmpty()) {
            _timeRangeText.value = "暂无数据"
            _filteredTransactions.value = emptyList()
            updateStats(emptyList())
            return
        }
        val minTime = _allTransactions.value.minOf { it.timestamp }
        // 结束时间取数据最晚时间和当前时间的较大值，确保包含今天
        val maxDataTime = _allTransactions.value.maxOf { it.timestamp }
        val now = System.currentTimeMillis()
        val maxTime = maxOf(maxDataTime, now)
        applyTimeRange(minTime, maxTime)
    }

    private fun applyTimeRange(startTime: Long, endTime: Long) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val text = "${sdf.format(Date(startTime))} 至 ${sdf.format(Date(endTime))}"
        _timeRangeText.value = text

        val filtered = _allTransactions.value.filter { it.timestamp in startTime..endTime }
        _filteredTransactions.value = filtered
        updateStats(filtered)
    }

    private fun updateStats(transactions: List<Transaction>) {
        _totalExpense.value = transactions.filter { it.type == "expense" }.sumOf { it.amount }
        _totalIncome.value = transactions.filter { it.type == "income" }.sumOf { it.amount }
        _expenseCount.value = transactions.count { it.type == "expense" }
        _incomeCount.value = transactions.count { it.type == "income" }

        // 根据排序方式排序
        _merchantTotals.value = transactions.groupBy { it.merchant }
            .map { (merchant, txs) ->
                val byType = txs.groupBy { it.type }
                byType.map { (type, typeTxs) ->
                    MerchantTotal(
                        merchant = merchant,
                        total = typeTxs.sumOf { it.amount },
                        type = type,
                        lastTimestamp = typeTxs.maxOfOrNull { it.timestamp } ?: 0L
                    )
                }
            }
            .flatten()
            .let { list ->
                when (_sortMode.value) {
                    1 -> if (_sortAscending.value) list.sortedBy { it.lastTimestamp }
                         else list.sortedByDescending { it.lastTimestamp }
                    else -> if (_sortAscending.value) list.sortedBy { it.total }
                            else list.sortedByDescending { it.total }
                }
            }

        // 更新均值统计
        _averages.value = calculateAverages(transactions)
    }

    fun refreshData() {
        _isRefreshing.value = true
        viewModelScope.launch {
            val allTransactions = transactionRepository.getAllTransactions()
                .filter { it.dataSource == "notification" } // 只显示通知监听数据
            _allTransactions.value = allTransactions
            applyTimeFilter()
            _isRefreshing.value = false
            _toastMessage.value = "已刷新，共 ${allTransactions.size} 条记录"
        }
    }

    fun updateTransactionAmount(id: Long, amount: Double) {
        viewModelScope.launch {
            transactionRepository.updateAmount(id, amount)
            val allTransactions = transactionRepository.getAllTransactions()
                .filter { it.dataSource == "notification" }
            _allTransactions.value = allTransactions
            applyTimeFilter()
            _toastMessage.value = "金额已更新"
        }
    }

    fun getPendingTransactions(): List<Transaction> {
        return _allTransactions.value.filter { it.amount <= 0 }
    }

    // 基于筛选后的数据计算均值
    private fun calculateAverages(transactions: List<Transaction>): Averages {
        if (transactions.isEmpty()) return Averages(0.0, 0.0, 0.0, "")

        val expenses = transactions.filter { it.type == "expense" }
        val totalExp = expenses.sumOf { it.amount }

        val minTime = transactions.minOf { it.timestamp }
        val maxTime = transactions.maxOf { it.timestamp }
        val diffMillis = maxTime - minTime
        val diffDays = (diffMillis / (1000L * 60 * 60 * 24)).coerceAtLeast(1)
        val diffMonths = (diffDays / 30.0).coerceAtLeast(1.0)
        val diffYears = (diffDays / 365.0).coerceAtLeast(1.0)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val rangeText = "${sdf.format(Date(minTime))} 至 ${sdf.format(Date(maxTime))}"

        return Averages(
            daily = totalExp / diffDays,
            monthly = totalExp / diffMonths,
            yearly = totalExp / diffYears,
            timeRange = rangeText
        )
    }

    data class Averages(
        val daily: Double,
        val monthly: Double,
        val yearly: Double,
        val timeRange: String
    )

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(transactionRepository, categoryRepository) as T
        }
    }
}
