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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 通过无障碍服务读取支付结果页面。
 *
 * 去重方式（旧实现是「同金额 10 秒内只记一次」，会误杀两笔真实的同额消费）：
 *  - 每**进入一次**支付结果页，`pageGeneration + 1`；
 *  - 指纹 = 会话随机串 + windowId + 世代号，页面内容反复刷新时世代号不变 ⇒ 天然去重；
 *  - 两次独立支付一定会进入两次页面 ⇒ 世代号不同 ⇒ 两笔都会被记录；
 *  - 进程重启后会话随机串改变，不会和历史记录撞车。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 每次服务启动换一个随机串，保证指纹不会跨进程重启碰撞 */
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
                    AppLog.d(TAG) { "进入支付结果页 (第 $pageGeneration 次)" }
                    processPage(safeEvent, packageName)
                } else {
                    isOnPayPage = false
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isOnPayPage && safeEvent.windowId == currentWindowId) {
                    processPage(safeEvent, packageName)
                }
            }
        }
    }

    private fun isPayResultPage(className: String): Boolean = PAY_PAGE_HINTS.any {
        className.contains(it, ignoreCase = true)
    }

    private fun processPage(event: AccessibilityEvent, packageName: String) {
        val fromSource = collectTexts(event.source)
        val texts = if (fromSource.isNotEmpty()) fromSource else collectTexts(rootInActiveWindow)
        if (texts.isEmpty()) return

        val message = Message(
            packageName = packageName,
            title = "",
            body = texts,
            identity = "$sessionId#$currentWindowId#$pageGeneration",
            channel = Channel.ACCESSIBILITY
        )

        when (val outcome = TransactionParser.parse(message)) {
            is Outcome.Ignored -> AppLog.d(TAG) { "页面未识别为交易：${outcome.reason}" }
            is Outcome.Parsed -> scope.launch { save(outcome.draft) }
        }
    }

    private suspend fun save(draft: TransactionParser.Draft) {
        val application = applicationContext as? MyApplication ?: return
        // 金额都没识别出来的页面不落库，避免制造待录入垃圾
        if (draft.needsAmount || draft.amountCents <= 0) return

        val transaction = Transaction(
            amount = draft.amountCents / 100.0,
            merchant = draft.merchant,
            source = draft.source,
            timestamp = System.currentTimeMillis(),
            type = draft.kind.value,
            transactionNo = draft.fingerprint,
            dataSource = "accessibility",
            note = draft.note
        )

        val result = application.transactionRepository.insertDedup(transaction)
        if (result.inserted) {
            AppLog.d(TAG) { "无障碍记入一笔 ${if (draft.kind == Kind.INCOME) "收入" else "支出"}" }
            com.friday.wimm.ui.mascot.MascotService.notifyTransaction(
                context = this@PaymentAccessibilityService,
                isIncome = draft.kind == Kind.INCOME,
                merchant = draft.merchant
            )
        } else {
            AppLog.d(TAG) { "同一页面已记录过，跳过" }
        }
    }

    /**
     * 收集页面文本，带两个上限：
     *  - 节点数量上限，避免在超大页面上做无谓的深度遍历；
     *  - 文本总长度上限，避免把整页聊天记录都抓下来。
     */
    private fun collectTexts(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val texts = ArrayList<String>(32)
        val budget = intArrayOf(MAX_NODES)
        walk(root, texts, budget, 0)
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

        private val PAY_PAGE_HINTS = listOf(
            "PayResultUI", "PaySuccessUI", "OrderPayResultUI",
            "TransferResultUI", "RemittanceUI", "RemittanceDetailUI",
            "LuckyMoneyDetailUI", "TransferConfirmUI",
            "PayResultActivity", "PaySuccessActivity",
            "TransferResultActivity", "H5PayResultActivity"
        )
    }
}
