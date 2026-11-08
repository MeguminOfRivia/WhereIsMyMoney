package com.friday.wimm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固定日额度：本月额度 ÷ 当月天数，余数补给前几天，结余继承。
 *
 * 这一块最容易出错的地方是「除不尽」：31 天分 2000 元，
 * 每天 64.51 元加起来只有 1999.81，少的两毛不能凭空消失。
 */
class FixedDailyBudgetTest {

    @Test
    fun `除不尽的零头一分不少`() {
        val split = BudgetMath.splitDaily(200_000, 31)
        assertEquals(6_451, split.baseCents)
        assertEquals(19, split.extraDays)
        val total = (1..31).sumOf { split.quotaOfDay(it) }
        assertEquals(200_000, total)
    }

    @Test
    fun `正好整除时没有零头`() {
        val split = BudgetMath.splitDaily(300_000, 30)
        assertEquals(10_000, split.baseCents)
        assertEquals(0, split.extraDays)
        assertEquals(300_000, (1..30).sumOf { split.quotaOfDay(it) })
    }

    @Test
    fun `小额也能精确分配`() {
        val split = BudgetMath.splitDaily(10_000, 7)
        assertEquals(1_428, split.baseCents)
        assertEquals(4, split.extraDays)
        assertEquals(10_000, (1..7).sumOf { split.quotaOfDay(it) })
    }

    @Test
    fun `月初没有结转`() {
        val limit = BudgetMath.fixedDailyLimit(
            availableCents = 310_000,
            daysInMonth = 31,
            dayOfMonth = 1,
            spentBeforeTodayCents = 0
        )
        assertEquals(10_000, limit)
    }

    @Test
    fun `前几天没花钱今天的额度会变大`() {
        // 31 天分 3100 分 = 每天 100 分，前 4 天一分没花累计 400 分
        val limit = BudgetMath.fixedDailyLimit(
            availableCents = 3_100,
            daysInMonth = 31,
            dayOfMonth = 5,
            spentBeforeTodayCents = 0
        )
        assertEquals(500, limit)
    }

    @Test
    fun `前几天超支今天的额度会被扣掉`() {
        // 前 2 天额度 200 分，实际花了 300 分，多花 100
        val limit = BudgetMath.fixedDailyLimit(
            availableCents = 3_100,
            daysInMonth = 31,
            dayOfMonth = 3,
            spentBeforeTodayCents = 300
        )
        assertEquals(0, limit)
    }

    @Test
    fun `超支到没有额度时返回负数`() {
        val limit = BudgetMath.fixedDailyLimit(
            availableCents = 3_100,
            daysInMonth = 31,
            dayOfMonth = 3,
            spentBeforeTodayCents = 500
        )
        assertEquals(-200, limit)
    }

    @Test
    fun `负数额度也能平摊`() {
        val split = BudgetMath.splitDaily(-3_100, 31)
        assertEquals(-3_100, (1..31).sumOf { split.quotaOfDay(it) })
    }

    @Test
    fun `天数非法时不会崩`() {
        val split = BudgetMath.splitDaily(1_000, 0)
        assertEquals(1, split.daysInMonth)
        assertTrue(split.baseCents > 0)
    }
}
