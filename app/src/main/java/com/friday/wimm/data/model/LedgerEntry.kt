package com.friday.wimm.data.model

/**
 * 一条流水 + 它关联的大类与账户信息。
 *
 * 列表里要显示「大类名」「账户名」，如果每次都单独查一次数据库，
 * 一屏 20 条就要 40 次查询。所以查询时直接 LEFT JOIN 出来，一次拿全。
 */
data class LedgerEntry(
    val tx: Transaction,
    val categoryName: String,
    val categoryColor: String,
    val accountName: String
) {
    val isIncome: Boolean get() = tx.type == "income"
    val amountCents: Long get() = com.friday.wimm.util.Money.toCents(tx.amount)
}

/** 按大类汇总的一行（统计页饼图 / 预算页都用它） */
data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val color: String,
    val cents: Long,
    val count: Int
)

/** 按收款方汇总的一行 */
data class MerchantTotalRow(
    val merchant: String,
    val cents: Long,
    val count: Int,
    val lastTimestamp: Long
)

/** 趋势图的一个点：天或月 */
data class TrendPoint(
    val label: String,
    val expenseCents: Long,
    val incomeCents: Long
)

/** 一段区间的收支合计 */
data class LedgerSums(
    val expenseCents: Long,
    val incomeCents: Long,
    val expenseCount: Int,
    val incomeCount: Int
) {
    val netCents: Long get() = incomeCents - expenseCents
    val count: Int get() = expenseCount + incomeCount
}
