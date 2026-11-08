package com.friday.wimm.ui.category

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Category
import com.friday.wimm.data.model.MerchantRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PALETTE = listOf(
    "#EF5350", "#EC407A", "#AB47BC", "#7E57C2", "#5C6BC0",
    "#42A5F5", "#29B6F6", "#26C6DA", "#26A69A", "#66BB6A",
    "#9CCC65", "#FFCA28", "#FFA726", "#FF7043", "#8D6E63", "#9E9E9E"
)

/**
 * 大类 & 收款方规则管理。
 *
 * 这里是「自动分类」的大脑：
 *  - 大类决定钱花在哪个筐里；
 *  - 规则决定「美团 → 日常饮食」这种映射，越精确的规则越优先。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRuleScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MyApplication
    val financeDao = app.financeDao
    val ledgerDao = app.ledgerDao
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var rules by remember { mutableStateOf<List<MerchantRule>>(emptyList()) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showAddCategory by remember { mutableStateOf(false) }
    var showAddRule by remember { mutableStateOf(false) }

    suspend fun reload() {
        val data = withContext(Dispatchers.IO) {
            financeDao.getCategories(includeArchived = true) to financeDao.getMerchantRules()
        }
        categories = data.first
        rules = data.second
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大类与收款方规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("大类") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("收款方规则") })
            }

            if (tab == 0) {
                CategoryList(
                    categories = categories,
                    onEdit = { editingCategory = it },
                    onToggleArchive = { category ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                financeDao.setCategoryArchived(category.id, !category.archived)
                            }
                            reload()
                        }
                    },
                    onAdd = { showAddCategory = true }
                )
            } else {
                RuleList(
                    rules = rules,
                    categories = categories,
                    onDelete = { rule ->
                        scope.launch {
                            withContext(Dispatchers.IO) { financeDao.deleteMerchantRule(rule.id) }
                            reload()
                        }
                    },
                    onAdd = { showAddRule = true }
                )
            }
        }
    }

    if (showAddCategory || editingCategory != null) {
        val editing = editingCategory
        CategoryDialog(
            initial = editing,
            onDismiss = { showAddCategory = false; editingCategory = null },
            onConfirm = { name, kind, color ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (editing == null) {
                            financeDao.insertCategory(
                                Category(
                                    name = name,
                                    color = color,
                                    kind = kind,
                                    sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
                                )
                            )
                        } else {
                            financeDao.updateCategory(editing.copy(name = name, color = color))
                        }
                    }
                    showAddCategory = false
                    editingCategory = null
                    reload()
                }
            }
        )
    }

    if (showAddRule) {
        RuleDialog(
            categories = categories.filter { it.kind == Category.KIND_EXPENSE && !it.archived },
            onDismiss = { showAddRule = false },
            onConfirm = { pattern, matchType, categoryId, applyHistory ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        financeDao.upsertMerchantRule(
                            MerchantRule(
                                pattern = pattern,
                                matchType = matchType,
                                categoryId = categoryId
                            )
                        )
                        if (applyHistory) {
                            if (matchType == MerchantRule.MATCH_EXACT) {
                                ledgerDao.bulkSetCategoryForMerchant(pattern, categoryId)
                            } else {
                                ledgerDao.distinctMerchants(pattern, 500).forEach { merchant ->
                                    if (merchant.contains(pattern)) {
                                        ledgerDao.bulkSetCategoryForMerchant(merchant, categoryId)
                                    }
                                }
                            }
                        }
                    }
                    showAddRule = false
                    reload()
                }
            }
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onToggleArchive: (Category) -> Unit,
    onAdd: () -> Unit
) {
    val expense = categories.filter { it.kind == Category.KIND_EXPENSE }
    val income = categories.filter { it.kind == Category.KIND_INCOME }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { SectionTitle("支出大类") }
        items(expense, key = { "e${it.id}" }) { category ->
            CategoryRow(category, onEdit, onToggleArchive)
        }
        item { SectionTitle("收入大类") }
        items(income, key = { "i${it.id}" }) { category ->
            CategoryRow(category, onEdit, onToggleArchive)
        }
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  新增大类")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: (Category) -> Unit,
    onToggleArchive: (Category) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(category) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorDot(category.color)
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                category.name,
                modifier = Modifier.weight(1f),
                color = if (category.archived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (category.archived) {
                Text(
                    "已停用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { onToggleArchive(category) }) {
                Text(if (category.archived) "恢复" else "停用")
            }
        }
    }
}

@Composable
private fun RuleList(
    rules: List<MerchantRule>,
    categories: List<Category>,
    onDelete: (MerchantRule) -> Unit,
    onAdd: () -> Unit
) {
    val nameOf = remember(categories) { categories.associate { it.id to it.name } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                "规则优先级：完全相同 > 关键词越长越优先 > 内置词典 > 未分类。单笔手动改过的大类永远优先于规则。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (rules.isEmpty()) {
            item {
                Text(
                    "还没有规则。点下面「新增规则」，比如让所有包含「美团」的收款方都算作日常饮食。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(rules, key = { it.id }) { rule ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (rule.matchType == MerchantRule.MATCH_EXACT) {
                                "「${rule.pattern}」完全相同"
                            } else {
                                "包含「${rule.pattern}」"
                            },
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "→ ${nameOf[rule.categoryId] ?: "未知大类"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onDelete(rule) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除规则",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  新增规则")
            }
        }
    }
}

@Composable
private fun ColorDot(hex: String) {
    val color = remember(hex) {
        try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            Color(0xFF9E9E9E)
        }
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun CategoryDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, kind: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: Category.KIND_EXPENSE) }
    var color by remember { mutableStateOf(initial?.color ?: PALETTE[5]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增大类" else "编辑大类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = kind == Category.KIND_EXPENSE,
                            onClick = { kind = Category.KIND_EXPENSE },
                            label = { Text("支出") }
                        )
                        FilterChip(
                            selected = kind == Category.KIND_INCOME,
                            onClick = { kind = Category.KIND_INCOME },
                            label = { Text("收入") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("颜色", style = MaterialTheme.typography.labelMedium)
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), kind, color) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PALETTE.forEach { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            Box(
                modifier = Modifier
                    .size(if (hex == selected) 28.dp else 22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun RuleDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, matchType: String, categoryId: Long, applyHistory: Boolean) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf(MerchantRule.MATCH_CONTAINS) }
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var applyHistory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增收款方规则") },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("收款方关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = matchType == MerchantRule.MATCH_CONTAINS,
                        onClick = { matchType = MerchantRule.MATCH_CONTAINS },
                        label = { Text("包含即可") }
                    )
                    FilterChip(
                        selected = matchType == MerchantRule.MATCH_EXACT,
                        onClick = { matchType = MerchantRule.MATCH_EXACT },
                        label = { Text("完全相同") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("归到哪个大类", style = MaterialTheme.typography.labelMedium)
                com.friday.wimm.ui.ledger.ChipGrid(
                    items = categories.map { it.id to it.name },
                    selected = categoryId,
                    onSelect = { categoryId = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { applyHistory = !applyHistory }
                ) {
                    Checkbox(checked = applyHistory, onCheckedChange = { applyHistory = it })
                    Text("把已经记过的流水也一起改过来", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank() && categoryId > 0) {
                        onConfirm(pattern.trim(), matchType, categoryId, applyHistory)
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
