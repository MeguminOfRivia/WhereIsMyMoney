package com.friday.wimm.ui.mascot

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 形象类型。
 *
 * - [BUILTIN]：内置的像素钞票精灵 Wally，Canvas 绘制；
 * - [CUSTOM]：用户自己上传的一张照片 / GIF，按 [MascotAnimMode] 播放动画。
 */
enum class MascotStyle(val value: String) {
    BUILTIN("builtin"),
    CUSTOM("custom");

    companion object {
        fun from(value: String?): MascotStyle =
            entries.firstOrNull { it.value == value } ?: BUILTIN
    }
}

/**
 * 自定义形象的两种动画方式。
 *
 * - [JELLY]：对话出现时整体上下 Q 弹（体积守恒的挤压拉伸）；
 * - [MOUTH]：对话出现时切换到用户上传的"张口差分"图片。
 */
enum class MascotAnimMode(val value: String) {
    JELLY("jelly"),
    MOUTH("mouth");

    companion object {
        fun from(value: String?): MascotAnimMode =
            entries.firstOrNull { it.value == value } ?: JELLY
    }
}

/** 形象占用的画布尺寸（dp，未缩放） */
data class MascotBox(val width: Dp, val height: Dp)

/**
 * 自定义形象「哪一条边贴着屏幕边框」。
 *
 * 用户选一张图后，图的左右两侧往往不对称（角色可能侧身），
 * 需要告诉 Wally：贴左边框和贴右边框时，应该让图的哪一侧靠墙。
 */
enum class MascotSnapEdge(val value: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun from(value: String?): MascotSnapEdge =
            entries.firstOrNull { it.value == value } ?: LEFT
    }
}

/** Wally 当前贴着哪一侧（未贴边时为 NONE） */
enum class MascotSide { NONE, LEFT, RIGHT }

/**
 * 悬浮窗布局常量。
 *
 * 内置形象是 184×100 的横向钞票；自定义形象给一个偏竖向的盒子，
 * 这样竖构图的立绘 / 动图不会被压得太小。
 */
object MascotLayout {
    val BUILTIN_BOX = MascotBox(184.dp, 100.dp)
    val CUSTOM_BOX = MascotBox(170.dp, 200.dp)

    /** 气泡高度（dp，未缩放） */
    const val BUBBLE_H = 70f

    /** 快捷菜单宽高（dp） */
    const val MENU_W = 220f
    const val MENU_H = 300f

    /** 预算面板宽高（dp，长按 / 双击弹出） */
    const val BUDGET_PANEL_W = 236f
    const val BUDGET_PANEL_H = 176f

    fun boxFor(style: MascotStyle): MascotBox = when (style) {
        MascotStyle.BUILTIN -> BUILTIN_BOX
        MascotStyle.CUSTOM -> CUSTOM_BOX
    }
}
