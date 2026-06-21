package com.friday.wimm.ui.cards

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.repository.CardRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCardScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val application = LocalContext.current.applicationContext as MyApplication
    val cardRepository = remember { CardRepository(application.databaseHelper) }
    val context = LocalContext.current

    var cardName by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var endTime by remember { mutableStateOf(0L) }
    var hasEndTime by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

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

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建卡片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .alpha(contentAlpha)
                .offset(y = contentOffsetY.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 卡片名称
            OutlinedTextField(
                value = cardName,
                onValueChange = { cardName = it },
                label = { Text("卡片名称") },
                placeholder = { Text("例如：日常开销、旅行基金") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 起始时间
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val calendar = Calendar.getInstance().apply { timeInMillis = startTime }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCalendar = Calendar.getInstance().apply {
                                    set(year, month, dayOfMonth, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                startTime = newCalendar.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("起始时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFormat.format(Date(startTime)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.DateRange, contentDescription = "选择日期", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // 是否设置终止时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("设置终止时间", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = hasEndTime, onCheckedChange = { hasEndTime = it })
            }

            // 终止时间（如果开启）
            if (hasEndTime) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val calendar = Calendar.getInstance().apply { timeInMillis = endTime.takeIf { it > 0 } ?: startTime }
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCalendar = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 23, 59, 59)
                                        set(Calendar.MILLISECOND, 999)
                                    }
                                    endTime = newCalendar.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("终止时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (endTime > 0) dateFormat.format(Date(endTime)) else "请选择日期",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(Icons.Default.DateRange, contentDescription = "选择日期", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 错误信息
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 创建按钮
            Button(
                onClick = {
                    if (cardName.isBlank()) {
                        errorMessage = "请输入卡片名称"
                        return@Button
                    }
                    if (hasEndTime && endTime <= startTime) {
                        errorMessage = "终止时间必须大于起始时间"
                        return@Button
                    }

                    val card = Card(
                        name = cardName.trim(),
                        startTime = startTime,
                        endTime = if (hasEndTime) endTime else 0,
                        isGlobal = false
                    )
                    cardRepository.insertCard(card)
                    onCreated()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("创建卡片", fontSize = 16.sp)
            }
        }
    }
}
