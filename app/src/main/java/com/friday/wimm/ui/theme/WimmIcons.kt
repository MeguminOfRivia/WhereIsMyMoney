package com.friday.wimm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 应用自己画的几个小图标。
 *
 * 为什么不用 material-icons-extended？
 * 那个库为了几个图标要多塞进安装包好几 MB 的代码，
 * 这里三个图标加起来不到 100 行，还省得为了一棵树把整片森林打包。
 */
object WimmIcons {

    /** 账户：一张银行卡（外框 + 磁条镂空） */
    val AccountCard: ImageVector = icon("WimmAccountCard") {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(2f, 5f); lineTo(22f, 5f); lineTo(22f, 19f); lineTo(2f, 19f); close()
            moveTo(2f, 8f); lineTo(22f, 8f); lineTo(22f, 10.2f); lineTo(2f, 10.2f); close()
            // 右下角的小芯片
            moveTo(16.4f, 13.4f); lineTo(19.6f, 13.4f); lineTo(19.6f, 16.2f); lineTo(16.4f, 16.2f); close()
        }
    }

    /** 统计：三根柱子 */
    val BarChart: ImageVector = icon("WimmBarChart") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3.5f, 13f); lineTo(7.5f, 13f); lineTo(7.5f, 20f); lineTo(3.5f, 20f); close()
            moveTo(10f, 8.5f); lineTo(14f, 8.5f); lineTo(14f, 20f); lineTo(10f, 20f); close()
            moveTo(16.5f, 4f); lineTo(20.5f, 4f); lineTo(20.5f, 20f); lineTo(16.5f, 20f); close()
        }
    }

    /** 预算：一个靶心（外环 + 中心点） */
    val BudgetTarget: ImageVector = icon("WimmBudgetTarget") {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            circle(12f, 12f, 9.2f)
            circle(12f, 12f, 5.6f)
            circle(12f, 12f, 2.4f)
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx, cy - r)
        arcTo(r, r, 0f, true, true, cx, cy + r)
        arcTo(r, r, 0f, true, true, cx, cy - r)
        close()
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
}
