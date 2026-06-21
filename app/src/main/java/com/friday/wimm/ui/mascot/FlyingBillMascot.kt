package com.friday.wimm.ui.mascot

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// ── 配色 ──────────────────────────────────────────────────
private val BodyMain = Color(0xFFE8956A)
private val BodyFold = Color(0xFFD47850)
private val BodyHighlight = Color(0xFFF5C9A0)
private val BodyOutline = Color(0xFF7A4A2E)
private val WingA = Color(0xFFF0B090)
private val WingB = Color(0xFFE8A080)
private val EyeDark = Color(0xFF2D2D2D)
private val EarColor = Color(0xFFD4835A)
private val EarInner = Color(0xFFEEA07A)
private val ArmColor = Color(0xFFD4835A)
private val LegColor = Color(0xFFD4835A)
private val ShadowColor = Color(0x25000000)
private val YenColor = Color(0x44FFFFFF)
private val DizzyColor = Color(0xFFFFD700)
private val CheekColor = Color(0x55FF8888)
private val FlameOuter = Color(0xFFFF6B35)
private val FlameInner = Color(0xFFFFAA00)
// ── 新增配色 ──────────────────────────────────────────────
private val NotebookColor = Color(0xFF5B9BD5)
private val NotebookLine = Color(0xFFD6E4F0)
private val PenColor = Color(0xFF333333)
private val HeadphoneBand = Color(0xFF555555)
private val HeadphonePad = Color(0xFF444444)
private val HeadphonePadInner = Color(0xFF666666)
private val CoffeeCup = Color(0xFFFFFFFF)
private val CoffeeLiquid = Color(0xFF8B6914)
private val CoffeeSteam = Color(0x66FFFFFF)
private val CoinGold = Color(0xFFFFD700)
private val CoinDark = Color(0xFFDAA520)
private val PhoneScreen = Color(0xFF4FC3F7)
private val PhoneFrame = Color(0xFF333333)
private val NoteColor = Color(0xFF8B4513)

// ── 逻辑坐标空间 ─────────────────────────────────────────
private const val LOGIC_W = 200f
private const val LOGIC_H = 240f
private const val MASCOT_CX = LOGIC_W / 2f
private const val MASCOT_CY = 120f
private const val OFFSET_X = -9f
private const val OFFSET_Y = -70f
private const val CANVAS_W = 184
private const val CANVAS_H = 100
private const val BUBBLE_H = 70

