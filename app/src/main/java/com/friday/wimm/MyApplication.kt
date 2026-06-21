package com.friday.wimm

import android.app.Application
import com.friday.wimm.data.database.DatabaseHelper
import com.friday.wimm.data.repository.CardRepository
import com.friday.wimm.data.repository.CategoryRepository
import com.friday.wimm.data.repository.TransactionRepository

class MyApplication : Application() {
    val databaseHelper by lazy { DatabaseHelper(this) }
    val transactionRepository by lazy { TransactionRepository(databaseHelper) }
    val categoryRepository by lazy { CategoryRepository(databaseHelper) }
    val cardRepository by lazy { CardRepository(databaseHelper) }
}
