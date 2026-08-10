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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.components.Badge
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.components.SearchBar
import com.tvbox.core.ui.components.SectionHeader
import com.tvbox.core.ui.components.TVBoxChip
import com.tvbox.core.ui.components.VodGrid
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.mock.MockData
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

// ================= 搜索页 =================

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    onVodClick: (VodInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    var query by remember { mutableStateOf("") }
    val recentSearch = remember { mutableStateListOf("长安忆", "剑与魔法之书", "暗夜法官") }
    val hotKeywords = MockData.hotKeywords
    val categories = MockData.categories
    var selectedCategories by remember { mutableStateOf(emptyList<String>()) }
    val showResults = query.isNotEmpty()

    // 真实搜索状态
    var searching by remember { mutableStateOf(false) }
    val realResults = remember { mutableStateListOf<VodInfo>() }
    val queryFlow = remember { MutableStateFlow("") }

    // 防抖：用户停止输入 400ms 后发起请求
    LaunchedEffect(queryFlow) {
        queryFlow
            .debounce(400)
            .filter { it.isNotBlank() }
            .collect { kw ->
                searching = true
                realResults.clear()
                val repo = runCatching { ServiceLocator.getVodRepository() }.getOrNull()
                val results = repo?.runCatching { searchContent(kw, 1) }?.getOrNull()
                    .orEmpty()
                if (results.isNotEmpty()) {
                    realResults.addAll(
                        results.map { r ->
                            VodInfo(
                                vodId = r.vodId,
                                vodName = r.vodName,
                                vodPic = r.vodPic,
                                vodRemarks = r.vodRemarks,
                                sourceKey = r.sourceKey
                            )
                        }
                    )
                }
                searching = false
            }
    }

    LaunchedEffect(query) {
        queryFlow.value = query.trim()
    }

    // Mock 兜底筛选逻辑
    fun fallbackMockResults(): List<VodInfo> {
        val raw = (MockData.movieList + MockData.dramaList + MockData.animeList)
            .filter {
                query.isBlank() || it.vodName.contains(query, ignoreCase = true)
                    || it.vodActor.orEmpty().contains(query, ignoreCase = true)
                    || it.vodDirector.orEmpty().contains(query, ignoreCase = true)
            }
        return if (selectedCategories.isEmpty()) raw else raw.filter { v ->
            val src = when {
                v in MockData.movieList -> "电影"
                v in MockData.dramaList -> "电视剧"
                v in MockData.animeList -> "动漫"
                v in MockData.trending -> "电影"
                else -> null
            }
            src != null && src in selectedCategories
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = tokens.spacing.lg,
            end = tokens.spacing.lg,
            top = tokens.spacing.sm,
            bottom = tokens.spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        // 搜索输入
        item {
            SearchBar(
                value = query,
                onValueChange = { query = it },
                onClear = { query = "" },
                placeholder = "搜索影视、演员、导演…"
            )
        }

        if (showResults) {
            // 结果筛选 Tab
            item {
                ResultFilterTabs(
                    categories = categories.map { it.typeName },
                    selectedCategories = selectedCategories,
                    onToggle = { name ->
                        selectedCategories = if (name in selectedCategories)
                            selectedCategories - name
                        else
                            selectedCategories + name
                    }
                )
            }
            // 结果列表
            item {
                Column {
                    // 合并：优先真实搜索结果，空则 fallback
                    val merged: List<VodInfo> = if (realResults.isNotEmpty()) {
                        realResults.toList()
                    } else if (!searching) {
                        fallbackMockResults()
                    } else {
                        emptyList()
                    }
                    SectionHeader(
                        title = "搜索结果",
                        action = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(tokens.spacing.sm))
                                    Text("搜索中…", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Badge(
                                        text = if (merged.isNotEmpty())
                                            "约 ${merged.size} 个结果"
                                        else "无结果"
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    if (searching && merged.isEmpty()) {
                        // 骨架屏
                        EmptyView(
                            title = "正在跨源聚合搜索…",
                            description = "已接入多源搜索，等待结果返回",
                            icon = TVBoxIcons.Outlined.Search
                        )
                    } else if (merged.isEmpty()) {
                        EmptyView(
                            title = "没有找到相关结果",
                            description = if (selectedCategories.isNotEmpty())
                                "试试调整分类筛选或换个关键字"
                            else "试试换个关键字，或者在影视源中添加更多资源",
                            icon = TVBoxIcons.Outlined.Search
                        )
                    } else {
                        BoxWithConstraints {
                            val cols = when {
                                maxWidth >= 1200.dp -> 6
                                maxWidth >= 800.dp -> 5
                                maxWidth >= 560.dp -> 4
                                else -> 3
                            }
                            VodGrid(
                                items = merged.take(48),
                                columns = cols,
                                onClick = onVodClick,
                                contentPadding = PaddingValues(0.dp),
                                scrollEnabled = false
                            )
                        }
                    }
                }
            }
        } else {
            // 历史搜索
            if (recentSearch.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(title = "最近搜索", actionText = "清空", onAction = {
                            recentSearch.clear()
                        })
                        Spacer(modifier = Modifier.height(tokens.spacing.sm))
                        TagCloud(items = recentSearch) { keyword -> query = keyword }
                    }
                }
            }
            // 热门搜索
            item {
                Column {
                    SectionHeader(title = "热门搜索")
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    HotKeywordsGrid(keywords = hotKeywords, onClick = { query = it })
                }
            }
            // 分类快捷入口
            item {
                Column {
                    SectionHeader(title = "分类浏览")
                    Spacer(modifier = Modifier.height(tokens.spacing.sm))
                    CategoryChips(
                        categories = categories.map { it.typeName },
                        onClick = { name ->
                            // 点击分类直接做模糊搜索
                            query = if (query.isBlank()) name else "$query $name"
                        }
                    )
                }
            }
        }
    }
}

// ================= 结果筛选 Tabs（多选分类） =================

@Composable
private fun ResultFilterTabs(
    categories: List<String>,
    selectedCategories: List<String>,
    onToggle: (String) -> Unit
) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        val allSelected = selectedCategories.isEmpty()
        TVBoxChip(text = "全部", selected = allSelected, onClick = { /* noop */ })
        categories.forEach { cat ->
            TVBoxChip(
                text = cat,
                selected = cat in selectedCategories,
                onClick = { onToggle(cat) }
            )
        }
    }
}

