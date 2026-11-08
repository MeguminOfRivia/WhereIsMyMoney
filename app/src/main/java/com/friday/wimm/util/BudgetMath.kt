package com.friday.wimm.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 预算相关的纯计算逻辑（不依赖 Android，可单测）。
 *
 * 关键约定：
 *  - **记账日以凌晨 4 点为界**：03:59 的花费算前一天，04:00 之后算新的一天；
 *  - 金额一律用「分」的整数运算，避免浮点误差；
 *  - 日限额有两种模式：
 *      * 固定：用户填多少就是多少，花超/剩都会滚到第二天；
 *      * 动态：剩余预算 ÷ 剩余天数，越花越少。
 */
object BudgetMath {

    const val DAY_START_HOUR = 4

    /** 把真实时间戳映射到「记账日」 */
    fun accountingDay(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val local = Instant.ofEpochMilli(timestamp).atZone(zone)
        return if (local.hour < DAY_START_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** 记账日对应的 epochDay，便于做区间查询 */
    fun accountingEpochDay(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        accountingDay(timestamp, zone).toEpochDay()

    /** 某个记账日的起始时间戳（当天 04:00） */
    fun dayStartMillis(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        day.atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    /** "2026-11" 这种 period 字符串 */
    fun periodOf(day: LocalDate): String = "%04d-%02d".format(day.year, day.monthValue)

    fun parsePeriod(period: String): YearMonth = YearMonth.parse(period)

    fun daysInPeriod(period: String): Int = parsePeriod(period).lengthOfMonth()

    /** 包含今天在内，这个月还剩几天（最少 1） */
    fun remainingDaysIncludingToday(today: LocalDate): Int {
        val days = today.lengthOfMonth() - today.dayOfMonth + 1
        return days.coerceAtLeast(1)
    }

    /**
     * 动态日限额 = 剩余可花 ÷ 剩余天数。
     * 剩余为负时返回负数，表示"今天一开始就欠着"。
     */
    fun dynamicDailyLimit(remainingCents: Long, remainingDays: Int): Long {
        val days = remainingDays.coerceAtLeast(1)
        // 向零取整会留下零头，这里用 floorDiv 保证不会因为余数多花一分
        return Math.floorDiv(remainingCents, days.toLong())
    }

    /** 今天花完之后滚到明天（以及下个月）的结余：正数=省下的，负数=超支的 */
    fun carryoverDelta(limitCents: Long, spentCents: Long): Long = limitCents - spentCents

    /** 当月已过天数的基准额度（固定模式用来判断本月整体是否超支） */
    fun monthQuotaForDays(dailyCents: Long, days: Int): Long = dailyCents * days.coerceAtLeast(0)

    /**
     * 固定日额度的分配结果。
     *
     * 每天都是 [baseCents]，除不尽的 [remainderCents] 分钱全部加到「当天」的额度里，
     * 这样既保证 Σ(每天额度) 精确等于本月可用额度，又不用每天改基数。
     */
    data class DailyPlan(val baseCents: Long, val remainderCents: Long, val daysInMonth: Int)

    /** 本月可用额度 ÷ 当月天数，余数单独拎出来 */
    fun planDaily(availableCents: Long, daysInMonth: Int): DailyPlan {
        val days = daysInMonth.coerceAtLeast(1)
        val base = Math.floorDiv(availableCents, days.toLong())
        return DailyPlan(base, availableCents - base * days, days)
    }

    /**
     * 汇总「今日可用额度」时，超支的分类按 0 计。
     *
     * 不然一个花超了的分类会带着负额度，把别的分类今天本来能花的钱抵掉，
     * 汇总就变成「明明还有钱，却说今天额度是负的」。
     */
    fun sumPositive(values: List<Long>): Long = values.sumOf { it.coerceAtLeast(0L) }

    /**
     * 固定日额度模式下「今天还能花多少」。
     *
     * = 每天基数 + 余数 + 之前每一天的结余（省下的加进来，超支的扣掉），
     * 也就是「结余继承到下一天」。返回负数表示今天一开始就欠着。
     */
    fun fixedDailyLimit(
        availableCents: Long,
        daysInMonth: Int,
        dayOfMonth: Int,
        spentBeforeTodayCents: Long
    ): Long {
        val plan = planDaily(availableCents, daysInMonth)
        val daysBefore = (dayOfMonth - 1).coerceAtLeast(0)
        val carry = plan.baseCents * daysBefore - spentBeforeTodayCents
        return plan.baseCents + plan.remainderCents + carry
    }
}
