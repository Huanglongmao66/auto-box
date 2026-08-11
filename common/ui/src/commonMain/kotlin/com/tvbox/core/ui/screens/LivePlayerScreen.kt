package com.tvbox.core.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.LiveCategory
import com.tvbox.core.model.LiveChannel
import com.tvbox.core.ui.components.PlatformBackHandler
import com.tvbox.core.ui.components.PlatformVideoSurface
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens
import com.tvbox.deviceapi.LogLevel
import com.tvbox.deviceapi.ScreenOrientation
import com.tvbox.deviceapi.player.IPlayer
import com.tvbox.deviceapi.player.PlayerListener
import kotlinx.coroutines.delay

/** 缓冲超时阈值（毫秒）：持续在此时间内无状态变化即判定直播源失败 */
private const val BUFFERING_TIMEOUT_MS = 20_000L

/**
 * 直播播放器
 *
 * 真实接入 ExoPlayer（通过 [IPlayer]）渲染直播流，支持：
 * - 频道切换（上一台 / 下一台，当前分组内循环）
 * - 多线路切换（同一频道的多条地址依次回退）
 * - 横竖屏自动切换（根据视频尺寸）+ 手动锁定方向
 * - 控制层自动显隐
 * - 横屏全屏沉浸 + 竖屏视频+列表双栏
 *
 * @param categories 全部分组（用于跨分组切台）
 * @param initialCategoryIndex 初始分组序号
 * @param initialChannelIndex 初始频道序号
 * @param onBack 返回回调
 */