// ================= 历史搜索标签云 =================

@Composable
private fun TagCloud(
    items: List<String>,
    onClick: (String) -> Unit
) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        items.forEach { keyword ->
            HistoryTag(text = keyword, onClick = { onClick(keyword) })
        }
    }
}

@Composable
private fun HistoryTag(
    text: String,
    onClick: () -> Unit
) {
    val tokens = tvTokens()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
        ) {
            Icon(
                TVBoxIcons.Outlined.Visibility,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(tokens.size.iconSm)
            )
            Spacer(modifier = Modifier.width(tokens.spacing.xs))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ================= 热门搜索网格（带排名） =================

@Composable
private fun HotKeywordsGrid(
    keywords: List<String>,
    onClick: (String) -> Unit
) {
    val tokens = tvTokens()
    val cols = 2
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        keywords.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                row.forEachIndexed { idx, kw ->
                    val rank = (keywords.indexOf(kw) + 1)
                    HotRankItem(
                        keyword = kw,
                        rank = rank,
                        modifier = Modifier.weight(1f),
                        onClick = { onClick(kw) }
                    )
                }
                if (row.size < cols) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HotRankItem(
    keyword: String,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val rankColor = when (rank) {
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF97316)
        3 -> Color(0xFFEAB308)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isHot = rank <= 3
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = rankColor,
            modifier = Modifier.width(28.dp)
        )
        Spacer(modifier = Modifier.width(tokens.spacing.sm))
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (isHot) {
            Badge(text = "HOT", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ================= 分类 Chips =================

@Composable
private fun CategoryChips(
    categories: List<String>,
    onClick: (String) -> Unit
) {
    val tokens = tvTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        categories.forEach { name ->
            TVBoxChip(text = name, onClick = { onClick(name) }, icon = TVBoxIcons.Outlined.Category)
        }
    }
}
