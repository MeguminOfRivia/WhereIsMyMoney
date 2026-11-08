package com.friday.wimm.ui.mascot

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点一下 Wally 时的小对话。
 *
 * 这里守着一条硬规矩：**话里不能出现任何数字、金额和百分比**。
 * 气泡只有两行，报数字会挤成一坨；想看具体数字，双击 / 长按有预算面板。
 */
class MascotChatterTest {

    private fun samples(hasBudget: Boolean, over: Boolean, times: Int = 80): List<String> =
        (1..times).map { MascotSpeech.smallTalk(hasBudget = hasBudget, overBudgetToday = over) }

    @Test
    fun `话里不带数字和金额`() {
        samples(true, false).forEach { line ->
            assertTrue("不该出现数字：$line", line.none { it.isDigit() })
            assertTrue("不该出现金额符号：$line", !line.contains("¥") && !line.contains("￥"))
            assertTrue("不该出现百分比：$line", !line.contains("%"))
        }
    }

    @Test
    fun `每句话都够短`() {
        samples(true, false).forEach { assertTrue("太长了：$it", it.length <= 18) }
        samples(true, true).forEach { assertTrue("太长了：$it", it.length <= 18) }
    }

    @Test
    fun `超支时不会说盈余`() {
        samples(true, true).forEach {
            assertTrue("超支了还说盈余：$it", !it.contains("盈余"))
            assertTrue("超支了还说克制：$it", !it.contains("克制"))
            assertTrue("超支了还说开心：$it", !it.contains("开心"))
        }
    }

    @Test
    fun `没超支时不会提超支`() {
        samples(true, false).forEach {
            assertTrue("没超支却提超支：$it", !it.contains("超支"))
        }
    }

    @Test
    fun `没有预算时会提醒去设预算`() {
        samples(false, false).forEach {
            assertTrue("没预算要给出路：$it", it.contains("预算"))
        }
    }

    @Test
    fun `话题围绕记账生活`() {
        // 不要求每句都提到钱，但至少不能是纯随机字符串：
        // 抽 60 句，出现过的不同句子要足够多，说明在轮换
        val distinct = samples(true, false, 60).toSet()
        assertTrue("句子太少：${distinct.size}", distinct.size >= 5)
    }
}
