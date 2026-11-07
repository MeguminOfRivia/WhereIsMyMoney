package com.friday.wimm.data.repository

import com.friday.wimm.data.database.DatabaseHelper
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.model.CardCategory
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.util.Money

/** 金额必须按「分」累加，否则浮点数会累积误差 */
private fun List<Transaction>.sumCents(): Long = sumOf { Money.toCents(it.amount) }

class CardRepository(private val dbHelper: DatabaseHelper) {

    // 卡片操作
    fun insertCard(card: Card): Long = dbHelper.insertCard(card)

    fun getAllCards(): List<Card> = dbHelper.getAllCards()

    fun getCard(cardId: Long): Card? = dbHelper.getCard(cardId)

    fun updateCard(card: Card) = dbHelper.updateCard(card)

    fun deleteCard(cardId: Long) = dbHelper.deleteCard(cardId)

    // 卡片分类操作
    fun insertCardCategory(cardCategory: CardCategory): Long = dbHelper.insertCardCategory(cardCategory)

    fun getCardCategories(cardId: Long): List<CardCategory> = dbHelper.getCardCategories(cardId)

    fun deleteCardCategory(categoryId: Long) = dbHelper.deleteCardCategory(categoryId)

    // 卡片收款方分类映射
    fun setCardMerchantCategory(cardId: Long, merchant: String, categoryId: Long) =
        dbHelper.setCardMerchantCategory(cardId, merchant, categoryId)

    fun getCardMerchantCategory(cardId: Long, merchant: String): Long? =
        dbHelper.getCardMerchantCategory(cardId, merchant)

    fun getCardMerchantsByCategory(cardId: Long, categoryId: Long): List<String> =
        dbHelper.getCardMerchantsByCategory(cardId, categoryId)

    // 获取卡片的交易记录
    fun getTransactionsByCard(card: Card): List<Transaction> = dbHelper.getTransactionsByCard(card)

    // 获取卡片的统计信息
    fun getCardStatistics(card: Card): CardStatistics {
        val transactions = getTransactionsByCard(card)
        val expenseTransactions = transactions.filter { it.type == "expense" }
        val incomeTransactions = transactions.filter { it.type == "income" }

        val totalExpense = Money.fromCents(expenseTransactions.sumCents())
        val totalIncome = Money.fromCents(incomeTransactions.sumCents())
        val expenseCount = expenseTransactions.size
        val incomeCount = incomeTransactions.size

        // 按收款方分组
        val merchantGroups = transactions.groupBy { it.merchant }
            .map { (merchant, txs) ->
                val byType = txs.groupBy { it.type }
                MerchantSummary(
                    merchant = merchant,
                    expenseTotal = Money.fromCents(byType["expense"]?.sumCents() ?: 0L),
                    incomeTotal = Money.fromCents(byType["income"]?.sumCents() ?: 0L),
                    expenseCount = byType["expense"]?.size ?: 0,
                    incomeCount = byType["income"]?.size ?: 0,
                    lastTimestamp = txs.maxOfOrNull { it.timestamp } ?: 0L
                )
            }

        // 按分类分组（如果有分类）
        val categories = getCardCategories(card.id)
        val categoryStats = if (categories.isNotEmpty()) {
            categories.map { category ->
                val merchants = getCardMerchantsByCategory(card.id, category.id)
                val categoryTransactions = transactions.filter { it.merchant in merchants }
                val categoryExpense = Money.fromCents(
                    categoryTransactions.filter { it.type == "expense" }.sumCents()
                )
                val categoryIncome = Money.fromCents(
                    categoryTransactions.filter { it.type == "income" }.sumCents()
                )
                CategoryStatistics(
                    category = category,
                    expenseTotal = categoryExpense,
                    incomeTotal = categoryIncome,
                    transactionCount = categoryTransactions.size
                )
            }
        } else {
            emptyList()
        }

        // 均值计算
        val averages = if (transactions.isEmpty()) {
            Triple(0.0, 0.0, 0.0)
        } else {
            val minTime = transactions.minOf { it.timestamp }
            val maxTime = transactions.maxOf { it.timestamp }
            val diffDays = ((maxTime - minTime) / (1000L * 60 * 60 * 24)).coerceAtLeast(1)
            val diffMonths = (diffDays / 30.0).coerceAtLeast(1.0)
            val diffYears = (diffDays / 365.0).coerceAtLeast(1.0)
            Triple(totalExpense / diffDays, totalExpense / diffMonths, totalExpense / diffYears)
        }

        return CardStatistics(
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            expenseCount = expenseCount,
            incomeCount = incomeCount,
            merchantSummaries = merchantGroups,
            categoryStatistics = categoryStats,
            dailyAverage = averages.first,
            monthlyAverage = averages.second,
            yearlyAverage = averages.third
        )
    }
}

data class CardStatistics(
    val totalExpense: Double,
    val totalIncome: Double,
    val expenseCount: Int,
    val incomeCount: Int,
    val merchantSummaries: List<MerchantSummary>,
    val categoryStatistics: List<CategoryStatistics>,
    val dailyAverage: Double,
    val monthlyAverage: Double,
    val yearlyAverage: Double
)

data class MerchantSummary(
    val merchant: String,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val expenseCount: Int,
    val incomeCount: Int,
    val lastTimestamp: Long = 0
)

data class CategoryStatistics(
    val category: CardCategory,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val transactionCount: Int
)
