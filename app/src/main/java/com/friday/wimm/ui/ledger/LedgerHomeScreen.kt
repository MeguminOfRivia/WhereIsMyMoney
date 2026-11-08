package com.friday.wimm.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.budget.BudgetPlanner
import com.friday.wimm.data.budget.BudgetSnapshot
import com.friday.wimm.data.database.LedgerDao
import com.friday.wimm.data.database.LedgerDao.LedgerFilter
import com.friday.wimm.data.model.Account
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.LedgerEntry
import com.friday.wimm.data.model.LedgerSums
import com.friday.wimm.ui.components.WimmProgressBar
import com.friday.wimm.util.BudgetMath
import com.friday.wimm.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 首页 = 一眼看完「今天花了多少 / 这个月还剩多少 / 最近都花在哪」。
 *
 * 上：本月收支 + 预算进度
 * 中：筛选（时间 / 账户 / 大类 / 收支）
 * 下：按记账日分组的流水，点一条就能改大类、改备注、改账户
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerHomeScreen(refreshKey: Int = 0) {
    val app = LocalContext.current.applicationContext as MyApplication
    val ledgerDao = app.ledgerDao
    val financeDao = app.financeDao
    val scope = rememberCoroutineScope()

    // 筛选状态
    var rangeMode by remember { mutableStateOf("month") } // today / month / all
    var accountId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var sums by remember { mutableStateOf(LedgerSums(0, 0, 0, 0)) }
    var todayCents by remember { mutableStateOf(0L) }
    var budget by remember { mutableStateOf<BudgetSnapshot?>(null) }
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    // 右下角「+」：手动记一笔
    var adding by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val period = remember { BudgetMath.periodOf(today) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            accounts = financeDao.getAllAccounts()
            categories = financeDao.getCategories()
        }
    }

    suspend fun reload() {
        loading = true
        val data = withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            val range = when (rangeMode) {
                "today" -> LedgerDao.dayRange(today)
                "month" -> LedgerDao.monthRange(BudgetMath.periodOf(today))
                else -> 0L to Long.MAX_VALUE
            }
            val filter = LedgerFilter(
                fromTs = range.first,
                toTs = range.second,
                accountId = accountId,
                categoryId = categoryId,
                merchantKeyword = keyword,
                type = typeFilter
            )
            val dayStart = LedgerDao.dayRange(today)
            Triple(
                ledgerDao.query(filter, limit = 500),
                ledgerDao.sums(filter),
                ledgerDao.sums(LedgerFilter(fromTs = dayStart.first, toTs = dayStart.second, type = "expense"))
            )
        }
        entries = data.first
        sums = data.second
        todayCents = data.third.expenseCents
        budget = withContext(Dispatchers.IO) {
            BudgetPlanner.snapshot(financeDao, BudgetMath.periodOf(LocalDate.now()))
        }
        loading = false
    }

    LaunchedEffect(refreshKey, rangeMode, accountId, categoryId, typeFilter, keyword) { reload() }

    // 从微信/支付宝付完款切回来时，账本可能刚被通知服务写了一条，立刻刷新一下
    androidx.lifecycle.compose.LifecycleEventEffect(
        androidx.lifecycle.Lifecycle.Event.ON_RESUME
    ) {
        scope.launch { reload() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我钱呢？！") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索收款方")
                    }
                }
            )
        },
        floatingActionButton = {
            // 手动记一笔：金额、收支、大类、账户、收款方、备注
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OverviewCard(
                    sums = sums,
                    todayCents = todayCents,
                    rangeLabel = when (rangeMode) {
                        "today" -> "今天"
                        "month" -> "本月"
                        else -> "全部"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            budget?.takeIf { it.hasBudget }?.let { snapshot ->
                item { BudgetStrip(snapshot, Modifier.padding(horizontal = 12.dp)) }
            }

            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("按收款方搜索") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }
            }

            item {
                FilterRow(
                    rangeMode = rangeMode,
                    onRangeChange = { rangeMode = it },
                    typeFilter = typeFilter,
                    onTypeChange = { typeFilter = it },
                    accounts = accounts,
                    accountId = accountId,
                    onAccountChange = { accountId = it },
                    categories = categories,
                    categoryId = categoryId,
                    onCategoryChange = { categoryId = it }
                )
            }

            if (!loading && entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "这个范围里还没有流水",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val grouped = entries.groupBy { BudgetMath.accountingDay(it.tx.timestamp) }
            grouped.forEach { (day, dayEntries) ->
                item(key = "header-${day}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            day.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val dayExpense = dayEntries.filter { !it.isIncome }.sumOf { it.amountCents }
                        if (dayExpense > 0) {
                            Text(
                                "支出 ¥${Money.formatCents(dayExpense)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(dayEntries, key = { "tx-${it.tx.id}" }) { entry ->
                    LedgerRow(entry = entry, onClick = { editing = entry })
                }
            }

            if (entries.size >= 500) {
                item {
                    Text(
                        "只显示最近 500 条，用上面的筛选缩小范围",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    editing?.let { entry ->
        TransactionEditSheet(
            entry = entry,
            onDismiss = { editing = null },
            onChanged = { scope.launch { reload() } }
        )
    }

    if (adding) {
        TransactionEditSheet(
            entry = null,
            onDismiss = { adding = false },
            onChanged = { scope.launch { reload() } }
        )
    }
}

@Composable
private fun OverviewCard(
    sums: LedgerSums,
    todayCents: Long,
    rangeLabel: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$rangeLabel 支出", style = MaterialTheme.typography.bodySmall)
            Text(
                "¥${Money.formatCents(sums.expenseCents)}",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("$rangeLabel 收入", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "¥${Money.formatCents(sums.incomeCents)}",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("今日支出", style = MaterialTheme.typography.bodySmall)
                    Text("¥${Money.formatCents(todayCents)}", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun BudgetStrip(snapshot: BudgetSnapshot, modifier: Modifier = Modifier) {
    val todayRemaining = snapshot.totalTodayRemainingCents
    val overToday = todayRemaining < 0
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (snapshot.isOverBudget) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("今天还能花", style = MaterialTheme.typography.bodySmall)
                    Text(
                        Money.formatSignedCents(todayRemaining),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("本月还剩", style = MaterialTheme.typography.bodySmall)
                    Text(
                        Money.formatSignedCents(snapshot.totalRemainingCents),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (snapshot.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            WimmProgressBar(
                progress = snapshot.monthProgress,
                color = if (snapshot.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "预算内已花 ${Money.formatSignedCents(snapshot.totalSpentCents)} / 可用 ${Money.formatSignedCents(snapshot.totalAvailableCents)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (snapshot.totalUnbudgetedCents > 0) {
                Text(
                    "另有 ${Money.formatSignedCents(snapshot.totalUnbudgetedCents)} 不在预算里",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    rangeMode: String,
    onRangeChange: (String) -> Unit,
    typeFilter: String?,
    onTypeChange: (String?) -> Unit,
    accounts: List<Account>,
    accountId: Long?,
    onAccountChange: (Long?) -> Unit,
    categories: List<Category>,
    categoryId: Long?,
    onCategoryChange: (Long?) -> Unit
) {
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("today" to "今天", "month" to "本月", "all" to "全部").forEach { (value, label) ->
            FilterChip(
                selected = rangeMode == value,
                onClick = { onRangeChange(value) },
                label = { Text(label) }
            )
        }

        Box {
            FilterChip(
                selected = accountId != null,
                onClick = { accountMenu = true },
                label = { Text(accounts.firstOrNull { it.id == accountId }?.name ?: "全部账户") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                DropdownMenuItem(
                    text = { Text("全部账户") },
                    onClick = { onAccountChange(null); accountMenu = false }
                )
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = { onAccountChange(account.id); accountMenu = false }
                    )
                }
            }
        }

        Box {
            FilterChip(
                selected = categoryId != null,
                onClick = { categoryMenu = true },
                label = { Text(categories.firstOrNull { it.id == categoryId }?.name ?: "全部大类") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(
                    text = { Text("全部大类") },
                    onClick = { onCategoryChange(null); categoryMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("未分类") },
                    onClick = { onCategoryChange(LedgerDao.UNCATEGORIZED_FILTER); categoryMenu = false }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = { onCategoryChange(category.id); categoryMenu = false }
                    )
                }
            }
        }

        FilterChip(
            selected = typeFilter == "expense",
            onClick = { onTypeChange(if (typeFilter == "expense") null else "expense") },
            label = { Text("只看支出") }
        )
        FilterChip(
            selected = typeFilter == "income",
            onClick = { onTypeChange(if (typeFilter == "income") null else "income") },
            label = { Text("只看收入") }
        )
    }
}

/** 一列横向排列、可左右滑动的筛选芯片 */
@Composable
fun LedgerRow(entry: LedgerEntry, onClick: () -> Unit) {
    val dotColor = remember(entry.categoryColor) {
        try {
            Color(android.graphics.Color.parseColor(entry.categoryColor))
        } catch (e: Exception) {
            Color(0xFF9E9E9E)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.tx.merchant.ifBlank { "未知" },
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                val time = remember(entry.tx.timestamp) {
                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(java.util.Date(entry.tx.timestamp))
                }
                val subtitle = buildString {
                    append(entry.categoryName)
                    append(" · ")
                    append(entry.accountName)
                    append(" · ")
                    append(time)
                    if (entry.tx.note.isNotBlank()) {
                        append(" · ")
                        append(entry.tx.note)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                (if (entry.isIncome) "+¥" else "-¥") + Money.formatCents(entry.amountCents),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (entry.isIncome) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}
