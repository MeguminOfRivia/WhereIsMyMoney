package com.friday.wimm.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.friday.wimm.MyApplication
import com.friday.wimm.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 「误识别·删除」按钮的处理器 */
class DeleteTransactionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DeleteTransactionRx"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val pendingResult = goAsync()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            PaymentNotificationListener.ensureChannel(context)
            manager.notify(
                notificationId,
                NotificationCompat.Builder(context, PaymentNotificationListener.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("")
                    .setContentText("")
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build()
            )
        } catch (e: Exception) {
            AppLog.e(TAG) { "替换通知失败: ${e.javaClass.simpleName}" }
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(100)
            try {
                manager.cancel(notificationId)
            } catch (e: Exception) {
                AppLog.e(TAG) { "取消通知失败: ${e.javaClass.simpleName}" }
            }
            pendingResult.finish()
        }

        if (transactionId <= 0) {
            AppLog.d(TAG) { "无效的交易 ID，忽略" }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as MyApplication
                application.transactionRepository.deleteTransaction(transactionId)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "已删除误识别记录", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e(TAG) { "删除失败: ${e.javaClass.simpleName}" }
            }
        }
    }
}
