package com.friday.wimm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.data.transfer.TransferCoordinator
import com.friday.wimm.service.TransactionParser.Channel
import com.friday.wimm.service.TransactionParser.Draft
import com.friday.wimm.service.TransactionParser.Kind
import com.friday.wimm.service.TransactionParser.Message
import com.friday.wimm.service.TransactionParser.Outcome
import com.friday.wimm.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 微信 / 支付宝通知监听。
 *
 * 与旧实现的区别：
 *  - 解析逻辑搬到 [TransactionParser]，这里只负责「取通知字段 → 交给解析器 → 落库 → 反馈」；
 *  - 不再在主线程 `runBlocking` 写数据库（旧实现会卡住通知回调）；
 *  - 不再每来一笔交易就 `deleteNotificationChannel` + `createNotificationChannel`
 *    （那会把用户设置的声音/悬浮权限全部重置，还会触发系统限流）；
 *  - 不再打印含金额、商户、通知原文的日志。
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        AppLog.d(TAG) { "通知监听服务已创建" }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppLog.d(TAG) { "通知监听服务已连接" }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppLog.d(TAG) { "通知监听服务已断开" }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val packageName = notification.packageName ?: return
        if (!TransactionParser.isSupportedPackage(packageName)) return
        // 常驻通知（正在进行的通话、下载等）不可能是支付结果
        if (notification.isOngoing) return

        val message = buildMessage(notification) ?: return

        // 转账事件优先判断：这类通知往往没有金额，走普通解析会被丢掉
        val plainText = (listOf(message.title) + message.body).joinToString(" ")
        TransferEventParser.classify(message.title, plainText)?.let { event ->
            scope.launch { handleTransferEvent(event, message.packageName) }
            return
        }

        when (val outcome = TransactionParser.parse(message)) {
            is Outcome.Ignored -> AppLog.d(TAG) { "忽略一条通知：${outcome.reason}" }
            is Outcome.Parsed -> scope.launch { handleParsed(outcome.draft) }
        }
    }

    private fun buildMessage(sbn: StatusBarNotification): Message? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = listOfNotNull(
            extras.getString(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
            extras.getCharSequence("android.infoText")?.toString(),
            notification.tickerText?.toString()
        )
        if (title.isBlank() && body.all { it.isBlank() }) return null
        return Message(
            packageName = sbn.packageName,
            title = title,
            body = body,
            // key 在「同一条通知被更新」时保持不变，是天然的去重依据
            identity = sbn.key ?: sbn.id.toString(),
            channel = Channel.NOTIFICATION
        )
    }

    private suspend fun handleParsed(draft: Draft) {
        val application = applicationContext as? MyApplication ?: return
        val db = application.databaseHelper
        val now = System.currentTimeMillis()

        // 通知为准：如果屏幕已经先记了一条同金额、同方向、商户相容的记录，
        // 把它收编掉（删掉屏幕那条），只保留通知这条，避免一笔账记两遍。
        val adopted = if (draft.amountCents > 0) {
            db.findRecentPayment(
                amount = draft.amountCents / 100.0,
                type = draft.kind.value,
                since = now - CROSS_CHANNEL_WINDOW,
                until = now + CLOCK_SKEW_ALLOWANCE,
                dataSource = "accessibility"
            )?.takeIf { com.friday.wimm.util.MerchantNames.compatible(it.merchant, draft.merchant) }
        } else {
            null
        }
        if (adopted != null) {
            db.deleteTransaction(adopted.id)
            AppLog.d(TAG) { "通知认领了屏幕先记的一条，已合并" }
        }

        val merchant = com.friday.wimm.util.MerchantNames.better(adopted?.merchant, draft.merchant)
        val transaction = Transaction(
            amount = draft.amountCents / 100.0,
            merchant = merchant,
            source = draft.source,
            timestamp = now,
            type = draft.kind.value,
            transactionNo = draft.fingerprint,
            dataSource = "notification",
            note = draft.note
        )

        val result = application.transactionRepository.insertDedup(transaction)
        if (!result.inserted) {
            AppLog.d(TAG) { "同一笔交易已记录过，跳过通知" }
            return
        }

        val id = result.id
        if (id <= 0) return

        withContext(Dispatchers.Main) {
            com.friday.wimm.ui.mascot.MascotService.notifyTransaction(
                context = this@PaymentNotificationListener,
                isIncome = draft.kind == Kind.INCOME,
                merchant = transaction.merchant
            )
            if (draft.needsAmount) {
                notifyMissingAmount(draft, id)
            } else {
                notifyTransactionRecorded(transaction, id)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 转账事件
    // ─────────────────────────────────────────────────────────

    private suspend fun handleTransferEvent(
        event: TransferEventParser.Event,
        packageName: String
    ) {
        val application = applicationContext as? MyApplication ?: return
        val now = System.currentTimeMillis()
        val coordinator = application.transferCoordinator
        val source = if (packageName == TransactionParser.WECHAT_PACKAGE) "wechat" else "alipay"

        when (event) {
            is TransferEventParser.Event.IncomingNotice -> {
                coordinator.onIncomingNotice(event.merchant, now)
                AppLog.d(TAG) { "挂起一笔待确认收款（金额等待屏幕补充）" }
            }

            is TransferEventParser.Event.IncomingAccepted -> {
                val booking = coordinator.onIncomingAccepted(now) ?: return
                bookTransfer(application, booking, source)
            }

            is TransferEventParser.Event.OutgoingConfirmed -> {
                val booking = coordinator.onOutgoingConfirmed(now) ?: return
                bookTransfer(application, booking, source)
            }

            is TransferEventParser.Event.Refunded -> {
                when (val result = coordinator.onRefunded(now)) {
                    is TransferCoordinator.RefundResult.Reversed ->
                        bookReversal(application, result, source)

                    is TransferCoordinator.RefundResult.Cancelled ->
                        AppLog.d(TAG) { "转账已退回，作废那条待确认记录" }

                    TransferCoordinator.RefundResult.NotFound -> Unit
                }
            }
        }
    }

    private suspend fun bookTransfer(
        application: MyApplication,
        booking: TransferCoordinator.Booking,
        source: String
    ) {
        val transaction = Transaction(
            amount = booking.amountCents / 100.0,
            merchant = booking.merchant,
            source = source,
            timestamp = System.currentTimeMillis(),
            type = booking.kind,
            // 用待确认记录的 id 作为交易号：同一笔转账无论确认通知来几次都只入账一次
            transactionNo = "transfer:${booking.transferId}",
            dataSource = "notification",
            note = booking.note
        )
        val result = application.transactionRepository.insertDedup(transaction)
        if (!result.inserted) return

        withContext(Dispatchers.Main) {
            com.friday.wimm.ui.mascot.MascotService.notifyTransaction(
                context = this@PaymentNotificationListener,
                isIncome = booking.kind == "income",
                merchant = transaction.merchant
            )
            if (booking.amountCents <= 0) {
                notifyMissingAmount(
                    Draft(
                        amountCents = 0,
                        merchant = transaction.merchant,
                        kind = Kind.INCOME,
                        note = booking.note,
                        source = source,
                        fingerprint = "transfer:${booking.transferId}",
                        needsAmount = true
                    ),
                    result.id
                )
            } else {
                notifyTransactionRecorded(transaction, result.id)
            }
        }
    }

    private suspend fun bookReversal(
        application: MyApplication,
        reversed: TransferCoordinator.RefundResult.Reversed,
        source: String
    ) {
        val transaction = Transaction(
            amount = reversed.amountCents / 100.0,
            merchant = reversed.merchant.ifBlank { TransferCoordinator.UNKNOWN },
            source = source,
            timestamp = System.currentTimeMillis(),
            type = "income",
            transactionNo = "transfer-reverse:${reversed.transferId}",
            dataSource = "notification",
            note = "转账退回"
        )
        application.transactionRepository.insertDedup(transaction)
        AppLog.d(TAG) { "转账退回，已补一笔冲正" }
    }

    // ─────────────────────────────────────────────────────────
    // 通知反馈
    // ─────────────────────────────────────────────────────────

    private fun notifyMissingAmount(draft: Draft, transactionId: Long) {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = safeNotificationId(transactionId)
            val typeLabel = if (draft.kind == Kind.INCOME) "收入" else "支出"

            val remoteInput = RemoteInput.Builder(AmountReplyReceiver.KEY_TEXT_REPLY)
                .setLabel("输入金额 (如 88.5)")
                .build()

            val replyIntent = android.content.Intent(this, AmountReplyReceiver::class.java).apply {
                putExtra(AmountReplyReceiver.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(AmountReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(AmountReplyReceiver.EXTRA_TYPE, draft.kind.value)
                putExtra(AmountReplyReceiver.EXTRA_MERCHANT, draft.merchant)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                notificationId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_edit,
                "输入金额",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            val deleteAction = buildDeleteAction(transactionId, notificationId)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("检测到${typeLabel}交易")
                .setContentText("${draft.merchant} - 请在下方输入金额")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${draft.merchant}\n检测到${typeLabel}交易，请直接在通知中输入金额记录")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(launchAppIntent())
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .addAction(replyAction)
                .addAction(deleteAction)
                .build()

            manager.notify(notificationId, notification)
        } catch (e: Exception) {
            AppLog.e(TAG) { "发送补录提醒失败: ${e.javaClass.simpleName}" }
        }
    }

    private fun notifyTransactionRecorded(transaction: Transaction, transactionId: Long) {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = safeNotificationId(transactionId)
            val typeLabel = if (transaction.type == "income") "收入" else "支出"
            val amountText = com.friday.wimm.util.Money.format(transaction.amount)

            val deleteAction = buildDeleteAction(transactionId, notificationId)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("已记录${typeLabel}: ¥$amountText")
                .setContentText("${transaction.merchant} - 如误识别请点删除")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${transaction.merchant}\n已记录${typeLabel} ¥$amountText\n如误识别请点击下方删除按钮")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(launchAppIntent())
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(deleteAction)
                .build()

            manager.notify(notificationId, notification)
        } catch (e: Exception) {
            AppLog.e(TAG) { "发送记账提醒失败: ${e.javaClass.simpleName}" }
        }
    }

    private fun buildDeleteAction(transactionId: Long, notificationId: Int): NotificationCompat.Action {
        val deleteIntent = android.content.Intent(this, DeleteTransactionReceiver::class.java).apply {
            putExtra(DeleteTransactionReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(DeleteTransactionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_delete,
            "误识别·删除",
            PendingIntent.getBroadcast(
                this,
                notificationId + 1,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()
    }

    private fun launchAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun safeNotificationId(transactionId: Long): Int =
        (transactionId and 0x7FFFFFFF).toInt()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentNotification"
        const val CHANNEL_ID = "wimm_transaction_alert"

        /** 通知到达时，往前找多久内的"屏幕记录"来收编 */
        private const val CROSS_CHANNEL_WINDOW = 3 * 60 * 1000L

        /** 设备时间误差容忍 */
        private const val CLOCK_SKEW_ALLOWANCE = 60 * 1000L

        /** 渠道只创建一次，绝不删除重建，避免重置用户的声音/悬浮设置 */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "交易提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "交易金额提醒，支持悬浮通知"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
