package com.tvbox.core.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.LiveCategory
import com.tvbox.core.model.LiveChannel
import com.tvbox.core.source.LiveSourceManager
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 直播主页
 *
 * 左侧分组栏 + 右侧频道列表（大屏）/ 顶部分组 Tab + 下方频道列表（小屏）。
 * 点击频道触发 [onChannelClick] 进入直播播放器。
 *
 * @param onChannelClick 频道点击回调，参数为 (分组, 频道)
 * @param onCategoriesLoaded 分组加载完成回调，供上层缓存以便进入播放器时复用
 * @param modifier 修饰符
 */
@Composable
fun LiveScreen(
    onChannelClick: (LiveCategory, LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
    onCategoriesLoaded: (List<LiveCategory>) -> Unit = {}
) {
    val tokens = tvTokens()
    var categories by remember { mutableStateOf<List<LiveCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var fromRemote by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    val sourceManager = ServiceLocator.getLiveSourceManager()

    LaunchedEffect(retryKey) {
        loading = true
        loadError = null

        val configManager = runCatching { ServiceLocator.getConfigManager() }.getOrNull()
        val config = configManager?.runCatching { getConfig() }?.getOrNull()
        if (config != null) {
            val liveSources = config.liveSources
            val currentLiveKey = config.currentLiveKey
            val selectedSource = when {
                currentLiveKey.isNotBlank() -> liveSources.find { it.key == currentLiveKey }
                else -> liveSources.find { it.enabled }
            }
            if (selectedSource != null && selectedSource.url.isNotBlank()) {
                sourceManager.setSubscriptionUrl(selectedSource.url)
            }
        }

        val result = runCatching {
            withContext(Dispatchers.Default) { sourceManager.load() }
        }
        result.onSuccess {
            categories = it
            selectedCategoryIndex = 0
            fromRemote = sourceManager.loadedFromRemote
            remoteError = sourceManager.lastRemoteError
            onCategoriesLoaded(it)
        }.onFailure {
            loadError = it.message ?: "直播源加载失败"
        }
        loading = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(tokens.spacing.md))
                    Text(
                        text = "正在加载直播源…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.xs))
                    Text(
                        text = sourceManager.subscriptionUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            loadError != null -> {
                EmptyView(
                    title = "直播源加载失败",
                    description = loadError!!,
                    icon = TVBoxIcons.Outlined.Error,
                    actionText = "重试",
                    onAction = { retryKey++ }
                )
            }
            categories.isEmpty() -> {
                EmptyView(
                    title = "暂无直播频道",
                    description = "请在设置中添加直播源订阅",
                    icon = TVBoxIcons.Outlined.LiveTv
                )
            }
            else -> {
                val safeIndex = selectedCategoryIndex.coerceIn(0, categories.lastIndex)
                val currentCategory = categories[safeIndex]
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLarge = maxWidth >= 720.dp
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 来源提示条
                        SourceStatusBanner(
                            fromRemote = fromRemote,
                            remoteError = remoteError,
                            categoryCount = categories.size,
                            channelCount = categories.sumOf { it.channels.size },
                            onRetry = { retryKey++ }
                        )
                        if (isLarge) {
                            LiveLargeLayout(
                                categories = categories,
                                selectedIndex = safeIndex,
                                currentCategory = currentCategory,
                                onCategorySelect = { selectedCategoryIndex = it },
                                onChannelClick = { ch -> onChannelClick(currentCategory, ch) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            LiveSmallLayout(
                                categories = categories,
                                selectedIndex = safeIndex,
                                currentCategory = currentCategory,
                                onCategorySelect = { selectedCategoryIndex = it },
                                onChannelClick = { ch -> onChannelClick(currentCategory, ch) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 来源状态提示条：显示订阅来源、频道数、远端失败回退提示等。
 */
@Composable
private fun SourceStatusBanner(
    fromRemote: Boolean,
    remoteError: String?,
    categoryCount: Int,
    channelCount: Int,
    onRetry: () -> Unit
) {
    val tokens = tvTokens()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (fromRemote) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        tonalElevation = tokens.elevation.sm
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (fromRemote) TVBoxIcons.Filled.LiveTv else TVBoxIcons.Outlined.Error,
                contentDescription = null,
                tint = if (fromRemote) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(tokens.size.iconSm)
            )
            Spacer(modifier = Modifier.width(tokens.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (fromRemote) "订阅源已加载"
                           else "远端加载失败，已回退内置源",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (fromRemote) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$categoryCount 个分组 · $channelCount 个频道" +
                           (remoteError?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fromRemote) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!fromRemote) {
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onRetry() }
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs)
                )
            }
        }
    }
}

/**
 * 大屏布局：左侧分组栏 + 右侧频道列表
 */
@Composable
private fun LiveLargeLayout(
    categories: List<LiveCategory>,
    selectedIndex: Int,
    currentCategory: LiveCategory,
    onCategorySelect: (Int) -> Unit,
    onChannelClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Row(modifier = modifier.fillMaxSize()) {
        // 左侧分组栏
        Surface(
            modifier = Modifier.width(220.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = tokens.elevation.sm
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = tokens.spacing.sm)
            ) {
                items(categories.size) { index ->
                    val category = categories[index]
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelect(index) }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) TVBoxIcons.Filled.LiveTv
                            else TVBoxIcons.Outlined.Category,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(tokens.size.iconMd)
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        Column {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${category.channels.size} 个频道",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // 右侧频道列表
        ChannelList(
            category = currentCategory,
            onChannelClick = onChannelClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 小屏布局：顶部分组 Tab + 下方频道列表
 */
@Composable
private fun LiveSmallLayout(
    categories: List<LiveCategory>,
    selectedIndex: Int,
    currentCategory: LiveCategory,
    onCategorySelect: (Int) -> Unit,
    onChannelClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部分组横向滚动 Tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            categories.forEachIndexed { index, category ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onCategorySelect(index) },
                    label = {
                        Text(
                            text = "${category.name}(${category.channels.size})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    shape = MaterialTheme.shapes.large
                )
            }
        }
        // 频道列表
        ChannelList(
            category = currentCategory,
            onChannelClick = onChannelClick,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 频道列表（左右两栏复用）
 */
@Composable
private fun ChannelList(
    category: LiveCategory,
    onChannelClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    if (category.channels.isEmpty()) {
        EmptyView(
            title = "该分组下暂无频道",
            description = "请切换其他分组或更新直播源",
            icon = TVBoxIcons.Outlined.LiveTv
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = tokens.spacing.md,
            end = tokens.spacing.md,
            top = tokens.spacing.sm,
            bottom = tokens.spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        items(category.channels, key = { it.name + it.urls.firstOrNull().orEmpty() }) { channel ->
            ChannelRow(
                channel = channel,
                onClick = { onChannelClick(channel) }
            )
        }
    }
}

/**
 * 频道行：图标 + 名称 + 线路数 + 播放按钮
 */
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 频道图标占位
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = TVBoxIcons.Filled.LiveTv,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(tokens.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (channel.urls.size > 1) "${channel.urls.size} 条线路"
                       else "直播源",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 播放按钮
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = TVBoxIcons.Filled.PlayArrow,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
