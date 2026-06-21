package com.friday.wimm.ui.transaction

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionDetailScreen(
    card: Card,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as MyApplication
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editAmountText by remember { mutableStateOf("") }
    var editMerchantText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<Transaction?>(null) }
    var animatedItemCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        transactions = application.transactionRepository.getTransactionsByTimeRange(
            if (card.startTime > 0) card.startTime else 0,
            if (card.endTime > 0) card.endTime else System.currentTimeMillis()
        )
        isLoading = false
        repeat(4) {
            delay(60)
            animatedItemCount++
        }
    }

    fun refresh() {
        CoroutineScope(Dispatchers.IO).launch {
            val updated = application.transactionRepository.getTransactionsByTimeRange(
                if (card.startTime > 0) card.startTime else 0,
                if (card.endTime > 0) card.endTime else System.currentTimeMillis()
            )
            withContext(Dispatchers.Main) { transactions = updated }
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("交易详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 汇总信息
                item {
                    AnimatedVisibility(
                        visible = animatedItemCount > 0,
                        enter = fadeIn(tween(250, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) +
                                slideInVertically(tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { it / 4 }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("共 ${transactions.size} 笔记录", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // 按收款方分组统计
                val merchantGroups = transactions.groupBy { it.merchant }
                item {
                    AnimatedVisibility(
                        visible = animatedItemCount > 1,
                        enter = fadeIn(tween(250, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) +
                                slideInVertically(tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { it / 4 }
                    ) {
                        Text("收款方汇总", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                merchantGroups.forEach { (merchant, txs) ->
                    val totalExpense = txs.filter { it.type == "expense" }.sumOf { it.amount }
                    val totalIncome = txs.filter { it.type == "income" }.sumOf { it.amount }
                    item {
                        AnimatedVisibility(
                            visible = animatedItemCount > 2,
                            enter = fadeIn(tween(250, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) +
                                    slideInVertically(tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { it / 4 }
                        ) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(merchant, fontWeight = FontWeight.Medium)
                                        Text("${txs.size}笔", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (totalExpense > 0) Text("-¥${String.format("%.2f", totalExpense)}", color = MaterialTheme.colorScheme.error)
                                        if (totalIncome > 0) Text("+¥${String.format("%.2f", totalIncome)}", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                // 单条记录列表
                item {
                    AnimatedVisibility(
                        visible = animatedItemCount > 3,
                        enter = fadeIn(tween(250, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) +
                                slideInVertically(tween(300, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { it / 4 }
                    ) {
                        Text("所有记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                itemsIndexed(transactions) { idx, tx ->
                    AnimatedVisibility(
                        visible = animatedItemCount > 3,
                        enter = fadeIn(tween(250, delayMillis = idx * 50, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) +
                                slideInVertically(tween(300, delayMillis = idx * 50, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { it / 4 }
                    ) {
                        TransactionItem(
                            transaction = tx,
                            dateFormat = dateFormat,
                            onEdit = {
                                editingTransaction = tx
                                editAmountText = if (tx.amount > 0) tx.amount.toString() else ""
                                editMerchantText = tx.merchant
                            },
                            onDelete = { showDeleteConfirm = tx }
                        )
                    }
                }
            }
        }
    }

    // 编辑对话框
    editingTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { editingTransaction = null },
            title = { Text("编辑记录") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editMerchantText,
                        onValueChange = { editMerchantText = it },
                        label = { Text("收款方") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAmountText,
                        onValueChange = { editAmountText = it },
                        label = { Text("金额") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = editAmountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        editingTransaction = null
                        CoroutineScope(Dispatchers.IO).launch {
                            application.transactionRepository.updateTransaction(
                                tx.id, amount, editMerchantText
                            )
                            refresh()
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingTransaction = null }) { Text("取消") }
            }
        )
    }

    // 删除确认
    showDeleteConfirm?.let { tx ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除记录") },
            text = { Text("确定删除「${tx.merchant}」的这笔记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = null
                    CoroutineScope(Dispatchers.IO).launch {
                        application.transactionRepository.deleteTransaction(tx.id)
                        refresh()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TransactionItem(
    transaction: Transaction,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onEdit() }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.merchant, fontWeight = FontWeight.Medium)
                Text(dateFormat.format(Date(transaction.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (transaction.type == "income") "收入" else "支出",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (transaction.type == "income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "¥${String.format("%.2f", transaction.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == "income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
