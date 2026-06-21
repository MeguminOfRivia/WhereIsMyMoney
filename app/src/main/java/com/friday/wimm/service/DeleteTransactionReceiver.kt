package com.friday.wimm.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.friday.wimm.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeleteTransactionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DeleteTransactionRx"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        Log.d(TAG, "删除交易: id=$transactionId, notificationId=$notificationId")

        val pendingResult = goAsync()

        // 第一步：用空通知替换原通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            val emptyNotification = NotificationCompat.Builder(context, "wimm_transaction_alert")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            notificationManager.notify(notificationId, emptyNotification)
        } catch (e: Exception) {
            Log.e(TAG, "替换通知失败", e)
        }

        // 第二步：延迟100ms后取消通知
        CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(100)
                notificationManager.cancel(notificationId)
                Log.d(TAG, "已取消通知: $notificationId")
            } catch (e: Exception) {
                Log.e(TAG, "取消通知失败", e)
            }
            pendingResult.finish()
        }

        if (transactionId <= 0) {
            Log.d(TAG, "无效交易ID")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as MyApplication
                application.transactionRepository.deleteTransaction(transactionId)
                Log.d(TAG, "删除成功: id=$transactionId")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "已删除误识别记录", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除失败", e)
            }
        }
    }
}
