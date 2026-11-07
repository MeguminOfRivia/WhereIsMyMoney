package com.friday.wimm.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
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
fun CardsScreen(
    onCardClick: (Card) -> Unit,
    onCreateCard: () -> Unit,
    onSubPageChanged: (Boolean) -> Unit
) {
    val application = LocalContext.current.applicationContext as MyApplication
    val cardRepository = remember { CardRepository(application.databaseHelper) }

    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf<Card?>(null) }

    // 动画状态 - 页面整体渐显
    var pageVisible by remember { mutableStateOf(false) }

    // 加载卡片（数据库读取放在 IO 线程）
    LaunchedEffect(Unit) {
        cards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            cardRepository.getAllCards()
        }
        isLoading = false
        kotlinx.coroutines.delay(50)
        pageVisible = true
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卡片管理") },
                actions = {
                    IconButton(onClick = onCreateCard) {
                        Icon(Icons.Default.Add, contentDescription = "创建卡片")
                    }
                }
            )
        }
    ) { padding ->
        AnimatedVisibility(
            visible = pageVisible,
            enter = fadeIn(tween(350, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "暂无卡片",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右上角 + 创建卡片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(cards) { _, card ->
                        CardItem(
                            card = card,
                            dateFormat = dateFormat,
                            onClick = { onCardClick(card) },
                            onDelete = { showDeleteDialog = card }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { card ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除卡片") },
            text = { Text("确定要删除卡片「${card.name}」吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    // 数据库操作放到 IO 线程
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        cardRepository.deleteCard(card.id)
                        val refreshed = cardRepository.getAllCards()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            cards = refreshed
                            showDeleteDialog = null
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CardItem(
    card: Card,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (card.isGlobal) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (card.isGlobal) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("全局", fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (card.isGlobal) {
                        "起始：${dateFormat.format(Date(card.startTime))}"
                    } else if (card.endTime > 0) {
                        "${dateFormat.format(Date(card.startTime))} ~ ${dateFormat.format(Date(card.endTime))}"
                    } else {
                        "起始：${dateFormat.format(Date(card.startTime))}（无终止）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!card.isGlobal) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
