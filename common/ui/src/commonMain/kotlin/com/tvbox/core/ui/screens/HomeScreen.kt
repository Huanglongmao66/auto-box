package com.tvbox.core.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.UserVodSource
import com.tvbox.core.model.VodClass
import com.tvbox.core.model.VodInfo
import com.tvbox.core.repository.HomeContent
import com.tvbox.core.ui.components.Badge
import com.tvbox.core.ui.components.PosterPlaceholder
import com.tvbox.core.ui.components.ScoreBadge
import com.tvbox.core.ui.components.VodCard
import com.tvbox.core.ui.components.VodGrid
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.mock.MockData
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================= 首页（截图风格：AppBar + 胶囊Tab + 3列网格 + FAB + 3弹窗） =================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onVodClick: (VodInfo) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onPlayUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // ===== 弹窗状态 =====
    var showApiDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showPlayUrlDialog by remember { mutableStateOf(false) }

    // ===== 配置：API列表 / 点播线路 =====
    val configManager = remember { ServiceLocator.getConfigManager() }
    // 改用显式 List + mutableStateOf 防止 SnapshotStateList 与 Result/Iterable 扩展产生类型推断歧义
    var apiUrlsList: List<String> by remember { mutableStateOf(emptyList()) }
    var currentApiIndex by remember { mutableIntStateOf(0) }
    var vodSourcesList: List<UserVodSource> by remember { mutableStateOf(emptyList()) }
    var currentVodKey by remember { mutableStateOf("") }
    var apiParsing by remember { mutableStateOf(false) }

    // 数据刷新触发器（需在 LaunchedEffect(Unit) 之前声明，以便在其中引用）
    val refreshKey = remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val cfg = runCatching { configManager.getConfig() }.getOrNull() ?: return@LaunchedEffect
        if (cfg.apiUrls.isNotEmpty()) {
            apiUrlsList = cfg.apiUrls
            currentApiIndex = cfg.currentApiIndex.coerceIn(0, (apiUrlsList.size - 1).coerceAtLeast(0))
        }
        // 点播源：保证单选一致性（仅 currentVodKey → enabled=true）
        val key = cfg.currentVodKey.ifBlank {
            cfg.vodSources.firstOrNull { it.enabled }?.key ?: cfg.vodSources.firstOrNull()?.key ?: ""
        }
        vodSourcesList = cfg.vodSources.map { it.copy(enabled = it.key == key) }
        currentVodKey = if (vodSourcesList.any { it.key == key }) key else vodSourcesList.firstOrNull()?.key ?: ""

        // 检查是否有有效的源（支持 JSON/XML/Spider 各类型，只要有 api 即可）
        val hasValidSource = vodSourcesList.any { it.api.isNotBlank() }
        if (hasValidSource) {
            try { ServiceLocator.getSourceManager().syncFromUserSources(vodSourcesList) } catch (_: Throwable) {}
            refreshKey.intValue++
        } else if (apiUrlsList.isNotEmpty()) {
            // 配置中没有有效源，遍历所有 API URL 重新解析，直到找到含源的订阅
            apiParsing = true
            val parser = ServiceLocator.getApiSubscriptionParser()
            var foundValid = false
            for ((idx, url) in apiUrlsList.withIndex()) {
                try {
                    val result = parser.parse(url)
                    if (result.isSuccess && result.vodSources.isNotEmpty()) {
                        val firstKey = result.vodSources.first().key
                        val normalized = result.vodSources.mapIndexed { i, s: UserVodSource -> s.copy(enabled = i == 0) }
                        vodSourcesList = normalized
                        currentVodKey = firstKey
                        currentApiIndex = idx
                        configManager.update { c ->
                            c.copy(vodSources = normalized, currentVodKey = firstKey, currentApiIndex = idx,
                                liveSources = if (result.liveSources.isNotEmpty()) result.liveSources else c.liveSources)
                        }
                        try { ServiceLocator.getSourceManager().syncFromUserSources(normalized) } catch (_: Throwable) {}
                        refreshKey.intValue++
                        foundValid = true
                        break
                    }
                } catch (_: Throwable) {}
            }
            apiParsing = false
            if (!foundValid) {
                // 所有 API 都没找到源，检查 SourceManager 是否已有默认源（由 ServiceLocator 初始化时添加）
                val mgrSources = runCatching { ServiceLocator.getSourceManager().getSources() }.getOrDefault(emptyList())
                if (mgrSources.isNotEmpty()) {
                    val userSources = mgrSources.map { s ->
                        UserVodSource(key = s.key, name = s.name, api = s.api, enabled = s.enabled)
                    }
                    vodSourcesList = userSources
                    currentVodKey = userSources.firstOrNull { it.enabled }?.key ?: userSources.firstOrNull()?.key ?: ""
                    refreshKey.intValue++
                }
            }
        }
    }

    // ===== 真实数据加载状态 =====
    var loading by remember { mutableStateOf(true) }
    var homeContent by remember { mutableStateOf<HomeContent?>(null) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categoryPageData: MutableMap<String, List<VodInfo>> = remember { mutableStateMapOf() }
    val categoryPageInfo: MutableMap<String, Pair<Int, Int>> = remember { mutableStateMapOf() }
    var categoryLoading by remember { mutableStateOf(false) }
    var loadMoreLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey.intValue) {
        loading = true
        categoryPageData.clear()
        val repo = runCatching { ServiceLocator.getVodRepository() }.getOrNull()
        val content = repo?.runCatching { getHomeContent() }?.getOrNull()
        homeContent = content
        loading = false
    }

    // 分类推荐映射
    val recMap: Map<String, List<VodInfo>> = homeContent?.categoryRecommendations ?: emptyMap()
    val categories: List<VodClass> = homeContent?.categories.takeIf { !it.isNullOrEmpty() }
        ?: MockData.categories
    val listRecs: List<VodInfo> = recMap.values.flatten().distinctBy { it.sourceKey + "|" + it.vodId }
        .ifEmpty { MockData.trending }
    val rankingList: List<VodInfo> = homeContent?.rankingList.takeIf { !it.isNullOrEmpty() }
        ?: MockData.trending

    // 所有推荐合并（网格兜底）
    val recsAllMerged: List<VodInfo> = buildList {
        val seen = mutableSetOf<String>()
        for (v in listRecs) { val k = v.sourceKey + "|" + v.vodId; if (k in seen) continue; seen.add(k); add(v) }
        for (v in rankingList) { val k = v.sourceKey + "|" + v.vodId; if (k in seen) continue; seen.add(k); add(v) }
        for ((_, vs) in recMap) for (v in vs) {
            val k = v.sourceKey + "|" + v.vodId; if (k in seen) continue; seen.add(k); add(v)
        }
        if (isEmpty()) addAll(MockData.trending + MockData.movieList)
    }

    // 分类 Tab 切换时按需拉取分类内容（第一页）
    LaunchedEffect(selectedCategoryIndex, homeContent) {
        val current = categories.getOrNull(selectedCategoryIndex) ?: return@LaunchedEffect
        val currentName = current.typeName
        val tid = current.typeId
        if (tid.isBlank()) return@LaunchedEffect
        val fromRec = recMap[currentName].orEmpty()
        if (fromRec.isNotEmpty()) return@LaunchedEffect
        if (categoryPageData.containsKey(tid) && categoryPageData[tid]?.isNotEmpty() == true) return@LaunchedEffect
        categoryLoading = true
        val repo = runCatching { ServiceLocator.getVodRepository() }.getOrNull()
        val result = repo?.runCatching { getCategoryContent(tid = tid, page = 1) }?.getOrNull()
        if (result != null && result.list.isNotEmpty()) {
            categoryPageData[tid] = result.list
            categoryPageInfo[tid] = result.page to result.pageCount
        }
        categoryLoading = false
    }

    // 加载更多
    fun loadMore() {
        val current = categories.getOrNull(selectedCategoryIndex) ?: return
        val tid = current.typeId
        if (tid.isBlank()) return
        if (loadMoreLoading || categoryLoading) return
        val (curPage, totalPage) = categoryPageInfo[tid] ?: (1 to 1)
        if (curPage >= totalPage) return
        val nextPage = curPage + 1
        loadMoreLoading = true
        scope.launch {
            val repo = runCatching { ServiceLocator.getVodRepository() }.getOrNull()
            val result = repo?.runCatching { getCategoryContent(tid = tid, page = nextPage) }?.getOrNull()
            if (result != null && result.list.isNotEmpty()) {
                val curList = categoryPageData[tid].orEmpty()
                categoryPageData[tid] = curList + result.list
                categoryPageInfo[tid] = result.page to result.pageCount
            }
            loadMoreLoading = false
        }
    }

    // 下拉刷新
    fun pullRefresh() {
        if (isRefreshing || loading) return
        isRefreshing = true
        scope.launch {
            categoryPageData.clear()
            categoryPageInfo.clear()
            val repo = runCatching { ServiceLocator.getVodRepository() }.getOrNull()
            val content = repo?.runCatching { getHomeContent() }?.getOrNull()
            homeContent = content
            val current = categories.getOrNull(selectedCategoryIndex)
            val tid = current?.typeId.orEmpty()
            if (tid.isNotBlank() && !recMap[current?.typeName.orEmpty()].isNullOrEmpty().not()) {
                val result = repo?.runCatching { getCategoryContent(tid = tid, page = 1) }?.getOrNull()
                if (result != null && result.list.isNotEmpty()) {
                    categoryPageData[tid] = result.list
                    categoryPageInfo[tid] = result.page to result.pageCount
                }
            }
            isRefreshing = false
        }
    }

    // 当前分类数据（3列网格展示）
    val selectedCat = categories.getOrNull(selectedCategoryIndex)
    val currentCatItems: List<VodInfo> = run {
        val selectedName = selectedCat?.typeName ?: "推荐"
        val fromCatPage = categoryPageData[selectedCat?.typeId.orEmpty()].orEmpty()
        if (fromCatPage.isNotEmpty()) fromCatPage
        else if (recMap[selectedName].orEmpty().isNotEmpty()) recMap[selectedName].orEmpty()
        else if (selectedName == "推荐" || selectedCat == null) recsAllMerged
        else when (selectedName) {
            "电影" -> recsAllMerged.filter { it.vodClass.contains("电影") }.ifEmpty { MockData.movieList }
            "电视剧" -> recsAllMerged.filter { it.vodClass.contains("电视剧") or it.vodClass.contains("剧集") }.ifEmpty { MockData.dramaList }
            "综艺" -> recsAllMerged.filter { it.vodClass.contains("综艺") }.ifEmpty { MockData.trending }
            "动漫" -> recsAllMerged.filter { it.vodClass.contains("动漫") or it.vodClass.contains("动画") }.ifEmpty { MockData.animeList }
            else -> recsAllMerged
        }
    }

    // 当前线路名（顶部显示）
    val currentSourceName: String = run {
        val src = vodSourcesList.firstOrNull { it: UserVodSource -> it.key == currentVodKey }
        if (src == null) "未选择点播源"
        else src.name.ifBlank { src.api.take(32) }
    }

    // ============================== 主 UI ==============================
    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ========== 1. 顶部 AppBar ==========
            HomeTopBar(
                sourceName = currentSourceName,
                parsing = apiParsing,
                logoUrl = homeContent?.logo.orEmpty(),
                onClickAvatar = { showApiDialog = true },
                onClickSourceName = { showSourceDialog = true },
                onClickSearch = onNavigateToSearch,
                onClickStar = onNavigateToFavorites,
                onClickRefresh = onNavigateToHistory
            )

            // ========== 2. 分类胶囊 Tab（横向滚动） ==========
            CategoryPills(
                categories = categories.map { it.typeName },
                selectedIndex = selectedCategoryIndex.coerceAtMost((categories.size - 1).coerceAtLeast(0)),
                onSelect = { selectedCategoryIndex = it }
            )

            // ========== 3. 3列封面网格（主内容） ==========
            BoxWithConstraints(Modifier.fillMaxSize().weight(1f)) {
                val cols = when {
                    maxWidth >= 900.dp -> 5
                    maxWidth >= 600.dp -> 4
                    else -> 3
                }
                if (loading && currentCatItems.isEmpty()) {
                    // 骨架占位：3列空白封面
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = tokens.spacing.md,
                            end = tokens.spacing.md,
                            top = tokens.spacing.sm,
                            bottom = 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                    ) {
                        items((0 until cols * 4).toList()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                            ) {
                                for (c in 0 until cols) {
                                    PosterPlaceholder(title = "", modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else if (currentCatItems.isEmpty()) {
                    EmptyHome(
                        onRefresh = {
                            scope.launch {
                                categoryPageData.clear()
                                categoryPageInfo.clear()
                                refreshKey.intValue++
                            }
                        }
                    )
                } else {
                    val selectedTid = selectedCat?.typeId.orEmpty()
                    val (curPage, totalPage) = categoryPageInfo[selectedTid] ?: (1 to 1)
                    val canLoadMore = selectedTid.isNotBlank() && curPage < totalPage
                    VodGrid(
                        items = currentCatItems,
                        columns = cols,
                        onClick = onVodClick,
                        contentPadding = PaddingValues(
                            start = tokens.spacing.md,
                            end = tokens.spacing.md,
                            top = if (isRefreshing) tokens.spacing.lg else tokens.spacing.sm,
                            bottom = 140.dp
                        ),
                        scrollEnabled = true,
                        showLoadMore = canLoadMore && loadMoreLoading,
                        onLoadMore = { loadMore() }
                    )
                    // 下拉刷新指示器
                    if (isRefreshing) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = tokens.spacing.md),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(tokens.spacing.sm))
                                Text(
                                    "刷新中...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ========== 4. 右下角悬浮链接播放按钮（FAB） ==========
        FloatingActionButton(
            onClick = { showPlayUrlDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = tokens.spacing.lg, bottom = 88.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ) {
            Icon(
                imageVector = TVBoxIcons.Outlined.InsertLink,
                contentDescription = "输入链接播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // ============================== 3 个弹窗 ==============================

    // ---- 弹窗 A: 选择 API 接口（点击猫咪头像） ----
    if (showApiDialog) {
        PillListDialog(
            title = "选择接口",
            items = apiUrlsList,
            selectedIndex = currentApiIndex,
            parsing = apiParsing,
            onDismiss = { showApiDialog = false },
            onSelect = { idx ->
                if (idx !in apiUrlsList.indices || apiParsing) return@PillListDialog
                currentApiIndex = idx
                val targetUrl = apiUrlsList[idx]
                apiParsing = true
                scope.launch {
                    try {
                        val parser = ServiceLocator.getApiSubscriptionParser()
                        val result = parser.parse(targetUrl)
                        if (!result.isSuccess) {
                            snackbar.showSnackbar("解析失败：${result.error ?: "未知错误"}", duration = SnackbarDuration.Long)
                        } else {
                            if (result.vodSources.isNotEmpty()) {
                                val firstKey = (result.vodSources as List<UserVodSource>).firstOrNull()?.key ?: ""
                                val normalized = result.vodSources.mapIndexed { i, s: UserVodSource -> s.copy(enabled = i == 0) }
                                vodSourcesList = normalized
                                currentVodKey = firstKey
                                try { ServiceLocator.getSourceManager().syncFromUserSources(normalized) } catch (_: Throwable) {}
                            }
                            configManager.update { cfg ->
                                val newApiUrls = apiUrlsList
                                val newVodSources = if (result.vodSources.isNotEmpty()) vodSourcesList else cfg.vodSources
                                val newCurrVodKey = if (result.vodSources.isNotEmpty()) currentVodKey else cfg.currentVodKey
                                val newLiveSources = if (result.liveSources.isNotEmpty()) result.liveSources else cfg.liveSources
                                cfg.copy(
                                    apiUrls = newApiUrls,
                                    currentApiIndex = currentApiIndex,
                                    vodSources = newVodSources,
                                    currentVodKey = newCurrVodKey,
                                    liveSources = newLiveSources
                                )
                            }
                            // 刷新首页数据
                            categoryPageData.clear()
                            refreshKey.intValue++
                            val msg = buildString {
                                append("切换成功：")
                                if (result.vodSources.isNotEmpty()) append("点播源 ${result.vodSources.size} 个 ")
                                if (result.liveSources.isNotEmpty()) append("直播源 ${result.liveSources.size} 个")
                            }
                            snackbar.showSnackbar(msg, duration = SnackbarDuration.Long)
                        }
                    } catch (t: Throwable) {
                        snackbar.showSnackbar("异常：${t.message ?: "未知错误"}", duration = SnackbarDuration.Long)
                    }
                    apiParsing = false
                    showApiDialog = false
                }
            }
        )
    }

    // ---- 弹窗 B: 切换线路（点击顶部线路名称） ----
    if (showSourceDialog) {
        LineSwitchDialog(
            title = "切换线路",
            items = vodSourcesList,
            currentKey = currentVodKey,
            onDismiss = { showSourceDialog = false },
            onSelect = { selectedSrc ->
                currentVodKey = selectedSrc.key
                scope.launch {
                    val normalized = vodSourcesList.map { src: UserVodSource -> src.copy(enabled = src.key == currentVodKey) }
                    vodSourcesList = normalized
                    configManager.update { cfg -> cfg.copy(vodSources = normalized, currentVodKey = currentVodKey) }
                    try { ServiceLocator.getSourceManager().syncFromUserSources(normalized) } catch (_: Throwable) {}
                    categoryPageData.clear()
                    refreshKey.intValue++
                    snackbar.showSnackbar("已切换：${selectedSrc.name}", duration = SnackbarDuration.Short)
                }
                showSourceDialog = false
            },
            onDelete = { deletedSrc ->
                val idx = vodSourcesList.indexOfFirst { s: UserVodSource -> s.key == deletedSrc.key }
                if (idx in vodSourcesList.indices) {
                    val mutableCopy = vodSourcesList.toMutableList()
                    mutableCopy.removeAt(idx)
                    if (currentVodKey == deletedSrc.key) currentVodKey = mutableCopy.firstOrNull()?.key ?: ""
                    vodSourcesList = mutableCopy
                    scope.launch {
                        val normalized = mutableCopy.map { s: UserVodSource -> s.copy(enabled = s.key == currentVodKey) }
                        vodSourcesList = normalized
                        configManager.update { cfg -> cfg.copy(vodSources = normalized, currentVodKey = currentVodKey) }
                        try { ServiceLocator.getSourceManager().syncFromUserSources(normalized) } catch (_: Throwable) {}
                    }
                }
            }
        )
    }

    // ---- 弹窗 C: 输入链接播放（点击右下角 FAB） ----
    if (showPlayUrlDialog) {
        PlayUrlDialog(
            onDismiss = { showPlayUrlDialog = false },
            onConfirm = { url ->
                showPlayUrlDialog = false
                if (url.isNotBlank()) onPlayUrl(url)
            }
        )
    }
}

// ================= 精选 Banner（轮播） =================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBanner(
    items: List<VodInfo>,
    onClick: (VodInfo) -> Unit
) {
    val tokens = tvTokens()
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    // 自动轮播
    LaunchedEffect(pagerState) {
        while (true) {
            delay(4000)
            with(pagerState) {
                val next = if (currentPage + 1 < items.size) currentPage + 1 else 0
                animateScrollToPage(next)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
        ) { page ->
            val vod = items[page]
            BannerCard(vod = vod, onClick = { onClick(vod) })
        }
        Spacer(modifier = Modifier.height(tokens.spacing.md))
        PageIndicator(
            count = items.size,
            current = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
        )
    }
}

@Composable
private fun BannerCard(
    vod: VodInfo,
    onClick: () -> Unit
) {
    val tokens = tvTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.35f) // 宽银幕比例
            .clickableSafe(onClick = onClick)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
    ) {
        // 渐变遮罩（底部保证文字可读）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        // 占位大图
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.xl),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosterPlaceholder(
                    title = vod.vodName,
                    aspectRatio = 0.7f,
                    modifier = Modifier.width(120.dp),
                    accentColor = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(tokens.spacing.xl))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(score = vod.vodScore)
                        if (vod.vodRemarks.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(tokens.spacing.sm))
                            Badge(text = vod.vodRemarks, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    Text(
                        text = vod.vodName,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (vod.vodClass.isNotEmpty() || vod.vodYear.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(tokens.spacing.xs))
                        val meta = buildString {
                            if (vod.vodYear.isNotEmpty()) append(vod.vodYear)
                            if (vod.vodYear.isNotEmpty() && vod.vodClass.isNotEmpty()) append(" · ")
                            if (vod.vodClass.isNotEmpty()) append(vod.vodClass)
                            if (vod.vodArea.isNotEmpty()) append(" · ").append(vod.vodArea)
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                    if (vod.vodContent.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(tokens.spacing.md))
                        Text(
                            text = vod.vodContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.lg))
                    Row {
                        androidx.compose.material3.Button(
                            onClick = onClick,
                            contentPadding = PaddingValues(
                                horizontal = tokens.spacing.xl,
                                vertical = tokens.spacing.sm
                            )
                        ) {
                            Icon(TVBoxIcons.Filled.PlayArrow, null)
                            Spacer(modifier = Modifier.width(tokens.spacing.xs))
                            Text("立即播放", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.width(tokens.spacing.md))
                        OutlinedButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(
                                horizontal = tokens.spacing.xl,
                                vertical = tokens.spacing.sm
                            )
                        ) {
                            Icon(TVBoxIcons.Outlined.Info, null)
                            Spacer(modifier = Modifier.width(tokens.spacing.xs))
                            Text("详情", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit
) {
    val tokens = tvTokens()
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by androidx.compose.animation.core.animateDpAsState(
                if (selected) 24.dp else 8.dp, label = "indicator-w"
            )
            val color by androidx.compose.animation.animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                label = "indicator-c"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
                    .clickableSafe { onSelect(i) }
            )
        }
    }
}

// ================= 横向推荐行 =================

@Composable
private fun FeaturedRow(
    items: List<VodInfo>,
    onClick: (VodInfo) -> Unit
) {
    val tokens = tvTokens()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        items(items, key = { it.vodId + it.sourceKey }) { vod ->
            Column(modifier = Modifier.width(160.dp)) {
                VodCard(vodInfo = vod, onClick = onClick)
            }
        }
    }
}

// ================= 排行榜行 =================

@Composable
private fun RankingRow(
    items: List<VodInfo>,
    onClick: (VodInfo) -> Unit
) {
    val tokens = tvTokens()
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        items.take(5).forEachIndexed { index, vod ->
            RankingItem(vod = vod, rank = index + 1, onClick = { onClick(vod) })
        }
    }
}

@Composable
private fun RankingItem(
    vod: VodInfo,
    rank: Int,
    onClick: () -> Unit
) {
    val tokens = tvTokens()
    val rankColor = when (rank) {
        1 -> Color(0xFFEF4444) // 红
        2 -> Color(0xFFF97316) // 橙
        3 -> Color(0xFFEAB308) // 黄
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickableSafe(onClick = onClick)
            .padding(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = rankColor,
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(tokens.spacing.sm))
        Box(modifier = Modifier.width(72.dp)) {
            PosterPlaceholder(
                title = vod.vodName,
                aspectRatio = 0.7f
            )
        }
        Spacer(modifier = Modifier.width(tokens.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vod.vodName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val meta = buildString {
                if (vod.vodYear.isNotEmpty()) append(vod.vodYear)
                if (vod.vodYear.isNotEmpty() && vod.vodClass.isNotEmpty()) append(" · ")
                if (vod.vodClass.isNotEmpty()) append(vod.vodClass)
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (vod.vodScore.isNotEmpty()) {
            ScoreBadge(score = vod.vodScore)
        }
    }
}

@Composable
private fun RecentWatchItem(
    vod: VodInfo,
    rank: Int,
    onClick: () -> Unit
) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickableSafe(onClick = onClick)
            .padding(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(84.dp)) {
            PosterPlaceholder(title = vod.vodName)
        }
        Spacer(modifier = Modifier.width(tokens.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vod.vodName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(tokens.spacing.xs))
            Badge(text = "已看 第12集 / 第40集", color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(tokens.spacing.sm))
            // 进度条
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Icon(
            imageVector = TVBoxIcons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(tokens.size.iconLg)
        )
    }
}

// 小工具：clickableSafe（避免平台 API 差异编译失败，统一用 Modifier.clickable）
@Composable
private fun Modifier.clickableSafe(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickableSafeImpl(onClick = onClick))

// 直接内联 clickable，防止不同平台报错
private fun Modifier.clickableSafeImpl(onClick: () -> Unit): Modifier =
    (this as androidx.compose.ui.Modifier).clickable(onClick = onClick)

// ================= 首页空占位 =================

@Composable
private fun EmptyHome(
    onRefresh: () -> Unit
) {
    val tokens = tvTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = TVBoxIcons.Outlined.Source,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        Text(
            text = "暂无内容",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            text = "请前往「设置 → 点播源」添加可用的点播订阅源，或切换其他点播源。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(tokens.spacing.lg))
        OutlinedButton(onClick = onRefresh) {
            Icon(
                imageVector = TVBoxIcons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("重新加载")
        }
    }
}

// =====================================================================
// 新增：截图风格组件
// =====================================================================

// ========== 顶部 AppBar：猫咪头像 + 线路名 + 搜索/收藏/刷新 ==========

@Composable
private fun HomeTopBar(
    sourceName: String,
    parsing: Boolean,
    logoUrl: String = "",
    onClickAvatar: () -> Unit,
    onClickSourceName: () -> Unit,
    onClickSearch: () -> Unit,
    onClickStar: () -> Unit,
    onClickRefresh: () -> Unit
) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.md,
                end = tokens.spacing.sm,
                top = tokens.spacing.sm,
                bottom = tokens.spacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像按钮（猫咪图标 → 切API）
        Surface(
            onClick = onClickAvatar,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (logoUrl.isNotBlank()) {
                    val context = LocalPlatformContext.current
                    val model = remember(logoUrl) {
                        ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = "切换接口",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = TVBoxIcons.Outlined.Pets,
                        contentDescription = "切换接口",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(tokens.spacing.sm))
        // 线路名称（可点击 → 切线路）
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClickSourceName)
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (parsing) {
                Spacer(Modifier.width(tokens.spacing.sm))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.width(tokens.spacing.xs))
        // 右侧图标：搜索 / 收藏 / 刷新
        IconButton(onClick = onClickSearch) {
            Icon(
                imageVector = TVBoxIcons.Outlined.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onClickStar) {
            Icon(
                imageVector = TVBoxIcons.Outlined.Star,
                contentDescription = "收藏",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onClickRefresh) {
            Icon(
                imageVector = TVBoxIcons.Outlined.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ========== 分类胶囊 Tab（横向滚动的圆角胶囊按钮） ==========

@Composable
private fun CategoryPills(
    categories: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val tokens = tvTokens()
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        itemsIndexed(categories) { idx, name ->
            val isSelected = idx == selectedIndex
            val bgColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            val fgColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                onClick = { onSelect(idx) },
                shape = RoundedCornerShape(999.dp),
                color = bgColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = fgColor,
                    modifier = Modifier.padding(
                        horizontal = 18.dp,
                        vertical = 10.dp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ========== 弹窗 A: API 接口选择（胶囊列表样式） ==========

@Composable
private fun PillListDialog(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    parsing: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val tokens = tvTokens()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = "暂无 API 地址，请前往「设置 → API 服务」添加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    itemsIndexed(items) { idx, url ->
                        val isSelected = idx == selectedIndex
                        val bgColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        val fgColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                        Surface(
                            onClick = { onSelect(idx) },
                            enabled = !parsing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(999.dp),
                            color = bgColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fgColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (parsing && isSelected) {
                                        Spacer(Modifier.width(tokens.spacing.sm))
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ========== 弹窗 B: 线路切换（每项右侧带禁止/删除图标） ==========

@Composable
private fun LineSwitchDialog(
    title: String,
    items: List<UserVodSource>,
    currentKey: String,
    onDismiss: () -> Unit,
    onSelect: (UserVodSource) -> Unit,
    onDelete: (UserVodSource) -> Unit
) {
    val tokens = tvTokens()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = "暂无点播线路，请前往「设置 → 点播源」添加，或在「选择接口」中解析 API。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(items, key = { it.key }) { src ->
                        val isSelected = src.key == currentKey
                        val bgColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        val fgColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .background(bgColor)
                                .clickable { onSelect(src) }
                                .padding(start = 20.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = src.name.ifBlank { src.api.take(36) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = fgColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(src) }) {
                                Icon(
                                    imageVector = TVBoxIcons.Outlined.NotInterested,
                                    contentDescription = "删除线路",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ========== 弹窗 C: 输入链接播放 ==========

@Composable
private fun PlayUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入地址…") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            // 预留：剪贴板粘贴 / 文件选择
                            runCatching {
                                // 后续可扩展：从剪贴板读取
                            }
                        }) {
                            Icon(
                                imageVector = TVBoxIcons.Outlined.Folder,
                                contentDescription = "打开/粘贴",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
