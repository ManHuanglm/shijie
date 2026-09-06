package com.shiping.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shiping.app.ui.screen.detail.DetailScreen
import com.shiping.app.ui.screen.home.HomeScreen
import com.shiping.app.ui.screen.player.PlayerScreen
import com.shiping.app.ui.screen.settings.ParserManageScreen
import com.shiping.app.ui.screen.search.SearchScreen
import com.shiping.app.ui.screen.settings.ApiManageScreen
import com.shiping.app.ui.screen.settings.SettingsScreen
import androidx.navigation.NavType

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val API_MANAGE = "api_manage"
    const val PARSER_MANAGE = "parser_manage"
    const val DETAIL = "detail/{vodId}"
    const val PLAYER = "player?title={title}&url={url}"

    fun detail(vodId: Int) = "detail/$vodId"
    fun player(title: String, url: String) =
        "player?title=${java.net.URLEncoder.encode(title, "UTF-8")}&url=${java.net.URLEncoder.encode(url, "UTF-8")}"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "首页", Icons.Default.Home),
    BottomItem(Routes.SEARCH, "搜索", Icons.Default.Search),
    BottomItem(Routes.SETTINGS, "设置", Icons.Default.Settings)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 只在首页/搜索/设置显示底部导航
    val showBottomBar = bottomItems.any { it.route == currentDestination?.route }

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
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onVideoClick = { id -> navController.navigate(Routes.detail(id)) },
                    onSearchClick = { navController.navigate(Routes.SEARCH) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { id -> navController.navigate(Routes.detail(id)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onApiManage = { navController.navigate(Routes.API_MANAGE) },
                    onParserManage = { navController.navigate(Routes.PARSER_MANAGE) }
                )
            }
            composable(Routes.API_MANAGE) {
                ApiManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PARSER_MANAGE) {
                ParserManageScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("vodId") { type = NavType.IntType })
            ) { backStackEntry ->
                val vodId = backStackEntry.arguments?.getInt("vodId") ?: 0
                DetailScreen(
                    vodId = vodId,
                    onBack = { navController.popBackStack() },
                    onPlay = { title, url -> navController.navigate(Routes.player(title, url)) }
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: ""
                val url = backStackEntry.arguments?.getString("url") ?: ""
                PlayerScreen(
                    title = title,
                    url = url,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
