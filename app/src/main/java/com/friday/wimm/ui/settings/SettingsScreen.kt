package com.friday.wimm.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(onSubPageChanged: (Boolean) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAutoRecordPage by remember { mutableStateOf(false) }
    var showPermissionsPage by remember { mutableStateOf(false) }

    // 子页面
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

    // 动画状态
    var titleVisible by remember { mutableStateOf(false) }
    var card1Visible by remember { mutableStateOf(false) }
    var card2Visible by remember { mutableStateOf(false) }

    // 标题动画
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "titleAlpha"
    )
    val titleOffsetX by animateFloatAsState(
        targetValue = if (titleVisible) 0f else -50f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "titleOffsetX"
    )

    // 卡片动画
    val card1Alpha by animateFloatAsState(
        targetValue = if (card1Visible) 1f else 0f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "card1Alpha"
    )
    val card1OffsetY by animateFloatAsState(
        targetValue = if (card1Visible) 0f else 80f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "card1OffsetY"
    )

    val card2Alpha by animateFloatAsState(
        targetValue = if (card2Visible) 1f else 0f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "card2Alpha"
    )
    val card2OffsetY by animateFloatAsState(
        targetValue = if (card2Visible) 0f else 80f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "card2OffsetY"
    )

    // 动画编排
    LaunchedEffect(Unit) {
        titleVisible = true
        delay(200)
        card1Visible = true
        delay(200)
        card2Visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题 - 从左侧滑入
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .alpha(titleAlpha)
                .offset(x = titleOffsetX.dp)
        )

        // 自动识别 - 可跳转入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .alpha(card1Alpha)
                .offset(y = card1OffsetY.dp),
            onClick = { showAutoRecordPage = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动识别",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "通知监听 / 无障碍服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 权限设置 - 可跳转入口
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .alpha(card2Alpha)
                .offset(y = card2OffsetY.dp),
            onClick = { showPermissionsPage = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "权限设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "通知、自启动、悬浮通知等权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 关于
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .alpha(card2Alpha)
                .offset(y = card2OffsetY.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "我钱呢？！ v1.0",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "一个简洁的记账应用",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 支持作者
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(card2Alpha)
                .offset(y = card2OffsetY.dp),
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wqn.netlify.app/"))
                context.startActivity(intent)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "支持作者",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "如果对你有帮助，欢迎请作者喝杯咖啡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
