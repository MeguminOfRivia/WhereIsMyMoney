package com.friday.wimm.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转账事件分类器测试。
 *
 * 这里最容易出错的是「方向」：付款方收到的「对方已收款」字面上像收入，
 * 但实际是支出，必须分类成 OutgoingConfirmed 而不是 IncomingAccepted。
 */
class TransferEventParserTest {

    @Test
    fun `转账已被接收属于付款方的确认`() {
        val event = TransferEventParser.classify("微信支付", "转账已被接收")
        assertTrue(event is TransferEventParser.Event.OutgoingConfirmed)
    }

    @Test
    fun `对方已收款属于付款方的确认而不是我的收入`() {
        val event = TransferEventParser.classify("微信支付", "对方已收款")
        assertTrue(event is TransferEventParser.Event.OutgoingConfirmed)
    }

    @Test
    fun `转账已被领取属于付款方的确认`() {
        val event = TransferEventParser.classify("微信支付", "转账已被领取")
        assertTrue(event is TransferEventParser.Event.OutgoingConfirmed)
    }

    @Test
    fun `转账已退回属于退回事件`() {
        val event = TransferEventParser.classify("微信支付", "转账已退回")
        assertTrue(event is TransferEventParser.Event.Refunded)
    }

    @Test
    fun `对方未接收资金已退还属于退回事件`() {
        val event = TransferEventParser.classify("微信支付", "对方未接收，资金已退还")
        assertTrue(event is TransferEventParser.Event.Refunded)
    }

    @Test
    fun `你收到一笔转账属于收款提醒`() {
        val event = TransferEventParser.classify("张三", "你收到一笔转账")
        assertTrue(event is TransferEventParser.Event.IncomingNotice)
    }

    @Test
    fun `已存入零钱属于收款完成`() {
        val event = TransferEventParser.classify("微信支付", "已存入零钱")
        assertTrue(event is TransferEventParser.Event.IncomingAccepted)
    }

    @Test
    fun `普通支付不会被当成转账事件`() {
        assertNull(TransferEventParser.classify("微信支付", "向星巴克付款¥25.00"))
        assertNull(TransferEventParser.classify("支付宝", "你已成功付款9.90元"))
    }

    @Test
    fun `消费退款不会被当成转账退回`() {
        assertNull(TransferEventParser.classify("微信支付", "退款到账¥25.00"))
    }

    @Test
    fun `转账结果页与填写页能区分`() {
        assertTrue(TransferEventParser.isOutgoingResultPage("转账成功 等待对方确认收款 ¥100.00"))
        assertTrue(TransferEventParser.isOutgoingResultPage("已转账，对方尚未确认"))
        assertFalse(TransferEventParser.isOutgoingResultPage("请输入转账金额"))
    }

    @Test
    fun `收款详情页可识别`() {
        assertTrue(TransferEventParser.isIncomingDetailPage("待确认收款 ¥50.00"))
        assertTrue(TransferEventParser.isIncomingDetailPage("张三向你转账 ¥50.00"))
        assertFalse(TransferEventParser.isIncomingDetailPage("转账给张三"))
    }

    @Test
    fun `收款人昵称能从标题取到`() {
        val event = TransferEventParser.classify("张三", "你收到一笔转账")
        assertEquals("张三", (event as TransferEventParser.Event.IncomingNotice).merchant)
    }
}
