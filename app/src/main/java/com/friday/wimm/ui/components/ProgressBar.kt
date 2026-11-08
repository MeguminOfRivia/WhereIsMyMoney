package com.friday.wimm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * 在各个版本里不太稳定。这里就是一个圆角底 + 按比例填充的色块，所见即所得。
 *
 * 三种颜色各有含义：
 *  - [color]：正常用掉的部分；
 *  - [overProgress]：**超出预算的部分**，红色。超支时整条按「已花」为满格重算，
 *    所以超出一倍时红色正好占一半，倍数关系一眼能看出来；
 *  - [savedProgress]：**省下来、可以滚到明天用的部分**，蓝色，接在已花后面。
 *
 * 三个参数都用同一个分母（比如「本月可用」或「今日额度」）算成 0~1 的比例即可。
 */
@Composable
fun WimmProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    overProgress: Float = 0f,
    overColor: Color = Color(0xFFD32F2F),
    savedProgress: Float = 0f,
    savedColor: Color = Color(0xFF1E88E5)
) {
    val normal = progress.coerceIn(0f, 5f)
    val over = overProgress.coerceIn(0f, 5f)
    val saved = if (over > 0f) 0f else savedProgress.coerceIn(0f, 5f)
    val total = normal + over + saved
    // 超出 100% 时整条按总和缩放，这样「超出一倍」正好是红蓝各一半
    val scale = if (total > 1f) 1f / total else 1f
    val normalW = normal * scale
    val overW = over * scale
    val savedW = saved * scale
    val emptyW = (1f - normalW - overW - savedW).coerceAtLeast(0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            if (normalW > 0f) {
                Box(
                    Modifier
                        .weight(normalW.coerceAtLeast(0.0001f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
            if (overW > 0f) {
                Box(
                    Modifier
                        .weight(overW.coerceAtLeast(0.0001f))
                        .fillMaxHeight()
                        .background(overColor)
                )
            }
            if (savedW > 0f) {
                Box(
                    Modifier
                        .weight(savedW.coerceAtLeast(0.0001f))
                        .fillMaxHeight()
                        .background(savedColor)
                )
            }
            if (emptyW > 0f) {
                Spacer(Modifier.weight(emptyW.coerceAtLeast(0.0001f)))
            }
        }
    }
}
