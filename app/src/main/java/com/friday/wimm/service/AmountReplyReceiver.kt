package com.friday.wimm.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.friday.wimm.MyApplication
import com.friday.wimm.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「在通知栏里直接补录金额」的处理器。
 *
 * 注意：这里不再打印金额、商户或用户输入内容。
 */
class AmountReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AmountReplyReceiver"
        const val KEY_TEXT_REPLY = "key_amount_reply"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_MERCHANT = "extra_merchant"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val amountText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "expense"
        val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: "未知"

        val pendingResult = goAsync()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 先用一条空通知替换，让 RemoteInput 的「发送中」状态结束，再取消
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

        if (amountText.isNullOrBlank()) {
            AppLog.d(TAG) { "用户未输入金额" }
            return
        }

        val amount = amountText.replace("[¥￥,，\\s]".toRegex(), "").toDoubleOrNull()
        if (amount == null || amount <= 0) {
            AppLog.d(TAG) { "金额格式无法解析" }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as MyApplication
                if (transactionId > 0) {
                    application.transactionRepository.updateAmount(transactionId, amount)
                } else {
                    application.transactionRepository.insert(
                        com.friday.wimm.data.model.Transaction(
                            amount = amount,
                            type = type,
                            merchant = merchant,
                            source = "notification",
                            note = "通知补录",
                            timestamp = System.currentTimeMillis(),
                            dataSource = "notification"
                        )
                    )
                }
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "¥${com.friday.wimm.util.Money.format(amount)} 已录入",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e(TAG) { "保存补录金额失败: ${e.javaClass.simpleName}" }
            }
        }
    }
}
