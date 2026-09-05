package com.tvbox.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens

/**
 * 底部导航 Tab 项
 */
enum class BottomTab(
    val route: String,
    val title: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
) {
    HOME("home", "首页", TVBoxIcons.Outlined.Home, TVBoxIcons.Filled.Home),
    SEARCH("search", "搜索", TVBoxIcons.Outlined.Search, TVBoxIcons.Filled.Search),
    SOURCES("sources", "源管理", TVBoxIcons.Outlined.Source, TVBoxIcons.Filled.Source),
    FAVORITES("favorites", "收藏", TVBoxIcons.Outlined.Favorite, TVBoxIcons.Filled.Favorite),
    SETTINGS("settings", "设置", TVBoxIcons.Outlined.Settings, TVBoxIcons.Filled.Settings);

    companion object {
        fun fromRoute(route: String?) = entries.firstOrNull { it.route == route } ?: HOME
    }
}

/**
 * 二级路由（详情页等）
 */
object Routes {
    const val DETAIL = "detail/{vodId}/{sourceKey}"
    const val PLAYER = "player/{vodId}/{sourceKey}/{episodeId}"

    fun detail(vodId: String, sourceKey: String) = "detail/$vodId/$sourceKey"
    fun player(vodId: String, sourceKey: String, episodeId: String) = "player/$vodId/$sourceKey/$episodeId"
}

/**
 * TVBox 根 Scaffold：含 TopBar + 内容区 + BottomNavBar
 *
 * 支持首页/搜索/源管理/收藏/设置一级Tab切换；点击影视卡片可跳详情/播放器（二级）。
 * 当前实现不依赖 androidx.navigation（避免多平台兼容问题），采用简易路由。
 *
 * @param useSideNav 为 true 时使用左侧永久导航抽屉（大屏桌面/TV 友好），否则使用底部导航
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    currentTab: BottomTab,
    onTabChange: (BottomTab) -> Unit,
    useSideNav: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    topBarBehavior: TopAppBarScrollBehavior? = null,
    title: @Composable () -> Unit = { Text(currentTab.title) },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val tokens = tvTokens()
    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues()

    if (useSideNav) {
        // 大屏：左侧永久导航 + 内容
        PermanentNavigationDrawer(
            drawerContent = {
                Surface(
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = tokens.elevation.sm
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = tokens.spacing.xl,
                                start = tokens.spacing.md,
                                end = tokens.spacing.md,
                                bottom = tokens.spacing.xl
                            ),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                    ) {
                        // Logo / 标题
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    TVBoxIcons.Filled.PlayArrow, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(tokens.size.iconMd)
                                )
                            }
                            Spacer(modifier = Modifier.width(tokens.spacing.md))
                            Column {
                                Text(
                                    "TVBox",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "多平台版",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = tokens.spacing.sm),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(tokens.spacing.xs))
                        // Tab 列表
                        BottomTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = if (selected) MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        ) else MaterialTheme.typography.titleSmall
                                    )
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                                        contentDescription = tab.title,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                selected = selected,
                                onClick = { onTabChange(tab) },
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.padding(vertical = tokens.spacing.xs / 2)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // 底部版本信息
                        Text(
                            text = "© 2026 TVBox KMP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = tokens.spacing.md)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { scaffoldPadding ->
                val merged = PaddingValues(
                    start = scaffoldPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = scaffoldPadding.calculateEndPadding(LayoutDirection.Ltr),
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding()
                )
                Box(Modifier.fillMaxSize().padding(merged)) {
                    content(PaddingValues(0.dp))
                }
            }
        }
    } else {
        // 小屏：底部导航
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(tonalElevation = tokens.elevation.sm) {
                    if (topBarBehavior != null) {
                        CenterAlignedTopAppBar(
                            title = title,
                            navigationIcon = navigationIcon,
                            actions = { actions() },
                            scrollBehavior = topBarBehavior,
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    } else {
                        CenterAlignedTopAppBar(
                            title = title,
                            navigationIcon = navigationIcon,
                            actions = { actions() },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            },
            bottomBar = {
                Surface(tonalElevation = tokens.elevation.sm) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onTabChange(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { scaffoldPadding ->
            // 合并 Scaffold 内边距与系统导航栏边距
            val merged = PaddingValues(
                start = scaffoldPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = scaffoldPadding.calculateEndPadding(LayoutDirection.Ltr),
                top = scaffoldPadding.calculateTopPadding(),
                bottom = scaffoldPadding.calculateBottomPadding() +
                    bottomNavPadding.calculateBottomPadding()
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                modifier = Modifier.fillMaxSize().padding(merged)
            ) {
                content(merged)
            }
        }
    }
}

/**
 * 应用根导航状态
 */
class AppNavState {
    var currentTab by mutableStateOf(BottomTab.HOME)
    var detailVodId: String? by mutableStateOf(null)
    var detailSourceKey: String? by mutableStateOf(null)
    var playerVodId: String? by mutableStateOf(null)
    var playerSourceKey: String? by mutableStateOf(null)
    var playerEpisodeId: String? by mutableStateOf(null)

    fun navigateToDetail(vodId: String, sourceKey: String) {
        detailVodId = vodId
        detailSourceKey = sourceKey
    }

    fun navigateToPlayer(vodId: String, sourceKey: String, episodeId: String) {
        playerVodId = vodId
        playerSourceKey = sourceKey
        playerEpisodeId = episodeId
    }

    fun navigateToTab(tab: BottomTab) {
        currentTab = tab
        // 切Tab时清二级路由
        detailVodId = null
        detailSourceKey = null
        playerVodId = null
        playerSourceKey = null
        playerEpisodeId = null
    }

    fun backFromDetail() { detailVodId = null; detailSourceKey = null }
    fun backFromPlayer() { playerVodId = null; playerSourceKey = null; playerEpisodeId = null }

    val showDetail: Boolean get() = detailVodId != null && playerVodId == null
    val showPlayer: Boolean get() = playerVodId != null
}

@Composable
fun rememberAppNavState(): AppNavState = remember { AppNavState() }
