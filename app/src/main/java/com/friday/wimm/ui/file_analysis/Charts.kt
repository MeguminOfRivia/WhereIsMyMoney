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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// 图表数据
data class ChartData(
    val label: String,
    val value: Double,
    val color: Color
)

// 颜色列表
val chartColors = listOf(
    Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
    Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFF4DD0E1),
    Color(0xFFFF8A65), Color(0xFFA1887F), Color(0xFF90A4AE),
    Color(0xFFAED581)
)

// 柱状图
@Composable
fun BarChart(
    data: List<ChartData>,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val barWidth = size.width / data.size * 0.6f
            val spacing = size.width / data.size * 0.4f
            val chartHeight = size.height - 30f

            data.forEachIndexed { index, item ->
                val barHeight = (item.value / maxValue * chartHeight).toFloat()
                val x = index * (barWidth + spacing) + spacing / 2

                // 绘制柱子
                drawRect(
                    color = item.color,
                    topLeft = Offset(x, chartHeight - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.take(5).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(color = item.color)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(item.label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

// 饼状图
@Composable
fun PieChart(
    data: List<ChartData>,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.value }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        Canvas(
            modifier = Modifier.size(200.dp)
        ) {
            var startAngle = -90f
            data.forEach { item ->
                val sweepAngle = (item.value / total * 360f).toFloat()
                drawArc(
                    color = item.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                startAngle += sweepAngle
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 图例
        Column {
            data.take(6).forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(color = item.color)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${item.label}: ¥${String.format("%.0f", item.value)} (${String.format("%.1f", item.value / total * 100)}%)",
                        fontSize = 10.sp)
                }
            }
        }
    }
}

// 折线图
@Composable
fun LineChart(
    data: List<ChartData>,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val chartHeight = size.height - 30f
            val stepX = size.width / (data.size - 1).coerceAtLeast(1)

            val points = data.mapIndexed { index, item ->
                Offset(
                    x = index * stepX,
                    y = chartHeight - (item.value / maxValue * chartHeight).toFloat()
                )
            }

            // 绘制线条
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = chartColors[0],
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 3f
                )
            }

            // 绘制点
            points.forEach { point ->
                drawCircle(
                    color = chartColors[0],
                    radius = 5f,
                    center = point
                )
            }
        }
        // 标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { item ->
                Text(item.label, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

// 保存图表为图片（带水印）
fun saveChartAsImage(
    context: Context,
    chartType: String,
    data: List<ChartData>,
    onResult: (Boolean, String) -> Unit
) {
    try {
        val width = 1080
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景
        canvas.drawColor(android.graphics.Color.WHITE)

        // 绘制图表
        when (chartType) {
            "bar" -> drawBarChart(canvas, data, width, height)
            "pie" -> drawPieChart(canvas, data, width, height)
            "line" -> drawLineChart(canvas, data, width, height)
        }

        // 绘制水印
        val watermarkPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("by Where is my money", 20f, height - 20f, watermarkPaint)

        // 保存图片
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "chart_${chartType}_$timestamp.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WhereIsMyMoney")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                onResult(true, "图片已保存到相册/WhereIsMyMoney/$fileName")
            } ?: onResult(false, "保存失败")
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WhereIsMyMoney")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            onResult(true, "图片已保存到相册/WhereIsMyMoney/$fileName")
        }
        bitmap.recycle()
    } catch (e: Exception) {
        onResult(false, "保存失败: ${e.message}")
    }
}

private fun drawBarChart(canvas: Canvas, data: List<ChartData>, width: Int, height: Int) {
    val maxValue = data.maxOf { it.value }
    val padding = 60f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2 - 40f
    val barWidth = chartWidth / data.size * 0.6f
    val spacing = chartWidth / data.size * 0.4f

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    data.forEachIndexed { index, item ->
        val barHeight = (item.value / maxValue * chartHeight).toFloat()
        val x = padding + index * (barWidth + spacing) + spacing / 2
        val y = padding + chartHeight - barHeight

        paint.color = android.graphics.Color.parseColor(String.format("#%06X", 0xFFFFFF and item.color.hashCode()))
        canvas.drawRect(x, y, x + barWidth, padding + chartHeight, paint)
    }
}

private fun drawPieChart(canvas: Canvas, data: List<ChartData>, width: Int, height: Int) {
    val total = data.sumOf { it.value }
    val centerX = width / 2f
    val centerY = height / 2f - 20f
    val radius = minOf(width, height) / 2f - 80f

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    var startAngle = -90f
    data.forEach { item ->
        val sweepAngle = (item.value / total * 360f).toFloat()
        paint.color = android.graphics.Color.parseColor(String.format("#%06X", 0xFFFFFF and item.color.hashCode()))
        canvas.drawArc(
            centerX - radius, centerY - radius, centerX + radius, centerY + radius,
            startAngle, sweepAngle, true, paint
        )
        startAngle += sweepAngle
    }
}

private fun drawLineChart(canvas: Canvas, data: List<ChartData>, width: Int, height: Int) {
    val maxValue = data.maxOf { it.value }
    val padding = 60f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2 - 40f

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = android.graphics.Color.parseColor("#E57373")
    }

    val points = data.mapIndexed { index, item ->
        val x = padding + index * (chartWidth / (data.size - 1).coerceAtLeast(1))
        val y = padding + chartHeight - (item.value / maxValue * chartHeight).toFloat()
        Offset(x, y)
    }

    for (i in 0 until points.size - 1) {
        canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, paint)
    }

    paint.style = Paint.Style.FILL
    points.forEach { point ->
        canvas.drawCircle(point.x, point.y, 6f, paint)
    }
}
