package com.friday.wimm.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 转账状态机单元测试。
 *
 * 这些用例对应真实的微信转账流程：
 *  - 我转给别人：没有支付通知 → 屏幕记金额 → 确认通知转正 / 退回通知撤销
 *  - 别人转给我：通知没有金额 → 屏幕补金额 → 收款通知转正
 */
class TransferCoordinatorTest {

    private class FakeStore : TransferStore {
        val items = mutableListOf<PendingTransfer>()
        private var nextId = 1L

        override fun insert(transfer: PendingTransfer): Long {
            val id = nextId++
            items.add(transfer.copy(id = id))
            return id
        }

        override fun get(id: Long): PendingTransfer? = items.firstOrNull { it.id == id }

        override fun updateStatus(id: Long, status: TransferStatus) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(status = status)
        }

        override fun fillAmount(id: Long, amountCents: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(amountCents = amountCents)
        }

        override fun query(
            direction: TransferDirection,
            since: Long,
            status: TransferStatus?
        ): List<PendingTransfer> = items
            .filter { it.direction == direction && it.createdAt >= since }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt }
    }

    private lateinit var store: FakeStore
    private lateinit var coordinator: TransferCoordinator

    private val t0 = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = FakeStore()
        coordinator = TransferCoordinator(store)
    }

    // ── 付款方 ────────────────────────────────────────────

    @Test
    fun `屏幕记录金额后收到确认通知才入账`() {
        val id = coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        assertNotNull(id)
        assertEquals(1, store.items.size)
        assertEquals(TransferStatus.PENDING, store.items[0].status)

        val booking = coordinator.onOutgoingConfirmed(t0 + 30_000)
        assertNotNull(booking)
        assertEquals(10_000L, booking!!.amountCents)
        assertEquals("张三", booking.merchant)
        assertEquals("expense", booking.kind)
        assertEquals(TransferStatus.COMMITTED, store.items[0].status)
    }

    @Test
    fun `确认通知重复到达只入账一次`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        val first = coordinator.onOutgoingConfirmed(t0 + 30_000)
        val second = coordinator.onOutgoingConfirmed(t0 + 31_000)
        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun `没有待确认记录时确认通知被忽略`() {
        assertNull(coordinator.onOutgoingConfirmed(t0))
    }

    @Test
    fun `退回时取消待确认且不入账`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        val result = coordinator.onRefunded(t0 + 60_000)
        assertTrue(result is TransferCoordinator.RefundResult.Cancelled)
        assertEquals(TransferStatus.CANCELLED, store.items[0].status)
        assertEquals(0, store.items.count { it.status == TransferStatus.COMMITTED })
    }

    @Test
    fun `已入账后收到退回需要冲正`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        coordinator.onOutgoingConfirmed(t0 + 30_000)
        val result = coordinator.onRefunded(t0 + 120_000)
        assertTrue(result is TransferCoordinator.RefundResult.Reversed)
        assertEquals(10_000L, (result as TransferCoordinator.RefundResult.Reversed).amountCents)
    }

    @Test
    fun `两笔同金额但收款人不同的转账分别匹配`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        coordinator.onOutgoingPageSeen(10_000, "李四", t0 + 5_000)

        val booking = coordinator.onOutgoingConfirmed(t0 + 10_000, merchant = "李四")
        assertNotNull(booking)
        assertEquals("李四", booking!!.merchant)

        val zhangsan = store.items.first { it.merchant == "张三" }
        val lisi = store.items.first { it.merchant == "李四" }
        assertEquals(TransferStatus.PENDING, zhangsan.status)
        assertEquals(TransferStatus.COMMITTED, lisi.status)
    }

    @Test
    fun `金额不同不会误匹配`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        assertNull(coordinator.onOutgoingConfirmed(t0 + 5_000, amountCents = 20_000))
    }

    @Test
    fun `收款人不同不会误匹配`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        assertNull(coordinator.onOutgoingConfirmed(t0 + 5_000, merchant = "李四"))
    }

    @Test
    fun `同一张结果页重复触发不会重复记录`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        coordinator.onOutgoingPageSeen(10_000, "张三", t0 + 2_000)
        assertEquals(1, store.items.size)
    }

    @Test
    fun `超过确认窗口的旧记录不再匹配`() {
        coordinator.onOutgoingPageSeen(10_000, "张三", t0)
        val tooLate = t0 + TransferCoordinator.CONFIRM_WINDOW + 1
        assertNull(coordinator.onOutgoingConfirmed(tooLate))
    }

    // ── 收款方 ────────────────────────────────────────────

    @Test
    fun `收到转账通知后由屏幕补金额再转正`() {
        coordinator.onIncomingNotice("张三", t0)
        assertEquals(0L, store.items[0].amountCents)

        val filled = coordinator.onIncomingAmountSeen(5_000, t0 + 5_000, "张三")
        assertTrue(filled)
        assertEquals(5_000L, store.items[0].amountCents)

        val booking = coordinator.onIncomingAccepted(t0 + 60_000)
        assertNotNull(booking)
        assertEquals(5_000L, booking!!.amountCents)
        assertEquals("income", booking.kind)
        assertEquals(TransferStatus.COMMITTED, store.items[0].status)
    }

    @Test
    fun `金额仍未识别就确认收款时记为零元待补录`() {
        coordinator.onIncomingNotice("张三", t0)
        val booking = coordinator.onIncomingAccepted(t0 + 60_000)
        assertNotNull(booking)
        assertEquals(0L, booking!!.amountCents)
        assertEquals("income", booking.kind)
    }

    @Test
    fun `重复的收到转账通知只挂一条待确认`() {
        coordinator.onIncomingNotice("张三", t0)
        coordinator.onIncomingNotice("张三", t0 + 1_000)
        assertEquals(1, store.items.size)
    }

    @Test
    fun `转账过期后取消待确认收款`() {
        coordinator.onIncomingNotice("张三", t0)
        val cancelled = coordinator.onIncomingExpired(t0 + 60_000)
        assertNotNull(cancelled)
        assertEquals(TransferStatus.CANCELLED, store.items[0].status)
    }

    @Test
    fun `屏幕金额与已有金额冲突时不覆盖`() {
        coordinator.onIncomingNotice("张三", t0)
        coordinator.onIncomingAmountSeen(5_000, t0 + 1_000, "张三")
        val second = coordinator.onIncomingAmountSeen(8_000, t0 + 2_000, "张三")
        assertTrue(!second)
        assertEquals(5_000L, store.items[0].amountCents)
    }
}
