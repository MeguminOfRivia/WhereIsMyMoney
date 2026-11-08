package com.friday.wimm.data.budget

import com.friday.wimm.data.database.FinanceDao
import com.friday.wimm.data.database.LedgerDao
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.MonthlyBudget
import com.friday.wimm.util.BudgetMath
import java.time.LocalDate
import java.time.YearMonth

/**
 * 某一个大类在本月的预算状态。
 *
 * 金额全部是「分」。
 */
data class CategoryBudgetState(
    val category: Category,
    val budget: MonthlyBudget,
    /** 上月滚进来的结余（正=省下，负=超支） */
    val carryInCents: Long,
    /** 本月手动调整合计 */
    val adjustCents: Long,
    val spentCents: Long,
    /** 本月总共可用 = 预算 + 结转 + 调整 */
    val availableCents: Long,
    /** 本月还剩 = 可用 − 已花 */
    val remainingCents: Long,
    val remainingDaysInMonth: Int,
    /** 今天还能花多少（固定模式=用户填的日额度，动态模式=剩余÷剩余天数） */
    val dailyLimitCents: Long,
    val todaySpentCents: Long
) {
    /** 今天还剩多少可以花（负数=今天已经超了） */
    val todayRemainingCents: Long get() = dailyLimitCents - todaySpentCents

    /** 本月已花占可用额度的比例，0f..1f，超支封顶 1f 供进度条使用 */
    val monthProgress: Float
        get() = if (availableCents <= 0L) {
            if (spentCents > 0L) 1f else 0f
        } else {
            (spentCents.toDouble() / availableCents).coerceIn(0.0, 1.0).toFloat()
        }

    val isOverBudget: Boolean get() = remainingCents < 0L
}

/**
 * 一次算好首页和预算页要用的全部数字。
 *
 * [rows] 只包含**已经设了预算**的大类；没设预算的分类由界面自己从 [Category] 列表里补。
 */
data class BudgetSnapshot(
    val period: String,
    val rows: List<CategoryBudgetState>,
    val totalAvailableCents: Long,
    val totalSpentCents: Long,
    val totalCarryInCents: Long,
    val totalAdjustCents: Long,
    val totalDailyLimitCents: Long,
    val totalTodaySpentCents: Long
) {
    val hasBudget: Boolean get() = rows.isNotEmpty()
    val totalRemainingCents: Long get() = totalAvailableCents - totalSpentCents
    val totalTodayRemainingCents: Long get() = totalDailyLimitCents - totalTodaySpentCents
    val monthProgress: Float
        get() = if (totalAvailableCents <= 0L) {
            if (totalSpentCents > 0L) 1f else 0f
        } else {
            (totalSpentCents.toDouble() / totalAvailableCents).coerceIn(0.0, 1.0).toFloat()
        }
    val isOverBudget: Boolean get() = totalRemainingCents < 0L
}

/**
 * 预算计算器：只做查询和算术，不碰界面。
 *
 * 结转规则：某月有预算，就用「预算 + 上月结转 + 本月调整 − 实际支出」算出这个月的结余，
 * 再滚进下个月；某月没有预算，链条就断在那里（当作没结转）。
 * 最多往回追 [MAX_CARRY_MONTHS] 个月，避免历史太久时每次打开都查一堆数据。
 */
object BudgetPlanner {

    private const val MAX_CARRY_MONTHS = 6

