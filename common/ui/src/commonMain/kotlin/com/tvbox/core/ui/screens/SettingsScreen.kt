package com.tvbox.core.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.core.config.ConfigManager
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.AppConfig
import com.tvbox.core.model.UserVodSource
import com.tvbox.core.model.UserLiveSource
import com.tvbox.core.ui.components.ScoreBadge
import com.tvbox.core.ui.components.SettingGroup
import com.tvbox.core.ui.components.SettingItem
import com.tvbox.core.ui.components.SettingSwitch
import com.tvbox.core.ui.icons.OutlinedIcons
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.launch

// ================= 设置页 =================

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    cacheSize: String = "328 MB",
    appVersion: String = "1.0.0",
    buildTime: String = "2026-08-08"
) {
    val tokens = tvTokens()
    val configManager = remember { ServiceLocator.getConfigManager() }
    var loadedConfig by remember { mutableStateOf<AppConfig?>(null) }
    LaunchedEffect(Unit) {
        loadedConfig = runCatching { configManager.getConfig() }.getOrNull() ?: AppConfig()
    }

    var isDark by remember { mutableStateOf(true) }
    var themeColorSeedIndex by remember { mutableStateOf(0) }
    var cardStyleIndex by remember { mutableStateOf(0) }
    var showScoreBadge by remember { mutableStateOf(true) }
    var skipTitle by remember { mutableStateOf(true) }
    var autoNext by remember { mutableStateOf(true) }
    var hdrEnhance by remember { mutableStateOf(false) }
    var useHwDecoding by remember { mutableStateOf(true) }
    var keepRatio by remember { mutableStateOf(true) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loadedConfig) {
        val cfg = loadedConfig ?: return@LaunchedEffect
        isDark = cfg.isDarkMode
        themeColorSeedIndex = cfg.themeColorSeed
        cardStyleIndex = cfg.cardStyle
        showScoreBadge = cfg.showScoreBadge
        skipTitle = cfg.skipTitle
        autoNext = cfg.autoPlayNext
        hdrEnhance = cfg.hdrEnhance
        useHwDecoding = cfg.hardwareDecode
        keepRatio = cfg.keepRatio
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showCardStyleDialog by remember { mutableStateOf(false) }

    // ===== 子路由：settings-root（设置主页） / sources-screen（源管理覆盖） =====
    var settingsRoute by remember { mutableStateOf("settings-root") }

    // ===== Group A: API 管理相关状态 =====
    var showApiDialog by remember { mutableStateOf(false) }
    val apiList = remember { mutableStateListOf<String>() }
    var currentApiIndex by remember { mutableStateOf(0) }
    var newApiUrl by remember { mutableStateOf("") }
    var apiParsing by remember { mutableStateOf(false) }
    // 编辑 API 地址状态（-1 表示未编辑）
    var editingApiIndex by remember { mutableStateOf(-1) }
    var editingApiUrl by remember { mutableStateOf("") }

    // ===== Group B: 点播源管理相关状态 =====
    var showVodDialog by remember { mutableStateOf(false) }
    val vodList = remember { mutableStateListOf<UserVodSource>() }
    var currentVodKey by remember { mutableStateOf("") }
    var newVodName by remember { mutableStateOf("") }
    var newVodPath by remember { mutableStateOf("") }

    // ===== Group C: 直播订阅源相关状态 =====
    var showLiveDialog by remember { mutableStateOf(false) }
    val liveList = remember { mutableStateListOf<UserLiveSource>() }
    var currentLiveKey by remember { mutableStateOf("") }
    var newLiveUrl by remember { mutableStateOf("") }
    var newLiveName by remember { mutableStateOf("") }

    // ===== Group D: 壁纸相关状态 =====
    data class WallpaperPreset(val name: String, val colors: List<Color>)
    val wallpaperPresets = remember {
        listOf(
            WallpaperPreset("默认", listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
            WallpaperPreset("靛紫", listOf(Color(0xFF5B86E5), Color(0xFF36D1DC))),
            WallpaperPreset("青蓝", listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))),
            WallpaperPreset("樱粉", listOf(Color(0xFFFF8DC8), Color(0xFFFDCBF8))),
            WallpaperPreset("日落", listOf(Color(0xFFFF7E5F), Color(0xFFFEC89A))),
            WallpaperPreset("海蓝", listOf(Color(0xFF2E3192), Color(0xFF1BFFFF))),
            WallpaperPreset("薄荷", listOf(Color(0xFF00B09B), Color(0xFF96C93D))),
            WallpaperPreset("暗夜", listOf(Color(0xFF141E30), Color(0xFF243B55)))
        )
    }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var currentWallpaperIndex by remember { mutableStateOf(0) }

    LaunchedEffect(loadedConfig) {
        val cfg = loadedConfig ?: return@LaunchedEffect
        if (cfg.apiUrls.isNotEmpty()) {
            apiList.clear()
            apiList.addAll(cfg.apiUrls)
            currentApiIndex = cfg.currentApiIndex.coerceIn(0, (apiList.size - 1).coerceAtLeast(0))
        }
        // 点播源：保证单选一致性（仅 currentVodKey 对应 enabled=true，其余 false）
        vodList.clear()
        val keyFromCfg = cfg.currentVodKey.ifBlank { cfg.vodSources.firstOrNull { it.enabled }?.key ?: cfg.vodSources.firstOrNull()?.key ?: "" }
        val normalizedVod = cfg.vodSources.map { src ->
            src.copy(enabled = (src.key == keyFromCfg))
        }
        vodList.addAll(normalizedVod)
        currentVodKey = if (normalizedVod.any { it.key == keyFromCfg }) keyFromCfg else normalizedVod.firstOrNull()?.key ?: ""
        // 直播源
        liveList.clear()
        liveList.addAll(cfg.liveSources)
        currentLiveKey = cfg.currentLiveKey.ifBlank {
            liveList.firstOrNull { it.enabled }?.key ?: liveList.firstOrNull()?.key ?: ""
        }
        currentWallpaperIndex = cfg.wallpaperIndex.coerceIn(0, wallpaperPresets.size - 1)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ===== 根路由：显示设置 LazyColumn =====
        if (settingsRoute == "settings-root") {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = tokens.spacing.lg,
                    end = tokens.spacing.lg,
                    top = tokens.spacing.sm,
                    bottom = tokens.spacing.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
            ) {
                // 版本信息卡片
                item {
                    AppVersionCard(version = appVersion, buildTime = buildTime)
                }

                // 外观设置
                item {
                    SettingGroup(title = "外观") {
                        SettingSwitch(
                            title = "深色模式",
                            subtitle = "影视应用推荐使用深色主题以获得更佳观影体验",
                            icon = TVBoxIcons.Outlined.Palette,
                            checked = isDark,
                            onCheckedChange = { v ->
                                isDark = v
                                scope.launch {
                                    configManager.update { it.copy(isDarkMode = v) }
                                    snackbarHostState.showSnackbar(
                                        "已切换" + if (v) "深色模式" else "浅色模式",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "主题配色",
                            subtitle = com.tvbox.core.ui.theme.TVBoxColorScheme.themePresets.getOrNull(themeColorSeedIndex)?.name + "（当前）",
                            icon = TVBoxIcons.Outlined.Palette,
                            trailing = "${com.tvbox.core.ui.theme.TVBoxColorScheme.themePresets.size} 款",
                            onClick = { showThemeDialog = true }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "首页卡片样式",
                            subtitle = when (cardStyleIndex) {
                                0 -> "标准卡片"
                                1 -> "紧凑卡片"
                                else -> "大号卡片"
                            } + "（当前）",
                            icon = TVBoxIcons.Outlined.GridViewSafeInvoke,
                            trailing = "3 种",
                            onClick = { showCardStyleDialog = true }
                        )
                        HorizontalDividerItem()
                        SettingSwitch(
                            title = "显示评分角标",
                            subtitle = "在影视卡片右上角显示评分",
                            icon = TVBoxIcons.Outlined.Star,
                            checked = showScoreBadge,
                            onCheckedChange = { v ->
                                showScoreBadge = v
                                scope.launch {
                                    configManager.update { it.copy(showScoreBadge = v) }
                                    snackbarHostState.showSnackbar(
                                        if (v) "已显示评分角标" else "已隐藏评分角标",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }
                }

                // 播放设置
                item {
                    SettingGroup(title = "播放") {
                        SettingSwitch(
                            title = "自动进入下一集",
                            subtitle = "当前集播放完成后自动切换下一集",
                            icon = TVBoxIcons.Outlined.SkipNext,
                            checked = autoNext,
                            onCheckedChange = { v ->
                                autoNext = v
                                scope.launch {
                                    configManager.update { it.copy(autoPlayNext = v) }
                                    snackbarHostState.showSnackbar(
                                        "已切换自动下一集",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingSwitch(
                            title = "自动跳过片头片尾",
                            subtitle = "智能识别不重复观看相同片段",
                            icon = TVBoxIcons.Outlined.Visibility,
                            checked = skipTitle,
                            onCheckedChange = { v ->
                                skipTitle = v
                                scope.launch {
                                    configManager.update { it.copy(skipTitle = v) }
                                    snackbarHostState.showSnackbar(
                                        "已切换跳过片头片尾",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingSwitch(
                            title = "硬件解码优先",
                            subtitle = "使用GPU解码提升播放流畅度",
                            icon = TVBoxIcons.Outlined.Tune,
                            checked = useHwDecoding,
                            onCheckedChange = { v ->
                                useHwDecoding = v
                                scope.launch {
                                    configManager.update { it.copy(hardwareDecode = v) }
                                    snackbarHostState.showSnackbar(
                                        if (v) "已启用硬件解码" else "已切换为软件解码",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingSwitch(
                            title = "保持视频原始比例",
                            subtitle = "关闭将强制铺满屏幕",
                            icon = TVBoxIcons.Outlined.Fullscreen,
                            checked = keepRatio,
                            onCheckedChange = { v ->
                                keepRatio = v
                                scope.launch {
                                    configManager.update { it.copy(keepRatio = v) }
                                    snackbarHostState.showSnackbar(
                                        if (v) "保持原始比例" else "已设置强制全屏",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingSwitch(
                            title = "HDR 画质增强",
                            subtitle = "需要设备支持 HDR 显示",
                            icon = TVBoxIcons.Outlined.Lightbulb,
                            checked = hdrEnhance,
                            onCheckedChange = { v ->
                                hdrEnhance = v
                                scope.launch {
                                    configManager.update { it.copy(hdrEnhance = v) }
                                    snackbarHostState.showSnackbar(
                                        if (v) "已启用 HDR 增强" else "已关闭 HDR 增强",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "默认播放倍速",
                            subtitle = "1.0x",
                            icon = TVBoxIcons.Outlined.PlayArrow,
                            trailing = "8 档"
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "播放器内核",
                            subtitle = "系统播放器",
                            icon = TVBoxIcons.Outlined.Source,
                            trailing = "3 款"
                        )
                    }
                }

                // 存储与缓存
                item {
                    SettingGroup(title = "存储与缓存") {
                        SettingItem(
                            title = "已用缓存",
                            subtitle = "自动清理 7 天前未观看的缓存",
                            icon = TVBoxIcons.Outlined.Storage,
                            trailing = cacheSize
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "清除缓存",
                            subtitle = "仅清除图片和网络请求缓存，保留播放历史",
                            icon = TVBoxIcons.Outlined.Delete,
                            onClick = { showClearCacheDialog = true }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "下载目录",
                            subtitle = "/storage/emulated/0/TVBox/Download",
                            icon = TVBoxIcons.Outlined.Download
                        )
                        HorizontalDividerItem()
                        SettingSwitch(
                            title = "仅 Wi-Fi 自动更新订阅",
                            subtitle = "避免使用移动网络消耗流量",
                            icon = TVBoxIcons.Outlined.Language,
                            checked = true,
                            onCheckedChange = { /* 网络策略预留 */ }
                        )
                    }
                }

                // ====== 新增 Group A: API 管理 ======
                item {
                    SettingGroup(title = "API 服务") {
                        SettingItem(
                            title = "当前 API",
                            subtitle = apiList.getOrNull(currentApiIndex) ?: "https://api.example.com",
                            icon = TVBoxIcons.Outlined.Language,
                            trailing = "查看/编辑",
                            onClick = { showApiDialog = true }
                        )
                    }
                }

                // ====== 新增 Group B: 点播源管理 ======
                item {
                    SettingGroup(title = "点播源") {
                        val enabledCount = vodList.count { it.enabled }
                        val defaultName = vodList.firstOrNull { it.key == currentVodKey }?.name ?: "未设置"
                        SettingItem(
                            title = "当前点播源",
                            subtitle = "已启用 $enabledCount / 总数 ${vodList.size}，默认 $defaultName",
                            icon = TVBoxIcons.Outlined.Source,
                            trailing = "查看/编辑",
                            onClick = { showVodDialog = true }
                        )
                    }
                }

                // ====== 新增 Group C: 直播订阅源管理 ======
                item {
                    SettingGroup(title = "直播订阅源") {
                        val enabledCount = liveList.count { it.enabled }
                        SettingItem(
                            title = "订阅源列表",
                            subtitle = "已启用 $enabledCount / 共 ${liveList.size}",
                            icon = TVBoxIcons.Outlined.LiveTv,
                            trailing = "查看/编辑",
                            onClick = { showLiveDialog = true }
                        )
                    }
                }

                // ====== 新增 Group D: 壁纸设置 ======
                item {
                    SettingGroup(title = "壁纸") {
                        SettingItem(
                            title = "当前壁纸",
                            subtitle = wallpaperPresets[currentWallpaperIndex].name,
                            icon = TVBoxIcons.Outlined.Palette,
                            trailing = "切换",
                            onClick = { showWallpaperDialog = true }
                        )
                    }
                }

                // 数据管理
                item {
                    SettingGroup(title = "数据管理") {
                        // ====== 新增：源管理（子路由入口） ======
                        SettingItem(
                            title = "源管理",
                            subtitle = "影视源订阅的高级管理",
                            icon = TVBoxIcons.Outlined.Source,
                            onClick = { settingsRoute = "sources-screen" }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "导入/导出配置",
                            subtitle = "将设置、影视源、收藏记录打包为 JSON",
                            icon = TVBoxIcons.Outlined.Article,
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "导入/导出功能开发中...",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "同步云端备份",
                            subtitle = "未登录",
                            icon = TVBoxIcons.Outlined.Translate,
                            trailing = "立即登录",
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "请先登录后使用云备份功能",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "清除播放历史",
                            subtitle = "删除所有观看进度与历史记录",
                            icon = TVBoxIcons.Outlined.Delete,
                            onClick = { showClearHistoryDialog = true }
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "清除全部收藏",
                            subtitle = "取消所有影视的收藏标记",
                            icon = TVBoxIcons.Outlined.Favorite,
                            onClick = { showClearFavoritesDialog = true }
                        )
                    }
                }

                // 关于
                item {
                    SettingGroup(title = "关于") {
                        SettingItem(
                            title = "检查更新",
                            subtitle = "当前版本 v$appVersion",
                            icon = TVBoxIcons.Outlined.Download,
                            trailing = "已是最新"
                        )
                        HorizontalDividerItem()
                        SettingItem(
                            title = "开源仓库",
                            subtitle = "github.com/Huanglongmao66/auto-box",
                            icon = TVBoxIcons.Outlined.Info
                        )
                        HorizontalDividerItem()
                        SettingItem(title = "用户协议", icon = TVBoxIcons.Outlined.Article)
                        HorizontalDividerItem()
                        SettingItem(title = "隐私政策", icon = TVBoxIcons.Outlined.Article)
                    }
                }

                // 退出按钮
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "已退出登录",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.5f),
                            contentPadding = PaddingValues(vertical = tokens.spacing.md)
                        ) {
                            Text("退出登录", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(tokens.spacing.sm))
                        Text(
                            text = "© 2026 TVBox Multiplatform · $buildTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        // ===== 子路由：整屏覆盖 SourcesScreen，带顶部返回条 =====
        else if (settingsRoute == "sources-screen") {
            Column(modifier = Modifier.fillMaxSize()) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tokens.spacing.md,
                                vertical = tokens.spacing.sm
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { settingsRoute = "settings-root" }) {
                            Icon(
                                imageVector = TVBoxIcons.Outlined.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        Text(
                            text = "源管理",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    SourcesScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ====== 主题配色对话框 ======
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题配色") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    com.tvbox.core.ui.theme.TVBoxColorScheme.themePresets.forEachIndexed { i, seed ->
                        val sel = i == themeColorSeedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    themeColorSeedIndex = i
                                    scope.launch {
                                        configManager.update { it.copy(themeColorSeed = i) }
                                        snackbarHostState.showSnackbar(
                                            "已切换主题：${seed.name}",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDark) seed.primaryDark else seed.primaryLight
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = seed.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                            if (sel) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = TVBoxIcons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (i < com.tvbox.core.ui.theme.TVBoxColorScheme.themePresets.size - 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("关闭") }
            }
        )
    }

    // ====== 卡片样式对话框 ======
    if (showCardStyleDialog) {
        val cardStyles = listOf("标准卡片", "紧凑卡片", "大号卡片")
        AlertDialog(
            onDismissRequest = { showCardStyleDialog = false },
            title = { Text("首页卡片样式") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    cardStyles.forEachIndexed { i, name ->
                        val sel = i == cardStyleIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    cardStyleIndex = i
                                    scope.launch {
                                        configManager.update { it.copy(cardStyle = i) }
                                        snackbarHostState.showSnackbar(
                                            "已切换：$name",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TVBoxIcons.Outlined.GridViewSafeInvoke,
                                contentDescription = null,
                                tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                            if (sel) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = TVBoxIcons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (i < cardStyles.size - 1) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCardStyleDialog = false }) { Text("关闭") }
            }
        )
    }

    // ====== 清除缓存/历史/收藏 对话框（原有保留） ======
    if (showClearCacheDialog || showClearHistoryDialog || showClearFavoritesDialog) {
        val (title, message) = when {
            showClearCacheDialog -> "清除缓存" to "确定要清除所有图片和网络请求缓存吗？此操作不可撤销。"
            showClearHistoryDialog -> "清除播放历史" to "确定要删除所有观看进度与历史记录吗？此操作不可撤销。"
            else -> "清除全部收藏" to "确定要取消所有影视的收藏标记吗？此操作不可撤销。"
        }
        AlertDialog(
            onDismissRequest = {
                showClearCacheDialog = false
                showClearHistoryDialog = false
                showClearFavoritesDialog = false
            },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    val action = when {
                        showClearCacheDialog -> "缓存已清除"
                        showClearHistoryDialog -> "播放历史已清除"
                        else -> "收藏已全部清除"
                    }
                    showClearCacheDialog = false
                    showClearHistoryDialog = false
                    showClearFavoritesDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            action,
                            duration = SnackbarDuration.Short
                        )
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    showClearHistoryDialog = false
                    showClearFavoritesDialog = false
                }) { Text("取消") }
            }
        )
    }

    // ====== 新增 Dialog A: API 地址列表（单选模式 + 解析加载） ======
    if (showApiDialog) {
        AlertDialog(
            onDismissRequest = {
                scope.launch {
                    configManager.update { cfg ->
                        cfg.copy(apiUrls = apiList.toList(), currentApiIndex = currentApiIndex)
                    }
                }
                showApiDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("API 地址列表")
                    Spacer(Modifier.width(8.dp))
                    if (apiParsing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("解析中…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 说明
                    Text(
                        text = "只能选择一个 API 作为启用项，点击「启用并解析」可获取点播/直播订阅源列表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(apiList) { url ->
                            val idx = apiList.indexOf(url)
                            val isActive = idx == currentApiIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else
                                            Color.Transparent
                                    )
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 单选圆点
                                IconButton(onClick = {
                                    if (idx in apiList.indices) currentApiIndex = idx
                                }) {
                                    Icon(
                                        imageVector = if (isActive) TVBoxIcons.Filled.Check else TVBoxIcons.Outlined.Check,
                                        contentDescription = if (isActive) "当前启用" else "点击启用",
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // 启用并解析按钮
                                TextButton(
                                    onClick = {
                                        if (idx !in apiList.indices || apiParsing) return@TextButton
                                        currentApiIndex = idx
                                        val targetUrl = apiList[idx]
                                        apiParsing = true
                                        scope.launch {
                                            runCatching {
                                                val parser = ServiceLocator.getApiSubscriptionParser()
                                                val result = parser.parse(targetUrl)
                                                if (!result.isSuccess) {
                                                    snackbarHostState.showSnackbar(
                                                        "解析失败：${result.error ?: "未知错误"}",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                } else {
                                                    // 点播源列表：写入并默认选第一个
                                                    if (result.vodSources.isNotEmpty()) {
                                                        vodList.clear()
                                                        vodList.addAll(result.vodSources)
                                                        currentVodKey = result.vodSources.firstOrNull()?.key ?: ""
                                                        runCatching { ServiceLocator.getSourceManager().syncFromUserSources(vodList.toList()) }
                                                    }
                                                    // 直播源列表：写入并默认选第一个
                                                    if (result.liveSources.isNotEmpty()) {
                                                        liveList.clear()
                                                        liveList.addAll(result.liveSources)
                                                        currentLiveKey = result.liveSources.firstOrNull()?.key ?: ""
                                                    }
                                                    // 持久化
                                                    configManager.update { cfg ->
                                                        cfg.copy(
                                                            apiUrls = apiList.toList(),
                                                            currentApiIndex = currentApiIndex,
                                                            vodSources = vodList.toList(),
                                                            currentVodKey = currentVodKey,
                                                            liveSources = liveList.toList(),
                                                            currentLiveKey = currentLiveKey
                                                        )
                                                    }
                                                    val msg = buildString {
                                                        append("解析成功：")
                                                        if (result.vodSources.isNotEmpty()) append("点播源 ${result.vodSources.size} 个 ")
                                                        if (result.liveSources.isNotEmpty()) append("直播源 ${result.liveSources.size} 个")
                                                    }
                                                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                                                }
                                            }.onFailure {
                                                snackbarHostState.showSnackbar(
                                                    "解析异常：${it.message ?: "未知错误"}",
                                                    duration = SnackbarDuration.Long
                                                )
                                            }
                                            apiParsing = false
                                        }
                                    },
                                    enabled = !apiParsing
                                ) {
                                    Text(if (isActive) "解析" else "启用并解析", style = MaterialTheme.typography.labelSmall)
                                }
                                // 编辑按钮
                                IconButton(onClick = {
                                    if (idx in apiList.indices && !apiParsing) {
                                        editingApiIndex = idx
                                        editingApiUrl = apiList[idx]
                                    }
                                }) {
                                    Icon(
                                        imageVector = TVBoxIcons.Outlined.Edit,
                                        contentDescription = "修改",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // 删除按钮
                                IconButton(onClick = {
                                    if (idx in apiList.indices && !apiParsing) {
                                        apiList.removeAt(idx)
                                        if (currentApiIndex >= apiList.size) {
                                            currentApiIndex = (apiList.size - 1).coerceAtLeast(0)
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = TVBoxIcons.Outlined.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newApiUrl,
                            onValueChange = { newApiUrl = it },
                            placeholder = { Text("输入新 API 地址，如 http://www.饭太硬.net/tv") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !apiParsing
                        )
                        TextButton(
                            onClick = {
                                if (newApiUrl.isNotBlank() && !apiParsing) {
                                    apiList.add(newApiUrl.trim())
                                    newApiUrl = ""
                                }
                            },
                            enabled = !apiParsing
                        ) {
                            Text("添加")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            configManager.update { cfg ->
                                cfg.copy(apiUrls = apiList.toList(), currentApiIndex = currentApiIndex)
                            }
                        }
                        showApiDialog = false
                    },
                    enabled = !apiParsing
                ) { Text("关闭") }
            }
        )
    }

    // ====== Dialog A-2: 编辑 API 地址 ======
    if (editingApiIndex >= 0) {
        AlertDialog(
            onDismissRequest = {
                editingApiIndex = -1
                editingApiUrl = ""
            },
            title = { Text("修改 API 地址") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "修改后点击「保存」将立即生效，若该地址为当前启用项，需重新点击「解析」以刷新订阅源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    OutlinedTextField(
                        value = editingApiUrl,
                        onValueChange = { editingApiUrl = it },
                        label = { Text("API 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = editingApiUrl.trim()
                    if (trimmed.isNotBlank() && editingApiIndex in apiList.indices) {
                        apiList[editingApiIndex] = trimmed
                        scope.launch {
                            configManager.update { cfg ->
                                cfg.copy(apiUrls = apiList.toList(), currentApiIndex = currentApiIndex)
                            }
                            snackbarHostState.showSnackbar("API 地址已更新", duration = SnackbarDuration.Short)
                        }
                    }
                    editingApiIndex = -1
                    editingApiUrl = ""
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingApiIndex = -1
                    editingApiUrl = ""
                }) { Text("取消") }
            }
        )
    }

    // ====== 新增 Dialog B: 点播源列表（单选模式：只能选一个应用到首页/搜索） ======
    if (showVodDialog) {
        AlertDialog(
            onDismissRequest = { showVodDialog = false },
            title = { Text("点播源列表（单选）") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "从下方列表中选择一个点播源作为当前使用源，将应用到首页内容展示与搜索页面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(tokens.spacing.sm))
                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(vodList, key = { it.key }) { source ->
                            val isSelected = source.key == currentVodKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else
                                            Color.Transparent
                                    )
                                    .clickable {
                                        currentVodKey = source.key
                                        scope.launch {
                                            // 所有点播源中，仅当前选中的设为 enabled=true，其余 false（保证单选）
                                            val synced = vodList.map { it.copy(enabled = (it.key == currentVodKey)) }
                                            vodList.clear()
                                            vodList.addAll(synced)
                                            configManager.update {
                                                it.copy(vodSources = vodList.toList(), currentVodKey = currentVodKey)
                                            }
                                            snackbarHostState.showSnackbar("已切换点播源：${source.name}", duration = SnackbarDuration.Short)
                                            runCatching { ServiceLocator.getSourceManager().syncFromUserSources(vodList.toList()) }
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 单选标记
                                Icon(
                                    imageVector = if (isSelected) TVBoxIcons.Filled.Check else TVBoxIcons.Outlined.Check,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = source.api,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    val idx = vodList.indexOfFirst { it.key == source.key }
                                    if (idx in vodList.indices) {
                                        vodList.removeAt(idx)
                                        if (currentVodKey == source.key) {
                                            currentVodKey = vodList.firstOrNull()?.key ?: ""
                                        }
                                        scope.launch {
                                            val synced = vodList.map { it.copy(enabled = (it.key == currentVodKey)) }
                                            vodList.clear()
                                            vodList.addAll(synced)
                                            configManager.update {
                                                it.copy(vodSources = vodList.toList(), currentVodKey = currentVodKey)
                                            }
                                            runCatching { ServiceLocator.getSourceManager().syncFromUserSources(vodList.toList()) }
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = TVBoxIcons.Outlined.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                        OutlinedTextField(
                            value = newVodName,
                            onValueChange = { newVodName = it },
                            placeholder = { Text("源名称（如：站长资源）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newVodPath,
                                onValueChange = { newVodPath = it },
                                placeholder = { Text("源 API 地址") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            TextButton(onClick = {
                                if (newVodName.isNotBlank() && newVodPath.isNotBlank()) {
                                    val name = newVodName.trim()
                                    val generatedKey = "vod_" + (vodList.size + 1) + "_" + name.filter { it.isLetterOrDigit() }.take(8)
                                    val isFirst = vodList.isEmpty()
                                    vodList.add(
                                        UserVodSource(
                                            key = generatedKey,
                                            name = name,
                                            api = newVodPath.trim(),
                                            enabled = isFirst || currentVodKey.isBlank()
                                        )
                                    )
                                    if (currentVodKey.isBlank()) {
                                        currentVodKey = generatedKey
                                    }
                                    newVodName = ""
                                    newVodPath = ""
                                    scope.launch {
                                        configManager.update {
                                            it.copy(vodSources = vodList.toList(), currentVodKey = currentVodKey)
                                        }
                                        runCatching { ServiceLocator.getSourceManager().syncFromUserSources(vodList.toList()) }
                                    }
                                }
                            }) {
                                Text("添加")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVodDialog = false }) { Text("关闭") }
            }
        )
    }

    // ====== 新增 Dialog C: 直播订阅源列表 ======
    if (showLiveDialog) {
        AlertDialog(
            onDismissRequest = { showLiveDialog = false },
            title = { Text("直播订阅源") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(liveList, key = { it.key }) { src ->
                            val isSelected = src.key == currentLiveKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else
                                            Color.Transparent
                                    )
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = src.enabled,
                                    onCheckedChange = { checked ->
                                        val idx = liveList.indexOfFirst { it.key == src.key }
                                        if (idx in liveList.indices) {
                                            liveList[idx] = src.copy(enabled = checked)
                                            scope.launch {
                                                configManager.update {
                                                    it.copy(liveSources = liveList.toList(), currentLiveKey = currentLiveKey)
                                                }
                                                snackbarHostState.showSnackbar("直播源已保存", duration = SnackbarDuration.Short)
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${src.name} ${src.url}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    currentLiveKey = src.key
                                    scope.launch {
                                        configManager.update {
                                            it.copy(liveSources = liveList.toList(), currentLiveKey = currentLiveKey)
                                        }
                                        snackbarHostState.showSnackbar("直播源已保存", duration = SnackbarDuration.Short)
                                    }
                                }) {
                                    Icon(
                                        imageVector = TVBoxIcons.Outlined.Check,
                                        contentDescription = "设为默认",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    val idx = liveList.indexOfFirst { it.key == src.key }
                                    if (idx in liveList.indices) {
                                        liveList.removeAt(idx)
                                        if (currentLiveKey == src.key) {
                                            currentLiveKey = liveList.firstOrNull { it.enabled }?.key ?: liveList.firstOrNull()?.key ?: ""
                                        }
                                        scope.launch {
                                            configManager.update {
                                                it.copy(liveSources = liveList.toList(), currentLiveKey = currentLiveKey)
                                            }
                                            snackbarHostState.showSnackbar("直播源已保存", duration = SnackbarDuration.Short)
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = TVBoxIcons.Outlined.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                        ) {
                            OutlinedTextField(
                                value = newLiveName,
                                onValueChange = { newLiveName = it },
                                placeholder = { Text("备注名") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newLiveUrl,
                                onValueChange = { newLiveUrl = it },
                                placeholder = { Text("订阅 URL") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            TextButton(onClick = {
                                if (newLiveUrl.isNotBlank()) {
                                    val name = newLiveName.ifBlank { "新源" }.trim()
                                    val generatedKey = "live_" + (liveList.size + 1) + "_" + name.filter { it.isLetterOrDigit() }.take(8)
                                    liveList.add(
                                        UserLiveSource(
                                            key = generatedKey,
                                            name = name,
                                            url = newLiveUrl.trim(),
                                            enabled = true
                                        )
                                    )
                                    if (currentLiveKey.isBlank()) {
                                        currentLiveKey = generatedKey
                                    }
                                    newLiveUrl = ""
                                    newLiveName = ""
                                    scope.launch {
                                        configManager.update {
                                            it.copy(liveSources = liveList.toList(), currentLiveKey = currentLiveKey)
                                        }
                                        snackbarHostState.showSnackbar("直播源已保存", duration = SnackbarDuration.Short)
                                    }
                                }
                            }) {
                                Text("添加")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLiveDialog = false }) { Text("关闭") }
            }
        )
    }

    // ====== 新增 Dialog D: 选择壁纸 ======
    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("选择壁纸") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
                ) {
                    wallpaperPresets.chunked(4).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)
                        ) {
                            rowPresets.forEach { preset ->
                                val idx = wallpaperPresets.indexOf(preset)
                                val isSelected = idx == currentWallpaperIndex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            currentWallpaperIndex = idx
                                            scope.launch {
                                                configManager.update { it.copy(wallpaperIndex = idx) }
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(
                                                if (isSelected)
                                                    Modifier.border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                else Modifier
                                            )
                                            .background(
                                                brush = Brush.linearGradient(preset.colors)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(tokens.spacing.xs))
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // 不足 4 个补齐空白宽度
                            repeat(4 - rowPresets.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWallpaperDialog = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showWallpaperDialog = false }) { Text("取消") }
            }
        )
    }
}

// ================= 工具：分组内分割线 =================

@Composable
private fun HorizontalDividerItem() {
    val tokens = tvTokens()
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

// 处理 GridViewSafeInvoke（Material Icons Outlined 中没有 GridView）
private val OutlinedIcons.GridViewSafeInvoke
    get() = OutlinedIcons.Category

// ================= 版本信息头部卡片 =================

@Composable
private fun AppVersionCard(
    version: String,
    buildTime: String
) {
    val tokens = tvTokens()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
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
                    imageVector = TVBoxIcons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(tokens.spacing.xl))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TVBox 多平台版",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(tokens.spacing.xs))
                Text(
                    text = "v$version · $buildTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                    ScoreBadge(score = "稳定版")
                    com.tvbox.core.ui.components.Badge(
                        text = "KMP",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    com.tvbox.core.ui.components.Badge(
                        text = "Compose",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
