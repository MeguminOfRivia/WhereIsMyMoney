package com.friday.wimm.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.friday.wimm.MyApplication
import com.friday.wimm.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 「这笔支出确认一下」通知上的两个按钮。
 *
 *  - 确认记账：记录本来就已经在账本里了，这里只需要把提醒消掉；
 *  - 误识别·删除：把那条记录删掉。
 *
 * 用户不理会这条通知时，记录保留，不做任何自动删除。
 */
class ReviewReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(ReviewNotifier.EXTRA_TRANSACTION_ID, -1)
        val notificationId = intent.getIntExtra(ReviewNotifier.EXTRA_NOTIFICATION_ID, 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ReviewNotifier.ACTION_DELETE -> {
                manager.cancel(notificationId)
                if (transactionId <= 0) return
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val application = context.applicationContext as MyApplication
                        application.transactionRepository.deleteTransaction(transactionId)
                        withMainToast(context, "已删除这条误识别记录")
                    } catch (e: Exception) {
                        AppLog.e(TAG) { "删除失败: ${e.javaClass.simpleName}" }
                    }
                }
            }

            ReviewNotifier.ACTION_CONFIRM -> {
                manager.cancel(notificationId)
                withMainToast(context, "已确认这笔支出")
            }

            else -> manager.cancel(notificationId)
        }
    }

    private fun withMainToast(context: Context, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "ReviewReceiver"
    }
}
