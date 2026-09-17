package com.shiping.app.ui.navigation

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shiping.app.ui.screen.detail.DetailScreen
import com.shiping.app.ui.screen.download.DownloadScreen
import com.shiping.app.ui.screen.favorite.FavoriteScreen
import com.shiping.app.ui.screen.history.HistoryScreen
import com.shiping.app.ui.screen.home.HomeScreen
import com.shiping.app.ui.screen.player.PlayerScreen
import com.shiping.app.ui.screen.player.PlayerViewModel
import com.shiping.app.ui.screen.search.SearchScreen
import com.shiping.app.ui.screen.settings.ApiManageScreen
import com.shiping.app.ui.screen.settings.AppLogScreen
import com.shiping.app.ui.screen.settings.ParserManageScreen
import com.shiping.app.ui.screen.settings.SettingsScreen
import com.shiping.app.ui.screen.settings.TvSourceManageScreen
import com.shiping.app.ui.screen.tv.TvLivePlayerScreen
import com.shiping.app.ui.screen.tv.TvScreen
import com.shiping.app.data.player.PipController
import com.shiping.app.di.AppContainer
import com.shiping.app.util.Constants
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val TV = "tv"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val API_MANAGE = "api_manage"
    const val PARSER_MANAGE = "parser_manage"
    const val TV_SOURCE_MANAGE = "tv_source_manage"
    const val TV_PLAYER = "tv_player"
    const val DOWNLOAD = "download"
    const val APP_LOG = "app_log"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val DETAIL = "detail/{vodId}?sourceUrl={sourceUrl}"
    const val PLAYER = "player?title={title}&url={url}"
    const val SEARCH_WITH_QUERY = "search_with_query?query={query}"

    fun detail(vodId: Int, sourceUrl: String = ""): String {
        if (sourceUrl.isEmpty()) return "detail/$vodId"
        val encodedUrl = URLEncoder.encode(sourceUrl, "UTF-8")
        return "detail/$vodId?sourceUrl=$encodedUrl"
    }

    /** 带初始关键字的搜索页（详情页点击片名跳入） */
    fun searchWithQuery(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "search_with_query?query=$encoded"
    }

    fun player(title: String, url: String): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        return "player?title=$encodedTitle&url=$encodedUrl"
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "首页", Icons.Default.Home),
    BottomItem(Routes.TV, "直播", Icons.Default.LiveTv),
    BottomItem(Routes.SEARCH, "搜索", Icons.Default.Search),
    BottomItem(Routes.SETTINGS, "设置", Icons.Default.Settings),
)

// region 页面转场动画（翻页效果）
// 前进：新页从右侧整页滑入，旧页向左侧视差滑动；返回时反向

/** 前进进入：新页自右侧滑入 */
private val navEnterTransition: EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

/** 前进退出：旧页向左侧视差滑出 */
private val navExitTransition: ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 3 } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

/** 返回进入：下层页自左侧视差滑回 */
private val navPopEnterTransition: EnterTransition =
    slideInHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { -it / 3 } +
        fadeIn(tween(Constants.NAV_ANIM_DURATION_MS))

/** 返回退出：顶层页向右侧整页滑出 */
private val navPopExitTransition: ExitTransition =
    slideOutHorizontally(tween(Constants.NAV_ANIM_DURATION_MS)) { it } +
        fadeOut(tween(Constants.NAV_ANIM_DURATION_MS))

