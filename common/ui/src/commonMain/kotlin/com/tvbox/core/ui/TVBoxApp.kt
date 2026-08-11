package com.tvbox.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.core.config.ConfigManager
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.AppConfig
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodInfo
import com.tvbox.core.model.LiveCategory
import com.tvbox.core.repository.VodRepository
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.layout.AdaptiveLayout
import com.tvbox.core.ui.navigation.AppNavState
import com.tvbox.core.ui.navigation.AppScaffold
import com.tvbox.core.ui.navigation.BottomTab
import com.tvbox.core.ui.navigation.rememberAppNavState
import com.tvbox.core.ui.screens.DetailScreen
import com.tvbox.core.ui.screens.FavoritesScreen
import com.tvbox.core.ui.screens.HistoryScreen
import com.tvbox.core.ui.screens.HomeScreen
import com.tvbox.core.ui.screens.LivePlayerScreen
import kotlinx.coroutines.flow.collectLatest
import com.tvbox.core.ui.screens.LiveScreen
import com.tvbox.core.ui.screens.PlayerScreen
import com.tvbox.core.ui.screens.SearchScreen
import com.tvbox.core.ui.screens.SettingsScreen
import com.tvbox.core.ui.theme.AppTheme
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.launch

/**
 * TVBox 根应用组件。
 * 统一接入：主题 + 响应式布局 + 底部/侧边导航 + 路由分发（详情/播放器二级页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVBoxApp(
    modifier: Modifier = Modifier,
    initialDarkMode: Boolean = true
) {
    val configManager = remember { ServiceLocator.getConfigManager() }
    var appConfig by remember { mutableStateOf(AppConfig()) }
    LaunchedEffect(Unit) {
        configManager.configFlow.collectLatest {
            appConfig = it
        }
    }
    LaunchedEffect(Unit) {
        runCatching { configManager.getConfig() }
    }
    AppTheme(isDark = appConfig.isDarkMode, themeSeed = appConfig.themeColorSeed) {
        val navState = rememberAppNavState()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val tokens = tvTokens()
        val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )

        val wallpaperColors = remember(appConfig.wallpaperIndex) {
            when (appConfig.wallpaperIndex) {
                1 -> listOf(Color(0xFF5B86E5), Color(0xFF36D1DC))
                2 -> listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))
                3 -> listOf(Color(0xFFFF8DC8), Color(0xFFFDCBF8))
                4 -> listOf(Color(0xFFFF7E5F), Color(0xFFFEC89A))
                5 -> listOf(Color(0xFF2E3192), Color(0xFF1BFFFF))
                6 -> listOf(Color(0xFF00B09B), Color(0xFF96C93D))
                7 -> listOf(Color(0xFF141E30), Color(0xFF243B55))
                else -> listOf(Color(0xFF667EEA), Color(0xFF764BA2))
            }
        }

        AdaptiveLayout(
            modifier = modifier
                .fillMaxSize()
                .drawWithContent {
                    drawRect(
                        brush = Brush.verticalGradient(wallpaperColors),
                        size = size
                    )
                    drawContent()
                }
        ) { isLandscape, isLarge, useSideNav ->
            // 直播分组数据：LiveScreen 加载后共享给 LivePlayerScreen，避免重复请求
            var liveCategories by remember { mutableStateOf<List<LiveCategory>>(emptyList()) }

            val showLP = navState.showLivePlayer
            val showP = navState.showPlayer
            val showD = navState.showDetail
            val showFav = navState.showFavoritesScreen
            val showHist = navState.showHistoryScreen
            when {
                // 直播播放器：全屏沉浸式
                showLP -> {
                    val cats = navState.liveCategories
                    if (cats != null) {
                        LivePlayerScreen(
                            categories = cats,
                            initialCategoryIndex = navState.liveCategoryIndex,
                            initialChannelIndex = navState.liveChannelIndex,
                            onBack = { navState.backFromLivePlayer() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        EmptyView(
                            title = "无法播放",
                            description = "未找到直播频道数据",
                            icon = TVBoxIcons.Outlined.Error,
                            onAction = { navState.backFromLivePlayer() }
                        )
                    }
                }
                // 播放器：全屏沉浸式
                showP -> {
                    val directUrl = navState.playerDirectUrl
                    if (directUrl != null) {
                        // 直接URL播放模式：构造合成VodInfo + VodEpisode
                        val syntheticVod = VodInfo(
                            vodId = "__direct_url__",
                            sourceKey = "",
                            vodName = "直链播放",
                            vodRemarks = "链接输入",
                            episodes = listOf(
                                VodEpisode(
                                    episodeId = "__direct_ep__",
                                    name = "直接播放",
                                    url = directUrl
                                )
                            )
                        )
                        PlayerScreen(
                            vodInfo = syntheticVod,
                            episode = syntheticVod.episodes.first(),
                            onBack = { navState.backFromPlayer() },
                            onEpisodeChange = { },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        var playerVod by remember(navState.playerVodId, navState.playerSourceKey) { mutableStateOf<VodInfo?>(null) }
                        LaunchedEffect(navState.playerVodId, navState.playerSourceKey) {
                            val id = navState.playerVodId
                            val sk = navState.playerSourceKey
                            if (id == null) { playerVod = null; return@LaunchedEffect }
                            playerVod = runCatching {
                                val repo = ServiceLocator.getVodRepository()
                                if (!sk.isNullOrBlank()) repo.getDetailContent(id, sk)
                                else null
                            }.getOrNull()
                            // 拉取失败时使用详情页已有的 VodInfo 兜底
                            if (playerVod == null) playerVod = navState.detailVodInfo
                        }
                        val vod = playerVod
                        val ep = navState.playerEpisodeId?.let { epId ->
                            vod?.episodes?.firstOrNull { it.episodeId == epId }
                                ?: VodEpisode(episodeId = epId, name = "第${epId.toIntOrNull() ?: 1}集", url = "")
                        }
                        if (vod != null && ep != null) {
                            PlayerScreen(
                                vodInfo = vod,
                                episode = ep,
                                onBack = { navState.backFromPlayer() },
                                onEpisodeChange = { newEp ->
                                    navState.navigateToPlayer(
                                        vodId = vod.vodId,
                                        sourceKey = vod.sourceKey,
                                        episodeId = newEp.episodeId
                                    )
                                }
                            )
                        } else {
                            EmptyView(
                                title = "无法播放",
                                description = "未找到对应的影视或剧集",
                                icon = TVBoxIcons.Outlined.Error,
                                onAction = { navState.backFromPlayer() }
                            )
                        }
                    }
                }
                // 收藏页
                showFav -> {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        FavoritesScreen(
                            onVodClick = {
                                navState.backFromFavorites()
                                navState.navigateToDetail(it)
                            },
                            onBack = { navState.backFromFavorites() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 历史记录页
                showHist -> {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        HistoryScreen(
                            onVodClick = {
                                navState.backFromHistory()
                                navState.navigateToDetail(it)
                            },
                            onBack = { navState.backFromHistory() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // 详情页
                showD -> {
                    val vod = navState.detailVodInfo
                    if (vod != null) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            DetailScreen(
                                vodInfo = vod,
                                onBack = { navState.backFromDetail() },
                                onPlayClick = { ep ->
                                    navState.navigateToPlayer(
                                        vodId = vod.vodId,
                                        sourceKey = vod.sourceKey,
                                        episodeId = ep.episodeId
                                    )
                                },
                                onVodClick = { v ->
                                    navState.navigateToDetail(v)
                                }
                            )
                        }
                    } else {
                        EmptyView(
                            title = "未找到内容",
                            description = "影视资源不存在或已下架",
                            icon = TVBoxIcons.Outlined.Error,
                            onAction = { navState.backFromDetail() }
                        )
                    }
                }
                // 主导航（首页、搜索、源、收藏、设置）
                else -> {
                    AppScaffold(
                        currentTab = navState.currentTab,
                        onTabChange = { navState.navigateToTab(it) },
                        useSideNav = useSideNav,
                        snackbarHostState = snackbarHostState,
                        topBarBehavior = topBarBehavior,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!useSideNav) {
                                    Text(
                                        text = "TVBox",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(tokens.spacing.xs))
                                    Text(
                                        text = navState.currentTab.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = navState.currentTab.title,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .nestedScroll(topBarBehavior.nestedScrollConnection)
                        ) {
                            when (navState.currentTab) {
                                BottomTab.HOME -> HomeScreen(
                                    onVodClick = {
                                        navState.navigateToDetail(it)
                                    },
                                    onNavigateToSearch = {
                                        navState.navigateToTab(BottomTab.SEARCH)
                                    },
                                    onNavigateToFavorites = {
                                        navState.showFavorites = true
                                    },
                                    onNavigateToHistory = {
                                        navState.showHistory = true
                                    },
                                    onPlayUrl = { url ->
                                        navState.navigateToDirectPlayer(url)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                BottomTab.LIVE -> LiveScreen(
                                    onChannelClick = { category, channel ->
                                        val catIdx = liveCategories.indexOf(category)
                                        val chIdx = category.channels.indexOf(channel)
                                        navState.navigateToLivePlayer(
                                            categories = liveCategories,
                                            categoryIndex = catIdx.coerceAtLeast(0),
                                            channelIndex = chIdx.coerceAtLeast(0)
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    onCategoriesLoaded = { liveCategories = it }
                                )
                                BottomTab.SEARCH -> SearchScreen(
                                    onVodClick = {
                                        navState.navigateToDetail(it)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                BottomTab.FAVORITES -> FavoritesScreen(
                                    onVodClick = {
                                        navState.navigateToDetail(it)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                BottomTab.SETTINGS -> SettingsScreen(
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
