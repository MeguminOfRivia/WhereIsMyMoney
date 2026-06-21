package com.friday.wimm.ui.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.friday.wimm.MyApplication
import com.friday.wimm.data.database.MerchantTotal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as MyApplication
    val viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(
            application.transactionRepository,
            application.categoryRepository
        )
    )

    // 每次页面可见时刷新数据
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val merchantTotals by viewModel.merchantTotals.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val expenseCount by viewModel.expenseCount.collectAsState()
    val incomeCount by viewModel.incomeCount.collectAsState()
    val filteredTransactions by viewModel.filteredTransactions.collectAsState()
    val timeRangeText by viewModel.timeRangeText.collectAsState()
    val averages by viewModel.averages.collectAsState()

    // 排行 Tab
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("总排行", "支出排行", "收入排行")

    // 日期选择器状态
    var showDatePicker by remember { mutableStateOf(false) }

    // 动画状态
    var cardVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var tabsVisible by remember { mutableStateOf(false) }
    var listVisible by remember { mutableStateOf(false) }

    val cardAlpha by animateFloatAsState(
        targetValue = if (cardVisible) 1f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "cardAlpha"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (cardVisible) 0f else 60f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "cardOffsetY"
    )
    val statsAlpha by animateFloatAsState(
        targetValue = if (statsVisible) 1f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "statsAlpha"
    )
    val statsOffsetY by animateFloatAsState(
        targetValue = if (statsVisible) 0f else 40f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "statsOffsetY"
    )
    val tabsAlpha by animateFloatAsState(
        targetValue = if (tabsVisible) 1f else 0f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "tabsAlpha"
    )

    LaunchedEffect(Unit) {
        cardVisible = true
        delay(200)
        statsVisible = true
        delay(200)
        tabsVisible = true
        delay(100)
        listVisible = true
    }

    val expenseTotals = remember(merchantTotals) { merchantTotals.filter { it.type == "expense" } }
    val incomeTotals = remember(merchantTotals) { merchantTotals.filter { it.type == "income" } }

    // 使用LazyColumn实现CollapsingToolbar效果
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 总金额卡片
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp)
                    .alpha(cardAlpha)
                    .offset(y = cardOffsetY.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("总支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("¥${String.format("%.2f", totalExpense)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("${expenseCount} 笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("总收入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("¥${String.format("%.2f", totalIncome)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text("${incomeCount} 笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Divider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("净结余", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val net = totalIncome - totalExpense
                        Text(
                            "${if (net >= 0) "+" else ""}¥${String.format("%.2f", net)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text("共 ${expenseCount + incomeCount} 笔交易", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 待补录提醒
        item {
            val pendingTransactions = remember(filteredTransactions) { filteredTransactions.filter { t -> t.amount <= 0 } }
            if (pendingTransactions.isNotEmpty()) {
                var showEditDialog by remember { mutableStateOf(false) }
                var editingTransaction by remember { mutableStateOf<com.friday.wimm.data.model.Transaction?>(null) }
                var editAmountText by remember { mutableStateOf("") }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "\u26A0\uFE0F 有 ${pendingTransactions.size} 笔交易待补录金额",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        for (tx in pendingTransactions.take(5)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                        MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tx.merchant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (tx.type == "income") "收入" else "支出",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        editingTransaction = tx
                                        editAmountText = ""
                                        showEditDialog = true
                                    }
                                ) {
                                    Text("补录金额", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (pendingTransactions.size > 5) {
                            Text(
                                "...还有 ${pendingTransactions.size - 5} 笔",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                if (showEditDialog && editingTransaction != null) {
                    AlertDialog(
                        onDismissRequest = { showEditDialog = false },
                        title = { Text("补录金额") },
                        text = {
                            Column {
                                Text("${editingTransaction!!.merchant} - ${if (editingTransaction!!.type == "income") "收入" else "支出"}")
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.OutlinedTextField(
                                    value = editAmountText,
                                    onValueChange = { editAmountText = it },
                                    label = { Text("金额") },
                                    prefix = { Text("¥") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                    ),
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val amount = editAmountText.toDoubleOrNull()
                                    if (amount != null && amount > 0) {
                                        viewModel.updateTransactionAmount(editingTransaction!!.id, amount)
                                        showEditDialog = false
                                    }
                                }
                            ) { Text("确认") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { showEditDialog = false }
                            ) { Text("取消") }
                        }
                    )
                }
            }
        }

        // 时间范围选择按钮 + 刷新按钮
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .alpha(statsAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    content = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "时间",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = timeRangeText,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                // 刷新按钮
                IconButton(
                    onClick = { viewModel.refreshData() },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 均值统计卡片
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .alpha(statsAlpha)
                    .offset(y = statsOffsetY.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        Text(
                            text = "支出均值分析",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = averages.timeRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("日均", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${String.format("%.2f", averages.daily)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("月均", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${String.format("%.2f", averages.monthly)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("年均", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${String.format("%.2f", averages.yearly)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 排行 Tab
        stickyHeader {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .alpha(tabsAlpha)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }
        }

        // 排序选项 - 带动画
        item {
            val sortAlpha by animateFloatAsState(
                targetValue = if (tabsVisible) 1f else 0f,
                animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
                label = "sortAlpha"
            )
            val sortOffsetY by animateFloatAsState(
                targetValue = if (tabsVisible) 0f else 20f,
                animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
                label = "sortOffsetY"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .alpha(sortAlpha)
                    .offset(y = sortOffsetY.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "排序：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = sortMode == 0,
                    onClick = { viewModel.setSortMode(0) },
                    label = { Text("金额", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = sortMode == 1,
                    onClick = { viewModel.setSortMode(1) },
                    label = { Text("时间", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                // 正序/倒序按钮
                IconButton(
                    onClick = { viewModel.toggleSortDirection() },
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (sortAscending) "正序" else "倒序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 滑动切换的列表（每个页面）
        item {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val list = when (page) {
                    0 -> merchantTotals
                    1 -> expenseTotals
                    2 -> incomeTotals
                    else -> merchantTotals
                }
                Column {
                    if (list.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "暂无数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        list.take(30).forEachIndexed { index, merchantTotal ->
                            AnimatedStatsItem(
                                merchantTotal = merchantTotal,
                                index = index,
                                visible = listVisible
                            )
                        }
                    }
                }
            }
        }
    }

    // Toast消息监听
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 日期范围选择器（简化UI）
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { startTime, endTime ->
                viewModel.setTimeRange(startTime, endTime)
                showDatePicker = false
            }
        )
    }
}

@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    // 快速选择选项
    val quickOptions = listOf(
        "本月", "上月", "近3个月", "近半年", "近一年"
    )
    var selectedOption by remember { mutableStateOf(-1) }

    // 自定义日期输入
    var showCustomInput by remember { mutableStateOf(false) }
    var startYear by remember { mutableStateOf("2025") }
    var startMonth by remember { mutableStateOf("1") }
    var startDay by remember { mutableStateOf("1") }
    var endYear by remember { mutableStateOf("2026") }
    var endMonth by remember { mutableStateOf("6") }
    var endDay by remember { mutableStateOf("20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (showCustomInput) {
                TextButton(
                    onClick = {
                        try {
                            val cal = java.util.Calendar.getInstance()
                            cal.set(startYear.toInt(), startMonth.toInt() - 1, startDay.toInt(), 0, 0, 0)
                            val start = cal.timeInMillis
                            cal.set(endYear.toInt(), endMonth.toInt() - 1, endDay.toInt(), 23, 59, 59)
                            val end = cal.timeInMillis
                            if (end > start) {
                                onConfirm(start, end)
                            }
                        } catch (e: Exception) {
                            // 忽略无效输入
                        }
                    }
                ) {
                    Text("确认")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = {
            Text(
                text = "选择时间范围",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // 快速选择
                Text(
                    text = "快速选择",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                quickOptions.forEachIndexed { index, option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            selectedOption = index
                            val cal = java.util.Calendar.getInstance()
                            val now = java.util.Calendar.getInstance()

                            when (option) {
                                "本月" -> {
                                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                }
                                "上月" -> {
                                    cal.add(java.util.Calendar.MONTH, -1)
                                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                }
                                "近3个月" -> {
                                    cal.add(java.util.Calendar.MONTH, -3)
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                }
                                "近半年" -> {
                                    cal.add(java.util.Calendar.MONTH, -6)
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                }
                                "近一年" -> {
                                    cal.add(java.util.Calendar.YEAR, -1)
                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    cal.set(java.util.Calendar.MINUTE, 0)
                                    cal.set(java.util.Calendar.SECOND, 0)
                                }
                            }
                            onConfirm(cal.timeInMillis, now.timeInMillis)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedOption == index)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 自定义日期切换
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showCustomInput = !showCustomInput },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = if (showCustomInput) "收起自定义日期" else "自定义日期范围",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 自定义日期输入
                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "开始日期",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startYear,
                            onValueChange = { startYear = it },
                            label = { Text("年") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startMonth,
                            onValueChange = { startMonth = it },
                            label = { Text("月") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startDay,
                            onValueChange = { startDay = it },
                            label = { Text("日") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "结束日期",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = endYear,
                            onValueChange = { endYear = it },
                            label = { Text("年") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endMonth,
                            onValueChange = { endMonth = it },
                            label = { Text("月") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endDay,
                            onValueChange = { endDay = it },
                            label = { Text("日") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun AnimatedStatsItem(
    merchantTotal: MerchantTotal,
    index: Int,
    visible: Boolean
) {
    val shouldAnimate = index < 8
    var itemVisible by remember { mutableStateOf(!shouldAnimate) }

    LaunchedEffect(visible) {
        if (visible && shouldAnimate) {
            delay(index * 50L)
            itemVisible = true
        }
    }

    val itemAlpha by animateFloatAsState(
        targetValue = if (itemVisible || !shouldAnimate) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "itemAlpha"
    )
    val itemOffsetY by animateFloatAsState(
        targetValue = if (itemVisible || !shouldAnimate) 0f else 40f,
        animationSpec = tween(400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "itemOffsetY"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(itemAlpha)
            .offset(y = itemOffsetY.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        MerchantTotalItem(merchantTotal)
    }
}

@Composable
fun MerchantTotalItem(merchantTotal: MerchantTotal) {
    val isIncome = merchantTotal.type == "income"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(
                text = merchantTotal.merchant,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isIncome) "收入" else "支出",
                style = MaterialTheme.typography.bodySmall,
                color = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.padding(4.dp))
        Text(
            text = "${if (isIncome) "+" else "-"}¥${String.format("%.2f", merchantTotal.total)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
        )
    }
}
