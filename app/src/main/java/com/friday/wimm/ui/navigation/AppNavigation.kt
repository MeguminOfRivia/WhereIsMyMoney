package com.friday.wimm.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.friday.wimm.ui.accounts.AccountsScreen
import com.friday.wimm.ui.budget.BudgetScreen
import com.friday.wimm.ui.category.CategoryRuleScreen
import com.friday.wimm.ui.import_screen.ImportScreen
import com.friday.wimm.ui.ledger.LedgerHomeScreen
import com.friday.wimm.ui.mascot.MascotScreen
import com.friday.wimm.ui.permissions.PermissionsScreen
import com.friday.wimm.ui.settings.SettingsRoutes
import com.friday.wimm.ui.settings.SettingsScreen
import com.friday.wimm.ui.stats.StatsScreen
import com.friday.wimm.ui.theme.WimmIcons
import kotlinx.coroutines.launch

sealed class Tab(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Tab("home", "首页", Icons.Default.Home)
    data object Accounts : Tab("accounts", "账户", WimmIcons.AccountCard)
    data object Stats : Tab("stats", "统计", WimmIcons.BarChart)
    data object Budget : Tab("budget", "预算", WimmIcons.BudgetTarget)
    data object Settings : Tab("settings", "设置", Icons.Default.Settings)
}

/**
 * 应用入口。
 *
 * 第一次进来先过权限页，之后是五个底部标签：
 * 首页（流水）/ 账户 / 统计 / 预算 / 设置。
 * Wally 形象、账单导入、大类规则都放在设置里，作为全屏子页面。
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("wimm_prefs", android.content.Context.MODE_PRIVATE)
    }
    var permissionsDone by remember { mutableStateOf(prefs.getBoolean("permissions_done", false)) }

    if (!permissionsDone) {
        PermissionsScreen(onAllGranted = {
            prefs.edit().putBoolean("permissions_done", true).apply()
            permissionsDone = true
        })
    } else {
        MainContent()
    }
}

@Composable
private fun MainContent() {
    val tabs = remember {
        listOf(Tab.Home, Tab.Accounts, Tab.Stats, Tab.Budget, Tab.Settings)
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // 子页面：null 表示当前停在标签页上
    var subPage by remember { mutableStateOf<String?>(null) }
    // 从子页面回来或者切页时 +1，让首页重新查一次数据
    var refreshTick by remember { mutableIntStateOf(0) }

    fun closeSubPage() {
        subPage = null
        refreshTick++
    }

    BackHandler(enabled = subPage != null) { closeSubPage() }

    Scaffold(
        bottomBar = {
            if (subPage == null) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                val distance = kotlin.math.abs(index - pagerState.currentPage)
                                scope.launch {
                                    if (distance <= 1) {
                                        pagerState.animateScrollToPage(index)
                                    } else {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                                if (index == 0) refreshTick++
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (subPage) {
            SettingsRoutes.CATEGORIES -> CategoryRuleScreen(onBack = { closeSubPage() })
            SettingsRoutes.IMPORT -> ImportScreen(onSubPageChanged = {})
            SettingsRoutes.MASCOT -> MascotScreen(onSubPageChanged = {})
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(innerPadding)
                ) { page ->
                    when (page) {
                        0 -> LedgerHomeScreen(refreshKey = refreshTick)
                        1 -> AccountsScreen()
                        2 -> StatsScreen()
                        3 -> BudgetScreen()
                        4 -> SettingsScreen(onNavigate = { subPage = it })
                    }
                }
            }
        }
    }
}
