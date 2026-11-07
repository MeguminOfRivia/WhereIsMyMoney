package com.friday.wimm.service

import com.friday.wimm.service.TransactionParser.Channel
import com.friday.wimm.service.TransactionParser.Draft
import com.friday.wimm.service.TransactionParser.Kind
import com.friday.wimm.service.TransactionParser.Message
import com.friday.wimm.service.TransactionParser.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器单元测试。
 *
 * 这里的每一条用例都对应一个真实场景（或一个真实踩过的坑），
 * 改动识别逻辑时必须先让这些用例保持绿色。
 */
class TransactionParserTest {

    private fun outcome(
        title: String,
        body: String,
        pkg: String = TransactionParser.WECHAT_PACKAGE,
        identity: String = "notif-key-1",
        channel: Channel = Channel.NOTIFICATION
    ): Outcome = TransactionParser.parse(
        Message(
            packageName = pkg,
            title = title,
            body = listOf(body),
            identity = identity,
            channel = channel
        )
    )

    private fun draft(
        title: String,
        body: String,
        pkg: String = TransactionParser.WECHAT_PACKAGE,
        identity: String = "notif-key-1",
        channel: Channel = Channel.NOTIFICATION
    ): Draft {
        val result = outcome(title, body, pkg, identity, channel)
        assertTrue("期望识别为交易，实际被忽略：$result", result is Outcome.Parsed)
        return (result as Outcome.Parsed).draft
    }

    private fun ignoredReason(
        title: String,
        body: String,
        pkg: String = TransactionParser.WECHAT_PACKAGE
    ): String {
        val result = outcome(title, body, pkg)
        assertTrue("期望被忽略，实际识别成了交易：$result", result is Outcome.Ignored)
        return (result as Outcome.Ignored).reason
    }

    // ── 正常交易 ────────────────────────────────────────────

    @Test
    fun `微信扫码付款识别正确`() {
        val d = draft("微信支付", "向星巴克付款¥25.00")
        assertEquals(2500L, d.amountCents)
        assertEquals(Kind.EXPENSE, d.kind)
        assertEquals("星巴克", d.merchant)
        assertEquals("wechat", d.source)
        assertEquals(false, d.needsAmount)
    }

    @Test
    fun `支付宝付款识别正确`() {
        val d = draft(
            "支付宝",
            "你已成功付款9.90元",
            pkg = TransactionParser.ALIPAY_PACKAGE
        )
        assertEquals(990L, d.amountCents)
        assertEquals(Kind.EXPENSE, d.kind)
        assertEquals("支付宝", d.merchant)
        assertEquals("alipay", d.source)
    }

    @Test
    fun `带人民币符号和小数位也识别`() {
        val d = draft("微信支付", "支付成功 ￥12.5")
        assertEquals(1250L, d.amountCents)
        assertEquals(Kind.EXPENSE, d.kind)
    }

    @Test
    fun `千分位金额不会丢位`() {
        val d = draft("微信支付", "向京东付款¥3,268.50")
        assertEquals(326850L, d.amountCents)
        assertEquals("京东", d.merchant)
    }

    @Test
    fun `收到转账是收入且商户取对方昵称`() {
        val d = draft("张三", "你收到一笔转账¥88.00")
        assertEquals(8800L, d.amountCents)
        assertEquals(Kind.INCOME, d.kind)
        assertEquals("张三", d.merchant)
    }

    // ── 收支方向 ────────────────────────────────────────────

    @Test
    fun `别人向我转账是收入`() {
        val d = draft("微信支付", "张三向你转账¥100.00")
        assertEquals(Kind.INCOME, d.kind)
        assertEquals(10000L, d.amountCents)
    }

    @Test
    fun `我向别人转账是支出`() {
        val d = draft("微信支付", "你向张三转账¥100.00")
        assertEquals(Kind.EXPENSE, d.kind)
        assertEquals(10000L, d.amountCents)
    }

    @Test
    fun `发出红包是支出`() {
        val d = draft("微信支付", "你发出了红包¥66.00")
        assertEquals(Kind.EXPENSE, d.kind)
        assertEquals(6600L, d.amountCents)
    }

    @Test
    fun `退款记为收入并打上备注`() {
        val d = draft("微信支付", "退款到账¥25.00")
        assertEquals(Kind.INCOME, d.kind)
        assertEquals("退款", d.note)
        assertEquals(2500L, d.amountCents)
    }

    // ── 必须被忽略的非交易消息（这些曾经都会产生垃圾记录）────────

    @Test
    fun `普通微信聊天不记账`() {
        ignoredReason("微信", "张三: 晚上一起吃饭吗")
    }

    @Test
    fun `支付失败不记账`() {
        ignoredReason("微信支付", "支付失败，请检查网络后重试")
    }