// ── 主组件 ────────────────────────────────────────────────
@Composable
fun FlyingBillMascot(
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    state: MascotStateHolder = MascotStateHolder()
) {
    val t = rememberInfiniteTransition(label = "mascot")

    val floatPhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse),
        label = "float"
    )
    val blinkPhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3600), RepeatMode.Restart),
        label = "blink"
    )
    val wingPhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(450, easing = EaseInOut), RepeatMode.Reverse),
        label = "wing"
    )
    val swayPhase by t.animateFloat(
        -1.5f, 1.5f,
        infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse),
        label = "sway"
    )
    val armPhase by t.animateFloat(
        -8f, 8f,
        infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "arm"
    )
    val dizzySpin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Restart),
        label = "dizzySpin"
    )
    val bouncePhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label = "bounce"
    )
    val excitedBreath by t.animateFloat(
        -2f, 2f,
        infiniteRepeatable(tween(300, easing = EaseInOut), RepeatMode.Reverse),
        label = "excitedBreath"
    )
    val legPhase by t.animateFloat(
        -6f, 6f,
        infiniteRepeatable(tween(400, easing = EaseInOut), RepeatMode.Reverse),
        label = "leg"
    )
    // ── 新增动画相位 ──────────────────────────────────────
    val walkPhase by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(500, easing = EaseInOut), RepeatMode.Reverse),
        label = "walk"
    )
    val yawnPhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse),
        label = "yawn"
    )
    val stretchPhase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2500, easing = EaseInOut), RepeatMode.Reverse),
        label = "stretch"
    )
    val musicBop by t.animateFloat(
        -3f, 3f,
        infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label = "musicBop"
    )
    val coinSpin by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(400, easing = EaseInOut), RepeatMode.Restart),
        label = "coinSpin"
    )
    val phoneScroll by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = EaseInOut), RepeatMode.Restart),
        label = "phoneScroll"
    )
    val coffeeSteam by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Restart),
        label = "coffeeSteam"
    )
    val writePhase by t.animateFloat(
        -2f, 2f,
        infiniteRepeatable(tween(300, easing = EaseInOut), RepeatMode.Reverse),
        label = "write"
    )

    val density = LocalDensity.current.density

    val boxW = (CANVAS_W * scale).dp
    val boxH = (CANVAS_H * scale).dp

    Box(modifier = modifier.size(boxW, boxH)) {
        Canvas(modifier = Modifier.size(boxW, boxH)) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density * scale, density * scale)

            val animState = state.animationState

            // ── 浮动Y ─────────────────────────────────────
            val floatY = when (animState) {
                AnimationState.BOUNCING -> (bouncePhase - 0.5f) * 20f
                AnimationState.SLEEPING -> (floatPhase - 0.5f) * 4f
                AnimationState.EXCITED -> (floatPhase - 0.5f) * 6f + excitedBreath
                AnimationState.WALK_SIDE -> (floatPhase - 0.5f) * 6f
                AnimationState.YAWN -> (floatPhase - 0.5f) * 5f
                AnimationState.STRETCH -> (floatPhase - 0.5f) * 5f
                AnimationState.LISTEN_MUSIC -> (floatPhase - 0.5f) * 6f + musicBop
                AnimationState.COUNT_MONEY -> (floatPhase - 0.5f) * 8f
                AnimationState.SNEEZE -> (floatPhase - 0.5f) * 4f
                AnimationState.LOOK_PHONE -> (floatPhase - 0.5f) * 4f
                AnimationState.DRINK_COFFEE -> (floatPhase - 0.5f) * 4f
                AnimationState.ACCOUNTING -> (floatPhase - 0.5f) * 4f
                else -> (floatPhase - 0.5f) * 10f
            }

            // ── 旋转角度 ───────────────────────────────────
            val spinAngle = when (animState) {
                AnimationState.SPINNING -> dizzySpin
                AnimationState.DIZZY -> dizzySpin * 0.3f
                AnimationState.WALK_SIDE -> walkPhase * 5f  // 侧走时身体微微倾斜
                AnimationState.LISTEN_MUSIC -> musicBop * 2f  // 听歌时微微摇摆
                else -> 0f
            }

            // ── 手臂角度 ───────────────────────────────────
            val armAngle = when (animState) {
                AnimationState.EXCITED -> armPhase * 2.5f
                AnimationState.BOUNCING -> armPhase * 1.5f
                AnimationState.DIZZY -> armPhase * 0.3f
                AnimationState.SLEEPING -> 0f
                AnimationState.WALK_SIDE -> armPhase * 1.2f  // 侧走摆臂
                AnimationState.STRETCH -> 0f  // 伸懒腰单独处理
                AnimationState.LOOK_PHONE -> 0f  // 看手机单独处理
                AnimationState.ACCOUNTING -> writePhase  // 记账写字
                AnimationState.LISTEN_MUSIC -> 0f  // 听歌单独处理
                AnimationState.COUNT_MONEY -> armPhase * 2f  // 数钱搓手
                AnimationState.DRINK_COFFEE -> 0f  // 喝咖啡单独处理
                AnimationState.SNEEZE -> armPhase * 0.5f
                AnimationState.YAWN -> 0f  // 打哈欠单独处理
                else -> armPhase
            }

            // ── 腿角度 ─────────────────────────────────────
            val legAngle = when (animState) {
                AnimationState.BOUNCING -> legPhase
                AnimationState.EXCITED -> legPhase * 0.8f
                AnimationState.DIZZY -> legPhase * 0.3f
                AnimationState.SLEEPING -> 0f
                AnimationState.WALK_SIDE -> legPhase * 1.5f  // 侧走迈步
                AnimationState.STRETCH -> 0f
                AnimationState.LISTEN_MUSIC -> legPhase * 0.3f  // 听歌轻踏
                AnimationState.COUNT_MONEY -> legPhase * 0.5f
                AnimationState.SNEEZE -> 0f
                AnimationState.LOOK_PHONE -> 0f
                AnimationState.DRINK_COFFEE -> 0f
                AnimationState.ACCOUNTING -> 0f
                AnimationState.YAWN -> 0f
                else -> legPhase * 0.3f
            }

            // ── 喷气强度 ───────────────────────────────────
            val jetIntensity = when (animState) {
                AnimationState.EXCITED -> 1.5f
                AnimationState.BOUNCING -> 1.3f
                AnimationState.SLEEPING -> 0.3f
                AnimationState.DIZZY -> 0.8f
                AnimationState.WALK_SIDE -> 1.2f  // 侧走喷气偏移
                AnimationState.LISTEN_MUSIC -> 0.5f  // 听歌低喷气
                AnimationState.COUNT_MONEY -> 0.8f
                AnimationState.SNEEZE -> 1.4f  // 打喷嚏喷气大
                AnimationState.STRETCH -> 0.6f
                AnimationState.YAWN -> 0.4f
                AnimationState.LOOK_PHONE -> 0.5f
                AnimationState.DRINK_COFFEE -> 0.5f
                AnimationState.ACCOUNTING -> 0.5f
                else -> 1.0f
            }

            // ── 横向偏移（侧走用） ─────────────────────────
            val swayX = when (animState) {
                AnimationState.WALK_SIDE -> swayPhase * 3f
                AnimationState.LISTEN_MUSIC -> musicBop * 1.5f
                AnimationState.SNEEZE -> 0f
                else -> swayPhase
            }

            translate(left = OFFSET_X + swayX, top = OFFSET_Y + floatY) {
                if (spinAngle != 0f) {
                    rotate(degrees = spinAngle, pivot = Offset(MASCOT_CX, MASCOT_CY)) {
                        drawWings(MASCOT_CX, MASCOT_CY, wingPhase, jetIntensity, animState)
                        drawEars(MASCOT_CX, MASCOT_CY, animState)
                        drawBody(MASCOT_CX, MASCOT_CY, animState)
                        drawFace(MASCOT_CX, MASCOT_CY, blinkPhase, state.eyeOffsetX, state.eyeOffsetY, state.isDizzy, animState)
                        drawArms(MASCOT_CX, MASCOT_CY, armAngle, animState)
                        drawLegs(MASCOT_CX, MASCOT_CY, legAngle, animState)
                        drawProps(MASCOT_CX, MASCOT_CY, animState, coinSpin, phoneScroll, coffeeSteam, writePhase, yawnPhase)
                    }
                } else {
                    drawWings(MASCOT_CX, MASCOT_CY, wingPhase, jetIntensity, animState)
                    drawEars(MASCOT_CX, MASCOT_CY, animState)
                    drawBody(MASCOT_CX, MASCOT_CY, animState)
                    drawFace(MASCOT_CX, MASCOT_CY, blinkPhase, state.eyeOffsetX, state.eyeOffsetY, state.isDizzy, animState)
                    drawArms(MASCOT_CX, MASCOT_CY, armAngle, animState)
                    drawLegs(MASCOT_CX, MASCOT_CY, legAngle, animState)
                    drawProps(MASCOT_CX, MASCOT_CY, animState, coinSpin, phoneScroll, coffeeSteam, writePhase, yawnPhase)
                }
            }
            canvas.restore()
        }
    }
}

