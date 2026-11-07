package com.friday.wimm.data.transfer

/**
 * 转账"待确认"记录。
 *
 * 为什么需要它：微信「我转给别人」时**不会**发支付通知，通知只在对方确认收款时出现，
 * 且那条通知里没有金额。所以金额只能从屏幕上读到，而"这笔钱到底算不算花出去"
 * 要等确认通知。把两者串起来的，就是这张"待确认小纸条"。
 */
data class PendingTransfer(
    val id: Long = 0,
    val direction: TransferDirection,
    /** 单位：分。0 表示金额未知（收款方向常见） */
    val amountCents: Long = 0,
    val merchant: String = "",
    val createdAt: Long,
    val status: TransferStatus = TransferStatus.PENDING,
    /** a11y / notification */
    val source: String = "",
    val note: String = ""
)

enum class TransferDirection(val value: String) {
    /** 我转给别人（付款方） */
    OUT("out"),

    /** 别人转给我（收款方） */
    IN("in");

    companion object {
        fun fromValue(value: String?): TransferDirection? =
            TransferDirection.entries.firstOrNull { it.value == value }
    }
}

enum class TransferStatus(val value: String) {
    /** 等确认 */
    PENDING("pending"),

    /** 已确认并入账 */
    COMMITTED("committed"),

    /** 已退回/已过期，明确不入账（记录保留，便于对账） */
    CANCELLED("cancelled");

    companion object {
        fun fromValue(value: String?): TransferStatus =
            TransferStatus.entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/**
 * 待确认转账的存储抽象。
 *
 * 抽成接口是为了让状态机能用内存实现做单元测试，不需要真机、不需要数据库。
 */
interface TransferStore {
    fun insert(transfer: PendingTransfer): Long
    fun get(id: Long): PendingTransfer?
    fun updateStatus(id: Long, status: TransferStatus)
    fun fillAmount(id: Long, amountCents: Long)

    /** 按方向 + 时间下限查待确认/已确认记录，按创建时间倒序 */
    fun query(direction: TransferDirection, since: Long, status: TransferStatus? = null): List<PendingTransfer>
}
