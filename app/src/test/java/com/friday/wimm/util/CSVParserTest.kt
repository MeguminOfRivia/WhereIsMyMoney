package com.friday.wimm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * 支付宝 / 微信导出的 CSV 到底能不能看懂。
 *
 * 这些测试用的是仿造的账单文本（不是真实账号数据），
 * 但列名、引号、GBK 编码、汇总行都照真实导出格式来。
 */
class CSVParserTest {

    private val alipayCsv = """
        支付宝交易记录明细查询
        账号:[test@example.com]
        起始日期:[2024-01-01 00:00:00]    终止日期:[2024-02-01 00:00:00]
        ---------------------------------交易记录明细列表------------------------------------
        交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
        2024-01-05 12:34:56,餐饮美食,美团,meituan@x,外卖订单,支出,35.50,余额,交易成功,2024010522001,mt001,
        2024-01-06 08:00:00,转账,张三,zhangsan@x,转账,不计收支,100.00,余额,交易成功,2024010622002,, 
        2024-01-07 19:20:30,日用百货,便利店,/,"纸巾, 湿巾",支出,12.00,花呗,交易成功,2024010722003,,家里用
        2024-01-08 10:00:00,退款,美团,meituan@x,退款,收入,20.00,余额,交易成功,2024010822004,,
        2024-01-09 10:00:00,餐饮美食,已关闭的店,shop@x,订单,支出,9.90,余额,交易关闭,2024010922005,,
    """.trimIndent()

    @Test
    fun `支付宝账单能按表头解析`() {
        val list = CSVParser.parse(ByteArrayInputStream(alipayCsv.toByteArray(Charsets.UTF_8)))
        // 不计收支、交易关闭各跳过一条，剩 3 条
        assertEquals(3, list.size)
        assertEquals("alipay", list[0].source)
    }

    @Test
    fun `支出和收入方向正确`() {
        val list = CSVParser.parse(ByteArrayInputStream(alipayCsv.toByteArray(Charsets.UTF_8)))
        assertEquals("expense", list[0].type)
        assertEquals("income", list[2].type)
    }

    @Test
    fun `金额和时间解析正确`() {
        val list = CSVParser.parse(ByteArrayInputStream(alipayCsv.toByteArray(Charsets.UTF_8)))
        assertEquals(35.50, list[0].amount, 0.001)
        val first = list[0].timestamp
        assertTrue("时间不该是现在", first < System.currentTimeMillis() - 24L * 3600 * 1000)
    }

    @Test
    fun `带引号含逗号的字段不会把行切错`() {
        val list = CSVParser.parse(ByteArrayInputStream(alipayCsv.toByteArray(Charsets.UTF_8)))
        assertEquals("便利店", list[1].merchant)
        assertEquals(12.00, list[1].amount, 0.001)
    }

    @Test
    fun `交易单号被保留`() {
        val list = CSVParser.parse(ByteArrayInputStream(alipayCsv.toByteArray(Charsets.UTF_8)))
        assertEquals("2024010522001", list[0].transactionNo)
    }

    @Test
    fun `GBK 编码的账单也能读`() {
        val bytes = alipayCsv.toByteArray(Charset.forName("GBK"))
        val list = CSVParser.parse(ByteArrayInputStream(bytes))
        assertEquals(3, list.size)
        assertEquals("美团", list[0].merchant)
    }

    @Test
    fun `单号重复时自动加后缀，不会丢记录`() {
        val csv = """
            交易时间,交易对方,商品说明,收/支,金额,交易状态,交易订单号
            2024-01-05 12:00:00,便利店,饮料,支出,3.00,交易成功,SAME001
            2024-01-05 12:00:30,便利店,饮料,支出,3.00,交易成功,SAME001
        """.trimIndent()
        val list = CSVParser.parse(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(2, list.size)
        assertEquals("SAME001", list[0].transactionNo)
        assertEquals("SAME001#2", list[1].transactionNo)
    }

    @Test
    fun `微信账单会被识别成微信来源`() {
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2024-01-05 12:34:56,商户消费,美团,外卖,支出,¥35.50,零钱,支付成功,wx001,mt001,/
        """.trimIndent()
        val list = CSVParser.parse(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(1, list.size)
        assertEquals("wechat", list[0].source)
        assertEquals(35.50, list[0].amount, 0.001)
        assertEquals("美团", list[0].merchant)
    }

    @Test
    fun `没有表头的老格式仍然能用`() {
        val csv = "12.5,早餐店\n30,超市"
        val list = CSVParser.parse(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(2, list.size)
        assertEquals("早餐店", list[0].merchant)
        // 两条时间要错开，否则会被当成重复
        assertTrue(list[0].timestamp != list[1].timestamp)
    }

    @Test
    fun `空文件不报错`() {
        val list = CSVParser.parse(ByteArrayInputStream(ByteArray(0)))
        assertNotNull(list)
        assertTrue(list.isEmpty())
    }
}
