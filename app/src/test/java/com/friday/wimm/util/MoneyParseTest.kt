package com.friday.wimm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 金额解析：用户手输的金额必须严格校验，不能把非法输入悄悄当成 0 元 */
class MoneyParseTest {

    @Test
    fun `普通金额转成分`() {
        assertEquals(1234L, Money.parseCents("12.34"))
        assertEquals(1200L, Money.parseCents("12"))
        assertEquals(50L, Money.parseCents("0.5"))
        assertEquals(1L, Money.parseCents("0.01"))
    }

    @Test
    fun `带符号和逗号也能解析`() {
        assertEquals(1234L, Money.parseCents(" ¥12.34 "))
        assertEquals(123456L, Money.parseCents("￥1,234.56"))
        assertEquals(123456L, Money.parseCents("1，234.56"))
    }

    @Test
    fun `非法输入返回null`() {
        assertNull(Money.parseCents(""))
        assertNull(Money.parseCents("   "))
        assertNull(Money.parseCents("abc"))
        assertNull(Money.parseCents("12.3.4"))
        assertNull(Money.parseCents("0"))
        assertNull(Money.parseCents("0.00"))
        assertNull(Money.parseCents("-5"))
    }

    @Test
    fun `大到离谱的输入被拒绝`() {
        // 多打了几个零，不能真的记成一亿元
        assertNull(Money.parseCents("999999999999"))
    }

    @Test
    fun `四舍五入到分`() {
        assertEquals(100L, Money.parseCents("0.999"))
        assertEquals(1L, Money.parseCents("0.005"))
    }
}
