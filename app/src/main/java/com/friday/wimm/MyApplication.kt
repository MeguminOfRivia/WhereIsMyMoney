package com.friday.wimm

import android.app.Application
import com.friday.wimm.data.database.DatabaseHelper
import com.friday.wimm.data.database.FinanceDao
import com.friday.wimm.data.repository.CardRepository
import com.friday.wimm.data.repository.CategoryRepository
import com.friday.wimm.data.repository.TransactionRepository
import com.friday.wimm.data.transfer.SqliteTransferStore
import com.friday.wimm.data.transfer.TransferCoordinator

class MyApplication : Application() {
    val databaseHelper by lazy { DatabaseHelper(this) }
    val transactionRepository by lazy { TransactionRepository(databaseHelper) }
    val categoryRepository by lazy { CategoryRepository(databaseHelper) }
    val cardRepository by lazy { CardRepository(databaseHelper) }

    /** 账户 / 大类 / 收款方规则 */
    val financeDao by lazy { FinanceDao(databaseHelper) }

    /** 转账「待确认」记录：屏幕读金额、通知定生死 */
    val transferStore by lazy { SqliteTransferStore(databaseHelper) }
    val transferCoordinator by lazy { TransferCoordinator(transferStore) }
}
