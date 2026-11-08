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
 * 金额全部是「分」。几个容易混淆的概念在这里一次说清：
 *  - [budgetCents]     本月这颗分类**填了多少**预算；
 *  - [carryInCents]    上个月（以及更早）滚进来的结余，正=省下、负=超支；
 *  - [adjustCents]     本月手动调整；
 *  - [availableCents]  = 预算 + 结转 + 调整，也就是「这个月这颗分类一共能花多少」；
 *  - [spentCents]      这个分类本月**已经花了多少**；
 *  - [remainingCents]  = 可用 − 已花；
 *  - [baseDailyCents]  每天的基础额度（固定模式=可用÷当月天数；动态模式=剩余÷剩余天数）；
 *  - [dailyLimitCents] 今天**实际能花**多少：固定模式把前几天没花完的额度顺延进来，
 *                      所以它会比基础额度大（或小）。
 */
data class CategoryBudgetState(
    val category: Category,
    val budget: MonthlyBudget,
    val carryInCents: Long,
    val adjustCents: Long,
    val spentCents: Long,
    val availableCents: Long,
    val remainingCents: Long,
    val remainingDaysInMonth: Int,
    val baseDailyCents: Long,
    val dailyLimitCents: Long,
    val todaySpentCents: Long,
    val isCurrentMonth: Boolean
) {
    val budgetCents: Long get() = budget.amountCents
    val isFixed: Boolean get() = budget.dailyMode == MonthlyBudget.MODE_FIXED

    /** 今天还能花多少（负数=今天已经超了） */
    val todayRemainingCents: Long get() = dailyLimitCents - todaySpentCents

    /** 本月已花占可用额度的比例，超支封顶 1f 供进度条使用 */
    val monthProgress: Float
        get() = if (availableCents <= 0L) {
            if (spentCents > 0L) 1f else 0f
        } else {
            (spentCents.toDouble() / availableCents).coerceIn(0.0, 1.0).toFloat()
        }

    /** 今日已花占今日可用额度的比例 */
    val dayProgress: Float
        get() = if (dailyLimitCents <= 0L) {
            if (todaySpentCents > 0L) 1f else 0f
        } else {
            (todaySpentCents.toDouble() / dailyLimitCents).coerceIn(0.0, 1.0).toFloat()
        }

    val isOverBudget: Boolean get() = remainingCents < 0L
    val isOverToday: Boolean get() = isCurrentMonth && todayRemainingCents < 0L
}

/**
 * 一次算好预算页、首页预算条、Wally 面板要用的全部数字。
 *
 * [rows] 只包含**已经设了预算**的大类。
 */
data class BudgetSnapshot(
    val period: String,
    val rows: List<CategoryBudgetState>,
    /** Σ 各分类填写的本月预算（不含结转/调整） */
    val totalBudgetCents: Long,
    /** Σ 可用（预算 + 结转 + 调整） */
    val totalAvailableCents: Long,
    /** Σ 预算内分类的已花 */
    val totalSpentCents: Long,
    /** 本月**全部**支出（含没设预算的分类、含「未分类」） */
    val totalAllSpentCents: Long,
    val totalCarryInCents: Long,
    val totalAdjustCents: Long,
    /** Σ 各分类今日可用额度，超支的分类按 0 计，不让它把别人的额度抵掉 */
    val totalDailyLimitCents: Long,
    val totalTodaySpentCents: Long,
    val daysInMonth: Int,
    val daysRemaining: Int,
    val isCurrentMonth: Boolean
) {
    val hasBudget: Boolean get() = rows.isNotEmpty()
    val totalRemainingCents: Long get() = totalAvailableCents - totalSpentCents

    /** 预算外支出：本月花了、但没占到任何预算的钱 */
    val totalUnbudgetedCents: Long
        get() = (totalAllSpentCents - totalSpentCents).coerceAtLeast(0L)

    val totalTodayRemainingCents: Long get() = totalDailyLimitCents - totalTodaySpentCents

    val monthProgress: Float
        get() = if (totalAvailableCents <= 0L) {
            if (totalSpentCents > 0L) 1f else 0f
        } else {
            (totalSpentCents.toDouble() / totalAvailableCents).coerceIn(0.0, 1.0).toFloat()
        }

    val dayProgress: Float
        get() = if (totalDailyLimitCents <= 0L) {
            if (totalTodaySpentCents > 0L) 1f else 0f
        } else {
            (totalTodaySpentCents.toDouble() / totalDailyLimitCents).coerceIn(0.0, 1.0).toFloat()
        }

    val isOverBudget: Boolean get() = totalRemainingCents < 0L
}