// ── 矩形快捷方法 ─────────────────────────────────────────
private fun DrawScope.block(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRect(color, topLeft = Offset(x, y), size = Size(w, h))
}

// ── 身体 ──────────────────────────────────────────────────
private fun DrawScope.drawBody(cx: Float, cy: Float, animState: AnimationState) {
    val bw = 78f; val bh = 56f
    val bx = cx - bw / 2f; val by = cy - bh / 2f - 2f

    val expand = when (animState) {
        AnimationState.EXCITED -> 2f
        AnimationState.BOUNCING -> 1f
        AnimationState.STRETCH -> 3f  // 伸懒腰身体拉长
        AnimationState.YAWN -> 1f
        else -> 0f
    }

    val stretchH = when (animState) {
        AnimationState.STRETCH -> 6f  // 伸懒腰纵向拉伸
        else -> 0f
    }

    block(bx + 3f, by + 4f, bw + expand, bh + expand + stretchH, ShadowColor)
    block(bx, by, bw + expand, bh + expand + stretchH, BodyMain)

    val foldSize = 14f
    block(bx + bw + expand - foldSize, by, foldSize, foldSize, BodyFold)
    block(bx + bw + expand - foldSize, by, foldSize, 3f, BodyOutline.copy(alpha = 0.3f))
    block(bx + bw + expand - 3f, by, 3f, foldSize, BodyOutline.copy(alpha = 0.2f))

    block(bx, by, 4f, bh + expand + stretchH, BodyHighlight)
    block(bx, by + bh + expand + stretchH - 5f, bw + expand, 5f, BodyFold.copy(alpha = 0.4f))

    val border = 2f
    block(bx + 7f, by + 6f, bw + expand - 13f, border, BodyOutline)
    block(bx + 7f, by + bh + expand + stretchH - 8f, bw + expand - 13f, border, BodyOutline)
    block(bx + 7f, by + 6f, border, bh + expand + stretchH - 14f, BodyOutline)
    block(bx + bw + expand - 9f, by + 6f, border, bh + expand + stretchH - 14f, BodyOutline)

    drawYenSign(cx + 18f, by + (bh + expand + stretchH) * 0.58f)
    block(bx + 12f, by + 11f, 4f, 4f, BodyOutline.copy(alpha = 0.5f))
}

// ── 猫娘耳朵 ──────────────────────────────────────────────
private fun DrawScope.drawEars(cx: Float, cy: Float, animState: AnimationState) {
    val bodyTop = cy - 56f / 2f - 2f
    val layers = 3; val baseW = 13f; val layerH = 5f; val taper = 3f

    val earTwitch = when (animState) {
        AnimationState.EXCITED -> 1f
        AnimationState.DIZZY -> -1f
        AnimationState.SNEEZE -> 2f  // 打喷嚏耳朵抖动
        AnimationState.LISTEN_MUSIC -> 1.5f  // 听歌耳朵微动
        else -> 0f
    }

    val lx = cx - 25f + earTwitch
    for (i in 0 until layers) {
        val w = baseW - i * taper
        block(lx + (baseW - w) / 2f + i * 0.5f, bodyTop - (i + 1) * layerH, w, layerH + 1f, EarColor)
    }
    for (i in 0 until 2) {
        val w = baseW * 0.45f - i * taper * 0.4f
        block(lx + (baseW - w) / 2f + i * 0.5f, bodyTop - (i + 1) * layerH - layerH * 0.3f, w, layerH, EarInner)
    }

    val rx = cx + 12f - earTwitch
    for (i in 0 until layers) {
        val w = baseW - i * taper
        block(rx + (baseW - w) / 2f - i * 0.5f, bodyTop - (i + 1) * layerH, w, layerH + 1f, EarColor)
    }
    for (i in 0 until 2) {
        val w = baseW * 0.45f - i * taper * 0.4f
        block(rx + (baseW - w) / 2f - i * 0.5f, bodyTop - (i + 1) * layerH - layerH * 0.3f, w, layerH, EarInner)
    }
}

