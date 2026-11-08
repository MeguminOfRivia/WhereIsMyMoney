package com.friday.wimm.ui.file_analysis

import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.friday.wimm.data.database.ImportRecord
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.ui.import_screen.ImportViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileAnalysisScreen(
    importRecord: ImportRecord,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val application = LocalContext.current.applicationContext as MyApplication
    val viewModel: ImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ImportViewModel.Factory(application)
    )

    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showAllExpense by remember { mutableStateOf(false) }
    var showAllIncome by remember { mutableStateOf(false) }
    var showChartAnalysis by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(0) } // 0=金额降序, 1=金额升序, 2=时间降序, 3=时间升序
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    // 图表分析页面
    if (showChartAnalysis) {
        val merchantDataList = transactions.groupBy { it.merchant }
            .map { (merchant, txs) ->
                val byType = txs.groupBy { it.type }
                MerchantData(
                    merchant = merchant,
                    expenseTotal = byType["expense"]?.sumOf { it.amount } ?: 0.0,
                    incomeTotal = byType["income"]?.sumOf { it.amount } ?: 0.0,
                    expenseCount = byType["expense"]?.size ?: 0,
                    incomeCount = byType["income"]?.size ?: 0
                )
            }
        ChartAnalysisScreen(
            merchants = merchantDataList,
            onBack = { showChartAnalysis = false }
        )
        return
    }

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

    LaunchedEffect(importRecord.id) {
        transactions = viewModel.getTransactionsByFileId(importRecord.id)
        isLoading = false
        contentVisible = true
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val expenseCount = transactions.count { it.type == "expense" }
    val incomeCount = transactions.count { it.type == "income" }

    // 按收款方合并
    val merchantGroups = transactions.groupBy { it.merchant }
        .map { (merchant, txs) ->
            val byType = txs.groupBy { it.type }
            MerchantSummary(
                merchant = merchant,
                expenseTotal = byType["expense"]?.sumOf { it.amount } ?: 0.0,
                incomeTotal = byType["income"]?.sumOf { it.amount } ?: 0.0,
                expenseCount = byType["expense"]?.size ?: 0,
                incomeCount = byType["income"]?.size ?: 0,
                lastTimestamp = txs.maxOfOrNull { it.timestamp } ?: 0L
            )
        }

    // 均值计算
    val averages = remember(transactions) {
        if (transactions.isEmpty()) return@remember Triple(0.0, 0.0, 0.0)
        val minTime = transactions.minOf { it.timestamp }
        val maxTime = transactions.maxOf { it.timestamp }
        val diffDays = ((maxTime - minTime) / (1000L * 60 * 60 * 24)).coerceAtLeast(1)
        val diffMonths = (diffDays / 30.0).coerceAtLeast(1.0)
        val diffYears = (diffDays / 365.0).coerceAtLeast(1.0)
        Triple(totalExpense / diffDays, totalExpense / diffMonths, totalExpense / diffYears)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                // 文件信息
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(importRecord.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            Text("导入时间：${dateFormat.format(Date(importRecord.importTime))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("记录数：${importRecord.count} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text("¥${String.format("%.2f", totalExpense)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    Text("${expenseCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("收入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("¥${String.format("%.2f", totalIncome)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("${incomeCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("净收入：¥${String.format("%.2f", totalIncome - totalExpense)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (totalIncome - totalExpense >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // 均值
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("支出均值", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("日均", style = MaterialTheme.typography.bodySmall); Text("¥${String.format("%.2f", averages.first)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("月均", style = MaterialTheme.typography.bodySmall); Text("¥${String.format("%.2f", averages.second)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("年均", style = MaterialTheme.typography.bodySmall); Text("¥${String.format("%.2f", averages.third)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }

                // 图表分析按钮
                item {
                    Button(onClick = { showChartAnalysis = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("📊 图表分析", fontSize = 16.sp)
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
                val expenseList = merchantGroups.filter { it.expenseTotal > 0 }.let { list ->
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
                if (merchantGroups.any { it.incomeTotal > 0 }) {
                    item {
                        Text("收入排行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    val incomeList = merchantGroups.filter { it.incomeTotal > 0 }.sortedByDescending { it.incomeTotal }
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

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("将删除此文件的所有分析数据（${importRecord.count} 条记录），此操作不可恢复。确定要删除吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

private data class MerchantSummary(
    val merchant: String,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val expenseCount: Int,
    val incomeCount: Int,
    val lastTimestamp: Long = 0
)

@Composable
private fun MerchantRankItem(summary: MerchantSummary, isExpense: Boolean, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
