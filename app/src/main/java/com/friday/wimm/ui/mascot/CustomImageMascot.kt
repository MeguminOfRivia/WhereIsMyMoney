package com.friday.wimm.ui.mascot

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 用户自定义形象：一张照片或 GIF，可切换「果冻 Q 弹」与「张口差分」两种动画，还能眨眼。
 *
 * 实现要点：
 *  - 不引入任何图片库：Android 9+ 用系统的 [ImageDecoder]，可直接解出会动的 GIF；
 *  - Q 弹效果用 `spring(dampingRatio ≈ 0.3)`，弹簧自然会过冲，不需要手写关键帧；
 *  - 挤压时按体积守恒换算横向缩放（`sx = 1/√sy`），看起来才像橡皮而不是压扁；
 *  - 变换原点在**底部中心**，所以是"脚不动、身体弹"，不是整体缩放；
 *  - 眨眼：真人平均每分钟眨 15～20 次（约 3～4 秒一次），每次闭合 100～150 毫秒，
 *    所以这里用 2.8～6.5 秒的随机间隔 + 130 毫秒闭眼，另有约两成概率连眨两下。
 */
@Composable
fun CustomImageMascot(
    imageUri: String?,
    mouthUri: String?,
    animMode: MascotAnimMode,
    clickJelly: Boolean,
    speechVisible: Boolean,
    clickTick: Int,
    flipHorizontal: Boolean = false,
    blinkUri: String? = null,
    blinkEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val image by produceState<Drawable?>(initialValue = null, imageUri) {
        // 用户还没选图时，用 App 内置的默认形象（已抠成透明背景）
        value = MascotImageLoader.load(context, imageUri) ?: MascotImageLoader.loadBundled(context)
    }
    val mouth by produceState<Drawable?>(initialValue = null, mouthUri) {
        value = MascotImageLoader.load(context, mouthUri)
    }
    val blink by produceState<Drawable?>(initialValue = null, blinkUri) {
        value = MascotImageLoader.load(context, blinkUri)
    }

    // 眨眼状态：闭眼的那一帧只保持很短时间
    var blinking by remember { mutableStateOf(false) }
    val blinkReady = blinkEnabled && blink != null
    LaunchedEffect(blinkReady) {
        if (!blinkReady) {
            blinking = false
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(2800L, 6500L))
            blinking = true
            delay(130L)
            blinking = false
            // 大约两成概率连眨两下，更像活物
            if (Random.nextFloat() < 0.22f) {
                delay(110L)
                blinking = true
                delay(120L)
                blinking = false
            }
        }
    }

    val showMouth = animMode == MascotAnimMode.MOUTH && speechVisible && mouth != null
    val current = when {
        blinking && blink != null -> blink
        showMouth -> mouth
        else -> image
    }

    val squash = remember { Animatable(1f) }

    // 对话出现：抬头 Q 弹（张口模式只做很轻的呼吸，主要靠换图）
    LaunchedEffect(speechVisible) {
        if (!speechVisible) return@LaunchedEffect
        val depth = if (animMode == MascotAnimMode.JELLY) 0.86f else 0.95f
        squash.snapTo(depth)
        squash.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.32f, stiffness = 420f)
        )
    }

    // 点击反馈
    LaunchedEffect(clickTick) {
        if (clickTick == 0 || !clickJelly) return@LaunchedEffect
        squash.snapTo(0.82f)
        squash.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.26f, stiffness = 520f)
        )
    }

    // 注意：不能叫 scaleX/scaleY，会和 graphicsLayer 作用域里的同名属性打架
    val squashY = squash.value
    val squashX = 1f / sqrt(squashY)

    Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.BottomCenter) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 贴边方向变化时整张图水平镜像，保证"同一侧始终靠墙"
                    scaleX = squashX * if (flipHorizontal) -1f else 1f
                    scaleY = squashY
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { view ->
                view.setImageDrawable(current)
                (current as? android.graphics.drawable.Animatable)?.start()
            }
        )
    }
}

/**
 * 把用户选的图片 Uri 解成可绘制的 [Drawable]。
 *
 * - Android 9+：`ImageDecoder` 可以直接把 GIF 解成 `AnimatedImageDrawable`，自动播放；
 * - Android 8.x：退回 `BitmapFactory`，GIF 只显示第一帧；
 * - 结果按 Uri 缓存，避免每次重组都重新解码。
 */
object MascotImageLoader {

    /** 超过 20MB 的图片直接拒绝，防止 OOM */
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024

    /** 最长边超过 900px 就按比例缩小，悬浮窗根本用不到那么大 */
    private const val MAX_TARGET_PX = 900

    private val cache = object : LruCache<String, Drawable>(4) {}

    fun clearCache() = cache.evictAll()

    /** App 内置的默认形象（像素小猫，背景已抠成透明） */
    fun loadBundled(context: Context): Drawable? =
        androidx.core.content.ContextCompat.getDrawable(
            context,
            com.friday.wimm.R.drawable.mascot_default
        )

    suspend fun load(context: Context, uriString: String?): Drawable? = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext null
        cache.get(uriString)?.let { return@withContext it }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext null
        if (queryFileSize(context, uri) > MAX_FILE_BYTES) return@withContext null

        val drawable = runCatching { decode(context, uri) }.getOrNull()
        if (drawable != null) cache.put(uriString, drawable)
        drawable
    }

    private fun decode(context: Context, uri: Uri): Drawable? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > MAX_TARGET_PX) {
                    val ratio = MAX_TARGET_PX.toFloat() / longest
                    // 部分动图不支持缩放，失败就按原尺寸
                    runCatching {
                        decoder.setTargetSize(
                            (width * ratio).toInt().coerceAtLeast(1),
                            (height * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                }
            }
        }

        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)?.let { BitmapDrawable(context.resources, it) }
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
    }.getOrDefault(0L)
}
