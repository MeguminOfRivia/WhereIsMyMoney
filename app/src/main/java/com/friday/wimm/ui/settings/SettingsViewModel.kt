package com.friday.wimm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _categories.value = categoryRepository.getAllCategories()
        }
    }

    fun addCategory(name: String, color: String) {
        viewModelScope.launch {
            categoryRepository.insert(Category(name = name, color = color))
            loadCategories()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.delete(category)
            loadCategories()
        }
    }

    class Factory(private val categoryRepository: CategoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(categoryRepository) as T
        }
    }
}
