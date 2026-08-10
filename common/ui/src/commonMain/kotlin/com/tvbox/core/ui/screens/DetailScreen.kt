package com.tvbox.core.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.core.model.VodEpisode
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.components.Badge
import com.tvbox.core.ui.components.EmptyView
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
import kotlinx.coroutines.launch
import kotlin.math.min

// ================= 影视详情页 =================

@Composable
fun DetailScreen(
    vodInfo: VodInfo,
    onBack: () -> Unit,
    onPlayClick: (VodEpisode) -> Unit,
    onVodClick: (VodInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    var isFavorited by remember { mutableStateOf(false) }
    var playSourceIndex by remember { mutableIntStateOf(0) }
    var playLineIndex by remember { mutableIntStateOf(0) }
    val playLines = listOf("播放线路1", "播放线路2", "备用线路")
    val episodes = vodInfo.episodes.ifEmpty {
        MockData.sampleVodDetail().episodes
    }
    val groupSize = 20
    val episodeGroups = episodes.chunked(groupSize).withIndex().toList()
    var groupIndex by remember { mutableIntStateOf(0) }
    var selectedEpId by remember { mutableStateOf<String?>(null) }
    var showAllRelated by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = tokens.spacing.xxl
            )
        ) {
            // 顶部返回条（因为二级详情页没有 TopBar）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(TVBoxIcons.Outlined.ArrowBack, contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "已复制分享链接：${vodInfo.vodName}",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }) {
                        Icon(TVBoxIcons.Outlined.Share, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

        // Hero 海报 + 信息头
        item {
            DetailHero(
                vod = vodInfo,
                isFavorited = isFavorited,
                onToggleFavorite = { isFavorited = !isFavorited },
                onPlayNow = { onPlayClick(episodes.firstOrNull() ?: return@DetailHero Unit) }
            )
        }

        // 播放线路切换
        if (episodes.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = tokens.spacing.lg)) {
                    SectionHeader(title = "播放线路", action = {
                        if (playLines.size > 1) {
                            SegmentedTabs(
                                tabs = playLines,
                                selectedIndex = playLineIndex,
                                onSelect = { playLineIndex = it },
                                modifier = Modifier.width(400.dp)
                            )
                        }
                    })
                }
            }
            // 选集：分组 Tab + 网格
            item {
                Column(modifier = Modifier.padding(horizontal = tokens.spacing.lg)) {
                    // 分组
                    if (episodeGroups.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                        ) {
                            episodeGroups.forEachIndexed { i, (_, list) ->
                                val label = buildString {
                                    val first = list.first().name
                                    val last = list.last().name
                                    append(first)
                                    if (first != last) append("-").append(last)
                                }
                                val sel = i == groupIndex
                                FilterChip(
                                    selected = sel,
                                    onClick = { groupIndex = i },
                                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                                    shape = MaterialTheme.shapes.large
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(tokens.spacing.md))
                    }
                    // 选集网格
                    val currentList = episodeGroups.getOrNull(groupIndex)?.value ?: episodes
                    EpisodeGrid(
                        items = currentList,
                        selectedId = selectedEpId,
                        onClick = { ep ->
                            selectedEpId = ep.episodeId
                            onPlayClick(ep)
                        }
                    )
                }
            }
        }

        // 剧情简介
        item {
            Column(modifier = Modifier.padding(horizontal = tokens.spacing.lg)) {
                SectionHeader(title = "剧情简介")
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                var expanded by remember { mutableStateOf(false) }
                Text(
                    text = vodInfo.vodContent.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (vodInfo.vodContent.length > 80) {
                    Spacer(modifier = Modifier.height(tokens.spacing.xs))
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = if (expanded) "收起 ↑" else "展开 ↓",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 演职人员
        if (vodInfo.vodActor.isNotEmpty() || vodInfo.vodDirector.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = tokens.spacing.lg)) {
                    SectionHeader(title = "演职人员")
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    SettingGroup(title = "主创团队") {
                        if (vodInfo.vodDirector.isNotEmpty())
                            com.tvbox.core.ui.components.SettingItem(
                                title = "导演",
                                trailing = vodInfo.vodDirector,
                                icon = TVBoxIcons.Outlined.Edit
                            )
                        if (vodInfo.vodActor.isNotEmpty())
                            com.tvbox.core.ui.components.SettingItem(
                                title = "主演",
                                trailing = vodInfo.vodActor.take(20),
                                icon = TVBoxIcons.Outlined.List
                            )
                    }
                }
            }
        }

        // 相关推荐
        item {
            Column(modifier = Modifier.padding(horizontal = tokens.spacing.lg)) {
                SectionHeader(
                    title = "相关推荐",
                    actionText = if (showAllRelated) "收起" else "更多",
                    onAction = { showAllRelated = !showAllRelated }
                )
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                val relatedItems = if (showAllRelated) {
                    MockData.trending + MockData.movieList.take(8)
                } else {
                    MockData.trending.take(6)
                }
                BoxWithConstraints {
                    val cols = when {
                        maxWidth >= 1200.dp -> 6
                        maxWidth >= 800.dp -> 5
                        maxWidth >= 560.dp -> 4
                        else -> 3
                    }
                    VodGrid(
                        items = relatedItems,
                        columns = cols,
                        onClick = onVodClick,
                        contentPadding = PaddingValues(0.dp),
                        scrollEnabled = false
                    )
                }
            }
        }
        }

        // Snackbar 显示分享提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ================= Hero 头部 =================

