package com.friday.wimm.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.friday.wimm.data.model.CardCategory
import com.friday.wimm.data.repository.CardRepository
import com.friday.wimm.data.repository.CardStatistics
import com.friday.wimm.data.repository.CategoryStatistics
import com.friday.wimm.data.repository.MerchantSummary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    card: Card,
    onBack: () -> Unit,
    onManageCategories: () -> Unit
) {
    val application = LocalContext.current.applicationContext as MyApplication
    val cardRepository = remember { CardRepository(application.databaseHelper) }

    var statistics by remember { mutableStateOf<CardStatistics?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAllExpense by remember { mutableStateOf(false) }
    var showAllIncome by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(0) }

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

    // 加载统计数据
    LaunchedEffect(card.id) {
        statistics = cardRepository.getCardStatistics(card)
        isLoading = false
        contentVisible = true
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val stats = statistics
            if (stats == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载失败", color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .alpha(contentAlpha)
                        .offset(y = contentOffsetY.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 卡片信息
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                Text(
                                    text = if (card.isGlobal) "全局卡片" else if (card.endTime > 0) "有终止时间" else "无终止时间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 统计概览
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("统计概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("¥${String.format("%.2f", stats.totalExpense)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                        Text("${stats.expenseCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("收入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("¥${String.format("%.2f", stats.totalIncome)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("${stats.incomeCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("净收入：¥${String.format("%.2f", stats.totalIncome - stats.totalExpense)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (stats.totalIncome - stats.totalExpense >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // 均值
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("支出均值", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("日均", style = MaterialTheme.typography.bodySmall); Text("¥${String.format("%.2f", stats.dailyAverage)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("月均", style = MaterialTheme.typography.bodySmall); Text("¥${String.format("%.2f", stats.monthlyAverage)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("年均", style = MaterialTheme.typography.bodySmall); Text("¥${String.format("%.2f", stats.yearlyAverage)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }

                    // 排序选项
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("支出排行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row {
                                FilterChip(selected = sortMode == 0, onClick = { sortMode = 0 }, label = { Text("金额↓", fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp))
                                FilterChip(selected = sortMode == 1, onClick = { sortMode = 1 }, label = { Text("金额↑", fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp))
                                FilterChip(selected = sortMode == 2, onClick = { sortMode = 2 }, label = { Text("时间↓", fontSize = 12.sp) })
                            }
                        }
                    }

                    // 支出排行
                    val expenseList = stats.merchantSummaries.filter { it.expenseTotal > 0 }.let { list ->
                        when (sortMode) {
                            0 -> list.sortedByDescending { it.expenseTotal }
                            1 -> list.sortedBy { it.expenseTotal }
                            2 -> list.sortedByDescending { it.lastTimestamp }
                            else -> list.sortedByDescending { it.expenseTotal }
                        }
                    }
                    val displayExpense = if (showAllExpense) expenseList else expenseList.take(10)
                    items(displayExpense) { summary ->
                        MerchantRankItem(summary = summary, isExpense = true, dateFormat = dateFormat)
                    }
                    if (expenseList.size > 10) {
                        item {
                            TextButton(onClick = { showAllExpense = !showAllExpense }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (showAllExpense) "收起" else "显示全部（共${expenseList.size}项）")
                            }
                        }
                    }

                    // 收入排行
                    if (stats.merchantSummaries.any { it.incomeTotal > 0 }) {
                        item {
                            Text("收入排行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                        val incomeList = stats.merchantSummaries.filter { it.incomeTotal > 0 }.sortedByDescending { it.incomeTotal }
                        val displayIncome = if (showAllIncome) incomeList else incomeList.take(10)
                        items(displayIncome) { summary ->
                            MerchantRankItem(summary = summary, isExpense = false, dateFormat = dateFormat)
                        }
                        if (incomeList.size > 10) {
                            item {
                                TextButton(onClick = { showAllIncome = !showAllIncome }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (showAllIncome) "收起" else "显示全部（共${incomeList.size}项）")
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryStatItem(categoryStat: CategoryStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(categoryStat.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${categoryStat.transactionCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (categoryStat.expenseTotal > 0) {
                    Text("支出 ¥${String.format("%.2f", categoryStat.expenseTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                if (categoryStat.incomeTotal > 0) {
                    Text("收入 ¥${String.format("%.2f", categoryStat.incomeTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MerchantRankItem(summary: MerchantSummary, isExpense: Boolean, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (summary.lastTimestamp > 0) {
                    Text(dateFormat.format(Date(summary.lastTimestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${if (isExpense) summary.expenseCount else summary.incomeCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "¥${String.format("%.2f", if (isExpense) summary.expenseTotal else summary.incomeTotal)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
