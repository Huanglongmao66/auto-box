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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.components.Badge
import com.tvbox.core.ui.components.PosterPlaceholder
import com.tvbox.core.ui.components.ScoreBadge
import com.tvbox.core.ui.components.SectionHeader
import com.tvbox.core.ui.components.SegmentedTabs
import com.tvbox.core.ui.components.SettingGroup
import com.tvbox.core.ui.components.VodCard
import com.tvbox.core.ui.components.VodGrid
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.mock.MockData
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ================= 首页 =================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onVodClick: (VodInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val categories = MockData.categories
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = tokens.spacing.lg,
            end = tokens.spacing.lg,
            top = tokens.spacing.sm,
            bottom = tokens.spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        // === 精选 Banner ===
        item {
            HomeBanner(
                items = MockData.homeBanner,
                onClick = onVodClick
            )
        }

        // === 分类分段选择 ===
        item {
            SegmentedTabs(
                tabs = categories.map { it.typeName },
                selectedIndex = selectedCategoryIndex,
                onSelect = { selectedCategoryIndex = it }
            )
        }

        // === 当前分类推荐 ===
        item {
            val currentName = categories[selectedCategoryIndex].typeName
            val data = when (currentName) {
                "电影" -> MockData.movieList
                "电视剧" -> MockData.dramaList
                "动漫" -> MockData.animeList
                else -> MockData.trending + MockData.movieList.take(4)
            }
            Column {
                SectionHeader(
                    title = "为你推荐·$currentName",
                    actionText = "全部",
                    onAction = { /* TODO: 跳转分类全览 */ }
                )
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                FeaturedRow(items = data, onClick = onVodClick)
            }
        }

        // === 热门榜单 ===
        item {
            Column {
                SectionHeader(
                    title = "本周热播榜",
                    actionText = "更多",
                    onAction = { /* TODO */ }
                )
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                RankingRow(
                    items = MockData.trending,
                    onClick = onVodClick
                )
            }
        }

        // === 大片网格 ===
        item {
            Column {
                SectionHeader(title = "精选影视")
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                BoxWithConstraints {
                    val cols = when {
                        maxWidth >= 1200.dp -> 6
                        maxWidth >= 800.dp -> 5
                        maxWidth >= 560.dp -> 4
                        else -> 3
                    }
                    VodGrid(
                        items = MockData.trending + MockData.movieList,
                        columns = cols,
                        onClick = onVodClick,
                        contentPadding = PaddingValues(0.dp)
                    )
                }
            }
        }
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

// ================= 收藏页 =================

@Composable
fun FavoritesScreen(
    onVodClick: (VodInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val data = MockData.favoriteSamples
    if (data.isEmpty()) {
        com.tvbox.core.ui.components.EmptyView(
            title = "暂无收藏",
            description = "在影视详情页点击「收藏」后将在此展示",
            icon = TVBoxIcons.Outlined.FavoriteBorder
        )
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = tokens.spacing.lg,
            end = tokens.spacing.lg,
            top = tokens.spacing.sm,
            bottom = tokens.spacing.xl
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        item {
            SectionHeader(title = "我的收藏", action = {
                Badge(text = "${data.size}部", color = MaterialTheme.colorScheme.primary)
            })
        }
        item {
            BoxWithConstraints {
                val cols = when {
                    maxWidth >= 1200.dp -> 6
                    maxWidth >= 800.dp -> 5
                    maxWidth >= 560.dp -> 4
                    else -> 3
                }
                VodGrid(
                    items = data,
                    columns = cols,
                    onClick = onVodClick,
                    contentPadding = PaddingValues(0.dp)
                )
            }
        }
        item {
            Column {
                SectionHeader(title = "最近观看")
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                MockData.historySamples.forEachIndexed { i, vod ->
                    RecentWatchItem(vod = vod, rank = i + 1, onClick = { onVodClick(vod) })
                    if (i != MockData.historySamples.lastIndex)
                        Spacer(modifier = Modifier.height(tokens.spacing.sm))
                }
            }
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
