package com.friday.wimm.ui.settings

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 设置页能跳去的子页面 */
object SettingsRoutes {
    const val MASCOT = "mascot"
    const val IMPORT = "import"
    const val CATEGORIES = "categories"
}

/**
 * 设置页。
 *
 * Wally（角色形象）、账单导入、大类与规则都收在这里，
 * 底部导航只留「首页 / 账户 / 统计 / 预算 / 设置」五个入口。
 */
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    onSubPageChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showAutoRecordPage by remember { mutableStateOf(false) }
    var showPermissionsPage by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "settingsAlpha"
    )

    LaunchedEffect(Unit) { visible = true }

    if (showAutoRecordPage) {
        LaunchedEffect(Unit) { onSubPageChanged(true) }
        AutoRecordScreen(onBack = {
            showAutoRecordPage = false
            onSubPageChanged(false)
        })
        return
    }

    if (showPermissionsPage) {
        LaunchedEffect(Unit) { onSubPageChanged(true) }
        com.friday.wimm.ui.permissions.PermissionsScreen(
            onAllGranted = {
                showPermissionsPage = false
                onSubPageChanged(false)
            },
            onBack = {
                showPermissionsPage = false
                onSubPageChanged(false)
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SettingsEntry(
                title = "自动识别",
                subtitle = "通知监听 / 无障碍服务，决定自动记账灵不灵",
                onClick = { showAutoRecordPage = true }
            )
        }
        item {
            SettingsEntry(
                title = "权限设置",
                subtitle = "通知、自启动、悬浮窗等权限",
                onClick = { showPermissionsPage = true }
            )
        }
        item {
            SettingsEntry(
                title = "大类与收款方规则",
                subtitle = "自定义分类，让「美团」自动算进日常饮食",
                onClick = { onNavigate(SettingsRoutes.CATEGORIES) }
            )
        }
        item {
            SettingsEntry(
                title = "账单导入",
                subtitle = "导入微信 / 支付宝账单，自动识别的漏账也能补上",
                onClick = { onNavigate(SettingsRoutes.IMPORT) }
            )
        }
        item {
            SettingsEntry(
                title = "Wally 形象",
                subtitle = "换照片或 GIF，调弹跳、贴边、点击反馈",
                onClick = { onNavigate(SettingsRoutes.MASCOT) }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("隐私", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text(
                        "所有账目都只存在这台手机里。应用没有联网权限，不上传、不备份、不统计。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("关于", fontWeight = FontWeight.Bold)
                    Text("我钱呢？！ v1.0-debug", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "测试版：还在改，账目请自行核对",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "一个只在本地记账的应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://wqn.netlify.app/")
                    )
                    runCatching { context.startActivity(intent) }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("支持作者", fontWeight = FontWeight.Bold)
                        Text(
                            "如果对你有帮助，欢迎请作者喝杯咖啡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                ">",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
