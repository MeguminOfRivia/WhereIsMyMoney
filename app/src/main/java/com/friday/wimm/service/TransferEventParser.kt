package com.friday.wimm.service

/**
 * 转账事件分类器（纯 Kotlin，可单测）。
 *
 * 微信转账的四种通知/页面文案，直接决定状态机怎么走：
 *  - 「你收到一笔转账」            → 收款方挂待确认（**不含金额**，按用户实际观察）
 *  - 「转账已被接收 / 对方已收款」  → 付款方那笔转账完成
 *  - 「转账已退回 / 对方未接收」    → 付款方那笔转账作废
 *  - 「已存入零钱」                → 收款方确认收款完成
 *
 * 分类不确定时一律返回 null，让调用方按普通支付处理或直接忽略——
 * 宁可不动，也不能把方向搞反。
 */
object TransferEventParser {

    sealed interface Event {
        /** 别人转给我：收到转账提醒（金额通常未知） */
        data class IncomingNotice(val merchant: String) : Event

        /** 别人转给我：我已收款 */
        data class IncomingAccepted(val merchant: String) : Event

        /** 我转给别人：对方已确认收款 */
        data class OutgoingConfirmed(val merchant: String) : Event

        /** 转账被退回/过期 */
        data class Refunded(val merchant: String) : Event
    }

    private val REFUND_HINTS = listOf("退回", "退还", "未接收", "已过期", "过期未", "未被领取", "未被接收")
    private val OUT_CONFIRM_HINTS = listOf("已被接收", "已被领取", "对方已收款", "已确认收款", "对方已领取")
    private val IN_ACCEPT_HINTS = listOf("已存入零钱", "已存入", "已收款")
    private val IN_NOTICE_HINTS = listOf("收到一笔转账", "收到转账", "向你转账", "请你收款", "待你确认收款")

    fun classify(title: String, body: String): Event? {
        val full = "$title $body"
        if (full.isBlank()) return null

        val merchant = merchantOf(title, body)

        // 1) 退回：必须出现「转账/红包/资金」之一的语境，
        //    否则「退款已原路退回」这种消费退款会被误判成转账退回
        val hasTransferContext = full.contains("转账") || full.contains("红包") || full.contains("资金")
        if (hasTransferContext && REFUND_HINTS.any { full.contains(it) }) {
            return Event.Refunded(merchant)
        }

        // 2) 付款方看到的「对方已确认」——必须排在「已收款」之前，
        //    否则「对方已收款」会被误判成"我收到了钱"
        if (OUT_CONFIRM_HINTS.any { full.contains(it) }) {
            return Event.OutgoingConfirmed(merchant)
        }

        // 3) 「已存入零钱」是收款方确认收款后的典型文案
        if (IN_ACCEPT_HINTS.any { full.contains(it) }) {
            return Event.IncomingAccepted(merchant)
        }

        // 4) 收款方收到的转账提醒
        if (IN_NOTICE_HINTS.any { full.contains(it) }) {
            return Event.IncomingNotice(merchant)
        }

        return null
    }

    /** 页面文字里是否像「转账成功/等待对方确认」这类**已完成**的结果页（而不是填写页） */
    fun isOutgoingResultPage(text: String): Boolean {
        if (!text.contains("转账")) return false
        return text.contains("等待对方确认") ||
            text.contains("待对方确认") ||
            text.contains("已转账") ||
            text.contains("转账成功") ||
            text.contains("对方尚未确认")
    }

    /** 页面文字里是否像「待确认收款」这类收款详情页 */
    fun isIncomingDetailPage(text: String): Boolean {
        if (!text.contains("转账") && !text.contains("收款")) return false
        return text.contains("待确认收款") ||
            text.contains("确认收款") ||
            text.contains("转账给你") ||
            text.contains("向你转账") ||
            text.contains("你收到一笔转账")
    }

    private fun merchantOf(title: String, body: String): String {
        TransactionParser.merchantCandidate(title)?.let { return it }
        val match = Regex("""(.{1,12}?)(?:向你转账|给你转账|向你转账)""").find(body)
        match?.groupValues?.getOrNull(1)?.let {
            TransactionParser.merchantCandidate(it)?.let { name -> return name }
        }
        return ""
    }
}