// ── ¥ 像素符号 ────────────────────────────────────────────
private fun DrawScope.drawYenSign(cx: Float, cy: Float) {
    val s = 3.5f; val c = YenColor
    block(cx - s * 2, cy - s * 3, s, s, c)
    block(cx + s, cy - s * 3, s, s, c)
    block(cx - s, cy - s * 2, s, s, c)
    block(cx, cy - s * 2, s, s, c)
    block(cx - s * 2, cy - s, s * 5, s, c)
    block(cx - s * 2, cy + s * 0.5f, s * 5, s, c)
    block(cx - s / 2, cy + s * 2, s, s * 2.5f, c)
}

// ── 喷气装置 ─────────────────────────────────────────────
private fun DrawScope.drawWings(cx: Float, cy: Float, wingPhase: Float, intensity: Float, animState: AnimationState) {
    val bodyW = 78f
    val bodyTop = cy - 56f / 2f - 2f
    val jetY = bodyTop + 10f

    // 侧走时喷气偏移
    val jetOffsetY = when (animState) {
        AnimationState.WALK_SIDE -> 5f  // 侧走喷气向下偏
        else -> 0f
    }

    drawJetDevice(cx - bodyW / 2f - 6f, jetY + jetOffsetY, wingPhase, intensity, left = true)
    drawJetDevice(cx + bodyW / 2f + 6f, jetY + jetOffsetY, wingPhase, intensity, left = false)
}

private fun DrawScope.drawJetDevice(jetX: Float, jetY: Float, wingPhase: Float, intensity: Float, left: Boolean) {
    val housingW = 10f; val housingH = 16f
    val hx = if (left) jetX - housingW else jetX
    block(hx, jetY - housingH, housingW, housingH, WingA.copy(alpha = 0.95f))
    block(hx, jetY - housingH - 3f, housingW, 3f, WingB.copy(alpha = 0.8f))

    val nozzleW = 6f; val nozzleH = 4f
    val nx = if (left) jetX - housingW + 2f else jetX + 2f
    block(nx, jetY, nozzleW, nozzleH, WingB.copy(alpha = 0.9f))

    val flamePulse = (0.5f + 0.5f * sin(wingPhase * PI.toFloat()).let { if (it < 0) -it else it }) * intensity
    val flameW = 4f * flamePulse; val flameH = (10f * flamePulse).coerceAtMost(20f)
    val fx = if (left) jetX - housingW + 3f else jetX + 3f

    block(fx - 2f, jetY + nozzleH, flameW + 4f, flameH + 4f, FlameOuter.copy(alpha = 0.3f * flamePulse))
    block(fx - 1f, jetY + nozzleH, flameW + 2f, flameH + 2f, FlameInner.copy(alpha = 0.5f * flamePulse))
    block(fx, jetY + nozzleH, flameW, flameH, WingA.copy(alpha = 0.7f * flamePulse))
}

