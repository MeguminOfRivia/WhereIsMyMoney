package com.friday.wimm.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friday.wimm.R
import kotlinx.coroutines.delay

private val SplashBg = Color(0xFFE2E2EC)
private val TextMuted = Color(0xFF8A8A9A)

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    // Logo 缩放淡入
    var logoVisible by remember { mutableStateOf(false) }
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(800, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.85f,
        animationSpec = tween(800, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "logoScale"
    )

    // by Friday 浮现
    var byFridayVisible by remember { mutableStateOf(false) }
    val byFridayAlpha by animateFloatAsState(
        targetValue = if (byFridayVisible) 1f else 0f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "byFridayAlpha"
    )
    val byFridayOffset by animateFloatAsState(
        targetValue = if (byFridayVisible) 0f else 12f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "byFridayOffset"
    )

    // 淡出
    var fadeOut by remember { mutableStateOf(false) }
    val exitAlpha by animateFloatAsState(
        targetValue = if (fadeOut) 0f else 1f,
        animationSpec = tween(500, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 1f)),
        label = "exitAlpha"
    )

    // 动画时序
    LaunchedEffect(Unit) {
        delay(200)
        logoVisible = true
        delay(600)
        byFridayVisible = true
        delay(1500)
        fadeOut = true
        delay(500)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha)
            .background(SplashBg),
        contentAlignment = Alignment.Center
    ) {
        // Logo 居中偏上
        Image(
            painter = painterResource(id = R.drawable.start_wally),
            contentDescription = "Logo",
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.Center)
                .alpha(logoAlpha)
                .scale(logoScale)
        )

        // by Friday 底部
        Text(
            text = "by Friday",
            fontSize = 11.sp,
            fontWeight = FontWeight(300),
            color = TextMuted,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .alpha(byFridayAlpha)
                .offset(y = byFridayOffset.dp)
        )
    }
}
