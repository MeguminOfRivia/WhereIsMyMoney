package com.friday.wimm.data.transfer

/**
 * 转账状态机（纯逻辑，可单测）。
 *
 * 解决三个真实问题：
 *  1. 微信「我转给别人」**不产生支付通知** → 金额只能从屏幕读到，先挂一笔待确认；
 *  2. 「转账已被接收」通知里**没有金额** → 靠待确认记录把金额补回去；
 *  3. 「转账已退回」必须撤销，而不是记成收入。
 *
 * 所有匹配都遵循一个原则：**宁可多留一条待确认，也不猜错金额或方向**。
 */
class TransferCoordinator(private val store: TransferStore) {

    data class Booking(
        val transferId: Long,
        val amountCents: Long,
        val merchant: String,
        /** expense / income */
        val kind: String,
        val note: String
    )

    sealed interface RefundResult {
        /** 只撤销了待确认记录，没有入过账，无需冲正 */
        data class Cancelled(val transfer: PendingTransfer) : RefundResult

        /** 已经入过账，需要补一笔反向记录把钱加回来 */
        data class Reversed(
            val transferId: Long,
            val amountCents: Long,
            val merchant: String
        ) : RefundResult

        data object NotFound : RefundResult
    }

    // ── 付款方：我转给别人 ────────────────────────────────

    /**
     * 屏幕上看到「转账成功 / 等待对方确认收款」的结果页时调用。
     *
     * 注意：调用方**必须**确认这是结果页而不是填写页，否则会把没转出去的钱记上。
     */
    fun onOutgoingPageSeen(
        amountCents: Long,
        merchant: String,
        now: Long,
        source: String = SOURCE_SCREEN
    ): Long? {
        if (amountCents <= 0) return null
        val existing = store.query(TransferDirection.OUT, now - PAGE_DEDUP_WINDOW, TransferStatus.PENDING)
            .firstOrNull {
                it.amountCents == amountCents &&
                    sameMerchant(it.merchant, merchant) &&
                    now - it.createdAt <= PAGE_DEDUP_WINDOW
            }
        if (existing != null) return existing.id

        return store.insert(
            PendingTransfer(
                direction = TransferDirection.OUT,
                amountCents = amountCents,
                merchant = merchant,
                createdAt = now,
                status = TransferStatus.PENDING,
                source = source,
                note = "转账待确认"
            )
        )
    }

    /**
     * 收到「转账已被接收 / 对方已收款」时调用。
     * 返回 null 表示没有对应的待确认转账 —— 这通常是重复确认，直接忽略。
     */
    fun onOutgoingConfirmed(
        now: Long,
        amountCents: Long? = null,
        merchant: String? = null
    ): Booking? {
        val candidates = store.query(
            TransferDirection.OUT,
            now - CONFIRM_WINDOW,
            TransferStatus.PENDING
        )
        val matched = bestMatch(candidates, amountCents, merchant) ?: return null
        if (matched.amountCents <= 0) return null

        store.updateStatus(matched.id, TransferStatus.COMMITTED)
        return Booking(
            transferId = matched.id,
            amountCents = matched.amountCents,
            merchant = matched.merchant.ifBlank { merchant ?: UNKNOWN },
            kind = "expense",
            note = "转账"
        )
    }

    /**
     * 收到「转账已退回 / 对方未接收 / 转账已过期」时调用。
     */
    fun onRefunded(
        now: Long,
        amountCents: Long? = null,
        merchant: String? = null
    ): RefundResult {
        val pending = store.query(
            TransferDirection.OUT,
            now - CONFIRM_WINDOW,
            TransferStatus.PENDING
        )
        bestMatch(pending, amountCents, merchant)?.let {
            store.updateStatus(it.id, TransferStatus.CANCELLED)
            return RefundResult.Cancelled(it)
        }

        val committed = store.query(
            TransferDirection.OUT,
            now - CONFIRM_WINDOW,
            TransferStatus.COMMITTED
        )
        bestMatch(committed, amountCents, merchant)?.let {
            store.updateStatus(it.id, TransferStatus.CANCELLED)
            return RefundResult.Reversed(
                transferId = it.id,
                amountCents = it.amountCents,
                merchant = it.merchant
            )
        }

        return RefundResult.NotFound
    }

    // ── 收款方：别人转给我 ────────────────────────────────