// endregion

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current
    var backPressTime by remember { mutableLongStateOf(0L) }

    // 全局唯一播放器：内嵌页与全屏页共享同一个 Activity 级 ViewModel/ExoPlayer，
    // 切换全屏时播放器实例不销毁、不重新缓冲
    val sharedPlayerViewModel: PlayerViewModel = viewModel(
        viewModelStoreOwner = context as ComponentActivity,
    )

    // 画中画开关（设置 → 播放设置）；pipEnabled 变化时重新注册 provider
    val pipEnabled by AppContainer.preferences.pipEnabled.collectAsState(initial = true)
    val isPipMode by PipController.isPipMode.collectAsState()

    // 向 PipController 注册状态 provider（lambda 不持有 Activity）
    // MainActivity.onUserLeaveHint 据此判断是否进入画中画
    DisposableEffect(pipEnabled) {
        PipController.pipEnabledProvider = { pipEnabled }
        PipController.isPlayingProvider = { sharedPlayerViewModel.uiState.value.isPlaying }
        PipController.isOnPlayerRouteProvider = {
            navController.currentBackStack.value.any { entry ->
                val route = entry.destination.route
                route != null && (
                    route == Routes.PLAYER || route == Routes.TV ||
                        route == Routes.TV_PLAYER || route.startsWith("detail/")
                )
            }
        }
        PipController.aspectProvider = {
            val s = sharedPlayerViewModel.uiState.value
            s.videoWidth to s.videoHeight
        }
        onDispose { PipController.reset() }
    }

    // 处理下载完成通知跳转：消费待播放项并导航到播放器
    LaunchedEffect(Unit) {
        PlayerArgsHolder.consumePendingPlay()?.let { (url, title) ->
            navController.navigate(Routes.player(title, url))
        }
    }

    // 播放器生命周期统一管理：返回栈中不存在详情页/全屏页时释放播放器并恢复亮度
    // PiP 模式下即使路由变化也不释放，否则小窗画面会冻结
    val backStack by navController.currentBackStack.collectAsState()
    LaunchedEffect(backStack, isPipMode) {
        val playerOnScreen = backStack.any { entry ->
            val route = entry.destination.route
            route != null && (route == Routes.PLAYER || route == Routes.TV || route == Routes.TV_PLAYER || route.startsWith("detail/"))
        }
        if (!playerOnScreen && !isPipMode) {
            sharedPlayerViewModel.releasePlayer()
            (context as? Activity)?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    // 首页双击返回退出应用
    val isOnHome = currentDestination?.route == Routes.HOME
    BackHandler(enabled = isOnHome) {
        val now = System.currentTimeMillis()
        if (now - backPressTime < Constants.BACK_PRESS_EXIT_INTERVAL_MS) {
            (context as? Activity)?.finish()
        } else {
            backPressTime = now
            Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    // PiP 模式下隐藏底部导航栏
    val showBottomBar = bottomItems.any { it.route == currentDestination?.route } && !isPipMode

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { navEnterTransition },
            exitTransition = { navExitTransition },
            popEnterTransition = { navPopEnterTransition },
            popExitTransition = { navPopExitTransition },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onVideoClick = { id -> navController.navigate(Routes.detail(id)) },
                    onFavoritesClick = { navController.navigate(Routes.FAVORITES) },
                    onHistoryClick = { navController.navigate(Routes.HISTORY) },
                    onSearchClick = { navController.navigate(Routes.SEARCH) },
                    onContinueClick = { record ->
                        // 继续观看：回跳对应来源的详情页续播
                        navController.navigate(Routes.detail(record.vodId, record.sourceUrl))
                    },
                )
            }
            composable(Routes.TV) {
                TvScreen(
                    playerViewModel = sharedPlayerViewModel,
                    onFullscreen = { title, url ->
                        navController.navigate(Routes.TV_PLAYER)
                    },
                )
            }
            composable(Routes.TV_PLAYER) {
                TvLivePlayerScreen(
                    playerViewModel = sharedPlayerViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { record ->
                        // 跳转详情页，由用户自行点击播放
                        navController.navigate(Routes.detail(record.vodId, record.sourceUrl))
                    },
                )
            }
            composable(Routes.FAVORITES) {
                FavoriteScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { id, sourceUrl ->
                        navController.navigate(Routes.detail(id, sourceUrl))
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { id, sourceUrl -> navController.navigate(Routes.detail(id, sourceUrl)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onApiManage = { navController.navigate(Routes.API_MANAGE) },
                    onParserManage = { navController.navigate(Routes.PARSER_MANAGE) },
                    onDownloadManage = { navController.navigate(Routes.DOWNLOAD) },
                    onTvSourceManage = { navController.navigate(Routes.TV_SOURCE_MANAGE) },
                    onAppLog = { navController.navigate(Routes.APP_LOG) },
                )
            }
            composable(Routes.APP_LOG) {
                AppLogScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.API_MANAGE) {
                ApiManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PARSER_MANAGE) {
                ParserManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TV_SOURCE_MANAGE) {
                TvSourceManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DOWNLOAD) {
                DownloadScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { title, url ->
                        navController.navigate(Routes.player(title, url))
                    },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("vodId") { type = NavType.IntType },
                    navArgument("sourceUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStackEntry ->
                val vodId = backStackEntry.arguments?.getInt("vodId") ?: 0
                val sourceUrl = backStackEntry.arguments?.getString("sourceUrl").orEmpty()
                DetailScreen(
                    vodId = vodId,
                    sourceUrl = sourceUrl,
                    playerViewModel = sharedPlayerViewModel,
                    onBack = { navController.popBackStack() },
                    onFullscreen = { title, url ->
                        // 全屏复用同一个播放器实例，播放不中断
                        navController.navigate(Routes.player(title, url))
                    },
                    onSearchTitle = { title ->
                        // 点击片名跳转搜索
                        navController.navigate(Routes.searchWithQuery(title))
                    },
                    onRecommendClick = { id ->
                        // 同类型推荐跳转详情（保持当前源）
                        navController.navigate(Routes.detail(id, sourceUrl))
                    },
                )
            }
            composable(
                route = Routes.SEARCH_WITH_QUERY,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStackEntry ->
                val initialQuery = backStackEntry.arguments?.getString("query")
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    .orEmpty()
                SearchScreen(
                    initialQuery = initialQuery,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { id, source -> navController.navigate(Routes.detail(id, source)) },
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title").orEmpty()
                val url = backStackEntry.arguments?.getString("url").orEmpty()
                PlayerScreen(
                    title = title,
                    url = url,
                    viewModel = sharedPlayerViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
            // PiP 模式：覆盖透明触摸吞层，屏蔽 Compose 控制层/手势对小窗内系统控件的触摸干扰
            if (isPipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }
    }
}
