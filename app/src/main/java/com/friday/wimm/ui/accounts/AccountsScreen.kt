package com.friday.wimm.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Account
import com.friday.wimm.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AccountRow(val account: Account, val balanceCents: Long)

/**
 * 账户列表：总资产 + 每个账户的余额。
 *
 * 余额口径：初始余额 + 起始时间之后的收入 − 支出（见 FinanceDao.getAccountBalanceCents）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(refreshKey: Int = 0) {
    val context = LocalContext.current
    val dao = (context.applicationContext as MyApplication).financeDao
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<AccountRow>>(emptyList()) }
    var totalCents by remember { mutableLongStateOf(0L) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountRow?>(null) }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            dao.getAllAccounts(includeArchived = true).map { AccountRow(it, dao.getAccountBalanceCents(it.id)) }
        }
        rows = loaded
        totalCents = loaded.filter { !it.account.archived }.sumOf { it.balanceCents }
    }

    LaunchedEffect(refreshKey) { reload() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("账户") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("总资产", style = MaterialTheme.typography.bodySmall)
                    Text(
                        Money.formatSignedCents(totalCents),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "含花呗等负余额账户",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rows) { row ->
                    AccountCard(row) { editing = row }
                }
                item {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.height(0.dp))
                        Text("  添加账户")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, initialCents ->
                showAddDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        dao.insertAccount(
                            Account(
                                name = name,
                                type = type,
                                initialBalanceCents = initialCents,
                                balanceStartAt = System.currentTimeMillis()
                            )
                        )
                    }
                    reload()
                }
            }
        )
    }

    editing?.let { row ->
        EditAccountDialog(
            row = row,
            onDismiss = { editing = null },
            onSave = { name, type, initialCents ->
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        dao.updateAccount(
                            row.account.copy(
                                name = name,
                                type = type,
                                initialBalanceCents = initialCents
                            )
                        )
                    }
                    reload()
                }
            },
            onToggleArchive = {
                val target = !row.account.archived
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setAccountArchived(row.account.id, target) }
                    reload()
                }
            },
            onDelete = {
                editing = null
                scope.launch {
                    withContext(Dispatchers.IO) { dao.deleteAccountPermanently(row.account.id) }
                    reload()
                }
            }
        )
    }
}

@Composable
private fun AccountCard(row: AccountRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(
                    row.account.name + if (row.account.archived) "（已归档）" else "",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (row.account.archived) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                // 账户名本来就等于类型名时（微信 / 支付宝），就不要重复显示两行了
                val typeLabel = Account.typeLabel(row.account.type)
                if (typeLabel != row.account.name) {
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                Money.formatSignedCents(row.balanceCents),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    row.account.archived -> MaterialTheme.colorScheme.onSurfaceVariant
                    row.balanceCents < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * 编辑账户：改名字、改类型、改初始余额，或者归档 / 彻底删除。
 *
 * 改初始余额是「我一开始就记错了」的唯一补救办法，所以放在最显眼的位置。
 */
@Composable
private fun EditAccountDialog(
    row: AccountRow,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, initialCents: Long) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(row.account.name) }
    var type by remember { mutableStateOf(row.account.type) }
    var balanceText by remember { mutableStateOf(Money.formatCents(row.account.initialBalanceCents)) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("彻底删除「${row.account.name}」？") },
            text = { Text("这个账户名下的所有流水也会一起删掉，不能恢复。如果只是不想再用，建议「归档」。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("类型", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Account.TYPE_WECHAT to "微信",
                        Account.TYPE_ALIPAY to "支付宝",
                        Account.TYPE_BANK to "银行卡",
                        Account.TYPE_CASH to "现金",
                        Account.TYPE_CREDIT to "信用",
                        Account.TYPE_OTHER to "其他"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = type == value,
                            onClick = { type = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("初始余额（可为负，如花呗欠款）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "当前余额 " + Money.formatSignedCents(row.balanceCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onToggleArchive) {
                        Text(if (row.account.archived) "恢复使用" else "归档（不再使用）")
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("彻底删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        // 允许 0 和负数：花呗欠款就是负的
                        val cents = Money.parseSignedCents(balanceText) ?: 0L
                        onSave(name.trim(), type, cents)
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, initialCents: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Account.TYPE_WECHAT) }
    var balanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("类型", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Account.TYPE_WECHAT to "微信",
                        Account.TYPE_ALIPAY to "支付宝",
                        Account.TYPE_BANK to "银行卡",
                        Account.TYPE_CASH to "现金",
                        Account.TYPE_OTHER to "其他"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = type == value,
                            onClick = { type = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("初始余额（可为负）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val cents = Money.parseSignedCents(balanceText) ?: 0L
                        onConfirm(name.trim(), type, cents)
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