/**
 * 预算计算器：只做查询和算术，不碰界面。
 *
 * 结转规则（按需求保持**无条件继承**）：某月有预算，就用
 * 「预算 + 上月结转 + 本月调整 − 实际支出」算出这个月的结余，再滚进下个月；
 * 某月没有预算，链条就断在那里。最多往回追 [MAX_CARRY_MONTHS] 个月。
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

        /** 把之前每个月「预算 + 调整 − 实际支出」累加起来，滚进当前月 */
        fun carryIntoCurrent(categoryId: Long): Long {
            var carry = 0L
            for (i in 1..MAX_CARRY_MONTHS) {
                val p = periods[i]
                val budget = budgetsByPeriod[p]?.firstOrNull { it.categoryId == categoryId } ?: break
                val adjust = adjustmentsByPeriod[p]?.get(categoryId) ?: 0L
                carry += budget.amountCents + adjust - spentOf(p, categoryId)
            }
            return carry
        }

        val isCurrentMonth = YearMonth.from(today) == yearMonth
        val isPastMonth = yearMonth < YearMonth.from(today)
        val daysInMonth = yearMonth.lengthOfMonth()
        val remainingDays = when {
            isCurrentMonth -> BudgetMath.remainingDaysIncludingToday(today)
            isPastMonth -> 0
            else -> daysInMonth
        }
        val todayRange = if (isCurrentMonth) LedgerDao.dayRange(today) else null
        val currentAdjust = adjustmentsByPeriod[period] ?: emptyMap()
        val currentBudgets = budgetsByPeriod[period].orEmpty()
        val (monthFrom, monthTo) = LedgerDao.monthRange(period)

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

            // 每天的基础额度
            val baseDaily = when {
                !isCurrentMonth -> 0L
                budget.dailyMode == MonthlyBudget.MODE_FIXED ->
                    BudgetMath.planDaily(available, daysInMonth).baseCents
                else -> BudgetMath.dynamicDailyLimit(remaining, remainingDays)
            }
            // 今天实际能花多少：固定模式把前几天没花完的顺延进来
            val dailyLimit = when {
                !isCurrentMonth -> 0L
                budget.dailyMode == MonthlyBudget.MODE_FIXED -> BudgetMath.fixedDailyLimit(
                    availableCents = available,
                    daysInMonth = daysInMonth,
                    dayOfMonth = today.dayOfMonth,
                    spentBeforeTodayCents = spent - todaySpent
                )
                else -> baseDaily
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
                baseDailyCents = baseDaily,
                dailyLimitCents = dailyLimit,
                todaySpentCents = todaySpent,
                isCurrentMonth = isCurrentMonth
            )
        }.sortedBy { it.category.sortOrder }

        val allSpent = financeDao.getSpentCents(monthFrom, monthTo, null)

        return BudgetSnapshot(
            period = period,
            rows = rows,
            totalBudgetCents = rows.sumOf { it.budgetCents },
            totalAvailableCents = rows.sumOf { it.availableCents },
            totalSpentCents = rows.sumOf { it.spentCents },
            totalAllSpentCents = allSpent,
            totalCarryInCents = rows.sumOf { it.carryInCents },
            totalAdjustCents = rows.sumOf { it.adjustCents },
            totalDailyLimitCents = BudgetMath.sumPositive(rows.map { it.dailyLimitCents }),
            totalTodaySpentCents = rows.sumOf { it.todaySpentCents },
            daysInMonth = daysInMonth,
            daysRemaining = remainingDays,
            isCurrentMonth = isCurrentMonth
        )
    }
}
