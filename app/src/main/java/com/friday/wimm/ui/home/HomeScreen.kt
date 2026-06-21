package com.friday.wimm.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.model.Transaction
import com.friday.wimm.data.repository.CardRepository
import com.friday.wimm.data.repository.CardStatistics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as MyApplication
    val cardRepository = remember { CardRepository(application.databaseHelper) }
    val prefs = remember { context.getSharedPreferences("wimm_prefs", android.content.Context.MODE_PRIVATE) }

    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var statistics by remember { mutableStateOf<CardStatistics?>(null) }
    var pendingTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCardSelector by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editAmountText by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("amount_desc") } // amount_desc, amount_asc, time_desc
    var showAllRanking by remember { mutableStateOf(false) }
    // 编辑收款方名称
    var editingMerchant by remember { mutableStateOf<String?>(null) }
    var editMerchantText by remember { mutableStateOf("") }
    var editMerchantAmount by remember { mutableStateOf("") }
    // 删除确认
    var deletingMerchant by remember { mutableStateOf<String?>(null) }
    // 查看收款方详情
    var viewingMerchant by remember { mutableStateOf<Pair<String, List<Transaction>>?>(null) }
    // 编辑单笔交易
    var editingSingleTx by remember { mutableStateOf<Transaction?>(null) }
    var editSingleTxAmount by remember { mutableStateOf("") }

    // 动画状态 - 页面整体渐显
    var pageVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            cards = cardRepository.getAllCards()
            val savedCardId = prefs.getLong("selected_card_id", -1L)
            selectedCard = if (savedCardId > 0) {
                cards.firstOrNull { it.id == savedCardId }
            } else {
                null
            } ?: cards.firstOrNull { it.isGlobal } ?: cards.firstOrNull()
        }
        isLoading = false
        kotlinx.coroutines.delay(50)
        pageVisible = true
    }

    // 加载选中卡片的统计数据和待录入交易
    LaunchedEffect(selectedCard) {
        selectedCard?.let { card ->
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                statistics = cardRepository.getCardStatistics(card)
                pendingTransactions = application.transactionRepository.getPendingTransactionsByCard(card)
            }
        }
    }

    AnimatedVisibility(
        visible = pageVisible,
        enter = fadeIn(tween(350, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    ) {
        if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无卡片", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("请到「卡片」页创建卡片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // item 0: 卡片选择器
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCardSelector = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("当前卡片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                selectedCard?.name ?: "请选择卡片",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "选择", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // item 1: 统计概览
            val stats = statistics
            if (stats != null) {
                item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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

                // item 2: 均值
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

                // item 3: 待录入交易
                if (pendingTransactions.isNotEmpty()) {
                    item {
                            Text("待录入交易", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.error)
                    }
                    items(pendingTransactions) { transaction ->
                            PendingTransactionItem(
                                transaction = transaction,
                                onEdit = {
                                    editingTransaction = transaction
                                    editAmountText = ""
                                },
                                onDelete = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        application.transactionRepository.deleteTransaction(transaction.id)
                                        selectedCard?.let { card ->
                                            pendingTransactions = application.transactionRepository.getPendingTransactionsByCard(card)
                                        }
                                    }
                                }
                            )
                    }
                }

                // item 4: 最近交易
                if (stats.merchantSummaries.isNotEmpty()) {
                    item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("最近交易", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row {
                                    FilterChip(
                                        selected = sortBy == "amount_desc",
                                        onClick = { sortBy = "amount_desc" },
                                        label = { Text("金额↓", fontSize = 11.sp) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilterChip(
                                        selected = sortBy == "amount_asc",
                                        onClick = { sortBy = "amount_asc" },
                                        label = { Text("金额↑", fontSize = 11.sp) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilterChip(
                                        selected = sortBy == "time_desc",
                                        onClick = { sortBy = "time_desc" },
                                        label = { Text("时间↓", fontSize = 11.sp) }
                                    )
                                }
                            }
                    }
                    val sortedSummaries = when (sortBy) {
                        "amount_desc" -> stats.merchantSummaries.sortedByDescending { it.expenseTotal }
                        "amount_asc" -> stats.merchantSummaries.sortedBy { it.expenseTotal }
                        "time_desc" -> stats.merchantSummaries.sortedByDescending { it.lastTimestamp }
                        else -> stats.merchantSummaries.sortedByDescending { it.lastTimestamp }
                    }
                    val displaySummaries = if (showAllRanking) sortedSummaries else sortedSummaries.take(10)
                    itemsIndexed(displaySummaries) { idx, summary ->
                            RecentTransactionItem(
                                summary = summary,
                                onEdit = {
                                    editingMerchant = summary.merchant
                                    editMerchantText = summary.merchant
                                },
                                onDelete = { deletingMerchant = summary.merchant },
                                onViewDetails = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val txs = application.transactionRepository.getTransactionsByMerchant(summary.merchant)
                                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                                            viewingMerchant = Pair(summary.merchant, txs)
                                        }
                                    }
                                }
                            )
                    }
                    if (sortedSummaries.size > 10) {
                        item {
                            TextButton(
                                onClick = { showAllRanking = !showAllRanking },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showAllRanking) "收起" else "显示全部 (${sortedSummaries.size})")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    }

    // 卡片选择对话框
    if (showCardSelector) {
        AlertDialog(
            onDismissRequest = { showCardSelector = false },
            title = { Text("选择卡片") },
            text = {
                LazyColumn {
                    items(cards) { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCard = card
                                    showCardSelector = false
                                    prefs.edit().putLong("selected_card_id", card.id).apply()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(card.name, style = MaterialTheme.typography.bodyLarge)
                            if (card.id == selectedCard?.id) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCardSelector = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑金额对话框
    if (editingTransaction != null) {
        AlertDialog(
            onDismissRequest = { editingTransaction = null; editAmountText = "" },
            title = { Text("录入金额") },
            text = {
                Column {
                    Text("收款方：${editingTransaction!!.merchant}")
                    Text("类型：${if (editingTransaction!!.type == "income") "收入" else "支出"}")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editAmountText,
                        onValueChange = { editAmountText = it },
                        label = { Text("金额") },
                        placeholder = { Text("请输入金额") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = editAmountText.toDoubleOrNull()
                        if (amount != null && amount > 0) {
                            val txId = editingTransaction!!.id
                            val txAmount = amount
                            editingTransaction = null
                            editAmountText = ""
                            CoroutineScope(Dispatchers.IO).launch {
                                application.transactionRepository.updateAmount(txId, txAmount)
                                val card = selectedCard
                                val updated = if (card != null) {
                                    application.transactionRepository.getPendingTransactionsByCard(card)
                                } else {
                                    application.transactionRepository.getPendingTransactions()
                                }
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    pendingTransactions = updated
                                }
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTransaction = null; editAmountText = "" }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑收款方名称对话框
    editingMerchant?.let { merchant ->
        AlertDialog(
            onDismissRequest = { editingMerchant = null },
            title = { Text("编辑收款方") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editMerchantText,
                        onValueChange = { editMerchantText = it },
                        label = { Text("收款方名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMerchantAmount,
                        onValueChange = { editMerchantAmount = it },
                        label = { Text("修改金额（留空则不修改）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = editMerchantText.trim()
                    val newAmount = editMerchantAmount.trim().toDoubleOrNull()
                    if (newName.isNotEmpty()) {
                        editingMerchant = null
                        CoroutineScope(Dispatchers.IO).launch {
                            val txs = application.transactionRepository.getTransactionsByMerchant(merchant)
                            txs.forEach { tx ->
                                application.transactionRepository.updateTransaction(tx.id, merchant = newName)
                                if (newAmount != null && newAmount > 0) {
                                    application.transactionRepository.updateTransaction(tx.id, amount = newAmount)
                                }
                            }
                            selectedCard?.let { card ->
                                statistics = cardRepository.getCardStatistics(card)
                            }
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingMerchant = null }) { Text("取消") }
            }
        )
    }

    // 删除收款方确认对话框
    deletingMerchant?.let { merchant ->
        AlertDialog(
            onDismissRequest = { deletingMerchant = null },
            title = { Text("删除记录") },
            text = { Text("确定删除「$merchant」的所有交易记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    deletingMerchant = null
                    CoroutineScope(Dispatchers.IO).launch {
                        val txs = application.transactionRepository.getTransactionsByMerchant(merchant)
                        txs.forEach { tx ->
                            application.transactionRepository.deleteTransaction(tx.id)
                        }
                        selectedCard?.let { card ->
                            statistics = cardRepository.getCardStatistics(card)
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMerchant = null }) { Text("取消") }
            }
        )
    }

    // 收款方交易详情对话框
    viewingMerchant?.let { (merchant, txs) ->
        AlertDialog(
            onDismissRequest = { viewingMerchant = null },
            title = { Text(merchant) },
            text = {
                Column {
                    Text("共 ${txs.size} 笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("提示：点击单笔可修改金额", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.height(8.dp))
                    txs.take(20).forEach { tx ->
                        val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingSingleTx = tx
                                    editSingleTxAmount = if (tx.amount > 0) tx.amount.toString() else ""
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(df.format(Date(tx.timestamp)), style = MaterialTheme.typography.bodySmall)
                                Text(if (tx.type == "income") "收入" else "支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "¥${String.format("%.2f", tx.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == "income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp).padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (txs.size > 20) {
                        Text("...还有 ${txs.size - 20} 笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingMerchant = null }) { Text("关闭") }
            }
        )
    }

    // 单笔交易金额编辑对话框
    editingSingleTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { editingSingleTx = null },
            title = { Text("修改单笔金额") },
            text = {
                Column {
                    Text("收款方：${tx.merchant}", style = MaterialTheme.typography.bodyMedium)
                    Text("类型：${if (tx.type == "income") "收入" else "支出"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editSingleTxAmount,
                        onValueChange = { editSingleTxAmount = it },
                        label = { Text("金额") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editSingleTxAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        editingSingleTx = null
                        CoroutineScope(Dispatchers.IO).launch {
                            application.transactionRepository.updateTransaction(tx.id, amount = amount)
                            // 刷新查看详情的交易列表
                            val updated = application.transactionRepository.getTransactionsByMerchant(tx.merchant)
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                viewingMerchant = Pair(tx.merchant, updated)
                            }
                            // 刷新统计
                            selectedCard?.let { card ->
                                statistics = cardRepository.getCardStatistics(card)
                            }
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        editingSingleTx = null
                        CoroutineScope(Dispatchers.IO).launch {
                            application.transactionRepository.deleteTransaction(tx.id)
                            val updated = application.transactionRepository.getTransactionsByMerchant(tx.merchant)
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (updated.isEmpty()) {
                                    viewingMerchant = null
                                } else {
                                    viewingMerchant = Pair(tx.merchant, updated)
                                }
                            }
                            selectedCard?.let { card ->
                                statistics = cardRepository.getCardStatistics(card)
                            }
                        }
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editingSingleTx = null }) { Text("取消") }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecentTransactionItem(
    summary: com.friday.wimm.data.repository.MerchantSummary,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onViewDetails: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    var showSheet by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onViewDetails() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSheet = true
                }
            ),
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
                Text("${summary.expenseCount + summary.incomeCount}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.lastTimestamp > 0) {
                    Text(dateFormat.format(Date(summary.lastTimestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (summary.expenseTotal > 0) {
                    Text("-¥${String.format("%.2f", summary.expenseTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                if (summary.incomeTotal > 0) {
                    Text("+¥${String.format("%.2f", summary.incomeTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false }
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    summary.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("编辑（重命名/改金额）") },
                    selected = false,
                    onClick = { showSheet = false; onEdit() },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("查看详情") },
                    selected = false,
                    onClick = { showSheet = false; onViewDetails() },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    label = { Text("删除所有记录", color = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = { showSheet = false; onDelete() },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingTransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    if (transaction.type == "income") "收入" else "支出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(dateFormat.format(Date(transaction.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "录入金额", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