@Composable
private fun DetailHero(
    vod: VodInfo,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayNow: () -> Unit
) {
    val tokens = tvTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.35f)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // 底部遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.xl),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(modifier = Modifier.width(180.dp)) {
                PosterPlaceholder(
                    title = vod.vodName,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.large
                        )
                )
            }
            Spacer(modifier = Modifier.width(tokens.spacing.xl))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreBadge(score = vod.vodScore)
                    if (vod.vodRemarks.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        Badge(text = vod.vodRemarks, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (vod.vodLang.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        Badge(text = vod.vodLang, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                Text(
                    text = vod.vodName,
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                val meta = buildString {
                    if (vod.vodYear.isNotEmpty()) append(vod.vodYear)
                    if (vod.vodArea.isNotEmpty()) append(" · ").append(vod.vodArea)
                    if (vod.vodClass.isNotEmpty()) append(" · ").append(vod.vodClass)
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(tokens.spacing.xl))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPlayNow,
                        contentPadding = PaddingValues(
                            horizontal = tokens.spacing.xl,
                            vertical = tokens.spacing.md
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(TVBoxIcons.Filled.PlayArrow, null)
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        Text("立即播放", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.width(tokens.spacing.md))
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        contentPadding = PaddingValues(
                            horizontal = tokens.spacing.xl,
                            vertical = tokens.spacing.md
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            if (isFavorited) TVBoxIcons.Filled.Favorite
                            else TVBoxIcons.Outlined.FavoriteBorder,
                            null,
                            tint = if (isFavorited) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        Text(
                            if (isFavorited) "已收藏" else "收藏",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

// ================= 选集网格 =================

@Composable
private fun EpisodeGrid(
    items: List<VodEpisode>,
    selectedId: String?,
    onClick: (VodEpisode) -> Unit
) {
    val tokens = tvTokens()
    BoxWithConstraints {
        val cols = when {
            maxWidth >= 1200.dp -> 10
            maxWidth >= 800.dp -> 8
            maxWidth >= 560.dp -> 6
            else -> 4
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            userScrollEnabled = false
        ) {
            items(items, key = { it.episodeId }) { ep ->
                val isSelected = ep.episodeId == selectedId
                val isWatched = ep.episodeId.toIntOrNull() != null &&
                        (ep.episodeId.substringAfter("ep").toIntOrNull() ?: 0) in 1..12
                EpisodeCell(
                    name = ep.name,
                    selected = isSelected,
                    watched = isWatched,
                    onClick = { onClick(ep) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeCell(
    name: String,
    selected: Boolean,
    watched: Boolean,
    onClick: () -> Unit
) {
    val tokens = tvTokens()
    val (bg, fg) = when {
        selected ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        watched ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .height(tokens.size.componentHeightLg)
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = tokens.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ================= 播放器页（精简版：全屏视频容器 + 控制条） =================

@Composable
fun PlayerScreen(
    vodInfo: VodInfo,
    episode: VodEpisode,
    onBack: () -> Unit,
    onEpisodeChange: (VodEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    var isPlaying by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(1800_000) } // 已播放毫秒（示例30分钟）
    val duration = 60 * 60 * 1000 // 1小时示例
    var volume by remember { mutableIntStateOf(70) }
    var showControls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf("none") } // "none" / "episodes" / "tracks"

    val progressF = (position.toFloat() / duration).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (showControls) Modifier.clickable { showControls = false } else Modifier.clickable { showControls = true })
    ) {
        // 视频区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f)
                .background(Brush.radialGradient(
                    listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0A0A0F),
                        Color.Black
                    )
                ))
        ) {
            // 中心图标（播放器占位）
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = TVBoxIcons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                Text(
                    text = vodInfo.vodName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Text(
                    text = "正在播放：${episode.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // 顶部控制条（仅在 controls 显示时）
            if (showControls) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(tokens.spacing.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                TVBoxIcons.Outlined.ArrowBack, null,
                                tint = Color.White,
                                modifier = Modifier.size(tokens.size.iconLg)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                vodInfo.vodName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                episode.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { isFullscreen = !isFullscreen }) {
                            Icon(
                                if (isFullscreen) TVBoxIcons.Outlined.FullscreenExit
                                else TVBoxIcons.Outlined.Fullscreen,
                                null, tint = Color.White
                            )
                        }
                    }
                }
            }

            // 底部控制条
            if (showControls) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(tokens.spacing.lg)
                ) {
                    Column {
                        // 进度条
                        PlayerProgressBar(progress = progressF)
                        Spacer(modifier = Modifier.height(tokens.spacing.sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 上一集/播放/下一集
                            IconButton(onClick = { /* 上一集 */ }) {
                                Icon(TVBoxIcons.Outlined.SkipPrevious, null, tint = Color.White)
                            }
                            IconButton(onClick = { isPlaying = !isPlaying }, modifier = Modifier.size(64.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isPlaying) TVBoxIcons.Outlined.Pause
                                        else TVBoxIcons.Filled.PlayArrow,
                                        null, tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { /* 下一集 */ }) {
                                Icon(TVBoxIcons.Outlined.SkipNext, null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(tokens.spacing.md))
                            // 时间
                            Text(
                                formatMs(position),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(" / ", style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.6f))
                            Text(
                                formatMs(duration),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 音量
                            Icon(TVBoxIcons.Outlined.VolumeUp, null,
                                tint = Color.White,
                                modifier = Modifier.size(tokens.size.iconMd))
                            Spacer(modifier = Modifier.width(tokens.spacing.xs))
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(volume / 100f)
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Spacer(modifier = Modifier.width(tokens.spacing.md))
                            TextButton(onClick = { panel = if (panel == "episodes") "none" else "episodes" }) {
                                Icon(TVBoxIcons.Outlined.List, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(tokens.size.iconSm))
                                Spacer(modifier = Modifier.width(tokens.spacing.xs))
                                Text("选集", style = MaterialTheme.typography.labelLarge,
                                    color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(tokens.spacing.sm))
                            TextButton(onClick = { panel = if (panel == "tracks") "none" else "tracks" }) {
                                Icon(TVBoxIcons.Outlined.ClosedCaption, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(tokens.size.iconSm))
                                Spacer(modifier = Modifier.width(tokens.spacing.xs))
                                Text("音轨/字幕", style = MaterialTheme.typography.labelLarge,
                                    color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 选集面板
        if (panel == "episodes") {
            val episodes = vodInfo.episodes.ifEmpty { MockData.sampleVodDetail().episodes }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = tokens.elevation.sm,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Column(modifier = Modifier.padding(tokens.spacing.lg)) {
                    SectionHeader(title = "选集（共${episodes.size}集）")
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    EpisodeGrid(
                        items = episodes.take(40),
                        selectedId = episode.episodeId,
                        onClick = onEpisodeChange
                    )
                }
            }
        }

        // 音轨字幕面板
        if (panel == "tracks") {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = tokens.elevation.sm,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Column(modifier = Modifier.padding(tokens.spacing.lg)) {
                    SectionHeader(title = "音轨")
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    val audioTracks = listOf("国语 (默认)", "粤语", "英语")
                    audioTracks.forEachIndexed { i, name ->
                        TrackRow(name = name, selected = i == 0)
                        if (i != audioTracks.lastIndex) Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.xl))
                    SectionHeader(title = "字幕")
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    val subs = listOf("简体中文 (内嵌)", "繁体中文", "英文", "不显示字幕")
                    subs.forEachIndexed { i, name ->
                        TrackRow(name = name, selected = i == 0)
                        if (i != subs.lastIndex) Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerProgressBar(progress: Float) {
    val tokens = tvTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .offset(x = (-1).dp, y = (-3).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun pad2(n: Int) = if (n < 10) "0$n" else n.toString()
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "${pad2(m)}:${pad2(s)}"
}

@Composable
private fun TrackRow(name: String, selected: Boolean) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) TVBoxIcons.Filled.Check else TVBoxIcons.Outlined.Category,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(tokens.spacing.md))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurface
        )
    }
}
