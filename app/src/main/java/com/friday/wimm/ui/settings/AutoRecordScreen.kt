package com.friday.wimm.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    // 检测状态
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var notificationListenerEnabled by remember { mutableStateOf(false) }

    // 刷新状态的函数
    fun refreshStatus() {
        accessibilityEnabled = isAccessibilityServiceEnabled(context)
        notificationListenerEnabled = isNotificationListenerEnabled(context)
    }

    // 生命周期监听 - 每次页面可见时刷新
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 首次加载 + 每次重组时刷新
    LaunchedEffect(Unit) {
        refreshStatus()
    }

    // 动画
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(400), label = "contentAlpha"
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 40f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "contentOffsetY"
    )
    LaunchedEffect(Unit) { contentVisible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动识别") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新状态")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .alpha(contentAlpha)
                .offset(y = contentOffsetY.dp)
                .padding(16.dp)
        ) {
            // 功能选择说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "如何选择？",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• 通知监听：推荐！轻量级，只需读取通知栏信息\n" +
                                "• 无障碍服务：可识别支付页面详情，但部分MIUI设备可能无法读取\n" +
                                "• 两个功能可同时开启，数据会自动去重\n\n" +
                                "注意：小米手机需额外开启\"交易提醒\"悬浮通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 通知监听
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "通知监听",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (notificationListenerEnabled) "已开启" else "未开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (notificationListenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = notificationListenerEnabled,
                            onCheckedChange = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "通过监听微信/支付宝的支付通知，自动记录交易信息。" +
                                "只需读取通知栏内容，不会获取你的账号密码等隐私信息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "开启步骤：",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "1. 点击上方开关，跳转到系统设置\n" +
                                "2. 找到\"我钱呢？！\"\n" +
                                "3. 打开右侧开关\n" +
                                "4. 确认允许\n" +
                                "5. 返回本应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // MIUI 悬浮通知指南
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "小米手机还需开启悬浮通知：",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "1. 打开手机 设置 → 通知与状态栏\n" +
                                        "2. 找到\"我钱呢？！\" → 通知权限设置\n" +
                                        "3. 点击\"通知类别\"下的\"交易提醒\"\n" +
                                        "4. 开启\"悬浮通知\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // 无障碍服务
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "无障碍服务",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (accessibilityEnabled) "已开启" else "未开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Switch(
                            checked = accessibilityEnabled,
                            onCheckedChange = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "通过无障碍服务自动识别微信/支付宝的支付成功页面，" +
                                "可获取更详细的交易信息（如收款方、订单号等）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "开启步骤：",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "1. 点击上方开关，跳转到系统设置\n" +
                                "2. 点击\"已下载的应用\"\n" +
                                "3. 找到\"我钱呢？！\"\n" +
                                "4. 打开右侧开关\n" +
                                "5. 点击\"确认\"授权\n" +
                                "6. 返回本应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 保活提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "如果无障碍服务被系统自动关闭，请尝试：",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "1. 关闭本应用的电池优化（推荐）\n" +
                                        "2. 开启\"自启动\"权限（小米/华为/OPPO）\n" +
                                        "3. 锁定最近任务中的本应用卡片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            // 跳转电池优化设置
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        )
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("打开电池优化设置")
                            }
                        }
                    }
                }
            }

            // 隐私说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "隐私说明",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• 仅用于查看通知录入记录\n" +
                                "• 看不到你的具体信息\n" +
                                "• 没有服务器，不存在数据泄露\n" +
                                "• 所有数据仅存储在本地",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    // 方法1：使用 AccessibilityManager（推荐，不需要特殊权限）
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
    )
    for (service in enabledServices) {
        val serviceName = service.resolveInfo.serviceInfo.name
        if (serviceName == "com.friday.wimm.service.PaymentAccessibilityService") {
            return true
        }
    }

    // 方法2：读取系统设置（兜底）
    try {
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        if (enabledServicesSetting.contains("PaymentAccessibilityService", ignoreCase = true)) {
            return true
        }
    } catch (_: Exception) {}

    return false
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(
        context,
        com.friday.wimm.service.PaymentNotificationListener::class.java
    ).flattenToString()
    val enabledListenersSetting = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListenersSetting.contains(expectedComponentName)
}
