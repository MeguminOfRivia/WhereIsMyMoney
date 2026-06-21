package com.friday.wimm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.ComponentName
import android.text.TextUtils
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "service_status"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 检查无障碍服务是否被关闭
            if (!isAccessibilityServiceEnabled(context)) {
                showReEnableNotification(context, "无障碍服务", Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(
            context,
            PaymentAccessibilityService::class.java
        ).flattenToString()
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun showReEnableNotification(context: Context, serviceName: String, settingsAction: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "服务状态提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "提醒您重新开启自动记账服务"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val settingsIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(settingsAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("自动记账服务已关闭")
            .setContentText("设备重启后${serviceName}已关闭，点击重新开启")
            .setContentIntent(settingsIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
