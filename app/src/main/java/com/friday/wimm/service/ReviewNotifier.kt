package com.friday.wimm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.friday.wimm.util.Money

/**
 * 「这笔支出确认一下」的静默询问通知。
 *
 * 只在「屏幕识别到一笔支付，但通知通道没有覆盖到」时出现，
 * 因为这种情况下无法确定它是真实消费还是误识别（比如你只是打开了付款码）。
 *
 * 设计要点：
 *  - 静默渠道（无声、无振动、不打扰）；
 *  - 先记账再询问，避免等用户响应期间丢失记录；
 *  - 用户不理会也不会自动删除，记录保留在账本里。
 */
object ReviewNotifier {

    const val CHANNEL_ID = "wimm_review"
    const val EXTRA_TRANSACTION_ID = "review_transaction_id"
    const val EXTRA_NOTIFICATION_ID = "review_notification_id"
    const val ACTION_CONFIRM = "com.friday.wimm.REVIEW_CONFIRM"
    const val ACTION_DELETE = "com.friday.wimm.REVIEW_DELETE"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "记账确认",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "屏幕识别到的交易需要你确认时，安静地提醒一下"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun showAsk(context: Context, transactionId: Long, amount: Double, merchant: String) {
        try {
            ensureChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = (transactionId and 0x7FFFFFFF).toInt() + 100_000

            val confirmIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, ReviewReceiver::class.java).apply {
                    action = ACTION_CONFIRM
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deleteIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                Intent(context, ReviewReceiver::class.java).apply {
                    action = ACTION_DELETE
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("这笔支出确认一下")
                .setContentText("¥${Money.format(amount)} · $merchant")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("刚刚在手机上识别到一笔支出：${merchant} ¥${Money.format(amount)}\n是真实消费吗？")
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .addAction(0, "确认记账", confirmIntent)
                .addAction(0, "误识别·删除", deleteIntent)
                .build()

            manager.notify(notificationId, notification)
        } catch (e: Exception) {
            com.friday.wimm.util.AppLog.e("ReviewNotifier") { "发送确认通知失败: ${e.javaClass.simpleName}" }
        }
    }
}
