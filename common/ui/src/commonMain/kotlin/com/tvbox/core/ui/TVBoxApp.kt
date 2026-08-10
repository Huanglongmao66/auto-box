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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.layout.AdaptiveLayout
import com.tvbox.core.ui.mock.MockData
import com.tvbox.core.ui.navigation.AppNavState
import com.tvbox.core.ui.navigation.AppScaffold
import com.tvbox.core.ui.navigation.BottomTab
import com.tvbox.core.ui.navigation.rememberAppNavState
import com.tvbox.core.ui.navigation.showDetail
import com.tvbox.core.ui.navigation.showPlayer
import com.tvbox.core.ui.screens.DetailScreen
import com.tvbox.core.ui.screens.FavoritesScreen
import com.tvbox.core.ui.screens.HomeScreen
import com.tvbox.core.ui.screens.PlayerScreen
import com.tvbox.core.ui.screens.SearchScreen
import com.tvbox.core.ui.screens.SettingsScreen
import com.tvbox.core.ui.screens.SourcesScreen
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
    AppTheme(isDark = initialDarkMode) {
        val navState = rememberAppNavState()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val tokens = tvTokens()
        val topBarBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )

        AdaptiveLayout(modifier = modifier) { isLandscape, isLarge, useSideNav ->
            val showP = navState.showPlayer
            val showD = navState.showDetail
            when {
                // 播放器：全屏沉浸式
                showP -> {
                    val vod = navState.playerVodId?.let { findVodById(it, navState.playerSourceKey) }
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
                            icon = TVBoxIcons.Outlined.Error
                        ) { navState.backFromPlayer() }
                    }
                }
                // 详情页
                showD -> {
                    val vod = navState.detailVodId?.let { findVodById(it, navState.detailSourceKey) }
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
                                    navState.navigateToDetail(vodId = v.vodId, sourceKey = v.sourceKey)
                                }
                            )
                        }
                    } else {
                        EmptyView(
                            title = "未找到内容",
                            description = "影视资源不存在或已下架",
                            icon = TVBoxIcons.Outlined.Error
                        ) { navState.backFromDetail() }
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
                                        navState.navigateToDetail(vodId = it.vodId, sourceKey = it.sourceKey)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                BottomTab.SEARCH -> SearchScreen(
                                    onVodClick = {
                                        navState.navigateToDetail(vodId = it.vodId, sourceKey = it.sourceKey)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                BottomTab.SOURCES -> SourcesScreen(
                                    modifier = Modifier.fillMaxSize()
                                )
                                BottomTab.FAVORITES -> FavoritesScreen(
                                    onVodClick = {
                                        navState.navigateToDetail(vodId = it.vodId, sourceKey = it.sourceKey)
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

/** 通过 id + sourceKey 在 MockData 中查找 Vod（用于二级页回显） */
private fun findVodById(vodId: String, sourceKey: String?): VodInfo? {
    val all = buildList<VodInfo> {
        addAll(MockData.homeBanner)
        addAll(MockData.movieList)
        addAll(MockData.dramaList)
        addAll(MockData.animeList)
        addAll(MockData.trending)
        addAll(MockData.historySamples)
        addAll(MockData.favoriteSamples)
    }
    val exact = all.firstOrNull { it.vodId == vodId }
    if (exact != null) {
        // 详情页需要 episodes，若不存在则从示例补充
        return if (exact.episodes.isEmpty()) {
            val sample = MockData.sampleVodDetail()
            exact.copy(
                vodRemarks = exact.vodRemarks.ifBlank { sample.vodRemarks },
                vodArea = exact.vodArea.ifBlank { sample.vodArea },
                vodClass = exact.vodClass.ifBlank { sample.vodClass },
                vodActor = exact.vodActor.ifBlank { sample.vodActor },
                vodDirector = exact.vodDirector.ifBlank { sample.vodDirector },
                vodContent = exact.vodContent.ifBlank { sample.vodContent },
                vodLang = exact.vodLang.ifBlank { sample.vodLang },
                episodes = sample.episodes
            )
        } else exact
    }
    // 找不到则返回示例详情，占位保证 UI 能打开
    return MockData.sampleVodDetail().copy(vodId = vodId)
}
