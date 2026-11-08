package com.friday.wimm.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Account
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.LedgerEntry
import com.friday.wimm.data.model.MerchantRule
import com.friday.wimm.util.MerchantNames
import com.friday.wimm.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

/**
 * 单笔流水的编辑面板。
 *
 * 可以改：金额、收支、收款方、大类、账户、备注。
 *
 * 改大类时如果这个收款方以前记过，会追问一句「以后都改吗」——
 * 因为大多数时候用户是真的想一劳永逸（比如「美团」永远算日常饮食），
 * 但偶尔只是想纠正这一笔，所以两个选项都给。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditSheet(
    entry: LedgerEntry,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val app = LocalContext.current.applicationContext as MyApplication
    val ledgerDao = app.ledgerDao
    val financeDao = app.financeDao
    val scope = rememberCoroutineScope()

    val tx = entry.tx
    var amountText by remember { mutableStateOf(Money.format(Money.fromCents(entry.amountCents))) }
    var merchant by remember { mutableStateOf(tx.merchant) }
    var note by remember { mutableStateOf(tx.note) }
    var type by remember { mutableStateOf(if (tx.type == "income") "income" else "expense") }
    var categoryId by remember { mutableStateOf(tx.categoryId) }
    var accountId by remember { mutableStateOf(tx.accountId) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var askRule by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    LaunchedEffect(type) {
        categories = withContext(Dispatchers.IO) {
            financeDao.getCategories(kind = type)
        }
        // 切换收支方向后，原来选的大类可能不属于这一类，清掉免得存进错误分类
        if (categoryId != null && categories.none { it.id == categoryId }) {
            categoryId = null
        }
        accounts = withContext(Dispatchers.IO) { financeDao.getAllAccounts() }
    }

    fun save() {
        val cents = Money.parseCents(amountText)
        if (cents == null) {
            errorText = "金额要填一个大于 0 的数字"
            return
        }
        if (merchant.isBlank()) {
            errorText = "收款方不能为空"
            return
        }
        errorText = null
        scope.launch {
            withContext(Dispatchers.IO) {
                ledgerDao.updateEntry(
                    id = tx.id,
                    amountCents = cents,
                    merchant = merchant.trim(),
                    note = note.trim(),
                    accountId = accountId,
                    type = type
                )
                ledgerDao.updateCategory(tx.id, categoryId)
            }
            onChanged()
            val categoryChanged = categoryId != tx.categoryId
            if (categoryChanged && categoryId != null && !MerchantNames.isGeneric(merchant)) {
                askRule = true
            } else {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("编辑流水", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                entry.accountName + " · " + if (type == "income") "收入" else "支出",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("金额（元）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("收支方向", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == "expense",
                    onClick = { type = "expense" },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = type == "income",
                    onClick = { type = "income" },
                    label = { Text("收入") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("收款方 / 商户") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("大类", style = MaterialTheme.typography.labelMedium)
            ChipGrid(
                items = categories.map { it.id to it.name },
                selected = categoryId,
                onSelect = { categoryId = if (categoryId == it) null else it }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("账户", style = MaterialTheme.typography.labelMedium)
            ChipGrid(
                items = accounts.map { it.id to it.name },
                selected = accountId,
                onSelect = { accountId = it }
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth()
            )

            errorText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("保存") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { askDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (askRule) {
        val categoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "这个大类"
        AlertDialog(
            onDismissRequest = { askRule = false; onDismiss() },
            title = { Text("以后都算「$categoryName」吗？") },
            text = {
                Text("你已经把「$merchant」改到「$categoryName」。要让以后所有这个收款方的流水都自动归到这一类吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val cid = categoryId
                            if (cid != null) {
                                financeDao.upsertMerchantRule(
                                    MerchantRule(
                                        pattern = merchant.trim(),
                                        matchType = MerchantRule.MATCH_EXACT,
                                        categoryId = cid
                                    )
                                )
                                ledgerDao.bulkSetCategoryForMerchant(merchant.trim(), cid)
                            }
                        }
                        askRule = false
                        onChanged()
                        onDismiss()
                    }
                }) { Text("以后都改") }
            },
            dismissButton = {
                TextButton(onClick = { askRule = false; onDismiss() }) { Text("只改这笔") }
            }
        )
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("删除这笔？") },
            text = { Text("删除后不能恢复，也不会影响其他流水。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ledgerDao.deleteEntry(tx.id) }
                        askDelete = false
                        onChanged()
                        onDismiss()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("取消") } }
        )
    }
}

/** 一排会自动换行的小圆角选择按钮 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipGrid(
    items: List<Pair<Long, String>>,
    selected: Long?,
    onSelect: (Long) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}
