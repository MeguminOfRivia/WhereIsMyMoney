package com.friday.wimm.ui.stats

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.database.LedgerDao
import com.friday.wimm.data.database.LedgerDao.LedgerFilter
import com.friday.wimm.data.model.Account
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.CategoryTotal
import com.friday.wimm.data.model.LedgerSums
import com.friday.wimm.data.model.MerchantTotalRow
import com.friday.wimm.data.model.TrendPoint
import com.friday.wimm.util.BudgetMath
import com.friday.wimm.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatsRange(val label: String) {
    THIS_MONTH("本月"),
    LAST_MONTH("上月"),
    LAST_3_MONTHS("近 3 个月"),
    THIS_YEAR("今年"),
    ALL("全部")
}

/**
 * 统计页。
 *
 * 所有数字都从 LedgerDao 来，和首页、预算页同一套筛选口径，
 * 所以「首页看 1200，统计页看 980」这种事不会再发生。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MyApplication
    val ledgerDao = app.ledgerDao
    val financeDao = app.financeDao
    val scope = rememberCoroutineScope()

    var range by remember { mutableStateOf(StatsRange.THIS_MONTH) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var keyword by remember { mutableStateOf("") }

    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var sums by remember { mutableStateOf(LedgerSums(0, 0, 0, 0)) }
    var prevSums by remember { mutableStateOf<LedgerSums?>(null) }
    var catTotals by remember { mutableStateOf<List<CategoryTotal>>(emptyList()) }
    var incomeTotals by remember { mutableStateOf<List<CategoryTotal>>(emptyList()) }
    var merchants by remember { mutableStateOf<List<MerchantTotalRow>>(emptyList()) }
    var trend by remember { mutableStateOf<List<TrendPoint>>(emptyList()) }
    var trendByMonth by remember { mutableStateOf(false) }
    var dayCount by remember { mutableStateOf(1L) }

    fun rangeBounds(): Pair<Long, Long> {
        val today = LocalDate.now()
        val thisMonth = YearMonth.from(today)
        return when (range) {
            StatsRange.THIS_MONTH -> LedgerDao.monthRange(thisMonth.toString())
            StatsRange.LAST_MONTH -> LedgerDao.monthRange(thisMonth.minusMonths(1).toString())
            StatsRange.LAST_3_MONTHS -> LedgerDao.monthRange(thisMonth.minusMonths(2).toString()).first to
                LedgerDao.monthRange(thisMonth.toString()).second
            StatsRange.THIS_YEAR -> {
                val start = BudgetMath.dayStartMillis(LocalDate.of(today.year, 1, 1))
                val end = BudgetMath.dayStartMillis(LocalDate.of(today.year + 1, 1, 1))
                start to end
            }
            StatsRange.ALL -> 0L to Long.MAX_VALUE
        }
    }

    fun previousBounds(): Pair<Long, Long>? {
        val today = LocalDate.now()
        val thisMonth = YearMonth.from(today)
        return when (range) {
            StatsRange.THIS_MONTH -> LedgerDao.monthRange(thisMonth.minusMonths(1).toString())
            StatsRange.LAST_MONTH -> LedgerDao.monthRange(thisMonth.minusMonths(2).toString())
            StatsRange.LAST_3_MONTHS -> LedgerDao.monthRange(thisMonth.minusMonths(5).toString()).first to
                LedgerDao.monthRange(thisMonth.minusMonths(3).toString()).second
            StatsRange.THIS_YEAR -> {
                val start = BudgetMath.dayStartMillis(LocalDate.of(today.year - 1, 1, 1))
                val end = BudgetMath.dayStartMillis(LocalDate.of(today.year, 1, 1))
                start to end
            }
            StatsRange.ALL -> null
        }
    }

    suspend fun reload() {
        val (from, to) = rangeBounds()
        val filter = LedgerFilter(
            fromTs = from,
            toTs = to,
            accountId = accountId,
            categoryId = categoryId,
            merchantKeyword = keyword
        )
        val prev = previousBounds()
        val now = System.currentTimeMillis()
        val effectiveEnd = if (to == Long.MAX_VALUE) now else to.coerceAtMost(now)
        val days = ((effectiveEnd - from) / 86_400_000L).coerceAtLeast(1)
        val data = withContext(Dispatchers.IO) {
            val byMonth = range == StatsRange.ALL || range == StatsRange.THIS_YEAR ||
                (effectiveEnd - from) > 92L * 86_400_000L
            StatsData(
                accounts = financeDao.getAllAccounts(),
                categories = financeDao.getCategories(),
                sums = ledgerDao.sums(filter),
                prevSums = prev?.let { (pf, pt) -> ledgerDao.sums(filter.copy(fromTs = pf, toTs = pt)) },
                catTotals = ledgerDao.categoryBreakdown(filter),
                incomeTotals = ledgerDao.incomeBreakdown(filter),
                merchants = ledgerDao.merchantBreakdown(filter, 12),
                trend = if (byMonth) ledgerDao.monthlyTrend(filter) else ledgerDao.dailyTrend(filter),
                byMonth = byMonth,
                days = days
            )
        }
        accounts = data.accounts
        categories = data.categories
        sums = data.sums
        prevSums = data.prevSums
        catTotals = data.catTotals
        incomeTotals = data.incomeTotals
        merchants = data.merchants
        trend = data.trend
        trendByMonth = data.byMonth
        dayCount = data.days
    }

    LaunchedEffect(range, accountId, categoryId, keyword) { reload() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (from, to) = rangeBounds()
                val rows = withContext(Dispatchers.IO) {
                    ledgerDao.query(
                        LedgerFilter(
                            fromTs = from,
                            toTs = to,
                            accountId = accountId,
                            categoryId = categoryId,
                            merchantKeyword = keyword
                        ),
                        limit = 20_000
                    )
                }
                withContext(Dispatchers.IO) { writeCsv(context, uri, rows) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                actions = {
                    IconButton(onClick = {
                        exportLauncher.launch("wimm-${System.currentTimeMillis()}.csv")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "导出 CSV")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                FilterBar(
                    range = range,
                    onRange = { range = it },
                    accounts = accounts,
                    accountId = accountId,
                    onAccount = { accountId = it },
                    categories = categories,
                    categoryId = categoryId,
                    onCategory = { categoryId = it },
                    keyword = keyword,
                    onKeyword = { keyword = it }
                )
            }

            item {
                SummaryCard(
                    sums = sums,
                    prev = prevSums,
                    days = dayCount,
                    rangeLabel = range.label
                )
            }

            if (catTotals.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("支出构成", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            PieChart(catTotals)
                            Spacer(modifier = Modifier.height(10.dp))
                            CategoryLegend(catTotals, sums.expenseCents)
                        }
                    }
                }
            }

            if (trend.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                if (trendByMonth) "每月支出" else "每天支出",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TrendChart(trend)
                        }
                    }
                }
            }

            if (merchants.isNotEmpty()) {
                item {
                    Text(
                        "花得最多的收款方",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(merchants, key = { it.merchant }) { row ->
                    MerchantRow(row, merchants.first().cents)
                }
            }

            if (incomeTotals.isNotEmpty()) {
                item {
                    Text("收入构成", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                items(incomeTotals, key = { "inc-${it.categoryId}" }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(row.name)
                        Text(
                            "¥${Money.formatCents(row.cents)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private data class StatsData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val sums: LedgerSums,
    val prevSums: LedgerSums?,
    val catTotals: List<CategoryTotal>,
    val incomeTotals: List<CategoryTotal>,
    val merchants: List<MerchantTotalRow>,
    val trend: List<TrendPoint>,
    val byMonth: Boolean,
    val days: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    range: StatsRange,
    onRange: (StatsRange) -> Unit,
    accounts: List<Account>,
    accountId: Long?,
    onAccount: (Long?) -> Unit,
    categories: List<Category>,
    categoryId: Long?,
    onCategory: (Long?) -> Unit,
    keyword: String,
    onKeyword: (String) -> Unit
) {
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatsRange.values().forEach { item ->
                FilterChip(
                    selected = range == item,
                    onClick = { onRange(item) },
                    label = { Text(item.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                        onClick = { onAccount(null); accountMenu = false }
                    )
                    accounts.forEach {
                        DropdownMenuItem(
                            text = { Text(it.name) },
                            onClick = { onAccount(it.id); accountMenu = false }
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
                        onClick = { onCategory(null); categoryMenu = false }
                    )
                    categories.forEach {
                        DropdownMenuItem(
                            text = { Text(it.name) },
                            onClick = { onCategory(it.id); categoryMenu = false }
                        )
                    }
                }
            }
            FilterChip(
                selected = showSearch || keyword.isNotBlank(),
                onClick = { showSearch = !showSearch },
                label = { Text(if (keyword.isBlank()) "搜收款方" else keyword) }
            )
        }
        if (showSearch) {
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeyword,
                label = { Text("收款方关键词") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryCard(sums: LedgerSums, prev: LedgerSums?, days: Long, rangeLabel: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$rangeLabel 支出", style = MaterialTheme.typography.bodySmall)
            Text(
                "¥${Money.formatCents(sums.expenseCents)}",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("收入", style = MaterialTheme.typography.labelSmall)
                    Text("¥${Money.formatCents(sums.incomeCents)}", fontWeight = FontWeight.Medium)
                }
                Column {
                    Text("日均支出", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "¥${Money.formatCents(sums.expenseCents / days.coerceAtLeast(1))}",
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("笔数", style = MaterialTheme.typography.labelSmall)
                    Text("${sums.count}", fontWeight = FontWeight.Medium)
                }
            }
            if (prev != null && prev.expenseCents > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                val diff = sums.expenseCents - prev.expenseCents
                val pct = diff * 100.0 / prev.expenseCents
                Text(
                    "比上一周期" + (if (diff >= 0) "多花" else "少花") +
                        " ¥${Money.formatCents(kotlin.math.abs(diff))}" +
                        "（${String.format(Locale.US, "%+.1f", pct)}%）",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PieChart(totals: List<CategoryTotal>) {
    val total = totals.sumOf { it.cents }.coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val diameter = kotlin.math.min(size.width, size.height) * 0.9f
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        totals.forEach { item ->
            val sweep = item.cents * 360f / total
            drawArc(
                color = parseColor(item.color),
                startAngle = startAngle,
                sweepAngle = (sweep - 1.2f).coerceAtLeast(0.6f),
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            startAngle += sweep
        }
        drawArc(
            color = Color.White.copy(alpha = 0.15f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun CategoryLegend(totals: List<CategoryTotal>, totalCents: Long) {
    Column {
        totals.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(parseColor(item.color))
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(item.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                Text(
                    "¥${Money.formatCents(item.cents)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    if (totalCents > 0) {
                        String.format(Locale.US, "%.1f", item.cents * 100.0 / totalCents) + "%"
                    } else "0%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>) {
    val max = points.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1) ?: 1
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val barSlot = size.width / points.size.coerceAtLeast(1)
            val barWidth = (barSlot * 0.62f).coerceAtLeast(2f)
            points.forEachIndexed { index, point ->
                val ratio = point.expenseCents.toFloat() / max
                val barHeight = (size.height - 4f) * ratio
                drawRect(
                    color = Color(0xFFEF5350),
                    topLeft = Offset(index * barSlot + (barSlot - barWidth) / 2f, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                points.firstOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "最高 ¥${Money.formatCents(max)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                points.lastOrNull()?.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MerchantRow(row: MerchantTotalRow, maxCents: Long) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(row.merchant, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                "¥${Money.formatCents(row.cents)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (row.cents.toFloat() / maxCents.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.error,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color(0xFF9E9E9E)
}

/** 导出 CSV：只用内容 URI 写入，不申请任何存储权限 */
private fun writeCsv(context: Context, uri: Uri, rows: List<com.friday.wimm.data.model.LedgerEntry>) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.bufferedWriter().use { writer ->
            writer.write("日期,类型,金额,收款方,大类,账户,备注,来源\n")
            rows.forEach { entry ->
                val time = java.time.Instant.ofEpochMilli(entry.tx.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(formatter)
                writer.write(
                    listOf(
                        time,
                        if (entry.isIncome) "收入" else "支出",
                        Money.formatCents(entry.amountCents),
                        entry.tx.merchant,
                        entry.categoryName,
                        entry.accountName,
                        entry.tx.note,
                        entry.tx.dataSource
                    ).joinToString(",") { csvCell(it) }
                )
                writer.write("\n")
            }
        }
    }
}

private fun csvCell(value: String): String {
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
    return value
}