@Composable
fun LivePlayerScreen(
    categories: List<LiveCategory>,
    initialCategoryIndex: Int,
    initialChannelIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val deviceApi = remember { ServiceLocator.getDeviceApi() }

    var categoryIndex by remember { mutableIntStateOf(initialCategoryIndex.coerceIn(0, categories.lastIndex)) }
    var channelIndex by remember { mutableIntStateOf(initialChannelIndex) }
    var lineIndex by remember { mutableIntStateOf(0) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var orientationLocked by remember { mutableStateOf(false) }
    var showChannelPanel by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    val safeCatIndex = categoryIndex.coerceIn(0, categories.lastIndex)
    val currentCategory = categories[safeCatIndex]
    val safeChIndex = channelIndex.coerceIn(0, currentCategory.channels.lastIndex)
    val currentChannel = currentCategory.channels[safeChIndex]
    val safeLineIndex = lineIndex.coerceIn(0, currentChannel.urls.lastIndex)
    val currentUrl = currentChannel.urls.getOrElse(safeLineIndex) { "" }

    // 创建播放器（仅一次）
    val player: IPlayer = remember {
        deviceApi.createPlayer().also { p ->
            p.addListener(object : PlayerListener {
                override fun onPlay() {
                    isPlaying = true; isBuffering = false; errorMessage = null
                    deviceApi.log(LogLevel.DEBUG, "LivePlayer", "onPlay url=$currentUrl")
                }
                override fun onPause() { isPlaying = false }
                override fun onBufferingStateChanged(buffering: Boolean) {
                    isBuffering = buffering
                    deviceApi.log(LogLevel.DEBUG, "LivePlayer", "buffering=$buffering")
                }
                override fun onError(errorCode: Int, message: String) {
                    errorMessage = "播放错误($errorCode)：$message"
                    isBuffering = false
                    isPlaying = false
                    deviceApi.log(LogLevel.ERROR, "LivePlayer", "onError code=$errorCode msg=$message url=$currentUrl")
                }
                override fun onVideoSizeChanged(width: Int, height: Int) {
                    videoWidth = width
                    videoHeight = height
                    deviceApi.log(LogLevel.INFO, "LivePlayer", "videoSize=${width}x${height}")
                    // 根据视频尺寸自动切换横竖屏（未锁定时）
                    if (!orientationLocked && width > 0 && height > 0) {
                        if (width >= height) {
                            deviceApi.setScreenOrientation(ScreenOrientation.LANDSCAPE)
                        } else {
                            deviceApi.setScreenOrientation(ScreenOrientation.PORTRAIT)
                        }
                    }
                }
                override fun onReady() {
                    isBuffering = false
                    deviceApi.log(LogLevel.INFO, "LivePlayer", "onReady")
                }
            })
        }
    }

    // 拦截系统返回键：退出播放器而非退出应用
    PlatformBackHandler { onBack() }

    // 频道 / 线路变化时重新加载源
    LaunchedEffect(currentUrl, currentChannel.name) {
        if (currentUrl.isBlank()) {
            errorMessage = "该频道无可用播放地址"
            return@LaunchedEffect
        }
        deviceApi.log(LogLevel.INFO, "LivePlayer", "setDataSource: $currentUrl")
        isBuffering = true
        errorMessage = null
        player.setDataSource(currentUrl, currentChannel.header)
        player.play()
    }

    // 控制层自动隐藏（5 秒后）
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // 缓冲超时看门狗：持续 buffering 超过 20s 视为失败，自动切换线路或报错
    LaunchedEffect(isBuffering, currentUrl, safeLineIndex) {
        if (!isBuffering) return@LaunchedEffect
        delay(BUFFERING_TIMEOUT_MS)
        if (isBuffering && errorMessage == null) {
            val totalLines = currentChannel.urls.size
            deviceApi.log(
                LogLevel.WARN, "LivePlayer",
                "缓冲超时(${BUFFERING_TIMEOUT_MS}ms) url=$currentUrl line=${safeLineIndex + 1}/$totalLines"
            )
            if (totalLines > 1 && safeLineIndex < totalLines - 1) {
                // 有备用线路，自动切换下一条
                lineIndex = safeLineIndex + 1
                errorMessage = "线路 ${safeLineIndex + 1} 缓冲超时，已切换至线路 ${safeLineIndex + 2}"
            } else {
                errorMessage = "缓冲超时：直播源长时间未响应，请检查网络或切换线路"
                isBuffering = false
                isPlaying = false
            }
        }
    }

    // 播放器错误：若有多线路则自动切换
    LaunchedEffect(errorMessage, safeLineIndex) {
        val err = errorMessage ?: return@LaunchedEffect
        val totalLines = currentChannel.urls.size
        // 仅当非"手动切换线路提示"类错误、且还有下一条线路时自动切换
        val canAutoSwitch = totalLines > 1 && safeLineIndex < totalLines - 1 &&
            !err.contains("已切换至线路") && !err.contains("缓冲超时")
        if (canAutoSwitch) {
            deviceApi.log(LogLevel.WARN, "LivePlayer", "播放错误自动切换线路: $err")
            delay(800)
            lineIndex = safeLineIndex + 1
        }
    }

    // 退出时释放播放器并恢复屏幕方向
    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.release()
            deviceApi.setOrientationLocked(false)
            deviceApi.setScreenOrientation(ScreenOrientation.FULL_SENSOR)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                showControls = !showControls
                showChannelPanel = false
            }
    ) {
        val panelWidthDp = if (maxWidth >= 600.dp) 360.dp else 280.dp

        LiveLandscapeContent(
            player = player,
            isBuffering = isBuffering,
            isPlaying = isPlaying,
            errorMessage = errorMessage,
            showControls = showControls,
            showChannelPanel = showChannelPanel,
            orientationLocked = orientationLocked,
            currentCategory = currentCategory,
            currentChannel = currentChannel,
            safeLineIndex = safeLineIndex,
            panelWidthDp = panelWidthDp,
            onBack = onBack,
            onPlayPause = { if (isPlaying) player.pause() else player.play() },
            onPrevChannel = { switchChannel(-1, categories, safeCatIndex, safeChIndex) { cat, ch ->
                categoryIndex = cat; channelIndex = ch; lineIndex = 0
            } },
            onNextChannel = { switchChannel(1, categories, safeCatIndex, safeChIndex) { cat, ch ->
                categoryIndex = cat; channelIndex = ch; lineIndex = 0
            } },
            onSwitchLine = { lineIndex = (safeLineIndex + 1) % currentChannel.urls.size },
            onToggleLock = {
                orientationLocked = !orientationLocked
                deviceApi.setOrientationLocked(orientationLocked)
            },
            onToggleChannelPanel = { showChannelPanel = !showChannelPanel },
            onChannelSelect = { idx ->
                channelIndex = idx
                lineIndex = 0
                showChannelPanel = false
            }
        )
    }
}

