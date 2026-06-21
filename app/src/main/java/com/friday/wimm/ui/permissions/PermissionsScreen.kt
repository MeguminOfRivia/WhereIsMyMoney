package com.friday.wimm.ui.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onAllGranted: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isMiui = remember { isMiui() }

    // 返回键处理
    BackHandler {
        if (onBack != null) onBack() else onAllGranted()
    }

    // 可检测的权限状态
    var notificationGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var listenerEnabled by remember { mutableStateOf(checkNotificationListener(context)) }

    // 自动弹出通知权限请求
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 从设置返回时刷新状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = checkNotificationPermission(context)
                listenerEnabled = checkNotificationListener(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首次弹窗提示
    var showTipDialog by remember { mutableStateOf(onBack == null) }

    // 动画状态
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "contentAlpha"
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 60f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "contentOffsetY"
    )

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .alpha(contentAlpha)
                .offset(y = contentOffsetY.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "为了正常使用自动记账功能，请开启以下权限：",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 重要提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "重要提示",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "本应用通过监听微信、支付宝的通知消息自动识别交易。请确保：\n" +
                            "1. 微信的通知权限已开启\n" +
                            "2. 支付宝的通知权限已开启\n" +
                            "3. 微信「微信支付」的消息通知已开启\n\n" +
                            "如果收不到交易提醒，请检查以上设置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 1. 通知权限（可自动检测+请求）
            PermissionItem(
                title = "通知权限",
                description = "允许发送交易提醒通知",
                granted = notificationGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            // 2. 通知监听（可自动检测）
            PermissionItem(
                title = "通知监听",
                description = "监听微信/支付宝交易通知，自动记账",
                granted = listenerEnabled,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )

            // MIUI 专属权限（无法自动检测，只能引导跳转）
            if (isMiui) {
                Text(
                    "后台保活设置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // 悬浮通知 - 跳转到通知渠道设置
                MiuiPermissionItem(
                    title = "悬浮通知",
                    description = "交易提醒以弹窗形式显示（必须手动开启）",
                    onClick = { openMiuiNotificationChannel(context) }
                )

                // 自启动
                MiuiPermissionItem(
                    title = "自启动权限",
                    description = "允许app在后台自动启动",
                    onClick = { openMiuiAutoStart(context) }
                )

                // 后台弹出界面
                MiuiPermissionItem(
                    title = "后台弹出界面",
                    description = "允许在后台弹出通知和页面",
                    onClick = { openMiuiPermissionDetail(context) }
                )

                // 省电策略
                MiuiPermissionItem(
                    title = "省电策略",
                    description = "设置为无限制，防止被系统杀后台",
                    onClick = { openMiuiBatterySettings(context) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (onBack != null) {
                // 从设置页面进入，只显示返回按钮
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("返回设置")
                }
            } else {
                // 首次进入
                Button(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成，进入应用")
                }

                OutlinedButton(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("稍后设置，先看看")
                }
            }
        }
    }

    // 首次进入弹窗提示
    if (showTipDialog) {
        AlertDialog(
            onDismissRequest = { showTipDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text("自动记账说明") },
            text = {
                Text(
                    "本应用通过监听微信、支付宝的通知消息自动识别交易并记账。\n\n" +
                    "请确保以下条件：\n" +
                    "1. 开启本应用的通知监听权限\n" +
                    "2. 微信的通知权限已开启\n" +
                    "3. 支付宝的通知权限已开启\n" +
                    "4. 微信「微信支付」的消息通知已开启\n\n" +
                    "设置完成后，当微信/支付宝收到付款或收款消息时，本应用会自动记录。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showTipDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) {
                Icon(Icons.Default.Check, "已开启", tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onClick) { Text("去开启") }
            }
        }
    }
}

@Composable
private fun MiuiPermissionItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick) { Text("去设置") }
        }
    }
}

// ============ 工具方法 ============

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
}

private fun checkNotificationListener(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat.isNullOrBlank()) return false
    val myComponent = ComponentName(context, "com.friday.wimm.service.PaymentNotificationListener").flattenToString()
    return flat.contains(myComponent)
}

private fun isMiui(): Boolean {
    return try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        val value = method.invoke(null, "ro.miui.ui.version.name", "") as String
        value.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

// 跳转到 MIUI 通知渠道设置（交易提醒）
private fun openMiuiNotificationChannel(context: Context) {
    try {
        // 直接跳转到应用的通知渠道设置
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e2: Exception) { }
    }
}

private fun openMiuiAutoStart(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.securitycenter.ui.settings.AppStartupSettingsActivity"
            )
            putExtra("package", context.packageName)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent("miui.intent.action.OP_AUTO_START").apply {
                putExtra("package", context.packageName)
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            openAppDetails(context)
        }
    }
}

private fun openMiuiPermissionDetail(context: Context) {
    try {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionAppsModifyActivity"
            )
            putExtra("packageName", context.packageName)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppDetails(context)
    }
}

private fun openMiuiBatterySettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.power.ui.PowerSettingsActivity"
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    })
}
