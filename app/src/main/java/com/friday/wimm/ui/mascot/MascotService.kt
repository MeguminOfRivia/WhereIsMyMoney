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

    // ── 形象外观（跨进程通过广播刷新） ──────────────────────
    private var _style by mutableStateOf(MascotStyle.BUILTIN)
    private var _imageUri by mutableStateOf<String?>(null)
    private var _mouthUri by mutableStateOf<String?>(null)
    private var _animMode by mutableStateOf(MascotAnimMode.JELLY)
    private var _clickJelly by mutableStateOf(true)

    /** 选了"自定义"但还没挑图片时，回退到内置形象 */
    private fun effectiveStyle(): MascotStyle =
        if (_style == MascotStyle.CUSTOM && _imageUri.isNullOrBlank()) MascotStyle.BUILTIN else _style

    private fun reloadAppearance() {
        prefs.edit().commit()
        _scale = prefs.getFloat(MascotPrefs.KEY_SCALE, 1.0f)
        _style = MascotStyle.from(prefs.getString(MascotPrefs.KEY_STYLE, MascotStyle.BUILTIN.value))
        _imageUri = prefs.getString(MascotPrefs.KEY_IMAGE_URI, null)
        _mouthUri = prefs.getString(MascotPrefs.KEY_MOUTH_URI, null)
        _animMode = MascotAnimMode.from(
            prefs.getString(MascotPrefs.KEY_ANIM_MODE, MascotAnimMode.JELLY.value)
        )
        _clickJelly = prefs.getBoolean(MascotPrefs.KEY_CLICK_JELLY, true)
    }

    private val prefs by lazy { getSharedPreferences(MascotPrefs.NAME, Context.MODE_MULTI_PROCESS) }

    // 跨进程读取设置：每次都从磁盘刷新缓存
    private fun isRandomMoveEnabled(): Boolean {
        prefs.edit().commit()
        return prefs.getBoolean(MascotPrefs.KEY_RANDOM_MOVE, true)
    }
    private fun isSpeechEnabled(): Boolean {
        prefs.edit().commit()
        return prefs.getBoolean(MascotPrefs.KEY_SPEECH, true)
    }
    private fun isEyeTrackEnabled(): Boolean {
        prefs.edit().commit()
        return prefs.getBoolean(MascotPrefs.KEY_EYE_TRACK, true)
    }
    private fun getEyeTrackProb(): Float {
        prefs.edit().commit()
        return prefs.getFloat(MascotPrefs.KEY_EYE_TRACK_PROB, 1.0f)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val CHANNEL_ID = "mascot_service"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_SCALE = "scale"
        const val ACTION_NOTIFICATION_EVENT = "com.friday.wimm.MASCOT_NOTIFICATION"
        const val EXTRA_IS_INCOME = "is_income"
        const val EXTRA_MERCHANT = "merchant"
        const val ACTION_SETTINGS_CHANGED = "com.friday.wimm.MASCOT_SETTINGS_CHANGED"

        const val MASCOT_W = 184
        const val MASCOT_H = 100
        const val BUBBLE_H = 70

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

        fun notifySettingsChanged(context: Context) {
            val intent = Intent(ACTION_SETTINGS_CHANGED).apply {
                setPackage(context.packageName)
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
                    reloadAppearance()
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
        _scale = newScale

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
    }

    @Composable
    private fun MascotContent() {
        var showMenu by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        // 快捷菜单的开关状态用 Compose state，点击后立即更新UI
        var menuRandomMove by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_RANDOM_MOVE, true)) }
        var menuSpeech by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_SPEECH, true)) }
        var menuEyeTrack by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_EYE_TRACK, true)) }
        var menuScale by remember { mutableFloatStateOf(scale) }

        // 5秒自动关闭菜单
        LaunchedEffect(showMenu) {
            if (showMenu) {
                delay(5000)
                if (showMenu) showMenu = false
            }
        }

        val currentScale = scale
        val box = MascotLayout.boxFor(effectiveStyle())
        val menuH = MascotLayout.MENU_H
        val mascotAreaH = (MascotLayout.BUBBLE_H + box.height.value) * currentScale
        val totalH = if (showMenu) mascotAreaH + menuH else mascotAreaH

        // 调整窗口大小（向下扩展，不改y）
        LaunchedEffect(showMenu, currentScale, box) {
            val view = composeView ?: return@LaunchedEffect
            val params = windowParams ?: return@LaunchedEffect
            val density = resources.displayMetrics.density
            val winW = maxOf(box.width.value * currentScale, MascotLayout.MENU_W)
            params.width = (winW * density).toInt()
            params.height = (totalH * density).toInt()
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }

        val containerW = maxOf(box.width.value * currentScale, MascotLayout.MENU_W)
        Box(
            modifier = Modifier.size(containerW.dp, totalH.dp)
        ) {
            // 气泡区域（最上方）
            if (mascotState.speechText != null) {
                Box(
                    modifier = Modifier
                        .size((box.width.value * currentScale).dp, (MascotLayout.BUBBLE_H * currentScale).dp)
                        .align(Alignment.TopCenter),
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
                    .align(Alignment.TopCenter)
                    .offset(y = (MascotLayout.BUBBLE_H * currentScale).dp)
                    .pointerInput(currentScale) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent()
                                val downChange = down.changes.firstOrNull() ?: continue
                                if (!downChange.pressed) continue

                                val downTime = System.currentTimeMillis()
                                var totalDragDist = 0f
                                var isDragging = false
                                var longPressTriggered = false
                                var prevAngle = 0f
                                var totalAngleChange = 0f
                                var angleInitialized = false

                                if (isEyeTrackEnabled() && Random.nextFloat() < getEyeTrackProb()) {
                                    updateEyeOffset(downChange.position.x, downChange.position.y)
                                }

                                val longPressJob = coroutineScope.launch {
                                    delay(500L)
                                    if (!isDragging && !longPressTriggered) {
                                        longPressTriggered = true
                                        showMenu = true
                                        if (isSpeechEnabled()) {
                                            showSpeech(MascotSpeech.randomLongPress())
                                        }
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
                                            onTap()
                                        }

                                        serviceScope.launch {
                                            delay(1500)
                                            mascotState.eyeOffsetX = 0f
                                            mascotState.eyeOffsetY = 0f
                                        }
                                        break
                                    }

                                    val dx = change.positionChange().x
                                    val dy = change.positionChange().y
                                    val deltaDist = sqrt(dx * dx + dy * dy)
                                    totalDragDist += deltaDist

                                    if (totalDragDist > 20f) {
                                        longPressJob.cancel()
                                        if (!isDragging) {
                                            isDragging = true
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
                        animMode = _animMode,
                        clickJelly = _clickJelly,
                        speechVisible = mascotState.speechText != null,
                        clickTick = mascotState.clickTick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 快捷设置菜单（桌宠下方，固定大小不缩放）
            if (showMenu) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = mascotAreaH.dp)
                        .width(220.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .background(
                            Color(0xFFF8F8FA),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "快捷设置",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0E0E0))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showMenu = false }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", fontSize = 13.sp, color = Color(0xFF666666))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

                    // 桌宠开关
                    MenuSwitchRow(
                        label = "Wally",
                        checked = prefs.getBoolean(MascotPrefs.KEY_ENABLED, true),
                        onCheckedChange = {
                            prefs.edit().putBoolean(MascotPrefs.KEY_ENABLED, it).commit()
                            if (!it) stopSelf()
                            showMenu = false
                        }
                    )

                    // 大小设置
                    MenuSizeRow(
                        currentScale = menuScale,
                        onScaleChange = { newScale ->
                            menuScale = newScale
                            _scale = newScale
                            prefs.edit().putFloat(MascotPrefs.KEY_SCALE, newScale).commit()
                            updateMascotSize(newScale)
                        }
                    )

                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

                    // 随机移动
                    MenuSwitchRow(
                        label = "随机移动",
                        checked = menuRandomMove,
                        onCheckedChange = {
                            menuRandomMove = it
                            prefs.edit().putBoolean(MascotPrefs.KEY_RANDOM_MOVE, it).commit()
                        }
                    )

                    // 说话
                    MenuSwitchRow(
                        label = "说话",
                        checked = menuSpeech,
                        onCheckedChange = {
                            menuSpeech = it
                            prefs.edit().putBoolean(MascotPrefs.KEY_SPEECH, it).commit()
                        }
                    )

                    // 眼睛注视
                    MenuSwitchRow(
                        label = "眼睛注视",
                        checked = menuEyeTrack,
                        onCheckedChange = {
                            menuEyeTrack = it
                            prefs.edit().putBoolean(MascotPrefs.KEY_EYE_TRACK, it).commit()
                        }
                    )
                }
            }
        }
    }

    private fun onTap() {
        // 自定义形象的点击 Q 弹反馈
        mascotState.clickTick += 1
        val interactions = listOf(
            { triggerClickSpeech() },
            { triggerExcitedAnimation() },
            { triggerBouncingAnimation() },
            { triggerAccountingAnimation() },
            { triggerLookPhoneAnimation() },
            { triggerCountMoneyAnimation() },
        )
        interactions.random()()
    }

    private fun triggerClickSpeech() {
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomClick())
        }
        mascotState.animationState = AnimationState.EXCITED
        serviceScope.launch {
            delay(1500)
            if (mascotState.animationState == AnimationState.EXCITED) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    private fun triggerExcitedAnimation() {
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomClick())
        }
        mascotState.animationState = AnimationState.EXCITED
        serviceScope.launch {
            delay(2000)
            if (mascotState.animationState == AnimationState.EXCITED) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    private fun triggerBouncingAnimation() {
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomClick())
        }
        mascotState.animationState = AnimationState.BOUNCING
        serviceScope.launch {
            delay(2000)
            if (mascotState.animationState == AnimationState.BOUNCING) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    private fun triggerAccountingAnimation() {
        mascotState.animationState = AnimationState.ACCOUNTING
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomForState(AnimationState.ACCOUNTING) ?: "记一笔...")
        }
        serviceScope.launch {
            delay(4000)
            if (mascotState.animationState == AnimationState.ACCOUNTING) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    private fun triggerLookPhoneAnimation() {
        mascotState.animationState = AnimationState.LOOK_PHONE
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomForState(AnimationState.LOOK_PHONE) ?: "看看手机...")
        }
        serviceScope.launch {
            delay(3500)
            if (mascotState.animationState == AnimationState.LOOK_PHONE) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    private fun triggerCountMoneyAnimation() {
        mascotState.animationState = AnimationState.COUNT_MONEY
        if (isSpeechEnabled()) {
            showSpeech(MascotSpeech.randomForState(AnimationState.COUNT_MONEY) ?: "数钱数钱~")
        }
        serviceScope.launch {
            delay(3500)
            if (mascotState.animationState == AnimationState.COUNT_MONEY) {
                mascotState.animationState = AnimationState.IDLE
            }
        }
    }

    @Composable
    private fun MenuSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onCheckedChange(!checked) }
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = Color(0xFF333333))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF4CAF50),
                    checkedThumbColor = Color.White
                )
            )
        }
    }

    @Composable
    private fun MenuSizeRow(currentScale: Float, onScaleChange: (Float) -> Unit) {
        val sizeOptions = listOf(0.5f to "50%", 0.75f to "75%", 1.0f to "100%", 1.5f to "150%", 2.0f to "200%")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("大小", fontSize = 13.sp, color = Color(0xFF333333))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                sizeOptions.forEach { (value, label) ->
                    val isSelected = kotlin.math.abs(currentScale - value) < 0.01f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) Color(0xFF4CAF50)
                                else Color(0xFFE8E8E8)
                            )
                            .clickable { onScaleChange(value) }
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            color = if (isSelected) Color.White else Color(0xFF666666)
                        )
                    }
                }
            }
        }
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
                val targetX = (fromX + Random.nextInt(-maxMoveX.toInt(), maxMoveX.toInt()))
                    .coerceIn(0, (screenWidth - winW).coerceAtLeast(1))
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

    private fun updateMascotSize(scale: Float) {
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val density = resources.displayMetrics.density
        val box = MascotLayout.boxFor(effectiveStyle())
        val widthDp = maxOf(box.width.value * scale, MascotLayout.MENU_W)
        params.width = (widthDp * density).toInt()
        params.height = ((box.height.value + MascotLayout.BUBBLE_H) * density * scale).toInt()
        windowParams = params
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun createLayoutParams(scale: Float): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val box = MascotLayout.boxFor(effectiveStyle())
        val w = (maxOf(box.width.value * scale, MascotLayout.MENU_W) * density).toInt()
        val h = ((box.height.value + MascotLayout.BUBBLE_H) * density * scale).toInt()
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