// ======================== 横屏布局 ========================

@Composable
private fun LiveLandscapeContent(
    player: IPlayer,
    isBuffering: Boolean,
    isPlaying: Boolean,
    errorMessage: String?,
    showControls: Boolean,
    showChannelPanel: Boolean,
    orientationLocked: Boolean,
    currentCategory: LiveCategory,
    currentChannel: LiveChannel,
    safeLineIndex: Int,
    panelWidthDp: Dp = 320.dp,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onSwitchLine: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleChannelPanel: () -> Unit,
    onChannelSelect: (Int) -> Unit
) {
    val tokens = tvTokens()

    Box(modifier = Modifier.fillMaxSize()) {
        // 全屏视频
        PlatformVideoSurface(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        // 缓冲指示
        if (isBuffering && errorMessage == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                Text("正在缓冲…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 错误提示
        if (errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(tokens.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    TVBoxIcons.Outlined.Error, null,
                    tint = Color(0xFFFFB74D), modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                Text(
                    errorMessage, color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                if (currentChannel.urls.size > 1) {
                    Spacer(modifier = Modifier.height(tokens.spacing.xs))
                    Text("可尝试切换线路", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 顶部控制条
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onBack() }) {
                        Icon(TVBoxIcons.Outlined.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(tokens.size.iconLg))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentChannel.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append(currentCategory.name)
                                if (currentChannel.urls.size > 1) append("  ·  线路 ${safeLineIndex + 1}/${currentChannel.urls.size}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onToggleChannelPanel) {
                        Icon(TVBoxIcons.Outlined.List, "频道列表", tint = Color.White)
                    }
                    IconButton(onClick = onToggleLock) {
                        Icon(
                            if (orientationLocked) TVBoxIcons.Outlined.Visibility else TVBoxIcons.Outlined.Fullscreen,
                            if (orientationLocked) "解锁方向" else "锁定方向",
                            tint = if (orientationLocked) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }
            }
        }

        // 底部控制条
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevChannel) {
                        Icon(TVBoxIcons.Outlined.SkipPrevious, "上一台", tint = Color.White)
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) TVBoxIcons.Outlined.Pause else TVBoxIcons.Filled.PlayArrow,
                                if (isPlaying) "暂停" else "播放",
                                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNextChannel) {
                        Icon(TVBoxIcons.Outlined.SkipNext, "下一台", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(tokens.spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentChannel.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("直播", style = MaterialTheme.typography.labelSmall, color = if (isPlaying) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f))
                    }
                    if (currentChannel.urls.size > 1) {
                        IconButton(onClick = onSwitchLine) {
                            Icon(TVBoxIcons.Outlined.Tune, "切换线路", tint = Color.White)
                        }
                    }
                }
            }
        }

        // 侧滑频道面板（叠加在视频右侧）
        if (showChannelPanel) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidthDp)
                    .background(Color.Black.copy(alpha = 0.92f))
            ) {
                // fillMaxSize 由外部 Box 提供确定尺寸（320dp × 父高度），安全传给 LazyColumn
                LiveChannelQuickList(
                    category = currentCategory,
                    selectedChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

// ======================== 竖屏布局 ========================

@Composable
private fun LivePortraitContent(
    player: IPlayer,
    isBuffering: Boolean,
    isPlaying: Boolean,
    errorMessage: String?,
    showControls: Boolean,
    orientationLocked: Boolean,
    currentCategory: LiveCategory,
    currentChannel: LiveChannel,
    safeLineIndex: Int,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onSwitchLine: () -> Unit,
    onToggleLock: () -> Unit,
    onChannelSelect: (Int) -> Unit
) {
    val tokens = tvTokens()
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部视频区（16:9）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f)
                .background(Color.Black)
        ) {
            PlatformVideoSurface(player = player, modifier = Modifier.fillMaxSize())

            // 缓冲指示
            if (isBuffering && errorMessage == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    Text("正在缓冲…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 错误提示
            if (errorMessage != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(tokens.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(TVBoxIcons.Outlined.Error, null, tint = Color(0xFFFFB74D), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    Text(errorMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (currentChannel.urls.size > 1) {
                        Spacer(modifier = Modifier.height(tokens.spacing.xs))
                        Text("可尝试切换线路", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 顶部控制条
            if (showControls) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(TVBoxIcons.Outlined.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(tokens.size.iconLg))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentChannel.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append(currentCategory.name)
                                    if (currentChannel.urls.size > 1) append("  ·  线路 ${safeLineIndex + 1}/${currentChannel.urls.size}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = onToggleLock) {
                            Icon(
                                if (orientationLocked) TVBoxIcons.Outlined.Visibility else TVBoxIcons.Outlined.Fullscreen,
                                if (orientationLocked) "解锁方向" else "锁定方向",
                                tint = if (orientationLocked) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }
            }

            // 底部控制条
            if (showControls) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrevChannel) {
                            Icon(TVBoxIcons.Outlined.SkipPrevious, "上一台", tint = Color.White)
                        }
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPlaying) TVBoxIcons.Outlined.Pause else TVBoxIcons.Filled.PlayArrow,
                                    if (isPlaying) "暂停" else "播放",
                                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNextChannel) {
                            Icon(TVBoxIcons.Outlined.SkipNext, "下一台", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(tokens.spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentChannel.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("直播", style = MaterialTheme.typography.labelSmall, color = if (isPlaying) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.6f))
                        }
                        if (currentChannel.urls.size > 1) {
                            IconButton(onClick = onSwitchLine) {
                                Icon(TVBoxIcons.Outlined.Tune, "切换线路", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 下方频道列表：weight 提供确定的高度约束，避免 LazyColumn 无限高度崩溃
        LiveChannelQuickList(
            category = currentCategory,
            selectedChannel = currentChannel,
            onChannelSelect = onChannelSelect,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

/**
 * 在全部分组中按方向切台（跨分组循环）。
 */
private fun switchChannel(
    direction: Int,
    categories: List<LiveCategory>,
    catIndex: Int,
    chIndex: Int,
    onUpdate: (categoryIndex: Int, channelIndex: Int) -> Unit
) {
    if (categories.isEmpty()) return
    val curCat = categories[catIndex.coerceIn(0, categories.lastIndex)]
    if (curCat.channels.isEmpty()) return
    var newCh = chIndex + direction
    var newCat = catIndex
    if (newCh < 0 || newCh >= curCat.channels.size) {
        newCat = if (newCh < 0) {
            (catIndex - 1 + categories.size) % categories.size
        } else {
            (catIndex + 1) % categories.size
        }
        val targetCat = categories[newCat]
        if (targetCat.channels.isEmpty()) {
            onUpdate(newCat, 0)
            return
        }
        newCh = if (newCh < 0) targetCat.channels.lastIndex else 0
    }
    onUpdate(newCat, newCh)
}

/**
 * 快速切台列表（当前分组频道列表）。
 *
 * 调用方必须保证传入的 [modifier] 提供确定的高度约束（例如
 * `Modifier.weight(1f)`、`Modifier.heightIn(max=...)` 或置于确定高度的容器内），
 * 否则 LazyColumn 会因无限高度约束抛出异常。
 */
@Composable
private fun LiveChannelQuickList(
    category: LiveCategory,
    selectedChannel: LiveChannel,
    onChannelSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    // 使用传入的约束，不再追加 fillMaxSize() —— 调用方已提供确定高度
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = tokens.spacing.md,
            end = tokens.spacing.md,
            top = tokens.spacing.sm,
            bottom = tokens.spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
    ) {
        items(category.channels.size) { idx ->
            val ch = category.channels[idx]
            val selected = ch.name == selectedChannel.name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onChannelSelect(idx) }
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selected) TVBoxIcons.Filled.PlayArrow else TVBoxIcons.Outlined.LiveTv,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(tokens.spacing.sm))
                Text(
                    text = ch.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ch.urls.size > 1) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${ch.urls.size}线路",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
