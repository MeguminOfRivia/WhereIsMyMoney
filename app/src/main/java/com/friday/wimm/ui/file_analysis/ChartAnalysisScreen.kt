package com.friday.wimm.ui.file_analysis

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class MerchantData(
    val merchant: String,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val expenseCount: Int,
    val incomeCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartAnalysisScreen(
    merchants: List<MerchantData>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedChart by remember { mutableStateOf(0) }
    var isLandscape by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    BackHandler(onBack = onBack)

    val chartNames = listOf("柱状图", "饼状图", "折线图")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图表分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { isLandscape = !isLandscape }) {
                        Icon(Icons.Default.Refresh, contentDescription = "切换方向")
                    }
                    IconButton(onClick = {
                        saveChartImage(context, selectedChart, merchants, isLandscape) { success, msg ->
                            saveMessage = msg
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .alpha(contentAlpha)
                .offset(y = contentOffsetY.dp)
        ) {
            // 图表类型选择
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                chartNames.forEachIndexed { index, name ->
                    FilterChip(
                        selected = selectedChart == index,
                        onClick = {
                            selectedChart = index
                            scale = 1f
                            offset = Offset.Zero
                        },
                        label = { Text(name) }
                    )
                }
            }

            // 提示
            Text(
                "双指缩放查看详情",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 图表显示区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        }
                    }
            ) {
                val bitmap = remember(selectedChart, merchants, isLandscape) {
                    generateChartBitmap(selectedChart, merchants, isLandscape)
                }

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = chartNames[selectedChart],
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = (offset.x / 2).dp, y = (offset.y / 2).dp)
                        .scale(scale)
                )
            }
        }
    }

    // 保存提示
    saveMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            saveMessage = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.contains("已保存")) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(msg, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

// 生成图表Bitmap（高质量）
private fun generateChartBitmap(chartType: Int, merchants: List<MerchantData>, isLandscape: Boolean): Bitmap {
    val width = if (isLandscape) 2160 else 1620
    val height = if (isLandscape) 1620 else 2160
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    canvas.drawColor(android.graphics.Color.WHITE)

    // 标题
    val titlePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#333333")
        textSize = 64f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val title = when (chartType) {
        0 -> "支出排行 - 柱状图"
        1 -> "支出占比 - 饼状图"
        2 -> "支出趋势 - 折线图"
        else -> ""
    }
    canvas.drawText(title, width / 2f, 100f, titlePaint)

    when (chartType) {
        0 -> drawBarChartFull(canvas, merchants, width, height)
        1 -> drawPieChartFull(canvas, merchants, width, height)
        2 -> drawLineChartFull(canvas, merchants, width, height)
    }

    // 水印
    val watermarkPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#CCCCCC")
        textSize = 36f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText("by Where is my money", 30f, height - 30f, watermarkPaint)

    return bitmap
}

// 柱状图（带收款方名称）
private fun drawBarChartFull(canvas: Canvas, merchants: List<MerchantData>, width: Int, height: Int) {
    val allData = merchants.filter { it.expenseTotal > 0 }.sortedByDescending { it.expenseTotal }
    val top10 = allData.take(10)
    val otherTotal = allData.drop(10).sumOf { it.expenseTotal }
    val data = if (otherTotal > 0) top10 + MerchantData("其它", otherTotal, 0.0, 0, 0) else top10
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.expenseTotal }
    val padding = 150f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2 - 200f
    val barWidth = chartWidth / data.size * 0.65f
    val spacing = chartWidth / data.size * 0.35f
    val startY = padding + 80f

    val barPaint = Paint().apply { isAntiAlias = true }
    val textPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#666666")
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val valuePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#333333")
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    val colors = intArrayOf(
        android.graphics.Color.parseColor("#E57373"),
        android.graphics.Color.parseColor("#81C784"),
        android.graphics.Color.parseColor("#64B5F6"),
        android.graphics.Color.parseColor("#FFD54F"),
        android.graphics.Color.parseColor("#BA68C8"),
        android.graphics.Color.parseColor("#4DD0E1"),
        android.graphics.Color.parseColor("#FF8A65"),
        android.graphics.Color.parseColor("#A1887F"),
        android.graphics.Color.parseColor("#90A4AE"),
        android.graphics.Color.parseColor("#AED581"),
        android.graphics.Color.parseColor("#BDBDBD")
    )

    data.forEachIndexed { index, item ->
        val barHeight = (item.expenseTotal / maxValue * chartHeight).toFloat()
        val x = padding + index * (barWidth + spacing) + spacing / 2
        val y = startY + chartHeight - barHeight

        barPaint.color = colors[index % colors.size]
        canvas.drawRoundRect(x, y, x + barWidth, startY + chartHeight, 12f, 12f, barPaint)

        // 金额
        canvas.drawText("¥${String.format("%.0f", item.expenseTotal)}", x + barWidth / 2, y - 20f, valuePaint)

        // 收款方名称
        canvas.save()
        canvas.rotate(45f, x + barWidth / 2, startY + chartHeight + 40f)
        canvas.drawText(item.merchant.take(8), x + barWidth / 2, startY + chartHeight + 80f, textPaint)
        canvas.restore()
    }
}

