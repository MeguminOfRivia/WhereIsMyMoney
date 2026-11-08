package com.friday.wimm.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import com.friday.wimm.ui.home.HomeScreen
import com.friday.wimm.ui.cards.CardsScreen
import com.friday.wimm.ui.cards.CreateCardScreen
import com.friday.wimm.ui.cards.CardDetailScreen
import com.friday.wimm.ui.cards.CategoryManageScreen
import com.friday.wimm.ui.import_screen.ImportScreen
import com.friday.wimm.ui.mascot.MascotScreen
import com.friday.wimm.ui.settings.SettingsScreen
import com.friday.wimm.ui.permissions.PermissionsScreen
import com.friday.wimm.MyApplication
import com.friday.wimm.data.model.Card
import com.friday.wimm.data.repository.CardRepository

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Accounts : Screen("accounts", "账户", Icons.Default.List)
    object Import : Screen("import", "导入", Icons.Default.Add)
    object Mascot : Screen("mascot", "Wally", Icons.Default.Star)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wimm_prefs", android.content.Context.MODE_PRIVATE) }
    var permissionsDone by remember { mutableStateOf(prefs.getBoolean("permissions_done", false)) }

    if (!permissionsDone) {
        PermissionsScreen(onAllGranted = {
            prefs.edit().putBoolean("permissions_done", true).apply()
            permissionsDone = true
            // 创建默认全局卡片
            createDefaultGlobalCard(context)
        })
    } else {
        // 确保全局卡片存在（数据库操作放到 IO 线程，避免启动时卡顿）
        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                createDefaultGlobalCard(context)
            }
        }
        MainContent()
    }
}

private fun createDefaultGlobalCard(context: android.content.Context) {
    val application = context.applicationContext as MyApplication
    val cardRepository = CardRepository(application.databaseHelper)
    val cards = cardRepository.getAllCards()
    if (cards.isEmpty()) {
        val globalCard = Card(
            name = "全局统计",
            startTime = System.currentTimeMillis(),
            endTime = 0,
            isGlobal = true
        )
        cardRepository.insertCard(globalCard)
    }
}

@Composable
private fun MainContent() {
    val screens = listOf(Screen.Home, Screen.Accounts, Screen.Import, Screen.Mascot, Screen.Settings)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { screens.size })
    val coroutineScope = rememberCoroutineScope()

    // 子页面状态 - 当有子页面时隐藏底部导航栏
    var showNavBar by remember { mutableStateOf(true) }
    var currentSubPage by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    // 跟踪所有子页面（包括屏幕内部的子页面），统一控制滑动
    var anySubPageActive by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (showNavBar) {
                NavigationBar {
                    screens.forEachIndexed { index, screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                val current = pagerState.currentPage
                                val distance = kotlin.math.abs(index - current)
                                coroutineScope.launch {
                                    if (distance <= 1) {
                                        pagerState.animateScrollToPage(index)
                                    } else {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (currentSubPage) {
            "create_card" -> {
                CreateCardScreen(
                    onBack = {
                        currentSubPage = null
                        showNavBar = true
                        anySubPageActive = false
                    },
                    onCreated = {
                        currentSubPage = null
                        showNavBar = true
                        anySubPageActive = false
                    }
                )
            }
            "card_detail" -> {
                selectedCard?.let { card ->
                    CardDetailScreen(
                        card = card,
                        onBack = {
                            currentSubPage = null
                            showNavBar = true
                            anySubPageActive = false
                        },
                        onManageCategories = {}
                    )
                }
            }
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(innerPadding),
                    userScrollEnabled = !anySubPageActive && currentSubPage == null
                ) { page ->
                    when (page) {
                        0 -> HomeScreen()
                        1 -> com.friday.wimm.ui.accounts.AccountsScreen()
                        2 -> ImportScreen(onSubPageChanged = { isSub ->
                            showNavBar = !isSub
                            anySubPageActive = isSub
                        })
                        3 -> MascotScreen(onSubPageChanged = { isSub ->
                            showNavBar = !isSub
                            anySubPageActive = isSub
                        })
                        4 -> SettingsScreen(onSubPageChanged = { isSub ->
                            showNavBar = !isSub
                            anySubPageActive = isSub
                        })
                    }
                }
            }
        }
    }
}
