package com.friday.wimm.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.budget.BudgetPlanner
import com.friday.wimm.data.budget.BudgetSnapshot
import com.friday.wimm.data.budget.CategoryBudgetState
import com.friday.wimm.data.model.BudgetAdjustment
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.MonthlyBudget
import com.friday.wimm.ui.components.WimmProgressBar
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
 * 排版原则（上一版就是在这里翻车的）：
 *  - 一行里只放「标签 + 数值」，长说明永远另起一行、占满整行宽度；
 *  - 所有会变长的那一列都加 `weight(1f, fill = false)`，右边的按钮/金额永远不会被挤成 0 宽；
 *  - 每条预算都同时有「每月」和「每日」两条进度条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(refreshKey: Int = 0) {
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
            BudgetPlanner.snapshot(financeDao, period) to
                financeDao.getCategories(kind = Category.KIND_EXPENSE)
        }
        snapshot = data.first
        expenseCategories = data.second
    }

    LaunchedEffect(refreshKey, period) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算") },
                actions = {
                    IconButton(onClick = {
                        period = YearMonth.parse(period).minusMonths(1).toString()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    Text(period, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    IconButton(onClick = {
                        period = YearMonth.parse(period).plusMonths(1).toString()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
            )
        }
    ) { padding ->
        val snap = snapshot
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (snap == null) return@LazyColumn

            item { OverviewCard(snap) }

            if (snap.rows.isEmpty()) {
                item {
                    Text(
                        "这个月还没有给任何大类设预算。\n点下面的「添加分类预算」，先给「日常饮食」这类最容易超支的设一个。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(snap.rows, key = { it.category.id }) { row ->
                BudgetCard(
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
                        .padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  添加分类预算")
                }
            }
        }
    }

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
            onSave = { amountCents, mode ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.upsertBudget(
                            MonthlyBudget(
                                categoryId = category.id,
                                period = period,
                                amountCents = amountCents,
                                dailyMode = mode,
                                dailyAmountCents = 0L
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
            onSave = { amountCents, mode ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.upsertBudget(
                            row.budget.copy(
                                amountCents = amountCents,
                                dailyMode = mode,
                                dailyAmountCents = 0L
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

// ── 总览 ─────────────────────────────────────────────────

@Composable
private fun OverviewCard(snap: BudgetSnapshot) {
    val container = if (snap.isOverBudget) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${snap.period} 可用总额", style = MaterialTheme.typography.bodySmall)
            Text(
                Money.formatSignedCents(snap.totalAvailableCents),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                buildString {
                    append("预算 ${Money.formatSignedCents(snap.totalBudgetCents)}")
                    if (snap.totalCarryInCents != 0L) {
                        append(" · 结转 ${Money.formatSignedCents(snap.totalCarryInCents)}")
                    }
                    if (snap.totalAdjustCents != 0L) {
                        append(" · 调整 ${Money.formatSignedCents(snap.totalAdjustCents)}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 每月一条
            BarLabelRow(
                label = "每月",
                spentCents = snap.totalSpentCents,
                limitCents = snap.totalAvailableCents,
                extra = "剩 ${snap.daysRemaining} 天"
            )
            Spacer(modifier = Modifier.height(4.dp))
            WimmProgressBar(
                progress = snap.monthProgress,
                color = if (snap.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            if (snap.isCurrentMonth) {
                Spacer(modifier = Modifier.height(10.dp))
                // 每日一条（注意：这里的数字是「今天能花多少」，包含前几天没用完顺延过来的额度）
                BarLabelRow(
                    label = "今日",
                    spentCents = snap.totalTodaySpentCents,
                    limitCents = snap.totalDailyLimitCents,
                    extra = ""
                )
                Spacer(modifier = Modifier.height(4.dp))
                WimmProgressBar(
                    progress = snap.dayProgress,
                    color = if (snap.totalTodayRemainingCents < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    trackColor = MaterialTheme.colorScheme.surface,
                    height = 6.dp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "还剩 ${Money.formatSignedCents(snap.totalRemainingCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "本月总支出 ${Money.formatSignedCents(snap.totalAllSpentCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (snap.totalUnbudgetedCents > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "其中 ${Money.formatSignedCents(snap.totalUnbudgetedCents)} 没走预算（没设预算的分类 / 未分类）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 「标签 + 已花/额度 + 百分比」这一行，保证长数字只在这一行内换行，不影响别处 */
@Composable
private fun BarLabelRow(label: String, spentCents: Long, limitCents: Long, extra: String = "") {
    val percent = if (limitCents <= 0L) {
        if (spentCents > 0L) 100 else 0
    } else {
        (spentCents * 100.0 / limitCents).toInt()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(34.dp)
        )
        Text(
            "${Money.formatSignedCents(spentCents)} / ${Money.formatSignedCents(limitCents)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (extra.isNotEmpty()) "$extra · $percent%" else "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ── 单条预算 ─────────────────────────────────────────────

@Composable
private fun BudgetCard(
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
            // 标题行：名字占满剩余宽度，右边固定放状态，永远不会被挤掉
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.category.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(row)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 每月进度
            BarLabelRow(
                label = "每月",
                spentCents = row.spentCents,
                limitCents = row.availableCents,
                extra = if (row.isFixed) "固定" else "动态"
            )
            Spacer(modifier = Modifier.height(4.dp))
            WimmProgressBar(
                progress = row.monthProgress,
                color = if (row.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            // 每日进度（只在本月显示）
            if (row.isCurrentMonth) {
                Spacer(modifier = Modifier.height(10.dp))
                BarLabelRow(
                    label = "今日",
                    spentCents = row.todaySpentCents,
                    limitCents = row.dailyLimitCents,
                    extra = ""
                )
                Spacer(modifier = Modifier.height(4.dp))
                WimmProgressBar(
                    progress = row.dayProgress,
                    color = if (row.isOverToday) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    trackColor = MaterialTheme.colorScheme.surface,
                    height = 6.dp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 明细 + 调整按钮：明细两行封顶，按钮固定在最右
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString {
                        append("预算 ${Money.formatSignedCents(row.budgetCents)}")
                        if (row.carryInCents != 0L) {
                            append(" · 结转 ${Money.formatSignedCents(row.carryInCents)}")
                        }
                        if (row.isCurrentMonth) {
                            append(" · 每日基础 ${Money.formatSignedCents(row.baseDailyCents)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onAdjust) { Text("调整") }
            }
        }
    }
}

@Composable
private fun StatusBadge(row: CategoryBudgetState) {
    val over = row.isOverBudget
    val text = if (over) {
        "超支 ${Money.formatSignedCents(-row.remainingCents)}"
    } else {
        "剩 ${Money.formatSignedCents(row.remainingCents)}"
    }
    val bg = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fg = if (over) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, color = fg, maxLines = 1)
    }
}

// ── 对话框 ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditDialog(
    title: String,
    categoryChoices: List<Category>,
    selectedCategory: Category?,
    onCategoryChange: (Category) -> Unit,
    initial: CategoryBudgetState?,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, mode: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var amountText by remember {
        mutableStateOf(initial?.let { Money.formatGrouped(it.budgetCents) } ?: "")
    }
    var mode by remember {
        mutableStateOf(initial?.budget?.dailyMode ?: MonthlyBudget.MODE_DYNAMIC)
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
                        onSelect = { id ->
                            categoryChoices.firstOrNull { it.id == id }?.let(onCategoryChange)
                        }
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
                Spacer(modifier = Modifier.height(12.dp))
                Text("日限额方式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == MonthlyBudget.MODE_DYNAMIC,
                        onClick = { mode = MonthlyBudget.MODE_DYNAMIC },
                        label = { Text("动态") }
                    )
                    FilterChip(
                        selected = mode == MonthlyBudget.MODE_FIXED,
                        onClick = { mode = MonthlyBudget.MODE_FIXED },
                        label = { Text("固定日额度") }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (mode == MonthlyBudget.MODE_FIXED) {
                        "固定日额度：本月可用 ÷ 当月天数，自动算，不用你填。" +
                            "除不尽的零头加到当天；每天没用完的额度自动顺延到第二天。"
                    } else {
                        "动态日额度：本月剩余 ÷ 剩余天数，越花越少，每天额度会自己变。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    onSave(amount, mode)
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
    var text by remember { mutableStateOf(Money.formatGrouped(row.carryInCents)) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整「${row.category.name}」的结转") },
        text = {
            Column {
                Text(
                    "现在从之前月份滚进来 ${Money.formatSignedCents(row.carryInCents)}。" +
                        "想多给点就填正数，想扣掉就填负数，清零就填 0。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("设置结转金额（元，可为负）") },
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
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 这里允许 0 和负数：结转本来就是可正可负的
                val target = Money.parseSignedCents(text)
                if (target == null) {
                    error = "填一个数字，可以为负"
                    return@TextButton
                }
                onApply(target - row.carryInCents, note.trim().ifBlank { "手动调整结转" })
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