// ── 脸：用眼睛形状变化表达表情 ────────────────────────────
private fun DrawScope.drawFace(
    cx: Float, cy: Float, blinkPhase: Float,
    eyeOffsetX: Float, eyeOffsetY: Float,
    isDizzy: Boolean, animState: AnimationState
) {
    val faceY = cy - 4f
    val eyeSpacing = 12f
    val isBlinking = blinkPhase > 0.93f || animState == AnimationState.SLEEPING

    if (isDizzy) {
        val s = 3f
        block(cx - eyeSpacing - 4f, faceY - 5f, s, 10f, DizzyColor)
        block(cx - eyeSpacing - 1f, faceY - 5f, s, 10f, DizzyColor)
        block(cx + eyeSpacing - 4f, faceY - 5f, s, 10f, DizzyColor)
        block(cx + eyeSpacing - 1f, faceY - 5f, s, 10f, DizzyColor)
        val starX = cx + 25f; val starY = faceY - 14f; val ss = 2f
        block(starX, starY, ss * 2, ss, DizzyColor.copy(alpha = 0.7f))
        block(starX + ss / 2, starY - ss / 2, ss, ss * 2, DizzyColor.copy(alpha = 0.7f))
    } else if (isBlinking) {
        // 闭眼：横线
        block(cx - eyeSpacing - 3f, faceY, 6f, 1.5f, EyeDark)
        block(cx + eyeSpacing - 3f, faceY, 6f, 1.5f, EyeDark)
        if (animState == AnimationState.SLEEPING) {
            val zx = cx + 22f; val zy = faceY - 16f; val zs = 3f
            block(zx, zy, zs * 2, zs, EyeDark.copy(alpha = 0.5f))
            block(zx + zs, zy + zs, zs, zs, EyeDark.copy(alpha = 0.4f))
            block(zx, zy + zs * 2, zs * 2, zs, EyeDark.copy(alpha = 0.3f))
        }
    } else if (animState == AnimationState.YAWN) {
        // 打哈欠：半闭眼（瞌睡眼，上眼皮垂下来）
        val ew = 6f; val eh = 6f  // 只露出下半部分眼睛
        block(cx - eyeSpacing - ew / 2f, faceY, ew, eh, EyeDark)
        block(cx + eyeSpacing - ew / 2f, faceY, ew, eh, EyeDark)
        // 上眼皮
        block(cx - eyeSpacing - ew / 2f - 1f, faceY - 2f, ew + 2f, 3f, BodyMain)
        block(cx + eyeSpacing - ew / 2f - 1f, faceY - 2f, ew + 2f, 3f, BodyMain)
    } else if (animState == AnimationState.SNEEZE) {
        // 打喷嚏：>w< 眯眼（大尺寸弧形）
        // 左眼：> 形
        val lx = cx - eyeSpacing
        block(lx - 6f, faceY - 5f, 5f, 3f, EyeDark)  // 左上斜线
        block(lx - 1f, faceY - 1f, 8f, 3f, EyeDark)   // 中间横线
        block(lx - 6f, faceY + 3f, 5f, 3f, EyeDark)  // 左下斜线
        // 右眼：< 形
        val rx = cx + eyeSpacing
        block(rx + 1f, faceY - 5f, 5f, 3f, EyeDark)  // 右上斜线
        block(rx - 7f, faceY - 1f, 8f, 3f, EyeDark)   // 中间横线
        block(rx + 1f, faceY + 3f, 5f, 3f, EyeDark)  // 右下斜线
    } else {
        val maxShift = 6f
        val shiftX = eyeOffsetX * maxShift
        val shiftY = eyeOffsetY * maxShift

        when (animState) {
            AnimationState.EXCITED -> {
                val ew = 8f; val eh = 16f
                block(cx - eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY, ew, eh, EyeDark)
            }
            AnimationState.BOUNCING -> {
                val ew = 7f; val eh = 12f
                block(cx - eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY, ew, eh, EyeDark)
            }
            AnimationState.SPINNING -> {
                val ew = 5f; val eh = 10f
                block(cx - eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY - 2f, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY + 2f, ew, eh, EyeDark)
            }
            AnimationState.LISTEN_MUSIC -> {
                // 听歌：闭眼享受（弧形线代替）
                block(cx - eyeSpacing - 4f, faceY, 8f, 2f, EyeDark)
                block(cx + eyeSpacing - 4f, faceY, 8f, 2f, EyeDark)
            }
            AnimationState.LOOK_PHONE -> {
                // 看手机：眼睛向下看
                val ew = 6f; val eh = 12f
                block(cx - eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY + 4f, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY + 4f, ew, eh, EyeDark)
            }
            AnimationState.COUNT_MONEY -> {
                // 数钱：眼睛变成¥形（兴奋+眯眼）
                val ew = 7f; val eh = 10f
                block(cx - eyeSpacing - ew / 2f, faceY - eh / 2f, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f, faceY - eh / 2f, ew, eh, EyeDark)
            }
            AnimationState.SNEEZE -> {
                // 打喷嚏：紧闭然后睁大
                val ew = 7f; val eh = 14f
                block(cx - eyeSpacing - ew / 2f, faceY - eh / 2f, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f, faceY - eh / 2f, ew, eh, EyeDark)
            }
            AnimationState.STRETCH -> {
                // 伸懒腰：舒服的眯眼
                block(cx - eyeSpacing - 4f, faceY, 8f, 2f, EyeDark)
                block(cx + eyeSpacing - 4f, faceY, 8f, 2f, EyeDark)
            }
            else -> {
                val ew = 6f; val eh = 14f
                block(cx - eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY, ew, eh, EyeDark)
                block(cx + eyeSpacing - ew / 2f + shiftX, faceY - eh / 2f + shiftY, ew, eh, EyeDark)
            }
        }
    }

    // 腮红
    when (animState) {
        AnimationState.EXCITED, AnimationState.SLEEPING -> {
            val cheekAlpha = if (animState == AnimationState.EXCITED) 0.4f else 0.2f
            block(cx - eyeSpacing - 10f, faceY + 4f, 6f, 3f, CheekColor.copy(alpha = cheekAlpha))
            block(cx + eyeSpacing + 4f, faceY + 4f, 6f, 3f, CheekColor.copy(alpha = cheekAlpha))
        }
        AnimationState.LISTEN_MUSIC, AnimationState.STRETCH, AnimationState.DRINK_COFFEE -> {
            // 享受状态腮红
            block(cx - eyeSpacing - 10f, faceY + 4f, 6f, 3f, CheekColor.copy(alpha = 0.3f))
            block(cx + eyeSpacing + 4f, faceY + 4f, 6f, 3f, CheekColor.copy(alpha = 0.3f))
        }
        AnimationState.COUNT_MONEY -> {
            block(cx - eyeSpacing - 10f, faceY + 4f, 6f, 3f, CheekColor.copy(alpha = 0.35f))
            block(cx + eyeSpacing + 4f, faceY + 4f, 6f, 3f, CheekColor.copy(alpha = 0.35f))
        }
        else -> {}
    }
}

