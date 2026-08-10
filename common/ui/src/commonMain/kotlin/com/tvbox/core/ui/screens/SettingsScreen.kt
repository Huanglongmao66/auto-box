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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvbox.core.ui.components.ScoreBadge
import com.tvbox.core.ui.components.SettingGroup
import com.tvbox.core.ui.components.SettingItem
import com.tvbox.core.ui.components.SettingSwitch
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens

// ================= 设置页 =================

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    cacheSize: String = "328 MB",
    appVersion: String = "1.0.0",
    buildTime: String = "2026-08-08"
) {
    val tokens = tvTokens()
    var isDark by remember { mutableStateOf(true) }
    var skipTitle by remember { mutableStateOf(true) }
    var autoNext by remember { mutableStateOf(true) }
    var hdrEnhance by remember { mutableStateOf(false) }
    var useHwDecoding by remember { mutableStateOf(true) }
    var keepRatio by remember { mutableStateOf(true) }

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
                    onCheckedChange = { isDark = it }
                )
                HorizontalDividerItem()
                SettingItem(
                    title = "主题配色",
                    subtitle = "靛紫（当前）",
                    icon = TVBoxIcons.Outlined.Palette,
                    trailing = "6 款"
                )
                HorizontalDividerItem()
                SettingItem(
                    title = "首页卡片样式",
                    subtitle = "标准卡片",
                    icon = TVBoxIcons.Outlined.GridViewSafeInvoke,
                    trailing = "3 种"
                )
                HorizontalDividerItem()
                SettingSwitch(
                    title = "显示评分角标",
                    subtitle = "在影视卡片右上角显示评分",
                    icon = TVBoxIcons.Outlined.Star,
                    checked = true,
                    onCheckedChange = { /* noop */ }
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
                    onCheckedChange = { autoNext = it }
                )
                HorizontalDividerItem()
                SettingSwitch(
                    title = "自动跳过片头片尾",
                    subtitle = "智能识别不重复观看相同片段",
                    icon = TVBoxIcons.Outlined.Visibility,
                    checked = skipTitle,
                    onCheckedChange = { skipTitle = it }
                )
                HorizontalDividerItem()
                SettingSwitch(
                    title = "硬件解码优先",
                    subtitle = "使用GPU解码提升播放流畅度",
                    icon = TVBoxIcons.Outlined.Tune,
                    checked = useHwDecoding,
                    onCheckedChange = { useHwDecoding = it }
                )
                HorizontalDividerItem()
                SettingSwitch(
                    title = "保持视频原始比例",
                    subtitle = "关闭将强制铺满屏幕",
                    icon = TVBoxIcons.Outlined.Fullscreen,
                    checked = keepRatio,
                    onCheckedChange = { keepRatio = it }
                )
                HorizontalDividerItem()
                SettingSwitch(
                    title = "HDR 画质增强",
                    subtitle = "需要设备支持 HDR 显示",
                    icon = TVBoxIcons.Outlined.Lightbulb,
                    checked = hdrEnhance,
                    onCheckedChange = { hdrEnhance = it }
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
                    icon = TVBoxIcons.Outlined.Delete
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
                    onCheckedChange = { /* noop */ }
                )
            }
        }

        // 数据管理
        item {
            SettingGroup(title = "数据管理") {
                SettingItem(
                    title = "导入/导出配置",
                    subtitle = "将设置、影视源、收藏记录打包为 JSON",
                    icon = TVBoxIcons.Outlined.Article
                )
                HorizontalDividerItem()
                SettingItem(
                    title = "同步云端备份",
                    subtitle = "未登录",
                    icon = TVBoxIcons.Outlined.Translate,
                    trailing = "立即登录"
                )
                HorizontalDividerItem()
                SettingItem(
                    title = "清除播放历史",
                    subtitle = "删除所有观看进度与历史记录",
                    icon = TVBoxIcons.Outlined.Delete
                )
                HorizontalDividerItem()
                SettingItem(
                    title = "清除全部收藏",
                    subtitle = "取消所有影视的收藏标记",
                    icon = TVBoxIcons.Outlined.Favorite
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
                    onClick = { /* 退出登录（预留） */ },
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
private val TVBoxIcons.Outlined.GridViewSafeInvoke
    get() = TVBoxIcons.Outlined.Category

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
