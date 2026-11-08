package com.friday.wimm.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetMathTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millisOf(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `凌晨四点前算前一天`() {
        val day = BudgetMath.accountingDay(millisOf(2026, 11, 8, 3, 59), zone)
        assertEquals(LocalDate.of(2026, 11, 7), day)
    }

    @Test
    fun `凌晨四点整算新的一天`() {
        val day = BudgetMath.accountingDay(millisOf(2026, 11, 8, 4, 0), zone)
        assertEquals(LocalDate.of(2026, 11, 8), day)
    }

    @Test
    fun `深夜二十三点仍算当天`() {
        val day = BudgetMath.accountingDay(millisOf(2026, 11, 8, 23, 30), zone)
        assertEquals(LocalDate.of(2026, 11, 8), day)
    }

    @Test
    fun `记账日起点等于当天四点`() {
        val start = BudgetMath.dayStartMillis(LocalDate.of(2026, 11, 8), zone)
        assertEquals(millisOf(2026, 11, 8, 4, 0), start)
    }

    @Test
    fun `二月天数按闰年计算`() {
        assertEquals(29, BudgetMath.daysInPeriod("2028-02"))
        assertEquals(28, BudgetMath.daysInPeriod("2026-02"))
        assertEquals(30, BudgetMath.daysInPeriod("2026-11"))
    }

    @Test
    fun `剩余天数包含今天`() {
        assertEquals(23, BudgetMath.remainingDaysIncludingToday(LocalDate.of(2026, 11, 8)))
        assertEquals(1, BudgetMath.remainingDaysIncludingToday(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `动态日限额用剩余预算除剩余天数`() {
        // 3000 元预算，还剩 800，剩 23 天
        assertEquals(3478L, BudgetMath.dynamicDailyLimit(800_00L, 23))
    }

    @Test
    fun `动态日限额在超支时返回负数`() {
        assertEquals(-1000L, BudgetMath.dynamicDailyLimit(-10_00L, 1))
    }

    @Test
    fun `结余为限额减实际花费`() {
        assertEquals(2000L, BudgetMath.carryoverDelta(10_000L, 8_000L))
        assertEquals(-500L, BudgetMath.carryoverDelta(10_000L, 10_500L))
    }
}
