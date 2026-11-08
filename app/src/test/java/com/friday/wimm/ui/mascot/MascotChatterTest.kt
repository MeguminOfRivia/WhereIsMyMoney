package com.friday.wimm.ui.mascot

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点一下 Wally 时它会说什么。
 *
 * 这些话直接对着用户的账本讲，所以宁可啰嗦也不能讲错：
 * 超支了必须说超支，没预算就不能假装知道还能花多少。
 */
class MascotChatterTest {

    private fun chatter(
        hasBudget: Boolean = true,
        todaySpentCents: Long = 0,
        todayLimitCents: Long = 10_000,
        monthRemainingCents: Long = 50_000,
        topCategory: String? = null,
        topCategoryCents: Long = 0
    ) = MascotSpeech.budgetChatter(
        hasBudget = hasBudget,
        todaySpentCents = todaySpentCents,
        todayLimitCents = todayLimitCents,
        monthRemainingCents = monthRemainingCents,
        topCategory = topCategory,
        topCategoryCents = topCategoryCents
    )

    @Test
    fun `没有预算时坦白说不知道`() {
        val line = chatter(hasBudget = false)
        assertTrue(line, line.contains("预算"))
    }

    @Test
    fun `今天没花钱时说还没花`() {
        val line = chatter(todaySpentCents = 0)
        assertTrue(line, line.contains("还没花钱") || line.contains("一分没花"))
    }

    @Test
    fun `今天超支时说超了多少`() {
        val line = chatter(todaySpentCents = 15_000, todayLimitCents = 10_000)
        assertTrue(line, line.contains("超"))
        assertTrue(line, line.contains("50.00"))
    }

    @Test
    fun `额度快见底时提醒悠着点`() {
        val line = chatter(todaySpentCents = 9_500, todayLimitCents = 10_000)
        assertTrue(line, line.contains("只剩") || line.contains("快见底") || line.contains("5.00"))
    }

    @Test
    fun `正常消费时汇报还能花多少`() {
        val line = chatter(todaySpentCents = 3_000, todayLimitCents = 10_000)
        assertTrue(line, line.contains("还能花") || line.contains("还剩"))
        assertTrue(line, line.contains("70.00"))
    }

    @Test
    fun `大头分类会被点名`() {
        // 一句话里只能塞下有限内容，随机到「大头」模板时才点名；
        // 这里直接检查多次调用中至少有一次提到大类
        val lines = (1..40).map {
            chatter(
                todaySpentCents = 3_000,
                todayLimitCents = 10_000,
                topCategory = "日常饮食",
                topCategoryCents = 2_500
            )
        }
        assertTrue(lines.any { it.contains("日常饮食") })
    }

    @Test
    fun `本月超支也会说一句`() {
        val lines = (1..40).map {
            chatter(
                todaySpentCents = 3_000,
                todayLimitCents = 10_000,
                monthRemainingCents = -2_000
            )
        }
        assertTrue(lines.any { it.contains("本月") })
    }

    @Test
    fun `话不能太长`() {
        val line = chatter(
            todaySpentCents = 3_000,
            todayLimitCents = 10_000,
            monthRemainingCents = -2_000,
            topCategory = "日常饮食",
            topCategoryCents = 2_500
        )
        // 气泡只有两行，太长的句子会被截断
        assertTrue(line, line.length <= 40)
    }
}
