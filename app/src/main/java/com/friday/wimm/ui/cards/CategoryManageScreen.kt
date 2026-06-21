package com.friday.wimm.ui.cards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.model.CardCategory
import com.friday.wimm.data.repository.CardRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    card: Card,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as MyApplication
    val cardRepository = remember { CardRepository(application.databaseHelper) }

    var categories by remember { mutableStateOf<List<CardCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<CardCategory?>(null) }
    var showMerchantDialog by remember { mutableStateOf<CardCategory?>(null) }

    BackHandler(onBack = onBack)

    // 动画状态
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "contentAlpha"
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 60f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "contentOffsetY"
    )

    // 加载分类
    LaunchedEffect(card.id) {
        categories = cardRepository.getCardCategories(card.id)
        isLoading = false
        contentVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理分类") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "创建分类")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无分类",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击右上角 + 创建分类",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .alpha(contentAlpha)
                    .offset(y = contentOffsetY.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { category ->
                    CategoryItem(
                        category = category,
                        onClick = { showMerchantDialog = category },
                        onDelete = { showDeleteDialog = category }
                    )
                }
            }
        }
    }

    // 创建分类对话框
    if (showCreateDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color ->
                val category = CardCategory(
                    cardId = card.id,
                    name = name,
                    color = color
                )
                cardRepository.insertCardCategory(category)
                categories = cardRepository.getCardCategories(card.id)
                showCreateDialog = false
            }
        )
    }

    // 删除确认对话框
    showDeleteDialog?.let { category ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除分类") },
            text = { Text("确定要删除分类「${category.name}」吗？该分类下的收款方将恢复为未分类状态。") },
            confirmButton = {
                TextButton(onClick = {
                    cardRepository.deleteCardCategory(category.id)
                    categories = cardRepository.getCardCategories(card.id)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 管理收款方对话框
    showMerchantDialog?.let { category ->
        MerchantManageDialog(
            card = card,
            category = category,
            cardRepository = cardRepository,
            onDismiss = { showMerchantDialog = null }
        )
    }
}

@Composable
private fun CategoryItem(
    category: CardCategory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("点击查看收款方", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#E57373") }

    val colors = listOf(
        "#E57373", "#81C784", "#64B5F6", "#FFD54F", "#BA68C8",
        "#4DD0E1", "#FF8A65", "#A1887F", "#90A4AE", "#AED581"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    placeholder = { Text("例如：餐饮、交通、娱乐") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("选择颜色", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.take(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { selectedColor = color }
                        ) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color))
                                )
                            ) {}
                            if (selectedColor == color) {
                                Text("✓", modifier = Modifier.align(Alignment.Center), color = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun MerchantManageDialog(
    card: Card,
    category: CardCategory,
    cardRepository: CardRepository,
    onDismiss: () -> Unit
) {
    val merchantsInCategory = remember { mutableStateOf<List<String>>(emptyList()) }
    val allMerchants = remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(card.id, category.id) {
        merchantsInCategory.value = cardRepository.getCardMerchantsByCategory(card.id, category.id)
        // 获取所有收款方（从交易记录中）
        val transactions = cardRepository.getTransactionsByCard(card)
        allMerchants.value = transactions.map { it.merchant }.distinct().sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理收款方 - ${category.name}") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                val merchantsNotInCategory = allMerchants.value.filter { it !in merchantsInCategory.value }
                if (merchantsNotInCategory.isEmpty()) {
                    item {
                        Text("所有收款方都已在此分类中", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(merchantsNotInCategory) { merchant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cardRepository.setCardMerchantCategory(card.id, merchant, category.id)
                                    merchantsInCategory.value = cardRepository.getCardMerchantsByCategory(card.id, category.id)
                                }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(merchant)
                            IconButton(onClick = {
                                cardRepository.setCardMerchantCategory(card.id, merchant, category.id)
                                merchantsInCategory.value = cardRepository.getCardMerchantsByCategory(card.id, category.id)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
