package com.friday.wimm.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.service.TransactionParser.Channel
import com.friday.wimm.service.TransactionParser.Kind
import com.friday.wimm.service.TransactionParser.Message
import com.friday.wimm.service.TransactionParser.Outcome
import com.friday.wimm.util.AppLog
import com.friday.wimm.util.MerchantNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 通过无障碍服务读取支付/转账结果页面。
 *
 * 定位：**通知的补充**，不是独立的记账来源。
 *  - 转账：付款方没有支付通知，金额只能从这里拿 → 写进待确认记录；
 *  - 普通支付：先看通知通道是否已经记过这笔（同金额同方向），记过就只补商户名；
 *  - 通知没覆盖到：先入账，3 分钟后再静默问一句「这是真实消费吗」。
 *
 * 去重不再依赖「同金额 10 秒」，而是「每进入一次结果页算一个世代」，
 * 页面反复刷新不会重复、两次独立支付也不会被误杀。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionId: String = UUID.randomUUID().toString().take(8)

    private var pageGeneration = 0
    private var currentWindowId = -1
    private var isOnPayPage = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLog.d(TAG) { "无障碍服务已连接" }
        val info = serviceInfo ?: return
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.packageNames = arrayOf(
            TransactionParser.WECHAT_PACKAGE,
            TransactionParser.ALIPAY_PACKAGE
        )
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 200
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent = event ?: return
        val packageName = safeEvent.packageName?.toString() ?: return
        if (!TransactionParser.isSupportedPackage(packageName)) return

        when (safeEvent.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = safeEvent.className?.toString().orEmpty()
                if (isPayResultPage(className)) {
                    pageGeneration += 1
                    currentWindowId = safeEvent.windowId
                    isOnPayPage = true
                    handlePage(collectPageTexts(safeEvent), packageName)
                } else {
                    isOnPayPage = false
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isOnPayPage && safeEvent.windowId == currentWindowId) {
                    handlePage(collectPageTexts(safeEvent), packageName)
                }
            }
        }
    }

    private fun isPayResultPage(className: String): Boolean = PAY_PAGE_HINTS.any {
        className.contains(it, ignoreCase = true)
    }

    private fun collectPageTexts(event: AccessibilityEvent): List<String> {
        val fromSource = collectTexts(event.source)
        return if (fromSource.isNotEmpty()) fromSource else collectTexts(rootInActiveWindow)
    }

    private fun handlePage(texts: List<String>, packageName: String) {
        if (texts.isEmpty()) return
        val fullText = texts.joinToString(" ")
        val message = Message(
            packageName = packageName,
            title = "",
            body = texts,
            identity = "$sessionId#$currentWindowId#$pageGeneration",
            channel = Channel.ACCESSIBILITY
        )
        val draft = (TransactionParser.parse(message) as? Outcome.Parsed)?.draft

        scope.launch {
            try {
                processPage(packageName, fullText, draft)
            } catch (e: Exception) {
                AppLog.e(TAG) { "处理页面失败: ${e.javaClass.simpleName}" }
            }
        }
    }

    private suspend fun processPage(
        packageName: String,
        fullText: String,
        draft: TransactionParser.Draft?
    ) {
        val application = applicationContext as? MyApplication ?: return
        val now = System.currentTimeMillis()
        val amountCents = draft?.amountCents ?: 0
        val merchant = draft?.merchant.orEmpty().let {
            if (MerchantNames.isGeneric(it)) "" else it
        }

        // ── 1) 转账页面：只负责"提供金额"，是否入账由状态机决定 ──
        if (TransferEventParser.isOutgoingResultPage(fullText)) {
            if (amountCents > 0) {
                application.transferCoordinator.onOutgoingPageSeen(amountCents, merchant, now)
                AppLog.d(TAG) { "已挂起一笔待确认的转出" }
            }
            return
        }
        if (TransferEventParser.isIncomingDetailPage(fullText)) {
            if (amountCents > 0) {
                application.transferCoordinator.onIncomingAmountSeen(amountCents, now, merchant)
                AppLog.d(TAG) { "已为待确认收款补上金额" }
            }
            return
        }

        // ── 2) 普通支付：通知优先，屏幕只做补充 ──
        if (draft == null || draft.needsAmount || amountCents <= 0) return
        val db = application.databaseHelper
        val amount = amountCents / 100.0
        val existing = db.findRecentPayment(
            amount = amount,
            type = draft.kind.value,
            since = now - NOTIFICATION_LOOKBACK,
            until = now + CLOCK_SKEW_ALLOWANCE,
            dataSource = "notification"
        )
        if (existing != null) {
            if (merchant.isNotEmpty()) {
                db.updateMerchantIfGeneric(existing.id, merchant, MerchantNames.GENERIC)
            }
            AppLog.d(TAG) { "通知通道已经记过这笔，屏幕只做补充" }
            return
        }

        val transaction = Transaction(
            amount = amount,
            merchant = draft.merchant,
            categoryId = application.financeDao.resolveCategoryIdForMerchant(draft.merchant)
                ?: application.financeDao.getFallbackCategoryId(draft.kind.value),
            source = draft.source,
            timestamp = now,
            type = draft.kind.value,
            transactionNo = draft.fingerprint,
            dataSource = "accessibility",
            note = draft.note,
            accountId = application.financeDao.getDefaultAccountIdForSource(draft.source)
        )
        val result = application.transactionRepository.insertDedup(transaction)
        if (!result.inserted) return

        AppLog.d(TAG) { "通知通道没覆盖到，先入账，稍后询问" }
        scheduleReview(result.id, amount, draft.merchant)
    }

    /**
     * 3 分钟后回头看一眼：这期间通知通道有没有认领这笔？
     * 没有才弹出静默询问，避免正常收到通知时打扰用户。
     */
    private fun scheduleReview(transactionId: Long, amount: Double, merchant: String) {
        scope.launch {
            delay(REVIEW_DELAY_MS)
            val application = applicationContext as? MyApplication ?: return@launch
            val db = application.databaseHelper
            val transaction = db.getTransactionById(transactionId) ?: return@launch
            val claimed = db.findRecentPayment(
                amount = transaction.amount,
                type = transaction.type,
                since = transaction.timestamp - NOTIFICATION_LOOKBACK,
                until = transaction.timestamp + REVIEW_DELAY_MS + CLOCK_SKEW_ALLOWANCE,
                dataSource = "notification"
            ) != null
            if (claimed) return@launch
            ReviewNotifier.showAsk(this@PaymentAccessibilityService, transactionId, amount, merchant)
        }
    }

    private fun collectTexts(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val texts = ArrayList<String>(32)
        walk(root, texts, intArrayOf(MAX_NODES), 0)
        return texts
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        budget: IntArray,
        depth: Int
    ) {
        if (budget[0] <= 0 || depth > MAX_DEPTH) return
        budget[0] -= 1

        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }

        for (index in 0 until node.childCount) {
            if (budget[0] <= 0) return
            val child = node.getChild(index) ?: continue
            walk(child, out, budget, depth + 1)
        }
    }

    override fun onInterrupt() {
        AppLog.d(TAG) { "无障碍服务被中断" }
    }

    override fun onDestroy() {
        scope.cancel()
        AppLog.d(TAG) { "无障碍服务已销毁" }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentAccessibility"
        private const val MAX_NODES = 300
        private const val MAX_DEPTH = 25

        /** 屏幕看到一笔支付时，往前找多久内的通知记录 */
        private const val NOTIFICATION_LOOKBACK = 10 * 60 * 1000L

        /** 设备时间误差容忍 */
        private const val CLOCK_SKEW_ALLOWANCE = 60 * 1000L

        /** 入账后等多久再询问用户 */
        private const val REVIEW_DELAY_MS = 3 * 60 * 1000L

        private val PAY_PAGE_HINTS = listOf(
            "PayResultUI", "PaySuccessUI", "OrderPayResultUI",
            "TransferResultUI", "RemittanceUI", "RemittanceDetailUI",
            "LuckyMoneyDetailUI", "TransferConfirmUI",
            "PayResultActivity", "PaySuccessActivity",
            "TransferResultActivity", "H5PayResultActivity"
        )
    }
}