// 饼状图（文字在区域内）
private fun drawPieChartFull(canvas: Canvas, merchants: List<MerchantData>, width: Int, height: Int) {
    val allData = merchants.filter { it.expenseTotal > 0 }.sortedByDescending { it.expenseTotal }
    val top8 = allData.take(8)
    val otherTotal = allData.drop(8).sumOf { it.expenseTotal }
    val data = if (otherTotal > 0) top8 + MerchantData("其它", otherTotal, 0.0, 0, 0) else top8
    if (data.isEmpty()) return

    val total = data.sumOf { it.expenseTotal }
    val centerX = width / 2f
    val centerY = height / 2f
    val radius = minOf(width, height) / 2f - 250f

    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
    }
    val percentPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
    }

    val colors = intArrayOf(
        android.graphics.Color.parseColor("#E57373"),
        android.graphics.Color.parseColor("#81C784"),
        android.graphics.Color.parseColor("#64B5F6"),
        android.graphics.Color.parseColor("#FFD54F"),
        android.graphics.Color.parseColor("#BA68C8"),
        android.graphics.Color.parseColor("#4DD0E1"),
        android.graphics.Color.parseColor("#FF8A65"),
        android.graphics.Color.parseColor("#A1887F"),
        android.graphics.Color.parseColor("#BDBDBD")
    )

    var startAngle = -90f
    data.forEachIndexed { index, item ->
        val sweepAngle = (item.expenseTotal / total * 360f).toFloat()
        paint.color = colors[index % colors.size]
        canvas.drawArc(
            centerX - radius, centerY - radius, centerX + radius, centerY + radius,
            startAngle, sweepAngle, true, paint
        )

        // 文字显示在扇形区域内
        if (sweepAngle > 15f) { // 只有扇形足够大时才显示文字
            val midAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            val labelRadius = radius * 0.65f
            val labelX = centerX + labelRadius * kotlin.math.cos(midAngle).toFloat()
            val labelY = centerY + labelRadius * kotlin.math.sin(midAngle).toFloat()

            canvas.drawText(item.merchant.take(4), labelX, labelY - 10f, textPaint)
            canvas.drawText("${String.format("%.1f", item.expenseTotal / total * 100)}%", labelX, labelY + 30f, percentPaint)
        }

        startAngle += sweepAngle
    }
}

// 折线图（名称在点位附近）
private fun drawLineChartFull(canvas: Canvas, merchants: List<MerchantData>, width: Int, height: Int) {
    val allData = merchants.filter { it.expenseTotal > 0 }.sortedByDescending { it.expenseTotal }
    val top10 = allData.take(10)
    val otherTotal = allData.drop(10).sumOf { it.expenseTotal }
    val data = if (otherTotal > 0) top10 + MerchantData("其它", otherTotal, 0.0, 0, 0) else top10
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.expenseTotal }
    val padding = 150f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2 - 250f
    val startY = padding + 80f

    val linePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#E57373")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    val pointPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#E57373")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val valuePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#333333")
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    val namePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#666666")
        textSize = 26f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    val stepX = chartWidth / (data.size - 1).coerceAtLeast(1)

    val points = data.mapIndexed { index, item ->
        val x = padding + index * stepX
        val y = startY + chartHeight - (item.expenseTotal / maxValue * chartHeight).toFloat()
        Offset(x, y)
    }

    // 绘制填充区域
    val fillPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#33E57373")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val path = android.graphics.Path()
    path.moveTo(points.first().x, startY + chartHeight)
    points.forEach { path.lineTo(it.x, it.y) }
    path.lineTo(points.last().x, startY + chartHeight)
    path.close()
    canvas.drawPath(path, fillPaint)

    // 绘制线条
    for (i in 0 until points.size - 1) {
        canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, linePaint)
    }

    // 绘制点和标签
    points.forEachIndexed { index, point ->
        canvas.drawCircle(point.x, point.y, 10f, pointPaint)

        // 金额（点上方）
        canvas.drawText("¥${String.format("%.0f", data[index].expenseTotal)}", point.x, point.y - 25f, valuePaint)

        // 名称（点下方）
        canvas.drawText(data[index].merchant.take(4), point.x, startY + chartHeight + 50f, namePaint)
    }
}

// 保存图片
private fun saveChartImage(context: Context, chartType: Int, merchants: List<MerchantData>, isLandscape: Boolean, onResult: (Boolean, String) -> Unit) {
    try {
        val bitmap = generateChartBitmap(chartType, merchants, isLandscape)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val chartName = when (chartType) { 0 -> "bar"; 1 -> "pie"; 2 -> "line"; else -> "chart" }
        val fileName = "chart_${chartName}_$timestamp.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WhereIsMyMoney")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                onResult(true, "图片已保存到相册/WhereIsMyMoney/$fileName")
            } ?: onResult(false, "保存失败")
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WhereIsMyMoney")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            onResult(true, "图片已保存到相册/WhereIsMyMoney/$fileName")
        }
        bitmap.recycle()
    } catch (e: Exception) {
        onResult(false, "保存失败: ${e.message}")
    }
}
