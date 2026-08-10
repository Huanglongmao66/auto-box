package com.tvbox.core.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.components.Badge
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.components.SectionHeader
import com.tvbox.core.ui.components.VodGrid
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.mock.MockData
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.launch

// ================= 收藏页 =================

private enum class SortOption(val label: String) {
    DATE_ADDED("最近收藏"),
    NAME("按名称"),
    YEAR("按年份")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onVodClick: (VodInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val favorites = remember {
        mutableStateListOf<VodInfo>().apply {
            addAll(MockData.favoriteSamples)
        }
    }

    var sortOption by remember { mutableStateOf(SortOption.DATE_ADDED) }
    var filterCategory by remember { mutableStateOf("全部") }

    val categories = listOf("全部") + MockData.categories.map { it.typeName }.distinct()

    fun getVodCategory(vod: VodInfo): String {
        return when {
            vod in MockData.movieList -> "电影"
            vod in MockData.dramaList -> "电视剧"
            vod in MockData.animeList -> "动漫"
            vod.vodClass.contains("动漫") -> "动漫"
            vod.vodClass.contains("剧") -> "电视剧"
            else -> "电影"
        }
    }

    val filteredAndSorted = favorites
        .filter { vod ->
            filterCategory == "全部" || getVodCategory(vod) == filterCategory
        }
        .let { list ->
            when (sortOption) {
                SortOption.DATE_ADDED -> list
                SortOption.NAME -> list.sortedBy { it.vodName }
                SortOption.YEAR -> list.sortedByDescending { it.vodYear }
            }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (favorites.isEmpty()) {
                EmptyView(
                    title = "暂无收藏",
                    description = "在详情页点击心形图标即可添加收藏",
                    icon = TVBoxIcons.Outlined.Favorite
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = tokens.spacing.lg,
                        end = tokens.spacing.lg,
                        top = tokens.spacing.sm,
                        bottom = tokens.spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
                ) {
                    // 统计信息
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Badge(text = "共 ${favorites.size} 部收藏")
                            if (filterCategory != "全部") {
                                Badge(text = "筛选：$filterCategory")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Badge(text = sortOption.label)
                        }
                    }

                    // 排序选项
                    item {
                        Column {
                            SectionHeader(title = "排序方式")
                            Spacer(modifier = Modifier.height(tokens.spacing.sm))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                            ) {
                                SortOption.values().forEach { option ->
                                    FilterChip(
                                        selected = sortOption == option,
                                        onClick = { sortOption = option },
                                        label = { Text(option.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 分类筛选
                    item {
                        Column {
                            SectionHeader(title = "分类筛选")
                            Spacer(modifier = Modifier.height(tokens.spacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                            ) {
                                categories.take(6).forEach { category ->
                                    FilterChip(
                                        selected = filterCategory == category,
                                        onClick = { filterCategory = category },
                                        label = { Text(category) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 收藏列表
                    if (filteredAndSorted.isEmpty()) {
                        item {
                            EmptyView(
                                title = "该分类暂无收藏",
                                description = "尝试切换分类筛选查看更多",
                                icon = TVBoxIcons.Outlined.Favorite
                            )
                        }
                    } else {
                        item {
                            Column {
                                SectionHeader(
                                    title = "收藏列表",
                                    actionText = "清空",
                                    onAction = {
                                        favorites.clear()
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "已清空所有收藏",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(tokens.spacing.sm))
                                VodGrid(
                                    items = filteredAndSorted,
                                    columns = 3,
                                    onClick = onVodClick,
                                    contentPadding = PaddingValues(0.dp),
                                    scrollEnabled = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
