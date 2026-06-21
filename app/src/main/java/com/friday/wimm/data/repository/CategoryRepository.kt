package com.friday.wimm.data.repository

import com.friday.wimm.data.database.DatabaseHelper
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.MerchantCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategoryRepository(private val dbHelper: DatabaseHelper) {

    suspend fun insert(category: Category): Long = withContext(Dispatchers.IO) {
        dbHelper.insertCategory(category)
    }

    suspend fun getAllCategories(): List<Category> = withContext(Dispatchers.IO) {
        dbHelper.getAllCategories()
    }

    suspend fun delete(category: Category) = withContext(Dispatchers.IO) {
        dbHelper.deleteCategory(category)
    }

    suspend fun setMerchantCategory(merchant: String, categoryId: Long) = withContext(Dispatchers.IO) {
        dbHelper.setMerchantCategory(merchant, categoryId)
    }

    suspend fun getMerchantCategory(merchant: String): MerchantCategory? = withContext(Dispatchers.IO) {
        dbHelper.getMerchantCategory(merchant)
    }
}
