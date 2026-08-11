package com.tvbox.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tvbox.core.di.ServiceLocator
import com.tvbox.core.model.PlayHistory
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.components.EmptyView
import com.tvbox.core.ui.components.VodGrid
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onVodClick: (VodInfo) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var historyList by remember { mutableStateOf<List<PlayHistory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            val manager = ServiceLocator.getHistoryManager()
            historyList = manager.getRecentHistory(100)
        }
        loading = false
    }

    val vodList = historyList.map { h ->
        VodInfo(
            vodId = h.vodId,
            sourceKey = h.sourceKey,
            vodName = h.vodName,
            vodPic = h.vodPic,
            vodRemarks = h.episodeName
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("观看历史") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(TVBoxIcons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching {
                                    ServiceLocator.getHistoryManager().clearAllHistory()
                                    historyList = emptyList()
                                }
                                snackbarHostState.showSnackbar("已清空历史记录", duration = SnackbarDuration.Short)
                            }
                        }) {
                            Icon(TVBoxIcons.Outlined.Delete, contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
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
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (vodList.isEmpty()) {
                EmptyView(
                    title = "暂无观看历史",
                    description = "看过的视频会自动记录在这里",
                    icon = TVBoxIcons.Outlined.Visibility
                )
            } else {
                VodGrid(
                    items = vodList,
                    columns = 3,
                    onClick = onVodClick,
                    contentPadding = PaddingValues(
                        start = tokens.spacing.md,
                        end = tokens.spacing.md,
                        top = tokens.spacing.sm,
                        bottom = tokens.spacing.xxl
                    ),
                    scrollEnabled = true
                )
            }
        }
    }
}