    fun snapshot(
        financeDao: FinanceDao,
        period: String,
        today: LocalDate = LocalDate.now()
    ): BudgetSnapshot {
        val yearMonth = BudgetMath.parsePeriod(period)
        val periods = (0..MAX_CARRY_MONTHS).map { yearMonth.minusMonths(it.toLong()).toString() }

        // 一次性把这几年的预算和调整都读出来，避免每个分类反复查库
        val budgetsByPeriod: Map<String, List<MonthlyBudget>> =
            periods.associateWith { financeDao.getBudgets(it) }
        val adjustmentsByPeriod: Map<String, Map<Long, Long>> =
            periods.associateWith { p ->
                financeDao.getAdjustments(p).groupBy { it.categoryId }
                    .mapValues { (_, list) -> list.sumOf { it.deltaCents } }
            }

        val spentCache = mutableMapOf<Pair<String, Long>, Long>()
        fun spentOf(p: String, categoryId: Long): Long = spentCache.getOrPut(p to categoryId) {
            val (from, to) = LedgerDao.monthRange(p)
            financeDao.getSpentCents(from, to, categoryId)
        }

        /** 从 [periods] 往回逐月滚，算出滚进当前月的结余 */
        fun carryIntoCurrent(categoryId: Long): Long {
            var carry = 0L
            for (i in 1..MAX_CARRY_MONTHS) {
                val p = periods[i]
                val budget = budgetsByPeriod[p]?.firstOrNull { it.categoryId == categoryId } ?: break
                val adjust = adjustmentsByPeriod[p]?.get(categoryId) ?: 0L
                carry = budget.amountCents + adjust + carry - spentOf(p, categoryId)
            }
            return carry
        }

        val isCurrentMonth = YearMonth.from(today) == yearMonth
        val isPastMonth = yearMonth < YearMonth.from(today)
        val remainingDays = when {
            isCurrentMonth -> BudgetMath.remainingDaysIncludingToday(today)
            isPastMonth -> 0
            else -> yearMonth.lengthOfMonth()
        }
        val todayRange = if (isCurrentMonth) LedgerDao.dayRange(today) else null
        val currentAdjust = adjustmentsByPeriod[period] ?: emptyMap()
        val currentBudgets = budgetsByPeriod[period].orEmpty()

        val rows = currentBudgets.mapNotNull { budget ->
            val category = financeDao.getCategory(budget.categoryId) ?: return@mapNotNull null
            val carryIn = carryIntoCurrent(budget.categoryId)
            val adjust = currentAdjust[budget.categoryId] ?: 0L
            val spent = spentOf(period, budget.categoryId)
            val available = budget.amountCents + carryIn + adjust
            val remaining = available - spent
            val todaySpent = todayRange?.let { (f, t) ->
                financeDao.getSpentCents(f, t, budget.categoryId)
            } ?: 0L
            val dailyLimit = when (budget.dailyMode) {
                MonthlyBudget.MODE_FIXED -> when {
                    // 固定日额度：本月可用 ÷ 当月天数（余数补给前几天），
                    // 并且之前每天没花完的额度自动滚到今天
                    isCurrentMonth -> BudgetMath.fixedDailyLimit(
                        availableCents = available,
                        daysInMonth = yearMonth.lengthOfMonth(),
                        dayOfMonth = today.dayOfMonth,
                        spentBeforeTodayCents = spent - todaySpent
                    )
                    isPastMonth -> 0L
                    else -> BudgetMath.splitDaily(available, yearMonth.lengthOfMonth()).quotaOfDay(1)
                }
                else -> BudgetMath.dynamicDailyLimit(remaining, remainingDays)
            }

            CategoryBudgetState(
                category = category,
                budget = budget,
                carryInCents = carryIn,
                adjustCents = adjust,
                spentCents = spent,
                availableCents = available,
                remainingCents = remaining,
                remainingDaysInMonth = remainingDays,
                dailyLimitCents = dailyLimit,
                todaySpentCents = todaySpent
            )
        }.sortedBy { it.category.sortOrder }

        return BudgetSnapshot(
            period = period,
            rows = rows,
            totalAvailableCents = rows.sumOf { it.availableCents },
            totalSpentCents = rows.sumOf { it.spentCents },
            totalCarryInCents = rows.sumOf { it.carryInCents },
            totalAdjustCents = rows.sumOf { it.adjustCents },
            totalDailyLimitCents = rows.sumOf { it.dailyLimitCents },
            totalTodaySpentCents = rows.sumOf { it.todaySpentCents }
        )
    }
}
