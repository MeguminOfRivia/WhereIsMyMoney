package com.friday.wimm.ui.mascot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.friday.wimm.data.database.DatabaseHelper
import com.friday.wimm.data.database.FinanceDao
import com.friday.wimm.data.database.LedgerDao
import kotlinx.coroutines.*
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

private val BubbleBg = Color(0xFFFFFFFF)
private val BubbleText = Color(0xFF333333)

class MascotService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val mascotState = MascotStateHolder()
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateRegistryController: SavedStateRegistryController
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var windowParams: WindowManager.LayoutParams? = null
    private var _scale by mutableFloatStateOf(1.0f)
    private val scale: Float get() = _scale

    // ── 形象外观 ──────────────────────────────────────────
    // 跨进程不靠 SharedPreferences 的进程内缓存（它不会自动刷新），
    // 而是主进程把设置塞进广播的 extras 里发过来，这里直接覆盖内存值。
    private var _style by mutableStateOf(MascotStyle.CUSTOM)
    private var _imageUri by mutableStateOf<String?>(null)
    private var _mouthUri by mutableStateOf<String?>(null)
    private var _blinkUri by mutableStateOf<String?>(null)
    private var _blinkEnabled by mutableStateOf(true)
    private var _animMode by mutableStateOf(MascotAnimMode.JELLY)
    private var _clickJelly by mutableStateOf(true)

    // ── 行为开关 ──────────────────────────────────────────
    private var _randomMove by mutableStateOf(true)
    private var _speech by mutableStateOf(true)
    private var _eyeTrack by mutableStateOf(true)
    private var _eyeTrackProb by mutableFloatStateOf(1f)

    // ── 贴边吸附 ──────────────────────────────────────────
    private var _snapEnabled by mutableStateOf(true)
    private var _snapEdge by mutableStateOf(MascotSnapEdge.LEFT)
    private var _side by mutableStateOf(MascotSide.NONE)
    private var _vAnchor by mutableStateOf(MascotVAnchor.NONE)

    // ── 预算面板（双击看今天 / 长按看本月） ────────────────
    private var _panel by mutableStateOf<BudgetPanelData?>(null)
    private var _panelMode by mutableStateOf(BudgetPanelMode.TODAY)
    private var panelHideJob: Job? = null

    /**
     * Wally 跑在 `:mascot` 进程里，读账本要自己开一份数据库连接。
     * 只读，不写，避免和主进程互相锁住。
     */
    private val budgetDb by lazy { DatabaseHelper(applicationContext) }
    private val budgetFinanceDao by lazy { FinanceDao(budgetDb) }
    private val budgetLedgerDao by lazy { LedgerDao(budgetDb) }

    // 单击 / 双击判定
    private var lastTapAt = 0L
    private var singleTapJob: Job? = null

    /** 是否正在拖拽：拖拽期间禁止其它地方改窗口位置，否则会跟手指抢 */
    private var windowDragging = false

    /** 贴边动画的 Job：开始拖拽时要先取消，不然会跟手指抢 */
    private var windowAnimJob: Job? = null

    /**
     * 自定义形象始终有内置图兜底，所以不再回退到 Canvas 形象。
     */
    private fun effectiveStyle(): MascotStyle = _style

    /**
     * 当「贴着的边」和「图片哪一边该贴边」不一致时，水平镜像。
     *
     * 例：用户选的图要「右边贴边」，此时贴在左侧，就必须翻过来。
     */
    private fun shouldFlip(): Boolean = when (_side) {
        MascotSide.LEFT -> _snapEdge == MascotSnapEdge.RIGHT
        MascotSide.RIGHT -> _snapEdge == MascotSnapEdge.LEFT
        MascotSide.NONE -> false
    }

    private fun reloadAppearance() {
        _scale = MascotPrefs.clampScale(prefs.getFloat(MascotPrefs.KEY_SCALE, 1.0f))
        _style = MascotStyle.from(prefs.getString(MascotPrefs.KEY_STYLE, MascotStyle.CUSTOM.value))
        _imageUri = prefs.getString(MascotPrefs.KEY_IMAGE_URI, null)
        _mouthUri = prefs.getString(MascotPrefs.KEY_MOUTH_URI, null)
        _blinkUri = prefs.getString(MascotPrefs.KEY_BLINK_URI, null)
        _blinkEnabled = prefs.getBoolean(MascotPrefs.KEY_BLINK_ENABLED, true)
        _animMode = MascotAnimMode.from(
            prefs.getString(MascotPrefs.KEY_ANIM_MODE, MascotAnimMode.JELLY.value)
        )
        _clickJelly = prefs.getBoolean(MascotPrefs.KEY_CLICK_JELLY, true)
        _snapEnabled = prefs.getBoolean(MascotPrefs.KEY_SNAP_ENABLED, true)
        _snapEdge = MascotSnapEdge.from(
            prefs.getString(MascotPrefs.KEY_SNAP_EDGE, MascotSnapEdge.LEFT.value)
        )
        _randomMove = prefs.getBoolean(MascotPrefs.KEY_RANDOM_MOVE, true)
        _speech = prefs.getBoolean(MascotPrefs.KEY_SPEECH, true)
        _eyeTrack = prefs.getBoolean(MascotPrefs.KEY_EYE_TRACK, true)
        _eyeTrackProb = prefs.getFloat(MascotPrefs.KEY_EYE_TRACK_PROB, 1.0f)
        if (!_snapEnabled) {
            _side = MascotSide.NONE
            _vAnchor = MascotVAnchor.NONE
        }
    }

    /**
     * 用广播里带过来的设置覆盖内存值。
     *
     * 只覆盖「带了这个 extra」的项，这样同一条广播既能改全部设置，
     * 也能只改一项，不会把没提到的设置清空。
     */
    private fun applySettings(intent: Intent) {
        if (intent.hasExtra(EXTRA_SCALE)) {
            _scale = MascotPrefs.clampScale(intent.getFloatExtra(EXTRA_SCALE, _scale))
        }
        if (intent.hasExtra(EXTRA_STYLE)) {
            _style = MascotStyle.from(intent.getStringExtra(EXTRA_STYLE))
        }
        if (intent.hasExtra(EXTRA_IMAGE_URI)) {
            _imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        }
        if (intent.hasExtra(EXTRA_MOUTH_URI)) {
            _mouthUri = intent.getStringExtra(EXTRA_MOUTH_URI)
        }
        if (intent.hasExtra(EXTRA_BLINK_URI)) {
            _blinkUri = intent.getStringExtra(EXTRA_BLINK_URI)
        }
        if (intent.hasExtra(EXTRA_BLINK_ENABLED)) {
            _blinkEnabled = intent.getBooleanExtra(EXTRA_BLINK_ENABLED, true)
        }
        if (intent.hasExtra(EXTRA_ANIM_MODE)) {
            _animMode = MascotAnimMode.from(intent.getStringExtra(EXTRA_ANIM_MODE))
        }
        if (intent.hasExtra(EXTRA_CLICK_JELLY)) {
            _clickJelly = intent.getBooleanExtra(EXTRA_CLICK_JELLY, true)
        }
        if (intent.hasExtra(EXTRA_SNAP_ENABLED)) {
            val enabled = intent.getBooleanExtra(EXTRA_SNAP_ENABLED, true)
            _snapEnabled = enabled
            if (!enabled) {
                _side = MascotSide.NONE
                _vAnchor = MascotVAnchor.NONE
            }
        }
        if (intent.hasExtra(EXTRA_SNAP_EDGE)) {
            _snapEdge = MascotSnapEdge.from(intent.getStringExtra(EXTRA_SNAP_EDGE))
        }
        if (intent.hasExtra(EXTRA_RANDOM_MOVE)) {
            _randomMove = intent.getBooleanExtra(EXTRA_RANDOM_MOVE, true)
        }
        if (intent.hasExtra(EXTRA_SPEECH)) {
            _speech = intent.getBooleanExtra(EXTRA_SPEECH, true)
        }
        if (intent.hasExtra(EXTRA_EYE_TRACK)) {
            _eyeTrack = intent.getBooleanExtra(EXTRA_EYE_TRACK, true)
        }
        if (intent.hasExtra(EXTRA_EYE_TRACK_PROB)) {
            _eyeTrackProb = intent.getFloatExtra(EXTRA_EYE_TRACK_PROB, 1f)
        }
    }

    private val prefs by lazy { getSharedPreferences(MascotPrefs.NAME, Context.MODE_PRIVATE) }

    private fun isRandomMoveEnabled(): Boolean = _randomMove
    private fun isSpeechEnabled(): Boolean = _speech
    private fun isEyeTrackEnabled(): Boolean = _eyeTrack
    private fun getEyeTrackProb(): Float = _eyeTrackProb

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val CHANNEL_ID = "mascot_service"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_SCALE = "scale"
        private const val EXTRA_STYLE = "style"
        private const val EXTRA_IMAGE_URI = "image_uri"
        private const val EXTRA_MOUTH_URI = "mouth_uri"
        private const val EXTRA_BLINK_URI = "blink_uri"
        private const val EXTRA_BLINK_ENABLED = "blink_enabled"
        private const val EXTRA_ANIM_MODE = "anim_mode"
        private const val EXTRA_CLICK_JELLY = "click_jelly"
        private const val EXTRA_SNAP_ENABLED = "snap_enabled"
        private const val EXTRA_SNAP_EDGE = "snap_edge"
        private const val EXTRA_RANDOM_MOVE = "random_move"
        private const val EXTRA_SPEECH = "speech"
        private const val EXTRA_EYE_TRACK = "eye_track"
        private const val EXTRA_EYE_TRACK_PROB = "eye_track_prob"
        const val ACTION_NOTIFICATION_EVENT = "com.friday.wimm.MASCOT_NOTIFICATION"
        const val EXTRA_IS_INCOME = "is_income"
        const val EXTRA_MERCHANT = "merchant"
        const val ACTION_SETTINGS_CHANGED = "com.friday.wimm.MASCOT_SETTINGS_CHANGED"

        /** 窗口最窄也要留一点，免得缩到 10% 时手指完全点不到 */
        private const val MIN_WINDOW_W = 56f

        const val MASCOT_W = 184
        const val MASCOT_H = 100
        const val BUBBLE_H = 70

        /** 离边框多近就吸附（dp）：做得很小，只有主动拖到边上才会吸 */
        private const val SNAP_THRESHOLD_DP = 18f
        /** 两次抬手间隔小于这个值就算双击 */
        private const val DOUBLE_TAP_WINDOW_MS = 280L

        /** 按住多久算长按 */
        private const val LONG_PRESS_MS = 550L

        /** 预算面板自动收起时间 */
        private const val PANEL_AUTO_HIDE_MS = 9_000L

        fun start(context: Context, scale: Float) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, MascotService::class.java).apply {
                putExtra(EXTRA_SCALE, scale)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MascotService::class.java))
        }

        fun updateScale(context: Context, scale: Float) {
            val intent = Intent(context, MascotService::class.java).apply {
                putExtra(EXTRA_SCALE, scale)
            }
            context.startService(intent)
        }

        fun notifyTransaction(context: Context, isIncome: Boolean, merchant: String) {
            val intent = Intent(ACTION_NOTIFICATION_EVENT).apply {
                putExtra(EXTRA_IS_INCOME, isIncome)
                putExtra(EXTRA_MERCHANT, merchant)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        /**
         * 把当前设置打包进广播。
         *
         * 不用「发个空广播让对方自己去读 SharedPreferences」——Android 的 SharedPreferences
         * 是按进程缓存的，跨进程读到的永远是旧值（MODE_MULTI_PROCESS 在 7.0 之后已失效）。
         * 所以这里把值直接带过去，Wally 收到就立刻变。
         */
        fun notifySettingsChanged(context: Context) {
            val p = context.getSharedPreferences(MascotPrefs.NAME, Context.MODE_PRIVATE)
            val intent = Intent(ACTION_SETTINGS_CHANGED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_SCALE, MascotPrefs.clampScale(p.getFloat(MascotPrefs.KEY_SCALE, 1.0f)))
                putExtra(
                    EXTRA_STYLE,
                    p.getString(MascotPrefs.KEY_STYLE, MascotStyle.CUSTOM.value)
                )
                putExtra(EXTRA_IMAGE_URI, p.getString(MascotPrefs.KEY_IMAGE_URI, null))
                putExtra(EXTRA_MOUTH_URI, p.getString(MascotPrefs.KEY_MOUTH_URI, null))
                putExtra(EXTRA_BLINK_URI, p.getString(MascotPrefs.KEY_BLINK_URI, null))
                putExtra(
                    EXTRA_BLINK_ENABLED,
                    p.getBoolean(MascotPrefs.KEY_BLINK_ENABLED, true)
                )
                putExtra(
                    EXTRA_ANIM_MODE,
                    p.getString(MascotPrefs.KEY_ANIM_MODE, MascotAnimMode.JELLY.value)
                )
                putExtra(
                    EXTRA_CLICK_JELLY,
                    p.getBoolean(MascotPrefs.KEY_CLICK_JELLY, true)
                )
                putExtra(
                    EXTRA_SNAP_ENABLED,
                    p.getBoolean(MascotPrefs.KEY_SNAP_ENABLED, true)
                )
                putExtra(
                    EXTRA_SNAP_EDGE,
                    p.getString(MascotPrefs.KEY_SNAP_EDGE, MascotSnapEdge.LEFT.value)
                )
                putExtra(
                    EXTRA_RANDOM_MOVE,
                    p.getBoolean(MascotPrefs.KEY_RANDOM_MOVE, true)
                )
                putExtra(EXTRA_SPEECH, p.getBoolean(MascotPrefs.KEY_SPEECH, true))
                putExtra(EXTRA_EYE_TRACK, p.getBoolean(MascotPrefs.KEY_EYE_TRACK, true))
                putExtra(
                    EXTRA_EYE_TRACK_PROB,
                    p.getFloat(MascotPrefs.KEY_EYE_TRACK_PROB, 1.0f)
                )
            }
            context.sendBroadcast(intent)
        }
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_NOTIFICATION_EVENT -> {
                    val isIncome = intent.getBooleanExtra(EXTRA_IS_INCOME, false)
                    if (isSpeechEnabled()) {
                        showSpeech(MascotSpeech.randomNotification(isIncome))
                    }
                    mascotState.animationState = AnimationState.EXCITED
                    serviceScope.launch {
                        delay(3000)
                        if (mascotState.animationState == AnimationState.EXCITED) {
                            mascotState.animationState = AnimationState.IDLE
                        }
                    }
                }
                ACTION_SETTINGS_CHANGED -> {
                    intent?.let { applySettings(it) }
                    // 换了图片要把解码缓存清掉，否则同一路径改了内容还是旧图
                    MascotImageLoader.clearCache()
                    updateMascotSize(_scale)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(ACTION_NOTIFICATION_EVENT)
            addAction(ACTION_SETTINGS_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }

        reloadAppearance()
    }

    private var lastGreetingHour = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newScale = intent?.getFloatExtra(EXTRA_SCALE, scale) ?: scale
        _scale = MascotPrefs.clampScale(newScale)

        if (composeView == null) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            showMascot()
            startIdleLoop()
            startRandomMoveLoop()
            startGreetingLoop()
            // 启动时问候
            if (isSpeechEnabled()) {
                showSpeech(MascotSpeech.randomGreeting())
            }
        } else {
            updateMascotSize(scale)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Wally 桌宠服务", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 Wally 在桌面显示"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wally 运行中")
            .setContentText("Wally 正在桌面上陪伴你~")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showMascot() {
        if (!Settings.canDrawOverlays(this)) return

        val params = createLayoutParams(scale)
        windowParams = params

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MascotService)
            setViewTreeSavedStateRegistryOwner(this@MascotService)
            clipChildren = false
            clipToPadding = false
            setContent {
                MascotContent()
            }
        }

        try {
            windowManager.addView(composeView, params)
        } catch (_: Exception) {}

        // 标定悬浮窗坐标原点：窗口的屏幕位置 − params.y = 状态栏高度。
        // 之后判断「贴底」时要用它把可用高度算准（见 [contentHeightPx]）。
        composeView?.post {
            runCatching {
                val location = IntArray(2)
                composeView?.getLocationOnScreen(location)
                containerTopPx = location[1] - params.y
            }
        }

        // 初始位置固定在左上角附近，默认视为贴在左侧
        _side = if (_snapEnabled && params.x <= 0) MascotSide.LEFT else MascotSide.NONE
    }

    @Composable
    private fun MascotContent() {
        val coroutineScope = rememberCoroutineScope()

        val currentScale = scale
        val box = MascotLayout.boxFor(effectiveStyle())
        val panel = _panel
        val side = _side
        val vAnchor = _vAnchor
        val speechVisible = mascotState.speechText != null

        val mascotW = box.width.value * currentScale
        val mascotH = box.height.value * currentScale
        // 没有对话时上方不预留空间，这样贴在顶边时形象能真的顶到屏幕边缘
        val bubbleH = if (speechVisible) MascotLayout.BUBBLE_H * currentScale else 0f
        val panelH = if (panel != null) MascotLayout.BUDGET_PANEL_H else 0f
        // 贴着顶边时，气泡改到形象下方出现（上面已经没有空间了）
        val bubbleBelow = vAnchor == MascotVAnchor.TOP
        // 贴着底边时，预算面板改到形象上方出现，否则会被屏幕底边裁掉
        val panelOnTop = vAnchor == MascotVAnchor.BOTTOM
        val totalH = bubbleH + mascotH + panelH
        val bubbleY = when {
            panelOnTop -> panelH
            bubbleBelow -> mascotH
            else -> 0f
        }
        val mascotY = when {
            panelOnTop -> panelH + bubbleH
            bubbleBelow -> 0f
            else -> bubbleH
        }
        val panelY = if (panelOnTop) 0f else totalH - panelH

        // 贴着哪一边，内容就靠哪一边。
        // 这一步是「真的贴边」的关键：窗口本身可能比形象宽（要放预算面板），
        // 如果内容居中，形象离屏幕边框会永远差半个窗口的宽度。
        val contentAlign = when (side) {
            MascotSide.LEFT -> Alignment.TopStart
            MascotSide.RIGHT -> Alignment.TopEnd
            MascotSide.NONE -> Alignment.TopCenter
        }

        // 调整窗口大小，贴边时重新对齐（上下左右都算）
        LaunchedEffect(panel, currentScale, box, side, vAnchor, speechVisible) {
            val view = composeView ?: return@LaunchedEffect
            val params = windowParams ?: return@LaunchedEffect
            // 拖拽过程中绝对不许动窗口位置，否则会和手指抢、看起来像「乱飞」
            if (windowDragging) return@LaunchedEffect
            val density = resources.displayMetrics.density
            val screenW = resources.displayMetrics.widthPixels
            val screenH = contentHeightPx()
            val oldWidth = params.width
            val oldHeight = params.height
            val newWidth = (windowWidthDp(box, currentScale, panel != null) * density).toInt()
            val newHeight = (totalH * density).toInt()

            if (_snapEnabled) {
                // 贴左/右要把 x 顶回去；贴上/下要把 y 顶回去，窗口变形后也不会飘开
                when (side) {
                    MascotSide.LEFT -> params.x = 0
                    MascotSide.RIGHT -> params.x = screenW - newWidth
                    // 没贴边时，宽度变化（比如弹出预算面板）会让居中的形象左右跳，
                    // 把窗口反向挪半个宽度差，形象就待在原地
                    MascotSide.NONE -> if (oldWidth > 0) params.x -= (newWidth - oldWidth) / 2
                }
                when (vAnchor) {
                    MascotVAnchor.TOP -> params.y = 0
                    MascotVAnchor.BOTTOM -> params.y = screenH - newHeight
                    // 气泡出现在形象上方时窗口是向上长的（y 不动、内容下移），
                    // 这里把 y 反向补偿，保证「说话时形象不动，只有气泡冒出来」
                    MascotVAnchor.NONE -> if (oldHeight > 0) params.y -= newHeight - oldHeight
                }
            }
            params.width = newWidth
            params.height = newHeight
            // 没贴底时，窗口别越过屏幕下沿
            if (params.y + params.height > screenH - 8) {
                params.y = (screenH - params.height - 8).coerceAtLeast(0)
            }
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }

        val containerW = windowWidthDp(box, currentScale, panel != null)
        Box(
            modifier = Modifier.size(containerW.dp, totalH.dp)
        ) {
            // 气泡（贴顶边时在下方，其余情况在上方）
            if (speechVisible) {
                Box(
                    modifier = Modifier
                        .size(mascotW.dp, bubbleH.dp)
                        .align(contentAlign)
                        .offset(y = bubbleY.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = (6 * currentScale).dp, vertical = (4 * currentScale).dp)
                            .background(BubbleBg, RoundedCornerShape((12 * currentScale).dp))
                            .padding(horizontal = (10 * currentScale).dp, vertical = (6 * currentScale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mascotState.speechText!!,
                            fontSize = (13 * currentScale).sp,
                            color = BubbleText,
                            maxLines = 2,
                            lineHeight = (18 * currentScale).sp
                        )
                    }
                }
            }

            // 桌宠区域（中间）+ 交互层
            Box(
                modifier = Modifier
                    .size((box.width.value * currentScale).dp, (box.height.value * currentScale).dp)
                    .align(contentAlign)
                    .offset(y = mascotY.dp)
                    .pointerInput(currentScale) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent()
                                val downChange = down.changes.firstOrNull() ?: continue
                                if (!downChange.pressed) continue
                                // 保险：上一次手势如果没收尾，这里重置
                                windowDragging = false

                                val downTime = System.currentTimeMillis()
                                var totalDragDist = 0f
                                var isDragging = false
                                var longPressTriggered = false
                                var prevAngle = 0f
                                var totalAngleChange = 0f
                                var angleInitialized = false
                                // 上一次的「屏幕坐标」，用于算真实位移
                                var lastRawX = (windowParams?.x ?: 0) + downChange.position.x
                                var lastRawY = (windowParams?.y ?: 0) + downChange.position.y

                                if (isEyeTrackEnabled() && Random.nextFloat() < getEyeTrackProb()) {
                                    updateEyeOffset(downChange.position.x, downChange.position.y)
                                }

                                val longPressJob = coroutineScope.launch {
                                    delay(LONG_PRESS_MS)
                                    if (!isDragging && !longPressTriggered) {
                                        longPressTriggered = true
                                        // 长按 = 看本月
                                        showBudgetPanel(BudgetPanelMode.MONTH)
                                    }
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        longPressJob.cancel()
                                        val elapsed = System.currentTimeMillis() - downTime
                                        if (isDragging && kotlin.math.abs(totalAngleChange) > 360f) {
                                            triggerDizzy()
                                        } else if (!isDragging && !longPressTriggered && elapsed < 500L) {
                                            handleTap()
                                        }
                                        // 松手：如果靠近边框就吸附过去
                                        // 松手：先结束拖拽状态，再判断要不要贴边
                                        if (isDragging) {
                                            windowDragging = false
                                            snapToNearestEdge()
                                        }

                                        serviceScope.launch {
                                            delay(1500)
                                            mascotState.eyeOffsetX = 0f
                                            mascotState.eyeOffsetY = 0f
                                        }
                                        break
                                    }

                                    // 用「屏幕坐标」算位移，不能用 change.positionChange()。
                                    // 因为窗口是跟着手指移动的，局部坐标会把窗口自身的位移抵消掉，
                                    // 结果窗口只按手指速度的一半走，拖快了还会被系统判成出界而中断。
                                    val win = windowParams
                                    val rawX = (win?.x ?: 0) + change.position.x
                                    val rawY = (win?.y ?: 0) + change.position.y
                                    val dx = rawX - lastRawX
                                    val dy = rawY - lastRawY
                                    lastRawX = rawX
                                    lastRawY = rawY
                                    val deltaDist = sqrt(dx * dx + dy * dy)
                                    totalDragDist += deltaDist

                                    if (totalDragDist > 20f) {
                                        longPressJob.cancel()
                                        if (!isDragging) {
                                            isDragging = true
                                            // 一开始拖就清掉贴边锚点：
                                            // 否则它一说话（气泡改变窗口高度）窗口就会被拉回边框
                                            _side = MascotSide.NONE
                                            _vAnchor = MascotVAnchor.NONE
                                            windowDragging = true
                                            windowAnimJob?.cancel()
                                        }

                                        if (isEyeTrackEnabled() && deltaDist > 1f) {
                                            val norm = sqrt(dx * dx + dy * dy)
                                            if (norm > 0) {
                                                mascotState.eyeOffsetX = (dx / norm).coerceIn(-1f, 1f)
                                                mascotState.eyeOffsetY = (dy / norm).coerceIn(-1f, 1f)
                                            }
                                        }

                                        val currentAngle = atan2(dy, dx) * 180f / kotlin.math.PI.toFloat()
                                        if (angleInitialized) {
                                            var deltaAngle = currentAngle - prevAngle
                                            if (deltaAngle > 180f) deltaAngle -= 360f
                                            if (deltaAngle < -180f) deltaAngle += 360f
                                            totalAngleChange += deltaAngle
                                        } else {
                                            angleInitialized = true
                                        }
                                        prevAngle = currentAngle
                                    }

                                    if (isDragging && deltaDist > 0.5f) {
                                        val params = windowParams ?: break
                                        params.x += dx.toInt()
                                        params.y += dy.toInt()
                                        try {
                                            windowManager.updateViewLayout(composeView, params)
                                        } catch (_: Exception) {}
                                    }

                                    change.consume()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                when (effectiveStyle()) {
                    MascotStyle.BUILTIN -> FlyingBillMascot(
                        scale = currentScale,
                        state = mascotState
                    )

                    MascotStyle.CUSTOM -> CustomImageMascot(
                        imageUri = _imageUri,
                        mouthUri = _mouthUri,
                        blinkUri = _blinkUri,
                        blinkEnabled = _blinkEnabled,
                        animMode = _animMode,
                        clickJelly = _clickJelly,
                        speechVisible = mascotState.speechText != null,
                        clickTick = mascotState.clickTick,
                        flipHorizontal = shouldFlip(),
                        alignSide = side,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 预算速览：双击看今天，长按看本月
            if (panel != null) {
                Box(
                    modifier = Modifier
                        .align(contentAlign)
                        .offset(y = panelY.dp)
                        .width(MascotLayout.BUDGET_PANEL_W.dp)
                ) {
                    BudgetPanelCard(
                        data = panel,
                        mode = _panelMode,
                        onClose = { hideBudgetPanel() }
                    )
                }
            }
        }
    }

    // ── 手势：单击聊天 / 双击·长按看预算 ────────────────────

    /**
     * 抬手后先等 [DOUBLE_TAP_WINDOW_MS] 再决定是「单击」还是「双击」。
     *
     * 这样双击不会先触发一次单击的随机动作，用户不会觉得「我刚点两下，它先乱动了一下」。
     */
    private fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapAt in 1L..DOUBLE_TAP_WINDOW_MS) {
            lastTapAt = 0L
            singleTapJob?.cancel()
            // 双击 = 看今天
            showBudgetPanel(BudgetPanelMode.TODAY)
        } else {
            lastTapAt = now
            singleTapJob?.cancel()
            singleTapJob = serviceScope.launch {
                delay(DOUBLE_TAP_WINDOW_MS)
                onSingleTap()
            }
        }
    }

    /** 单击：Q 弹一下 + 说一句和记账有关的小话（不报数字） */
    private fun onSingleTap() {
        mascotState.clickTick += 1
        playRandomAnimation()
        if (!isSpeechEnabled()) return
        serviceScope.launch {
            val line = withContext(Dispatchers.IO) {
                runCatching {
                    val data = loadBudgetPanelData(budgetFinanceDao, budgetLedgerDao)
                    MascotSpeech.smallTalk(
                        hasBudget = data.hasBudget,
                        overBudgetToday = data.hasBudget && data.todayRemainingCents < 0
                    )
                }.getOrElse { MascotSpeech.randomClick() }
            }
            showSpeech(line)
        }
    }

    /** 随机播一个动作，但不说话（话由单击自己决定） */
    private fun playRandomAnimation() {
        val states = listOf(
            AnimationState.EXCITED,
            AnimationState.BOUNCING,
            AnimationState.ACCOUNTING,
            AnimationState.LOOK_PHONE,
            AnimationState.COUNT_MONEY
        )
        val state = states.random()
        mascotState.animationState = state
        serviceScope.launch {
            val duration = if (state == AnimationState.EXCITED || state == AnimationState.BOUNCING) {
                1800L
            } else {
                3500L
            }
            delay(duration)
            if (mascotState.animationState == state) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    // ── 预算面板 ──────────────────────────────────────────

    /** 双击弹「今天」，长按弹「本月」 */
    private fun showBudgetPanel(mode: BudgetPanelMode) {
        _panelMode = mode
        panelHideJob?.cancel()
        serviceScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching { loadBudgetPanelData(budgetFinanceDao, budgetLedgerDao) }.getOrNull()
            }
            if (data != null) _panel = data
        }
        panelHideJob = serviceScope.launch {
            delay(PANEL_AUTO_HIDE_MS)
            _panel = null
        }
    }

    private fun hideBudgetPanel() {
        panelHideJob?.cancel()
        _panel = null
    }

    private fun updateEyeOffset(touchX: Float, touchY: Float) {
        val density = resources.displayMetrics.density
        val centerX = MASCOT_W * scale * density / 2f
        val centerY = MASCOT_H * scale * density / 2f

        val dx = touchX - centerX
        val dy = touchY - centerY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > 0) {
            mascotState.eyeOffsetX = (dx / dist).coerceIn(-1f, 1f)
            mascotState.eyeOffsetY = (dy / dist).coerceIn(-1f, 1f)
        }
    }

    private fun showSpeech(text: String) {
        mascotState.speechText = text
        serviceScope.launch {
            delay(3000)
            if (mascotState.speechText == text) {
                mascotState.speechText = null
            }
        }
    }

    private fun triggerDizzy() {
        mascotState.isDizzy = true
        mascotState.animationState = AnimationState.DIZZY
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomDizzy())
        }
        serviceScope.launch {
            delay(3000)
            mascotState.isDizzy = false
            if (mascotState.animationState == AnimationState.DIZZY) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    private fun startIdleLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(Random.nextLong(20000, 45000))
                val randomStates = listOf(
                    AnimationState.IDLE,
                    AnimationState.SPINNING,
                    AnimationState.BOUNCING,
                    AnimationState.SLEEPING,
                    AnimationState.WALK_SIDE,
                    AnimationState.YAWN,
                    AnimationState.STRETCH,
                    AnimationState.LOOK_PHONE,
                    AnimationState.SNEEZE,
                    AnimationState.ACCOUNTING,
                    AnimationState.LISTEN_MUSIC,
                    AnimationState.COUNT_MONEY,
                    AnimationState.DRINK_COFFEE,
                )
                val newState = randomStates.random()
                mascotState.animationState = newState

                // 显示对应语句
                val stateSpeech = MascotSpeech.randomForState(newState)
                if (stateSpeech != null && isSpeechEnabled()) {
                    showSpeech(stateSpeech)
                } else if (stateSpeech == null && isSpeechEnabled()) {
                    showSpeech(MascotSpeech.randomIdle())
                }

                // 侧走时配合随机移动
                if (newState == AnimationState.WALK_SIDE && isRandomMoveEnabled()) {
                    triggerWalkSideMove()
                }

                val duration = when (newState) {
                    AnimationState.SLEEPING -> Random.nextLong(4000, 7000)
                    AnimationState.ACCOUNTING -> Random.nextLong(4000, 6000)
                    AnimationState.LISTEN_MUSIC -> Random.nextLong(4000, 6000)
                    AnimationState.WALK_SIDE -> Random.nextLong(3000, 5000)
                    AnimationState.SNEEZE -> Random.nextLong(1500, 2500)
                    AnimationState.YAWN -> Random.nextLong(2000, 3500)
                    AnimationState.STRETCH -> Random.nextLong(2000, 3000)
                    AnimationState.LOOK_PHONE -> Random.nextLong(3000, 5000)
                    AnimationState.COUNT_MONEY -> Random.nextLong(3000, 5000)
                    AnimationState.DRINK_COFFEE -> Random.nextLong(3000, 5000)
                    else -> Random.nextLong(3000, 6000)
                }
                delay(duration)
                if (mascotState.animationState == newState) {
                    mascotState.animationState = AnimationState.IDLE
                }
            }
        }
    }

    private fun triggerWalkSideMove() {
        val params = windowParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val moveRange = (screenWidth * 0.2f).toInt()
        val fromX = params.x
        val fromY = params.y
        val direction = if (Random.nextBoolean()) 1 else -1
        val targetX = (fromX + direction * Random.nextInt(moveRange / 2, moveRange))
            .coerceIn(0, (screenWidth - (MASCOT_W * displayMetrics.density * scale).toInt()).coerceAtLeast(1))
        val midX = (fromX + targetX) / 2
        val midY = fromY + Random.nextInt(-30, 30)

        serviceScope.launch {
            animateMoveBezier(fromX, fromY, midX, midY, targetX, fromY)
        }
    }

    private fun startGreetingLoop() {
        serviceScope.launch {
            while (isActive) {
                val calendar = java.util.Calendar.getInstance()
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                // 检测时段变化（6/9/12/14/18/22点触发）
                val periodChanged = when {
                    currentHour in 6..9 && lastGreetingHour !in 6..9 -> true
                    currentHour in 9..12 && lastGreetingHour !in 9..12 -> true
                    currentHour in 12..14 && lastGreetingHour !in 12..14 -> true
                    currentHour in 14..18 && lastGreetingHour !in 14..18 -> true
                    currentHour in 18..22 && lastGreetingHour !in 18..22 -> true
                    currentHour >= 22 && lastGreetingHour < 22 -> true
                    currentHour < 6 && lastGreetingHour >= 6 -> true
                    else -> false
                }

                if (lastGreetingHour >= 0 && periodChanged && isSpeechEnabled()) {
                    showSpeech(MascotSpeech.randomGreeting())
                }
                lastGreetingHour = currentHour

                // 每分钟检查一次时段变化
                delay(60000)
            }
        }
    }

    private fun startRandomMoveLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(Random.nextLong(15000, 35000))
                if (!isRandomMoveEnabled()) continue
                if (mascotState.isDizzy) continue

                val params = windowParams ?: continue
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                val winW = (MASCOT_W * displayMetrics.density * scale).toInt()
                val winH = ((MASCOT_H + BUBBLE_H) * displayMetrics.density * scale).toInt()

                val fromX = params.x
                val fromY = params.y

                val maxMoveX = screenWidth * 0.4f
                val maxMoveY = screenHeight * 0.3f
                // 已贴边时只上下挪，避免随机移动把"靠边"破坏掉
                val keepSide = _snapEnabled && _side != MascotSide.NONE
                val targetX = if (keepSide) {
                    fromX
                } else {
                    (fromX + Random.nextInt(-maxMoveX.toInt(), maxMoveX.toInt()))
                        .coerceIn(0, (screenWidth - winW).coerceAtLeast(1))
                }
                val targetY = (fromY + Random.nextInt(-maxMoveY.toInt(), maxMoveY.toInt()))
                    .coerceIn(100, (screenHeight - winH - 100).coerceAtLeast(200))

                val midX = (fromX + targetX) / 2 + Random.nextInt(-100, 100)
                val midY = (fromY + targetY) / 2 + Random.nextInt(-80, 80)

                animateMoveBezier(fromX, fromY, midX, midY, targetX, targetY)
            }
        }
    }

    private suspend fun animateMoveBezier(fromX: Int, fromY: Int, midX: Int, midY: Int, toX: Int, toY: Int) {
        val totalSteps = 90
        for (i in 1..totalSteps) {
            val params = windowParams ?: return
            val t = i.toFloat() / totalSteps
            val eased = t * t * (3f - 2f * t)
            val oneMinusT = 1f - eased
            val x = oneMinusT * oneMinusT * fromX + 2 * oneMinusT * eased * midX + eased * eased * toX
            val y = oneMinusT * oneMinusT * fromY + 2 * oneMinusT * eased * midY + eased * eased * toY
            params.x = x.toInt()
            params.y = y.toInt()
            try {
                windowManager.updateViewLayout(composeView, params)
            } catch (_: Exception) {}
            delay(16)
        }
    }

    /**
     * 松手后判断是否要贴边。
     *
     * 规则：离最近的水平边小于阈值就贴过去，并记录贴的是哪一侧；
     * 同一侧一旦确定，[shouldFlip] 会保证"图片该贴边的那一侧"始终朝向边框。
     */
    private fun snapToNearestEdge() {
        if (!_snapEnabled) {
            _side = MascotSide.NONE
            _vAnchor = MascotVAnchor.NONE
            return
        }
        val params = windowParams ?: return
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = contentHeightPx()
        // 阈值故意做得很小（[SNAP_THRESHOLD_DP]）：只有真的把 Wally 拖到边框上才吸附，
        // 手滑路过边缘不会被"吸"走。
        val threshold = SNAP_THRESHOLD_DP * metrics.density

        var targetX = params.x
        var side = MascotSide.NONE
        val leftGap = params.x.toFloat()
        val rightGap = (screenW - params.x - params.width).toFloat()
        if (leftGap <= threshold && leftGap <= rightGap) {
            targetX = 0
            side = MascotSide.LEFT
        } else if (rightGap <= threshold) {
            targetX = screenW - params.width
            side = MascotSide.RIGHT
        }

        var targetY = params.y
        var vAnchor = MascotVAnchor.NONE
        val topGap = params.y.toFloat()
        val bottomGap = (screenH - params.y - params.height).toFloat()
        if (topGap <= threshold && topGap <= bottomGap) {
            targetY = 0
            vAnchor = MascotVAnchor.TOP
        } else if (bottomGap <= threshold) {
            targetY = screenH - params.height
            vAnchor = MascotVAnchor.BOTTOM
        }

        _side = side
        _vAnchor = vAnchor
        animateWindowTo(targetX, targetY)
    }

    private fun animateWindowTo(targetX: Int, targetY: Int) {
        val params = windowParams ?: return
        val startX = params.x
        val startY = params.y
        if (startX == targetX && startY == targetY) return
        windowAnimJob?.cancel()
        windowAnimJob = serviceScope.launch {
            val steps = 10
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val eased = t * t * (3f - 2f * t)
                params.x = (startX + (targetX - startX) * eased).toInt()
                params.y = (startY + (targetY - startY) * eased).toInt()
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (_: Exception) {}
                delay(14)
            }
        }
    }

    /**
     * 悬浮窗能活动的区域高度（px）。
     *
     * 实测这台机器上：
     *  - `displayMetrics.heightPixels` = 2154（已经扣掉了导航栏）
     *  - 悬浮窗坐标原点是状态栏下方（`getLocationOnScreen()[1] - params.y` = 84）
     *  - `getWindowVisibleDisplayFrame` 因为 FLAG_LAYOUT_NO_LIMITS 会返回 [-10000, 10000] 这种垃圾值
     * 所以容器顶部用「窗口实际屏幕位置 − params.y」自己标定一次，再用屏幕高度减掉它。
     */
    private var containerTopPx = -1

    private fun contentHeightPx(): Int {
        val dm = resources.displayMetrics
        val top = containerTopPx.coerceAtLeast(0)
        return (dm.heightPixels - top).coerceAtLeast(dm.heightPixels / 2)
    }

    private fun resourceDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    }

    private fun updateMascotSize(scale: Float) {
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val box = MascotLayout.boxFor(effectiveStyle())
        val speechVisible = mascotState.speechText != null
        val bubbleH = if (speechVisible) MascotLayout.BUBBLE_H * scale else 0f
        val panelH = if (_panel != null) MascotLayout.BUDGET_PANEL_H else 0f
        params.width = (windowWidthDp(box, scale, _panel != null) * density).toInt()
        params.height = ((box.height.value * scale + bubbleH + panelH) * density).toInt()
        // 贴边状态下改大小，必须重新顶到边框，否则会离开一条缝
        if (_snapEnabled) {
            when (_side) {
                MascotSide.LEFT -> params.x = 0
                MascotSide.RIGHT -> params.x = screenW - params.width
                MascotSide.NONE -> Unit
            }
            when (_vAnchor) {
                MascotVAnchor.TOP -> params.y = 0
                MascotVAnchor.BOTTOM -> params.y = contentHeightPx() - params.height
                MascotVAnchor.NONE -> Unit
            }
        }
        windowParams = params
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    /**
     * 窗口宽度（dp）。
     *
     * 没展开面板时，窗口宽度就等于形象宽度（最小留 [MIN_WINDOW_W] 方便点按），
     * 这样「窗口贴边」就等于「形象贴边」；展开预算面板时才临时变宽。
     */
    private fun windowWidthDp(box: MascotBox, scale: Float, panelVisible: Boolean): Float {
        val mascotW = box.width.value * scale
        return if (panelVisible) {
            maxOf(mascotW, MascotLayout.BUDGET_PANEL_W)
        } else {
            maxOf(mascotW, MIN_WINDOW_W)
        }
    }

    private fun createLayoutParams(scale: Float): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val box = MascotLayout.boxFor(effectiveStyle())
        val w = (windowWidthDp(box, scale, false) * density).toInt()
        // 初始没有对话，窗口高度只算形象本身，上方不留空
        val h = (box.height.value * density * scale).toInt()
        return WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) {}
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        composeView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        composeView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
