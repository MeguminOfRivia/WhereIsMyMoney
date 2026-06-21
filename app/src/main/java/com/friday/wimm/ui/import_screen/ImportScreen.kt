package com.friday.wimm.ui.import_screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.friday.wimm.MyApplication
import com.friday.wimm.data.database.ImportRecord
import com.friday.wimm.ui.file_analysis.FileAnalysisScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onSubPageChanged: (Boolean) -> Unit = {}) {
    val application = LocalContext.current.applicationContext as MyApplication
    val viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.Factory(application.transactionRepository)
    )

    val importState by viewModel.importState.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var importRecords by remember { mutableStateOf<List<ImportRecord>>(emptyList()) }
    var selectedRecord by remember { mutableStateOf<ImportRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFromUri(context, it)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // 加载导入记录
    LaunchedEffect(importState) {
        importRecords = viewModel.getImportRecords()
        isLoading = false
    }

    // 如果选中了某个文件，显示分析页面
    selectedRecord?.let { record ->
        LaunchedEffect(record.id) {
            onSubPageChanged(true)
        }
        FileAnalysisScreen(
            importRecord = record,
            onBack = {
                selectedRecord = null
                onSubPageChanged(false)
            },
            onDelete = {
                viewModel.deleteFileAnalysis(record.id)
                selectedRecord = null
                onSubPageChanged(false)
                coroutineScope.launch {
                    importRecords = viewModel.getImportRecords()
                }
            }
        )
        return
    }

    // 动画状态 - 页面整体渐显
    var pageVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        pageVisible = true
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectMode) "已选 ${selectedIds.size} 项" else "导入数据") },
                navigationIcon = {
                    if (isSelectMode) {
                        IconButton(onClick = { isSelectMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
                        }
                    }
                },
                actions = {
                    if (importRecords.isNotEmpty()) {
                        if (isSelectMode) {
                            // 全选/取消全选
                            IconButton(onClick = {
                                if (selectedIds.size == importRecords.size) {
                                    selectedIds = emptySet()
                                } else {
                                    selectedIds = importRecords.map { it.id }.toSet()
                                }
                            }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "全选")
                            }
                            // 删除选中
                            if (selectedIds.isNotEmpty()) {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        selectedIds.forEach { id ->
                                            viewModel.deleteImportRecord(id)
                                        }
                                        importRecords = viewModel.getImportRecords()
                                        isSelectMode = false
                                        selectedIds = emptySet()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            // 选择模式按钮
                            IconButton(onClick = { isSelectMode = true }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "选择删除")
                            }
                            // 删除所有按钮
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除所有", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (importRecords.size < 5) {
                FloatingActionButton(
                    onClick = { launcher.launch("*/*") },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "导入文件")
                }
            }
        }
    ) { padding ->
        AnimatedVisibility(
            visible = pageVisible,
            enter = fadeIn(tween(350, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 说明卡片
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "导入说明",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "• 支持微信/支付宝账单文件（CSV、XLSX）",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• 最多保存 5 个文件分析",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• 点击文件卡片查看详细分析",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• 重复导入会自动去重",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 文件列表
                if (importRecords.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无导入文件，点击右下角 + 导入",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(importRecords) { _, record ->
                        FileCard(
                            record = record,
                            dateFormat = dateFormat,
                            isSelectMode = isSelectMode,
                            isSelected = selectedIds.contains(record.id),
                            onClick = {
                                if (isSelectMode) {
                                    selectedIds = if (selectedIds.contains(record.id)) {
                                        selectedIds - record.id
                                    } else {
                                        selectedIds + record.id
                                    }
                                } else {
                                    selectedRecord = record
                                }
                            }
                        )
                    }
                }

                // 导入状态提示
                when (val state = importState) {
                    is ImportViewModel.ImportState.Loading -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("处理中...")
                                }
                            }
                        }
                    }
                    is ImportViewModel.ImportState.Success -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "导入成功！",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "新增 ${state.count} 条记录",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (state.overlapCount > 0) {
                                        Text(
                                            text = "检测到与 ${state.overlapCount} 个已导入文件时间重叠，已自动去重",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is ImportViewModel.ImportState.Error -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = "错误：${state.message}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    else -> {}
                }

                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
        }
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("删除所有文件分析") },
            text = { Text("将删除所有导入文件的分析数据，此操作不可恢复。通知监听的数据不会受影响。确定要删除吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData()
                    }
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FileCard(
    record: ImportRecord,
    dateFormat: SimpleDateFormat,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "导入时间：${dateFormat.format(Date(record.importTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "记录数：${record.count} 条",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