    @Test
    fun `已取消的支付不记账`() {
        ignoredReason("微信支付", "已取消支付")
    }

    @Test
    fun `零钱提现不记账`() {
        ignoredReason("微信支付", "零钱提现申请已提交")
    }

    @Test
    fun `版本更新不记账`() {
        ignoredReason("微信支付", "当前版本 9.8.5 已更新")
    }

    @Test
    fun `版本号不会被当成金额`() {
        ignoredReason("微信支付", "收款方已开启 2.0 版本收款提醒")
    }

    @Test
    fun `日期不会被当成金额`() {
        // 旧实现会把 "2026.11" 解析成 2026.11 元
        ignoredReason("微信", "订单 2026.11.08 已送达")
    }

    @Test
    fun `余额提醒不记账`() {
        ignoredReason("微信支付", "你的零钱余额为 3,268.50 元")
    }

    @Test
    fun `理财收益不记账`() {
        ignoredReason("支付宝", "余额宝昨日收益 1.23 元", TransactionParser.ALIPAY_PACKAGE)
    }

    @Test
    fun `折扣数字不会被当成金额`() {
        ignoredReason("王五", "周末全场 8.5折 起！")
    }

    // ── 金额缺失 ────────────────────────────────────────────

    @Test
    fun `强特征但缺金额时标记为待补录`() {
        val d = draft("张三", "张三向你转账")
        assertEquals(0L, d.amountCents)
        assertTrue(d.needsAmount)
        assertEquals(Kind.INCOME, d.kind)
    }

    @Test
    fun `没有强特征又没金额的一律丢弃`() {
        ignoredReason("微信支付", "今天天气不错")
    }

    // ── 指纹与去重（用户最关心的：两笔相同金额不能被误去重）──────

    @Test
    fun `同一条通知重复解析指纹不变`() {
        val first = draft("微信支付", "向星巴克付款¥25.00", identity = "0|com.tencent.mm|1001|null|1")
        val second = draft("微信支付", "向星巴克付款¥25.00", identity = "0|com.tencent.mm|1001|null|1")
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `短时间内两笔金额相同的真实交易指纹必须不同`() {
        // 3 秒内买两次 12 元，是两个不同的通知 key
        val first = draft("微信支付", "向便利店A付款¥12.00", identity = "0|com.tencent.mm|2001|null|1")
        val second = draft("微信支付", "向便利店B付款¥12.00", identity = "0|com.tencent.mm|2002|null|1")
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(first.amountCents, second.amountCents)
    }

    @Test
    fun `同一商户短时间内两笔相同金额也不能被去重`() {
        val first = draft("微信支付", "向便利店A付款¥12.00", identity = "0|com.tencent.mm|3001|null|1")
        val second = draft("微信支付", "向便利店A付款¥12.00", identity = "0|com.tencent.mm|3002|null|1")
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `无障碍同一页面刷新多次指纹不变`() {
        val first = draft(
            "微信支付",
            "支付成功 | ¥25.00 | 星巴克",
            identity = "session-a#100#1",
            channel = Channel.ACCESSIBILITY
        )
        val second = draft(
            "微信支付",
            "支付成功 | ¥25.00 | 星巴克",
            identity = "session-a#100#1",
            channel = Channel.ACCESSIBILITY
        )
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `无障碍两次进入支付页即使金额相同也是两笔`() {
        val first = draft(
            "微信支付",
            "支付成功 | ¥12.00 | 便利店A",
            identity = "session-a#100#1",
            channel = Channel.ACCESSIBILITY
        )
        val second = draft(
            "微信支付",
            "支付成功 | ¥12.00 | 便利店A",
            identity = "session-a#100#2",
            channel = Channel.ACCESSIBILITY
        )
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `通知通道与无障碍通道指纹不会互相碰撞`() {
        val notification = draft("微信支付", "向星巴克付款¥25.00", identity = "same")
        val accessibility = draft(
            "微信支付",
            "向星巴克付款¥25.00",
            identity = "same",
            channel = Channel.ACCESSIBILITY
        )
        assertNotEquals(notification.fingerprint, accessibility.fingerprint)
    }

    // ── 金额换算 ────────────────────────────────────────────

    @Test
    fun `金额换算为整数分`() {
        assertEquals(123450L, TransactionParser.toCents("1,234.5"))
        assertEquals(1200L, TransactionParser.toCents("12"))
        assertEquals(1L, TransactionParser.toCents("0.01"))
        assertEquals(2500L, TransactionParser.toCents("25.00"))
        assertEquals(null, TransactionParser.toCents(""))
        assertEquals(null, TransactionParser.toCents("abc"))
    }

    @Test
    fun `异常大的金额被拒绝`() {
        val result = outcome("微信支付", "向某人付款¥99,999,999.00")
        assertTrue(result is Outcome.Ignored)
    }
}