// ── 手臂 ──────────────────────────────────────────────────
private fun DrawScope.drawArms(cx: Float, cy: Float, armAngle: Float, animState: AnimationState) {
    val bodyW = 78f; val armW = 6f; val armH = 16f; val armY = cy - 2f

    when (animState) {
        AnimationState.EXCITED -> {
            rotate(degrees = -45f + armAngle, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY - 8f, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY - 8f + armH, 8f, 5f, ArmColor)
            }
            rotate(degrees = 45f - armAngle, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY - 8f, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY - 8f + armH, 8f, 5f, ArmColor)
            }
        }
        AnimationState.SLEEPING -> {
            block(cx - bodyW / 2f - armW + 2f, armY + 4f, armW, armH - 4f, ArmColor)
            block(cx + bodyW / 2f - 2f, armY + 4f, armW, armH - 4f, ArmColor)
        }
        AnimationState.DIZZY -> {
            rotate(degrees = -30f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 2f, armY + armH, 10f, 5f, ArmColor)
            }
            rotate(degrees = 30f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 8f, armY + armH, 10f, 5f, ArmColor)
            }
        }
        AnimationState.WALK_SIDE -> {
            // 侧走：手臂前后摆动
            rotate(degrees = armAngle * 1.5f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            rotate(degrees = -armAngle * 1.5f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
        AnimationState.STRETCH -> {
            // 伸懒腰：双手向上伸展
            rotate(degrees = -70f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY - 14f, armW, armH + 4f, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY - 18f, 8f, 5f, ArmColor)
            }
            rotate(degrees = 70f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY - 14f, armW, armH + 4f, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY - 18f, 8f, 5f, ArmColor)
            }
        }
        AnimationState.LOOK_PHONE -> {
            // 看手机：双手在胸前弯曲
            rotate(degrees = -35f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            rotate(degrees = 35f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
        AnimationState.ACCOUNTING -> {
            // 记账：左手持本，右手写字
            // 左手持本（弯曲到胸前）
            rotate(degrees = -30f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            // 右手写字
            rotate(degrees = 30f + armAngle * 0.3f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
                // 笔
                block(cx + bodyW / 2f + armW - 5f, armY + armH, 2f, 8f, PenColor)
            }
        }
        AnimationState.LISTEN_MUSIC -> {
            // 听歌：双手自然下垂微摆
            rotate(degrees = armAngle * 0.3f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY + 2f, armW, armH - 2f, ArmColor)
            }
            rotate(degrees = -armAngle * 0.3f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY + 2f, armW, armH - 2f, ArmColor)
            }
        }
        AnimationState.COUNT_MONEY -> {
            // 数钱：双手在胸前搓动
            rotate(degrees = -30f + armAngle * 0.5f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            rotate(degrees = 30f - armAngle * 0.5f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
        AnimationState.DRINK_COFFEE -> {
            // 喝咖啡：左手自然下垂，右手持杯到嘴边
            rotate(degrees = armAngle * 0.2f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            // 右手弯曲持杯到嘴边
            rotate(degrees = 45f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
        AnimationState.SNEEZE -> {
            // 打喷嚏：双手捂嘴
            rotate(degrees = -40f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            rotate(degrees = 40f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
        AnimationState.YAWN -> {
            // 打哈欠：左手自然下垂，右手放到嘴边
            rotate(degrees = armAngle * 0.2f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            // 右手弯曲到嘴边
            rotate(degrees = 50f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
        else -> {
            rotate(degrees = armAngle * 0.5f, pivot = Offset(cx - bodyW / 2f, armY)) {
                block(cx - bodyW / 2f - armW, armY, armW, armH, ArmColor)
                block(cx - bodyW / 2f - armW - 1f, armY + armH, 8f, 5f, ArmColor)
            }
            rotate(degrees = -armAngle * 0.5f, pivot = Offset(cx + bodyW / 2f, armY)) {
                block(cx + bodyW / 2f, armY, armW, armH, ArmColor)
                block(cx + bodyW / 2f + armW - 7f, armY + armH, 8f, 5f, ArmColor)
            }
        }
    }
}

// ── 腿 ────────────────────────────────────────────────────
private fun DrawScope.drawLegs(cx: Float, cy: Float, legAngle: Float, animState: AnimationState) {
    val bodyH = 56f; val legW = 6f; val legH = 10f
    val legY = cy + bodyH / 2f - 2f
    val legSpacing = 12f

    when (animState) {
        AnimationState.BOUNCING -> {
            rotate(degrees = legAngle, pivot = Offset(cx - legSpacing, legY)) {
                block(cx - legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx - legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
            rotate(degrees = -legAngle, pivot = Offset(cx + legSpacing, legY)) {
                block(cx + legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx + legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
        }
        AnimationState.EXCITED -> {
            rotate(degrees = legAngle * 0.6f, pivot = Offset(cx - legSpacing, legY)) {
                block(cx - legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx - legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
            rotate(degrees = -legAngle * 0.6f, pivot = Offset(cx + legSpacing, legY)) {
                block(cx + legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx + legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
        }
        AnimationState.SLEEPING -> {
            block(cx - legSpacing - legW / 2f + 2f, legY + 2f, legW, legH - 4f, LegColor)
            block(cx + legSpacing - legW / 2f - 2f, legY + 2f, legW, legH - 4f, LegColor)
        }
        AnimationState.WALK_SIDE -> {
            // 侧走：交替迈步
            rotate(degrees = legAngle * 1.5f, pivot = Offset(cx - legSpacing, legY)) {
                block(cx - legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx - legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
            rotate(degrees = -legAngle * 1.5f, pivot = Offset(cx + legSpacing, legY)) {
                block(cx + legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx + legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
        }
        AnimationState.LISTEN_MUSIC -> {
            // 听歌：轻踏节拍
            rotate(degrees = legAngle * 0.4f, pivot = Offset(cx - legSpacing, legY)) {
                block(cx - legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx - legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
            rotate(degrees = -legAngle * 0.4f, pivot = Offset(cx + legSpacing, legY)) {
                block(cx + legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx + legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
        }
        AnimationState.COUNT_MONEY -> {
            // 数钱：小碎步
            rotate(degrees = legAngle * 0.5f, pivot = Offset(cx - legSpacing, legY)) {
                block(cx - legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx - legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
            rotate(degrees = -legAngle * 0.5f, pivot = Offset(cx + legSpacing, legY)) {
                block(cx + legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx + legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
        }
        else -> {
            rotate(degrees = legAngle * 0.3f, pivot = Offset(cx - legSpacing, legY)) {
                block(cx - legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx - legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
            rotate(degrees = -legAngle * 0.3f, pivot = Offset(cx + legSpacing, legY)) {
                block(cx + legSpacing - legW / 2f, legY, legW, legH, LegColor)
                block(cx + legSpacing - legW / 2f - 1f, legY + legH, 8f, 4f, LegColor)
            }
        }
    }
}

// ── 道具绘制 ──────────────────────────────────────────────
private fun DrawScope.drawProps(
    cx: Float, cy: Float, animState: AnimationState,
    coinSpin: Float, phoneScroll: Float, coffeeSteam: Float,
    writePhase: Float, yawnPhase: Float
) {
    when (animState) {
        AnimationState.ACCOUNTING -> drawNotebook(cx, cy, writePhase)
        AnimationState.LISTEN_MUSIC -> drawHeadphones(cx, cy)
        AnimationState.COUNT_MONEY -> drawCoins(cx, cy, coinSpin)
        AnimationState.LOOK_PHONE -> drawPhone(cx, cy, phoneScroll)
        AnimationState.DRINK_COFFEE -> drawCoffeeCup(cx, cy, coffeeSteam)
        AnimationState.SNEEZE -> drawSneezeEffect(cx, cy)
        else -> {}
    }
}

// ── 记账本 ────────────────────────────────────────────────
private fun DrawScope.drawNotebook(cx: Float, cy: Float, writePhase: Float) {
    val bodyW = 78f
    val nbX = cx - bodyW / 2f + 8f
    val nbY = cy + 2f
    val nbW = 22f; val nbH = 28f

    // 本子
    block(nbX, nbY, nbW, nbH, NotebookColor)
    // 本子线条
    for (i in 0..4) {
        block(nbX + 3f, nbY + 5f + i * 5f, nbW - 6f, 1f, NotebookLine)
    }
    // 正在写的字（动态）
    val writeX = nbX + 3f + (writePhase + 2f) * 2f
    val writeY = nbY + 5f + (writePhase + 2f) * 1.5f
    block(writeX, writeY, 3f, 1f, PenColor)
    block(writeX + 4f, writeY + 2f, 4f, 1f, PenColor)
    // 本子装订线
    block(nbX + nbW - 3f, nbY, 3f, nbH, NotebookColor.copy(alpha = 0.7f))
}

// ── 耳机 ──────────────────────────────────────────────────
private fun DrawScope.drawHeadphones(cx: Float, cy: Float) {
    val bodyTop = cy - 56f / 2f - 2f
    val headTop = bodyTop - 15f  // 耳朵上方

    // 头箍
    block(cx - 18f, headTop - 8f, 36f, 4f, HeadphoneBand)
    // 左侧连接
    block(cx - 20f, headTop - 4f, 4f, 12f, HeadphoneBand)
    // 右侧连接
    block(cx + 16f, headTop - 4f, 4f, 12f, HeadphoneBand)
    // 左耳罩
    block(cx - 24f, headTop + 4f, 10f, 10f, HeadphonePad)
    block(cx - 22f, headTop + 6f, 6f, 6f, HeadphonePadInner)
    // 右耳罩
    block(cx + 14f, headTop + 4f, 10f, 10f, HeadphonePad)
    block(cx + 16f, headTop + 6f, 6f, 6f, HeadphonePadInner)

    // 音符效果
    val noteX = cx + 30f; val noteY = headTop - 4f
    block(noteX, noteY, 3f, 8f, NoteColor.copy(alpha = 0.6f))
    block(noteX - 2f, noteY + 7f, 5f, 3f, NoteColor.copy(alpha = 0.6f))
    block(noteX + 8f, noteY - 4f, 3f, 7f, NoteColor.copy(alpha = 0.4f))
    block(noteX + 6f, noteY + 2f, 5f, 3f, NoteColor.copy(alpha = 0.4f))
}

// ── 金币 ──────────────────────────────────────────────────
private fun DrawScope.drawCoins(cx: Float, cy: Float, coinSpin: Float) {
    val bodyW = 78f
    // 在身体前方飘浮的硬币
    val baseX = cx - 4f
    val baseY = cy - 8f

    // 硬币1（左侧）
    val c1x = baseX - 14f
    val c1y = baseY - 4f + coinSpin * 3f
    val c1w = 8f * (0.5f + 0.5f * sin(coinSpin * PI.toFloat() * 2f).let { if (it < 0) -it else it })
    block(c1x, c1y, c1w.coerceAtLeast(2f), 8f, CoinGold)
    block(c1x + 1f, c1y + 1f, (c1w - 2f).coerceAtLeast(1f), 6f, CoinDark)

    // 硬币2（中间）
    val c2x = baseX - 2f
    val c2y = baseY - 8f - coinSpin * 2f
    val c2w = 8f * (0.5f + 0.5f * sin(coinSpin * PI.toFloat() * 2f + 1f).let { if (it < 0) -it else it })
    block(c2x, c2y, c2w.coerceAtLeast(2f), 8f, CoinGold)
    block(c2x + 1f, c2y + 1f, (c2w - 2f).coerceAtLeast(1f), 6f, CoinDark)

    // 硬币3（右侧偏上）
    val c3x = baseX + 10f
    val c3y = baseY - 6f + coinSpin * 4f
    val c3w = 7f * (0.5f + 0.5f * sin(coinSpin * PI.toFloat() * 2f + 2f).let { if (it < 0) -it else it })
    block(c3x, c3y, c3w.coerceAtLeast(2f), 7f, CoinGold)
    block(c3x + 1f, c3y + 1f, (c3w - 2f).coerceAtLeast(1f), 5f, CoinDark)

    // ¥符号在硬币上
    val yenS = 2f
    block(c2x + 2f, c2y + 2f, yenS, yenS, YenColor.copy(alpha = 0.8f))
}

// ── 手机 ──────────────────────────────────────────────────
private fun DrawScope.drawPhone(cx: Float, cy: Float, scrollPhase: Float) {
    val phoneW = 14f; val phoneH = 22f
    val phoneX = cx - phoneW / 2f
    val phoneY = cy - 4f

    // 手机外壳
    block(phoneX - 1f, phoneY - 1f, phoneW + 2f, phoneH + 2f, PhoneFrame)
    // 屏幕
    block(phoneX, phoneY, phoneW, phoneH, PhoneScreen)
    // 屏幕内容滚动效果
    val scrollY = scrollPhase * 6f
    block(phoneX + 2f, phoneY + 3f + scrollY, phoneW - 4f, 2f, Color.White.copy(alpha = 0.5f))
    block(phoneX + 2f, phoneY + 7f + scrollY, phoneW - 4f, 2f, Color.White.copy(alpha = 0.4f))
    block(phoneX + 2f, phoneY + 11f + scrollY, phoneW - 4f, 2f, Color.White.copy(alpha = 0.3f))
    // 顶部状态栏
    block(phoneX, phoneY, phoneW, 2f, Color(0xFF333333).copy(alpha = 0.5f))
}

// ── 咖啡杯 ────────────────────────────────────────────────
private fun DrawScope.drawCoffeeCup(cx: Float, cy: Float, steamPhase: Float) {
    val cupW = 12f; val cupH = 14f
    val cupX = cx + 16f  // 右手位置
    val cupY = cy - 12f

    // 杯身
    block(cupX, cupY, cupW, cupH, CoffeeCup)
    // 咖啡液面
    block(cupX + 1f, cupY + 3f, cupW - 2f, cupH - 4f, CoffeeLiquid)
    // 杯把手
    block(cupX + cupW, cupY + 3f, 4f, 6f, CoffeeCup)
    block(cupX + cupW + 1f, cupY + 4f, 2f, 4f, CoffeeLiquid.copy(alpha = 0.3f))

    // 蒸汽
    val steamY1 = cupY - 3f - steamPhase * 6f
    val steamY2 = cupY - 5f - steamPhase * 8f
    block(cupX + 2f, steamY1, 3f, 3f, CoffeeSteam)
    block(cupX + 7f, steamY2, 3f, 3f, CoffeeSteam.copy(alpha = 0.4f))
    block(cupX + 4f, steamY1 - 4f, 2f, 2f, CoffeeSteam.copy(alpha = 0.3f))
}

// ── 打喷嚏效果 ────────────────────────────────────────────
private fun DrawScope.drawSneezeEffect(cx: Float, cy: Float) {
    // 飞溅粒子
    val faceY = cy - 4f
    val effectX = cx + 20f
    val effectY = faceY + 2f

    // 气雾
    block(effectX, effectY - 2f, 6f, 4f, Color(0x33FFFFFF))
    block(effectX + 4f, effectY - 4f, 5f, 3f, Color(0x22FFFFFF))
    block(effectX + 6f, effectY, 4f, 3f, Color(0x18FFFFFF))
    // 小星星
    block(effectX + 2f, effectY - 6f, 2f, 2f, DizzyColor.copy(alpha = 0.5f))
    block(effectX + 8f, effectY - 3f, 2f, 2f, DizzyColor.copy(alpha = 0.4f))
}
