package com.friday.wimm.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.budget.BudgetPlanner
import com.friday.wimm.data.budget.BudgetSnapshot
import com.friday.wimm.data.budget.CategoryBudgetState
import com.friday.wimm.data.model.BudgetAdjustment
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.MonthlyBudget
import com.friday.wimm.util.BudgetMath
import com.friday.wimm.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * 预算页。
 *
 * 口径：
 *  - 每个大类一个「本月预算」，总额 = 各分类之和（不单独存总预算，避免两套数字打架）；
 *  - 日限额固定 / 动态两种模式；
 *  - 每月的结余会自动滚到下个月，滚歪了可以手动调整或清零。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen() {
    val app = LocalContext.current.applicationContext as MyApplication
    val financeDao = app.financeDao
    val scope = rememberCoroutineScope()

    val currentPeriod = remember { BudgetMath.periodOf(LocalDate.now()) }
    var period by remember { mutableStateOf(currentPeriod) }
    var snapshot by remember { mutableStateOf<BudgetSnapshot?>(null) }
    var expenseCategories by remember { mutableStateOf<List<Category>>(emptyList()) }

    var editing by remember { mutableStateOf<CategoryBudgetState?>(null) }
    var addingFor by remember { mutableStateOf<Category?>(null) }
    var adjusting by remember { mutableStateOf<CategoryBudgetState?>(null) }

    suspend fun reload() {
        val data = withContext(Dispatchers.IO) {
            BudgetPlanner.snapshot(financeDao, period) to financeDao.getCategories(kind = Category.KIND_EXPENSE)
        }
        snapshot = data.first
        expenseCategories = data.second
    }

    LaunchedEffect(period) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算") },
                actions = {
                    IconButton(onClick = {
                        period = YearMonth.parse(period).minusMonths(1).toString()
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月") }
                    Text(period, fontWeight = FontWeight.Medium)
                    IconButton(onClick = {
                        period = YearMonth.parse(period).plusMonths(1).toString()
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月") }
                }
            )
        }
    ) { padding ->
        val snap = snapshot
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (snap == null) return@LazyColumn

            item { SummaryCard(snap) }

            if (snap.rows.isEmpty()) {
                item {
                    Text(
                        "这个月还没有给任何大类设预算。\n点下面的「添加分类预算」，先给「日常饮食」这类最容易超支的设一个。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(snap.rows, key = { it.category.id }) { row ->
                BudgetRow(
                    row = row,
                    onClick = { editing = row },
                    onAdjust = { adjusting = row }
                )
            }

            item {
                val used = snap.rows.map { it.category.id }.toSet()
                val available = expenseCategories.filter { it.id !in used }
                Button(
                    onClick = { addingFor = available.firstOrNull() },
                    enabled = available.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  添加分类预算")
                }
            }
        }
    }

    // 新增大类预算：先选分类，再填金额（分两步，避免对话框太长）
    addingFor?.let { category ->
        val unused = expenseCategories.filter { c ->
            snapshot?.rows?.none { it.category.id == c.id } == true
        }
        BudgetEditDialog(
            title = "给「${category.name}」设预算",
            categoryChoices = unused,
            selectedCategory = category,
            onCategoryChange = { addingFor = it },
            initial = null,
            onDismiss = { addingFor = null },
            onSave = { amountCents, mode, dailyCents ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.upsertBudget(
                            MonthlyBudget(
                                categoryId = category.id,
                                period = period,
                                amountCents = amountCents,
                                dailyMode = mode,
                                dailyAmountCents = dailyCents
                            )
                        )
                    }
                    addingFor = null
                    reload()
                }
            }
        )
    }

    editing?.let { row ->
        BudgetEditDialog(
            title = "修改「${row.category.name}」预算",
            categoryChoices = emptyList(),
            selectedCategory = null,
            onCategoryChange = {},
            initial = row,
            onDismiss = { editing = null },
            onSave = { amountCents, mode, dailyCents ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.upsertBudget(
                            row.budget.copy(
                                amountCents = amountCents,
                                dailyMode = mode,
                                dailyAmountCents = dailyCents
                            )
                        )
                    }
                    editing = null
                    reload()
                }
            },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.deleteBudget(row.category.id, period)
                    }
                    editing = null
                    reload()
                }
            }
        )
    }

    adjusting?.let { row ->
        AdjustDialog(
            row = row,
            onDismiss = { adjusting = null },
            onApply = { deltaCents, note ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.addBudgetAdjustment(
                            BudgetAdjustment(
                                categoryId = row.category.id,
                                period = period,
                                deltaCents = deltaCents,
                                note = note
                            )
                        )
                    }
                    adjusting = null
                    reload()
                }
            }
        )
    }
}