    /**
     * 收到「你收到一笔转账」通知（通常不含金额）时调用，先挂一条金额未知的待确认。
     */
    fun onIncomingNotice(merchant: String, now: Long): Long {
        val existing = store.query(
            TransferDirection.IN,
            now - INCOMING_DEDUP_WINDOW,
            TransferStatus.PENDING
        ).firstOrNull {
            sameMerchant(it.merchant, merchant) && now - it.createdAt <= INCOMING_DEDUP_WINDOW
        }
        if (existing != null) return existing.id

        return store.insert(
            PendingTransfer(
                direction = TransferDirection.IN,
                amountCents = 0,
                merchant = merchant,
                createdAt = now,
                status = TransferStatus.PENDING,
                source = SOURCE_NOTIFICATION,
                note = "待确认收款"
            )
        )
    }

    /**
     * 屏幕读到了转账详情页的金额，回填到最近的待确认收款记录。
     */
    fun onIncomingAmountSeen(
        amountCents: Long,
        now: Long,
        merchant: String? = null
    ): Boolean {
        if (amountCents <= 0) return false
        val candidates = store.query(
            TransferDirection.IN,
            now - CONFIRM_WINDOW,
            TransferStatus.PENDING
        )
        val matched = bestMatch(candidates, null, merchant) ?: return false
        if (matched.amountCents > 0 && matched.amountCents != amountCents) {
            // 已经填过一个不同的金额，不覆盖，避免把两笔搞混
            return false
        }
        store.fillAmount(matched.id, amountCents)
        return true
    }

    /**
     * 收到「已收款 / 已存入零钱」时调用，把待确认的收款转正。
     * 金额仍未知时返回 0 元的 Booking，由调用方提醒用户补录。
     */
    fun onIncomingAccepted(now: Long): Booking? {
        val candidates = store.query(
            TransferDirection.IN,
            now - CONFIRM_WINDOW,
            TransferStatus.PENDING
        )
        val matched = candidates.firstOrNull() ?: return null
        store.updateStatus(matched.id, TransferStatus.COMMITTED)
        return Booking(
            transferId = matched.id,
            amountCents = matched.amountCents,
            merchant = matched.merchant.ifBlank { UNKNOWN },
            kind = "income",
            note = if (matched.amountCents <= 0) "待补录金额" else "转账收款"
        )
    }

    /**
     * 收款方向：对方撤销/转账过期，屏幕或通知提示「转账已过期」时调用。
     */
    fun onIncomingExpired(now: Long, merchant: String? = null): PendingTransfer? {
        val candidates = store.query(
            TransferDirection.IN,
            now - CONFIRM_WINDOW,
            TransferStatus.PENDING
        )
        val matched = bestMatch(candidates, null, merchant) ?: return null
        store.updateStatus(matched.id, TransferStatus.CANCELLED)
        return matched
    }

    // ── 匹配工具 ──────────────────────────────────────────

    private fun bestMatch(
        candidates: List<PendingTransfer>,
        amountCents: Long?,
        merchant: String?
    ): PendingTransfer? {
        if (candidates.isEmpty()) return null
        val wantedMerchant = merchant?.takeIf { it.isNotBlank() && !isGeneric(it) }

        val scored = candidates.mapNotNull { candidate ->
            var score = 0
            if (wantedMerchant != null) {
                when {
                    candidate.merchant == wantedMerchant -> score += 3
                    isGeneric(candidate.merchant) -> score += 1
                    // 双方都有具体商户但不同 → 不是同一笔，直接排除
                    else -> return@mapNotNull null
                }
            }
            if (amountCents != null) {
                when {
                    candidate.amountCents == amountCents -> score += 3
                    candidate.amountCents == 0L -> score += 1
                    else -> return@mapNotNull null
                }
            }
            candidate to score
        }

        return scored.maxWithOrNull(
            compareBy<Pair<PendingTransfer, Int>> { it.second }
                .thenBy { it.first.createdAt }
        )?.first
    }

    private fun sameMerchant(a: String, b: String): Boolean =
        a == b || isGeneric(a) || isGeneric(b)

    private fun isGeneric(name: String): Boolean =
        name.isBlank() || GENERIC_MERCHANTS.any { name.contains(it) }

    companion object {
        const val SOURCE_SCREEN = "a11y"
        const val SOURCE_NOTIFICATION = "notification"
        const val UNKNOWN = "未知转账"

        /** 同一张结果页重复触发时的合并窗口 */
        const val PAGE_DEDUP_WINDOW = 60_000L

        /** 确认/退回的有效窗口：48 小时 */
        const val CONFIRM_WINDOW = 48 * 60 * 60 * 1000L

        /** 同一条「收到转账」通知重复到达时的合并窗口 */
        const val INCOMING_DEDUP_WINDOW = 5 * 60 * 1000L

        private val GENERIC_MERCHANTS = listOf(
            "未知支出", "未知收入", "未知转账", "微信支付", "支付宝", "微信"
        )
    }
}
