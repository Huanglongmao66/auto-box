package com.tvbox.core.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.MovieSource
import com.tvbox.core.model.UserVodSource
import com.tvbox.core.ui.components.Badge
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.components.SearchBar
import com.tvbox.core.ui.components.SectionHeader
import com.tvbox.core.ui.components.SettingGroup
import com.tvbox.core.ui.components.SettingItem
import com.tvbox.core.ui.components.TVBoxCard
import com.tvbox.core.ui.components.TVBoxChip
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.mock.MockData
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.launch

// ================= 影视源管理页 =================

@Composable
fun SourcesScreen(modifier: Modifier = Modifier) {
    val tokens = tvTokens()
    // 使用可变列表管理源，支持动态添加
    val allSources = remember { mutableStateListOf<MovieSource>().apply { addAll(MockData.movieSources) } }
    val subscriptions = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(MockData.sourceSubscriptions) } }
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddSubscriptionDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var importingUrl by remember { mutableStateOf<String?>(null) }

    val sources = remember(query) {
        if (query.isBlank()) allSources.toList() else allSources.filter {
            it.name.contains(query, ignoreCase = true) || it.key.contains(query, ignoreCase = true)
        }
    }
    val enabledCount = allSources.count { it.enabled }

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(
                start = tokens.spacing.lg,
                end = tokens.spacing.lg,
                top = tokens.spacing.sm,
                bottom = tokens.spacing.xxxl
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
        ) {
            // 搜索条
            item {
                SearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索影视源…",
                    onClear = { query = "" }
                )
            }
            // 统计概览
            item {
                OverviewRow(total = allSources.size, enabled = enabledCount)
            }
            // 订阅源分组
            item {
                SettingGroup(title = "订阅地址") {
                    subscriptions.forEachIndexed { index, (name, url) ->
                        val isImporting = importingUrl == url
                        SettingItem(
                            title = name,
                            subtitle = url,
                            icon = TVBoxIcons.Outlined.Language,
                            trailing = if (isImporting) "解析中…" else "已订阅",
                            onClick = {
                                if (isImporting) return@SettingItem
                                importingUrl = url
                                scope.launch {
                                    val configManager = try { ServiceLocator.getConfigManager() } catch (_: Throwable) { null }
                                    val parser = try { ServiceLocator.getApiSubscriptionParser() } catch (_: Throwable) { null }
                                    val srcMgr = try { ServiceLocator.getSourceManager() } catch (_: Throwable) { null }
                                    val liveMgr = try { ServiceLocator.getLiveSourceManager() } catch (_: Throwable) { null }
                                    try {
                                        // 使用 ApiSubscriptionParser 统一解析（支持单仓/多仓/图片伪装/Base64）
                                        val result = parser?.parse(url)
                                        if (result == null) {
                                            snackbarHostState.showSnackbar(
                                                "解析服务未初始化",
                                                duration = SnackbarDuration.Short
                                            )
                                        } else if (!result.isSuccess) {
                                            snackbarHostState.showSnackbar(
                                                "$name 解析失败：${result.error ?: "未知错误"}",
                                                duration = SnackbarDuration.Long
                                            )
                                        } else {
                                            val vodS = result.vodSources
                                            val liveS = result.liveSources
                                            if (vodS.isEmpty() && liveS.isEmpty()) {
                                                snackbarHostState.showSnackbar(
                                                    "$name 未解析到任何订阅源",
                                                    duration = SnackbarDuration.Long
                                                )
                                            } else {
                                                // 1. 同步点播源到 ServiceLocator SourceManager（过滤有效 HTTP JSON 源）
                                                if (vodS.isNotEmpty() && srcMgr != null) {
                                                    try { srcMgr.syncFromUserSources(vodS) } catch (_: Throwable) {}
                                                    // 刷新当前页面源列表（MovieSource 形式）
                                                    allSources.clear()
                                                    allSources.addAll(srcMgr.getSources())
                                                }
                                                // 2. 同步直播源到 LiveSourceManager（第一个启用）
                                                if (liveS.isNotEmpty()) {
                                                    val firstLive = liveS.firstOrNull { it.enabled } ?: liveS.first()
                                                    liveMgr?.setSubscriptionUrl(firstLive.url)
                                                }
                                                // 3. 持久化到 ConfigManager
                                                if (configManager != null) {
                                                    val cfg = try { configManager.getConfig() } catch (_: Throwable) { null }
                                                    if (cfg != null) {
                                                        val curVodKey = run {
                                                            val cur = cfg.currentVodKey
                                                            if (vodS.any { it.key == cur }) cur
                                                            else vodS.firstOrNull { it.enabled }?.key
                                                                ?: vodS.firstOrNull()?.key ?: ""
                                                        }
                                                        val normalizedVod = vodS.map { v: UserVodSource ->
                                                            v.copy(enabled = (v.key == curVodKey))
                                                        }
                                                        val curLiveKey = run {
                                                            val cur = cfg.currentLiveKey
                                                            if (liveS.any { it.key == cur }) cur
                                                            else liveS.firstOrNull { it.enabled }?.key
                                                                ?: liveS.firstOrNull()?.key ?: ""
                                                        }
                                                        val normalizedLive = liveS.map { l ->
                                                            l.copy(enabled = (l.key == curLiveKey))
                                                        }
                                                        val curApiIdx = run {
                                                            val cur = cfg.apiUrls.indexOf(url)
                                                            if (cur >= 0) cur else cfg.currentApiIndex.coerceIn(0, (cfg.apiUrls.size - 1).coerceAtLeast(0))
                                                        }
                                                        try {
                                                            configManager.update { c ->
                                                                c.copy(
                                                                    apiUrls = if (url in c.apiUrls) c.apiUrls else c.apiUrls + url,
                                                                    currentApiIndex = curApiIdx,
                                                                    vodSources = normalizedVod,
                                                                    currentVodKey = curVodKey,
                                                                    liveSources = normalizedLive,
                                                                    currentLiveKey = curLiveKey
                                                                )
                                                            }
                                                        } catch (_: Throwable) {}
                                                    }
                                                }
                                                // 4. 提示解析结果
                                                val msg = buildString {
                                                    append("$name 解析成功：")
                                                    if (vodS.isNotEmpty()) append("点播源 ${vodS.size} 个 ")
                                                    if (liveS.isNotEmpty()) append("直播源 ${liveS.size} 个")
                                                }
                                                snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        snackbarHostState.showSnackbar(
                                            "$name 解析异常：${e.message ?: "网络错误"}",
                                            duration = SnackbarDuration.Long
                                        )
                                    } finally {
                                        importingUrl = null
                                    }
                                }
                            }
                        )
                        if (index < subscriptions.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingItem(
                        title = "添加订阅",
                        subtitle = "从远程URL导入一组影视源配置",
                        icon = TVBoxIcons.Outlined.Add,
                        onClick = { showAddSubscriptionDialog = true }
                    )
                }
            }
            // 源列表
            item {
                Column {
                    SectionHeader(
                        title = "影视源列表",
                        action = { Badge(text = "$enabledCount / ${allSources.size} 启用", color = MaterialTheme.colorScheme.secondary) }
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    if (sources.isEmpty()) {
                        EmptyView(
                            title = "没有匹配的影视源",
                            description = "试试其它关键字，或点击右下角「+」添加新源",
                            icon = TVBoxIcons.Outlined.Source
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
                            sources.forEachIndexed { i, s ->
                                SourceCard(source = s)
                            }
                        }
                    }
                }
            }
        }
        // 浮动添加按钮
        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(tokens.spacing.xl),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(TVBoxIcons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(tokens.spacing.sm))
            Text("添加影视源", style = MaterialTheme.typography.labelLarge)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (importingUrl != null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { source ->
                allSources.add(0, source)
                showAddDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "已添加源：${source.name}",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    if (showAddSubscriptionDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddSubscriptionDialog = false },
            onAdd = { name, url ->
                subscriptions.add(name to url)
                showAddSubscriptionDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "已添加订阅：$name",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }
}

// ================= 概览统计 =================

@Composable
private fun OverviewRow(total: Int, enabled: Int) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        StatCard(title = "影视源总数", value = "$total", icon = TVBoxIcons.Outlined.Source, modifier = Modifier.weight(1f))
        StatCard(title = "已启用", value = "$enabled", icon = TVBoxIcons.Outlined.Check, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
        StatCard(title = "已禁用", value = "${total - enabled}", icon = TVBoxIcons.Outlined.Close, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.1f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(tokens.size.iconMd))
            }
            Spacer(modifier = Modifier.width(tokens.spacing.md))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ================= 源卡片 =================

@Composable
private fun SourceCard(source: MovieSource) {
    val tokens = tvTokens()
    var enabled by remember(source) { mutableStateOf(source.enabled) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        onClick = { /* 展开详情 */ }
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TVBoxIcons.Outlined.Source,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(tokens.size.iconLg)
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                        if (!enabled) {
                            Badge(text = "已禁用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (source.type == 0) Badge(text = "XML", color = MaterialTheme.colorScheme.tertiary)
                        if (source.type == 1) Badge(text = "JSON", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = source.api,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.sm))
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (source.desc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                Text(
                    text = source.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 能力标签
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                if (source.searchable)
                    TVBoxChip(text = "可搜索", selected = true, icon = TVBoxIcons.Outlined.Search)
                if (source.filterable)
                    TVBoxChip(text = "分类过滤", selected = true, icon = TVBoxIcons.Outlined.Tune)
                if (source.quickSearchable)
                    TVBoxChip(text = "快速搜索", selected = true, icon = TVBoxIcons.Outlined.Lightbulb)
            }
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                OutlinedButton(
                    onClick = { /* 刷新 */ },
                    contentPadding = PaddingValues(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                ) {
                    Icon(TVBoxIcons.Outlined.Download, null, modifier = Modifier.size(tokens.size.iconSm))
                    Spacer(modifier = Modifier.width(tokens.spacing.xs))
                    Text("刷新", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = { /* 编辑 */ },
                    contentPadding = PaddingValues(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
                ) {
                    Icon(TVBoxIcons.Outlined.Edit, null, modifier = Modifier.size(tokens.size.iconSm))
                    Spacer(modifier = Modifier.width(tokens.spacing.xs))
                    Text("编辑", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { /* 删除 */ }) {
                    Icon(
                        TVBoxIcons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ================= 添加影视源 Dialog =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (MovieSource) -> Unit
) {
    val tokens = tvTokens()
    var name by remember { mutableStateOf("") }
    var api by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(1) } // 0=XML, 1=JSON, 3=Spider

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable { /* 防止弹窗点击穿透 */ },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = tokens.elevation.lg
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TVBoxIcons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.width(tokens.spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("添加影视源", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("填写源配置信息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(TVBoxIcons.Outlined.Close, null) }
                }
                Spacer(modifier = Modifier.height(tokens.spacing.xl))

                DialogField(label = "源名称", placeholder = "例如：最好资源网", value = name, onValueChange = { name = it })
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                DialogField(label = "接口地址 (API)", placeholder = "https://...", value = api, onValueChange = { api = it })
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                DialogField(label = "源 Key（英文标识）", placeholder = "csp_xxx", value = key, onValueChange = { key = it })
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    listOf("JSON" to 1, "XML" to 0, "Spider" to 3).forEach { (label, type) ->
                        TVBoxChip(
                            text = label,
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(tokens.spacing.xl))

                Row {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = tokens.spacing.sm)
                    ) { Text("取消") }
                    Spacer(modifier = Modifier.width(tokens.spacing.md))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && api.isNotBlank()) {
                                val ts = com.tvbox.utils.DateUtils.currentTimeMillis()
                                val finalKey = if (key.isBlank()) "csp_$ts" else key
                                onAdd(
                                    MovieSource(
                                        key = finalKey,
                                        name = name.trim(),
                                        api = api.trim(),
                                        type = selectedType,
                                        enabled = true,
                                        searchable = true,
                                        filterable = selectedType == 1,
                                        order = (ts % 1000).toInt()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = tokens.spacing.sm)
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun DialogField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val tokens = tvTokens()
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(tokens.spacing.sm))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ================= 添加订阅 Dialog =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val tokens = tvTokens()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable { },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = tokens.elevation.lg
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TVBoxIcons.Outlined.Language, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.width(tokens.spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("添加订阅", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("输入订阅地址，支持单仓/多仓格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(TVBoxIcons.Outlined.Close, null) }
                }
                Spacer(modifier = Modifier.height(tokens.spacing.xl))

                DialogField(label = "订阅名称", placeholder = "例如：我的订阅", value = name, onValueChange = { name = it })
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                DialogField(label = "订阅地址 (URL)", placeholder = "https://...", value = url, onValueChange = { url = it })
                Spacer(modifier = Modifier.height(tokens.spacing.xl))

                Row {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = tokens.spacing.sm)
                    ) { Text("取消") }
                    Spacer(modifier = Modifier.width(tokens.spacing.md))
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                val finalName = if (name.isBlank()) "自定义订阅" else name.trim()
                                onAdd(finalName, url.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = tokens.spacing.sm)
                    ) { Text("添加") }
                }
            }
        }
    }
}