@Composable
private fun SummaryCard(snap: BudgetSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (snap.isOverBudget) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${snap.period} 可用总额", style = MaterialTheme.typography.bodySmall)
            Text(
                "¥${Money.formatCents(snap.totalAvailableCents)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { snap.monthProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (snap.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("已花", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥${Money.formatCents(snap.totalSpentCents)}",
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还剩", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥${Money.formatCents(snap.totalRemainingCents)}",
                        fontWeight = FontWeight.Medium,
                        color = if (snap.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("今日额度", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥${Money.formatCents(snap.totalDailyLimitCents)}",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (snap.totalCarryInCents != 0L || snap.totalAdjustCents != 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "其中结转 ¥${Money.formatCents(snap.totalCarryInCents)}，手动调整 ¥${Money.formatCents(snap.totalAdjustCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(
    row: CategoryBudgetState,
    onClick: () -> Unit,
    onAdjust: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isOverBudget) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(row.category.name, fontWeight = FontWeight.Medium)
                    Text(
                        "今日 ¥${Money.formatCents(row.todaySpentCents)} / ¥${Money.formatCents(row.dailyLimitCents)}" +
                            "（${if (row.budget.dailyMode == MonthlyBudget.MODE_FIXED) "固定" else "动态"}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "¥${Money.formatCents(row.spentCents)}",
                        fontWeight = FontWeight.Bold,
                        color = if (row.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "/ ¥${Money.formatCents(row.availableCents)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { row.monthProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (row.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append("本月剩余 ¥${Money.formatCents(row.remainingCents)}")
                        if (row.carryInCents != 0L) {
                            append(" · 结转 ${if (row.carryInCents > 0) "+" else ""}${Money.formatCents(row.carryInCents)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onAdjust) { Text("调整") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditDialog(
    title: String,
    categoryChoices: List<Category>,
    selectedCategory: Category?,
    onCategoryChange: (Category) -> Unit,
    initial: CategoryBudgetState?,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, mode: String, dailyCents: Long) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var amountText by remember {
        mutableStateOf(initial?.let { Money.formatCents(it.budget.amountCents) } ?: "")
    }
    var mode by remember { mutableStateOf(initial?.budget?.dailyMode ?: MonthlyBudget.MODE_DYNAMIC) }
    var dailyText by remember {
        mutableStateOf(
            initial?.budget?.dailyAmountCents?.takeIf { it > 0 }?.let { Money.formatCents(it) } ?: ""
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (categoryChoices.isNotEmpty()) {
                    Text("选一个还没设预算的大类", style = MaterialTheme.typography.labelMedium)
                    com.friday.wimm.ui.ledger.ChipGrid(
                        items = categoryChoices.map { it.id to it.name },
                        selected = selectedCategory?.id,
                        onSelect = { id -> categoryChoices.firstOrNull { it.id == id }?.let(onCategoryChange) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("本月预算总额（元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("日限额方式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == MonthlyBudget.MODE_DYNAMIC,
                        onClick = { mode = MonthlyBudget.MODE_DYNAMIC },
                        label = { Text("动态（剩余÷天数）") }
                    )
                    FilterChip(
                        selected = mode == MonthlyBudget.MODE_FIXED,
                        onClick = { mode = MonthlyBudget.MODE_FIXED },
                        label = { Text("固定日额度") }
                    )
                }
                if (mode == MonthlyBudget.MODE_FIXED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dailyText,
                        onValueChange = { dailyText = it },
                        label = { Text("每天额度（元）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = Money.parseCents(amountText)
                    if (amount == null) {
                        error = "预算要填一个大于 0 的金额"
                        return@TextButton
                    }
                    val daily = if (mode == MonthlyBudget.MODE_FIXED) Money.parseCents(dailyText) else null
                    if (mode == MonthlyBudget.MODE_FIXED && daily == null) {
                        error = "固定模式需要填每天额度"
                        return@TextButton
                    }
                    onSave(amount, mode, daily ?: 0L)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun AdjustDialog(
    row: CategoryBudgetState,
    onDismiss: () -> Unit,
    onApply: (deltaCents: Long, note: String) -> Unit
) {
    var text by remember { mutableStateOf(Money.formatCents(row.carryInCents)) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整「${row.category.name}」的结转") },
        text = {
            Column {
                Text(
                    "现在从之前月份滚进来 ¥${Money.formatCents(row.carryInCents)}。" +
                        "想凭空加钱就填正数，想扣掉就填负数，清零就填 0。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("设置结转金额（元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = text.trim().let {
                    if (it == "0" || it == "0.00") 0L else Money.parseCents(it) ?: Long.MIN_VALUE
                }
                if (target == Long.MIN_VALUE) return@TextButton
                val delta = target - row.carryInCents
                onApply(delta, note.trim().ifBlank { "手动调整结转" })
            }) { Text("应用") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(-row.carryInCents, "清零结转") }) { Text("清零") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
