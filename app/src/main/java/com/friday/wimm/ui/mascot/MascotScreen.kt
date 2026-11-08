package com.friday.wimm.ui.mascot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MascotScreen(onBack: () -> Unit = {}, onSubPageChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MascotPrefs.NAME, Context.MODE_MULTI_PROCESS) }

    var enabled by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_ENABLED, true)) }
    var scale by remember { mutableFloatStateOf(prefs.getFloat(MascotPrefs.KEY_SCALE, 1.0f)) }
    var randomMoveEnabled by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_RANDOM_MOVE, true)) }
    var speechEnabled by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_SPEECH, true)) }
    var eyeTrackEnabled by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_EYE_TRACK, true)) }
    var eyeTrackProb by remember { mutableFloatStateOf(prefs.getFloat(MascotPrefs.KEY_EYE_TRACK_PROB, 1.0f)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var pendingEnable by remember { mutableStateOf(false) }

    // ── 形象设置 ──────────────────────────────────────────
    var style by remember {
        mutableStateOf(
            MascotStyle.from(prefs.getString(MascotPrefs.KEY_STYLE, MascotStyle.BUILTIN.value))
        )
    }
    var imageUri by remember { mutableStateOf(prefs.getString(MascotPrefs.KEY_IMAGE_URI, null)) }
    var mouthUri by remember { mutableStateOf(prefs.getString(MascotPrefs.KEY_MOUTH_URI, null)) }
    var animMode by remember {
        mutableStateOf(
            MascotAnimMode.from(
                prefs.getString(MascotPrefs.KEY_ANIM_MODE, MascotAnimMode.JELLY.value)
            )
        )
    }
    var clickJelly by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_CLICK_JELLY, true)) }
    var blinkUri by remember { mutableStateOf(prefs.getString(MascotPrefs.KEY_BLINK_URI, null)) }
    var blinkEnabled by remember {
        mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_BLINK_ENABLED, true))
    }
    var snapEnabled by remember { mutableStateOf(prefs.getBoolean(MascotPrefs.KEY_SNAP_ENABLED, true)) }
    var snapEdge by remember {
        mutableStateOf(
            MascotSnapEdge.from(
                prefs.getString(MascotPrefs.KEY_SNAP_EDGE, MascotSnapEdge.LEFT.value)
            )
        )
    }
    var previewSpeech by remember { mutableStateOf(false) }

    /** 选图后要长期保留访问权限，否则重启 App 就读不到了 */
    fun persistUri(uri: android.net.Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        MascotImageLoader.clearCache()
        if (enabled) MascotService.notifySettingsChanged(context)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            prefs.edit().putString(MascotPrefs.KEY_IMAGE_URI, uri.toString()).commit()
            imageUri = uri.toString()
            persistUri(uri)
        }
    }

    val mouthPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            prefs.edit().putString(MascotPrefs.KEY_MOUTH_URI, uri.toString()).commit()
            mouthUri = uri.toString()
            persistUri(uri)
        }
    }

    val blinkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            prefs.edit().putString(MascotPrefs.KEY_BLINK_URI, uri.toString()).commit()
            blinkUri = uri.toString()
            persistUri(uri)
        }
    }

    // 每次进入页面时刷新设置（快捷菜单可能修改过）
    LaunchedEffect(Unit) {
        enabled = prefs.getBoolean(MascotPrefs.KEY_ENABLED, true)
        scale = MascotPrefs.clampScale(prefs.getFloat(MascotPrefs.KEY_SCALE, 1.0f))
        randomMoveEnabled = prefs.getBoolean(MascotPrefs.KEY_RANDOM_MOVE, true)
        speechEnabled = prefs.getBoolean(MascotPrefs.KEY_SPEECH, true)
        eyeTrackEnabled = prefs.getBoolean(MascotPrefs.KEY_EYE_TRACK, true)
        eyeTrackProb = prefs.getFloat(MascotPrefs.KEY_EYE_TRACK_PROB, 1.0f)
        style = MascotStyle.from(prefs.getString(MascotPrefs.KEY_STYLE, MascotStyle.BUILTIN.value))
        imageUri = prefs.getString(MascotPrefs.KEY_IMAGE_URI, null)
        mouthUri = prefs.getString(MascotPrefs.KEY_MOUTH_URI, null)
        blinkUri = prefs.getString(MascotPrefs.KEY_BLINK_URI, null)
        blinkEnabled = prefs.getBoolean(MascotPrefs.KEY_BLINK_ENABLED, true)
        animMode = MascotAnimMode.from(
            prefs.getString(MascotPrefs.KEY_ANIM_MODE, MascotAnimMode.JELLY.value)
        )
        clickJelly = prefs.getBoolean(MascotPrefs.KEY_CLICK_JELLY, true)
        snapEnabled = prefs.getBoolean(MascotPrefs.KEY_SNAP_ENABLED, true)
        snapEdge = MascotSnapEdge.from(
            prefs.getString(MascotPrefs.KEY_SNAP_EDGE, MascotSnapEdge.LEFT.value)
        )
    }

    // 动画预览状态
    var previewState by remember { mutableStateOf(MascotStateHolder()) }
    var selectedAnimation by remember { mutableStateOf(AnimationState.IDLE) }

    // 切换预览动画
    LaunchedEffect(selectedAnimation) {
        previewState = MascotStateHolder().apply {
            animationState = selectedAnimation
            isDizzy = selectedAnimation == AnimationState.DIZZY
            if (selectedAnimation == AnimationState.SLEEPING) {
                speechText = "Zzz..."
            }
        }
    }

    // 通知权限请求
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingEnable) {
            enabled = true
            prefs.edit().putBoolean(MascotPrefs.KEY_ENABLED, true).commit()
            MascotService.start(context, scale)
        }
        pendingEnable = false
    }

    // 从设置页返回时刷新权限
    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    fun enableMascot() {
        if (!hasOverlayPermission) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPerm = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifPerm) {
                pendingEnable = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        enabled = true
        prefs.edit().putBoolean(MascotPrefs.KEY_ENABLED, true).commit()
        MascotService.start(context, scale)
    }

    fun updateSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit() // commit()确保跨进程可见
        // 通知服务刷新设置
        if (enabled) MascotService.notifySettingsChanged(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 现在 Wally 页藏在「设置」里，自己带一个返回入口
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "Wally",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        // ── 预览区域 ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 桌宠预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (style == MascotStyle.CUSTOM) {
                        CustomImageMascot(
                            imageUri = imageUri,
                            mouthUri = mouthUri,
                            blinkUri = blinkUri,
                            blinkEnabled = blinkEnabled,
                            animMode = animMode,
                            clickJelly = false,
                            speechVisible = previewSpeech || previewState.speechText != null,
                            clickTick = 0,
                            flipHorizontal = false,
                            modifier = Modifier
                                .size(130.dp, 155.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    } else {
                        FlyingBillMascot(
                            scale = 1.0f,
                            state = previewState,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                    }
                }

                // 动画选择按钮
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        AnimationState.IDLE to "待机",
                        AnimationState.SPINNING to "旋转",
                        AnimationState.BOUNCING to "弹跳",
                        AnimationState.SLEEPING to "睡觉",
                        AnimationState.EXCITED to "兴奋",
                        AnimationState.DIZZY to "眩晕",
                        AnimationState.WALK_SIDE to "侧走",
                        AnimationState.YAWN to "哈欠",
                        AnimationState.STRETCH to "伸懒腰",
                        AnimationState.LOOK_PHONE to "看手机",
                        AnimationState.SNEEZE to "打喷嚏",
                        AnimationState.ACCOUNTING to "记账",
                        AnimationState.LISTEN_MUSIC to "听歌",
                        AnimationState.COUNT_MONEY to "数钱",
                        AnimationState.DRINK_COFFEE to "喝咖啡",
                    ).forEach { (state, label) ->
                        val isSelected = selectedAnimation == state
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedAnimation = state }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 启用开关 ──
        SettingSwitchItem(
            title = "启用 Wally",
            subtitle = "在桌面上显示悬浮桌宠 Wally",
            checked = enabled,
            onCheckedChange = { newValue ->
                if (newValue) {
                    enableMascot()
                } else {
                    enabled = false
                    prefs.edit().putBoolean(MascotPrefs.KEY_ENABLED, false).commit()
                    MascotService.stop(context)
                }
            }
        )

        if (!hasOverlayPermission) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "需要悬浮窗权限才能显示桌宠",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }) {
                        Text("去授权")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ── 大小调节 ──
        Text("Wally 大小", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("小", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = MascotPrefs.clampScale(scale),
                onValueChange = {
                    val clamped = MascotPrefs.clampScale(it)
                    scale = clamped
                    prefs.edit().putFloat(MascotPrefs.KEY_SCALE, clamped).commit()
                    if (enabled) MascotService.updateScale(context, clamped)
                },
                valueRange = MascotPrefs.MIN_SCALE..MascotPrefs.MAX_SCALE,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text("大", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "当前: ${(scale * 100).toInt()}%（可调范围 ${(MascotPrefs.MIN_SCALE * 100).toInt()}% ~ ${(MascotPrefs.MAX_SCALE * 100).toInt()}%）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ── 形象 ──
        Text("形象", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = style == MascotStyle.BUILTIN,
                onClick = {
                    style = MascotStyle.BUILTIN
                    prefs.edit().putString(MascotPrefs.KEY_STYLE, style.value).commit()
                    if (enabled) MascotService.notifySettingsChanged(context)
                },
                label = { Text("内置 Wally") }
            )
            FilterChip(
                selected = style == MascotStyle.CUSTOM,
                onClick = {
                    style = MascotStyle.CUSTOM
                    prefs.edit().putString(MascotPrefs.KEY_STYLE, style.value).commit()
                    if (enabled) MascotService.notifySettingsChanged(context)
                },
                label = { Text("自定义图片") }
            )
        }

        if (style == MascotStyle.CUSTOM) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { imagePicker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (imageUri.isNullOrBlank()) "选择角色图片 / GIF" else "更换角色图片 / GIF")
            }
            Text(
                when {
                    imageUri.isNullOrBlank() -> "还没有选择图片，悬浮窗会先显示内置 Wally"
                    else -> "已选择角色图片"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { mouthPicker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (mouthUri.isNullOrBlank()) "选择张口差分（可选）" else "更换张口差分")
            }
            if (!mouthUri.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        prefs.edit().remove(MascotPrefs.KEY_MOUTH_URI).commit()
                        mouthUri = null
                        if (animMode == MascotAnimMode.MOUTH) {
                            animMode = MascotAnimMode.JELLY
                            prefs.edit().putString(MascotPrefs.KEY_ANIM_MODE, animMode.value).commit()
                        }
                        if (enabled) MascotService.notifySettingsChanged(context)
                    }
                ) { Text("移除张口差分") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { blinkPicker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (blinkUri.isNullOrBlank()) "选择闭眼差分（可选）" else "更换闭眼差分")
            }
            if (!blinkUri.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        prefs.edit().remove(MascotPrefs.KEY_BLINK_URI).commit()
                        blinkUri = null
                        if (enabled) MascotService.notifySettingsChanged(context)
                    }
                ) { Text("移除闭眼差分") }
            }
            if (blinkUri.isNullOrBlank()) {
                Text(
                    "选一张闭着眼睛的差分图，Wally 就会时不时眨一下眼。" +
                        "真人平均每分钟眨眼 15~20 次（约 3~4 秒一次），这里按 3~6 秒随机，偶尔连眨两下。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                SettingSwitchItem(
                    title = "会眨眼",
                    subtitle = "每 3~6 秒眨一次，约两成概率连眨两下",
                    checked = blinkEnabled,
                    onCheckedChange = {
                        blinkEnabled = it
                        prefs.edit().putBoolean(MascotPrefs.KEY_BLINK_ENABLED, it).commit()
                        if (enabled) MascotService.notifySettingsChanged(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("对话出现时的动画", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = animMode == MascotAnimMode.JELLY,
                    onClick = {
                        animMode = MascotAnimMode.JELLY
                        prefs.edit().putString(MascotPrefs.KEY_ANIM_MODE, animMode.value).commit()
                        if (enabled) MascotService.notifySettingsChanged(context)
                    },
                    label = { Text("果冻 Q 弹") }
                )
                FilterChip(
                    selected = animMode == MascotAnimMode.MOUTH,
                    enabled = !mouthUri.isNullOrBlank(),
                    onClick = {
                        animMode = MascotAnimMode.MOUTH
                        prefs.edit().putString(MascotPrefs.KEY_ANIM_MODE, animMode.value).commit()
                        if (enabled) MascotService.notifySettingsChanged(context)
                    },
                    label = { Text("张口切换") }
                )
            }
            if (mouthUri.isNullOrBlank()) {
                Text(
                    "先选择一张「张口差分」图片，才能使用张口切换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingSwitchItem(
                title = "点击时 Q 弹",
                subtitle = "点击角色时也弹一下",
                checked = clickJelly,
                onCheckedChange = {
                    clickJelly = it
                    prefs.edit().putBoolean(MascotPrefs.KEY_CLICK_JELLY, it).commit()
                    if (enabled) MascotService.notifySettingsChanged(context)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            SettingSwitchItem(
                title = "贴边吸附",
                subtitle = "拖到屏幕边缘附近会自动贴边，左右切换时自动左右翻转",
                checked = snapEnabled,
                onCheckedChange = {
                    snapEnabled = it
                    prefs.edit().putBoolean(MascotPrefs.KEY_SNAP_ENABLED, it).commit()
                    if (enabled) MascotService.notifySettingsChanged(context)
                }
            )

            if (snapEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("图片哪一边贴着屏幕边框", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = snapEdge == MascotSnapEdge.LEFT,
                        onClick = {
                            snapEdge = MascotSnapEdge.LEFT
                            prefs.edit().putString(MascotPrefs.KEY_SNAP_EDGE, snapEdge.value).commit()
                            if (enabled) MascotService.notifySettingsChanged(context)
                        },
                        label = { Text("图片左边贴边") }
                    )
                    FilterChip(
                        selected = snapEdge == MascotSnapEdge.RIGHT,
                        onClick = {
                            snapEdge = MascotSnapEdge.RIGHT
                            prefs.edit().putString(MascotPrefs.KEY_SNAP_EDGE, snapEdge.value).commit()
                            if (enabled) MascotService.notifySettingsChanged(context)
                        },
                        label = { Text("图片右边贴边") }
                    )
                }
                Text(
                    "选了哪一边，Wally 贴左边框和贴右边框时都会让这一侧朝外，所以换边时会自动翻转。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { previewSpeech = !previewSpeech },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (previewSpeech) "停止预览动画" else "预览对话动画")
            }
            Text(
                "GIF 动态图需要 Android 9 及以上才能播放；更老的系统只显示第一帧。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ── 行为设置 ──
        Text("行为设置", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        // 随机移动
        SettingSwitchItem(
            title = "随机移动",
            subtitle = "Wally 会随机在屏幕上移动位置",
            checked = randomMoveEnabled,
            onCheckedChange = {
                randomMoveEnabled = it
                updateSetting(MascotPrefs.KEY_RANDOM_MOVE, it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 说话
        SettingSwitchItem(
            title = "说话",
            subtitle = "Wally 会在待机、点击、收到通知时说话",
            checked = speechEnabled,
            onCheckedChange = {
                speechEnabled = it
                updateSetting(MascotPrefs.KEY_SPEECH, it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 眼睛注视
        SettingSwitchItem(
            title = "眼睛注视",
            subtitle = "点击时 Wally 眼睛看向点击位置",
            checked = eyeTrackEnabled,
            onCheckedChange = {
                eyeTrackEnabled = it
                updateSetting(MascotPrefs.KEY_EYE_TRACK, it)
            }
        )

        // 眼睛注视概率（仅在开启时显示）
        AnimatedVisibility(
            visible = eyeTrackEnabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("触发概率", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${(eyeTrackProb * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = eyeTrackProb,
                    onValueChange = {
                        eyeTrackProb = it
                        prefs.edit().putFloat(MascotPrefs.KEY_EYE_TRACK_PROB, it).commit()
                        if (enabled) MascotService.notifySettingsChanged(context)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "100% = 每次点击都注视，0% = 从不注视",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ── 操作提示 ──
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("操作提示", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("· 拖动 Wally 可以移动位置", style = MaterialTheme.typography.bodySmall)
                Text("· 点击 Wally 会触发互动", style = MaterialTheme.typography.bodySmall)
                Text("· 长按 Wally 可以打开快捷设置", style = MaterialTheme.typography.bodySmall)
                Text("· 快速拖动 Wally 会触发眩晕", style = MaterialTheme.typography.bodySmall)
                Text("· 收到记账通知时 Wally 会说话", style = MaterialTheme.typography.bodySmall)
                Text("· 待机时 Wally 会随机说话和变换动画", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
