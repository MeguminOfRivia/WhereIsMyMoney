package com.friday.wimm.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.friday.wimm.data.database.MerchantTotal
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val _merchantTotals = MutableStateFlow<List<MerchantTotal>>(emptyList())
    val merchantTotals: StateFlow<List<MerchantTotal>> = _merchantTotals

    private val _expenseTotals = MutableStateFlow<List<MerchantTotal>>(emptyList())
    val expenseTotals: StateFlow<List<MerchantTotal>> = _expenseTotals

    private val _incomeTotals = MutableStateFlow<List<MerchantTotal>>(emptyList())
    val incomeTotals: StateFlow<List<MerchantTotal>> = _incomeTotals

    private val _allTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val allTransactions: StateFlow<List<Transaction>> = _allTransactions

    private val _totalExpense = MutableStateFlow(0.0)
    val totalExpense: StateFlow<Double> = _totalExpense

    private val _totalIncome = MutableStateFlow(0.0)
    val totalIncome: StateFlow<Double> = _totalIncome

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val allTotals = repository.getMerchantTotals()
            _merchantTotals.value = allTotals
            _expenseTotals.value = allTotals.filter { it.type == "expense" }
            _incomeTotals.value = allTotals.filter { it.type == "income" }
            _allTransactions.value = repository.getAllTransactions()
            val (expense, income) = repository.getTotalAmount()
            _totalExpense.value = expense
            _totalIncome.value = income
        }
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
