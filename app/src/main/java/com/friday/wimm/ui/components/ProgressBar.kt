package com.friday.wimm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自己画的进度条。
 *
 * 为什么不用 `LinearProgressIndicator`：Material3 1.3 的版本会在轨道末端画一个
 * 小圆点（stop indicator），进度为 0 时看起来就像「有一条进度」；而且它的颜色/圆角
 * 在各个版本里不太稳定。这里就是一个圆角底 + 一个按比例填充的圆角块，所见即所得。
 */
@Composable
fun WimmProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val safe = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safe)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
