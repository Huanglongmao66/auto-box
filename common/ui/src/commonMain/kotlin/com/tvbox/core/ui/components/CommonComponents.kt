package com.tvbox.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tvbox.core.model.VodInfo
import com.tvbox.core.ui.icons.TVBoxIcons
import com.tvbox.core.ui.theme.tvTokens
import androidx.compose.ui.unit.sp
import kotlin.math.min

// ============== 通用容器：TVBoxCard（带悬停/聚焦高亮） ==============

/**
 * 标准卡片容器，带渐变悬停效果
 */
@Composable
fun TVBoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit
) {
    val tokens = tvTokens()
    val cardModifier = modifier
        .shadow(tokens.elevation.sm, shape = shape)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(tokens.spacing.md)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.sm,
        onClick = onClick ?: {}
    ) {
        Box(modifier = Modifier.padding(tokens.spacing.md)) {
            content()
        }
    }
}

// ============== 徽章 Badge ==============

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val tokens = tvTokens()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = tokens.spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

@Composable
fun ScoreBadge(score: String, modifier: Modifier = Modifier) {
    if (score.isBlank()) return
    Badge(
        text = "★ $score",
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiary,
        textColor = MaterialTheme.colorScheme.tertiary
    )
}

// ============== Chip 组件 ==============

@Composable
fun TVBoxChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, modifier = Modifier.size(tokens.size.iconSm)) }
        } else null,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = MaterialTheme.shapes.large,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ============== 分段标签组 ==============

@Composable
fun SegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.xs)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                val bgColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    label = "tab-bg"
                )
                val textColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tab-text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(tokens.spacing.xs)
                        .clip(MaterialTheme.shapes.medium)
                        .background(bgColor)
                        .clickable { onSelect(index) }
                        .padding(vertical = tokens.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
                }
            }
        }
    }
}

// ============== 占位渐变封面 ==============

@Composable
fun PosterPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.7f,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val tokens = tvTokens()
    // 渐变动画（骨架屏效果）
    val transition = rememberInfiniteTransition(label = "poster-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gradientColors = listOf(
        baseColor,
        accentColor.copy(alpha = 0.25f + shimmer * 0.2f),
        baseColor
    )
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(1f, 1f)
                )
            )
            .padding(tokens.spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = TVBoxIcons.Outlined.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(tokens.size.iconLg)
            )
            Spacer(modifier = Modifier.height(tokens.spacing.sm))
            Text(
                text = title.take(2),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============== 影视卡片（现代化版本） ==============

@Composable
fun VodCard(
    vodInfo: VodInfo,
    onClick: (VodInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick(vodInfo) }
    ) {
        Box {
            PosterPlaceholder(title = vodInfo.vodName)
            if (vodInfo.vodRemarks.isNotEmpty()) {
                Badge(
                    text = vodInfo.vodRemarks,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tokens.spacing.xs),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (vodInfo.vodScore.isNotEmpty()) {
                ScoreBadge(
                    score = vodInfo.vodScore,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(tokens.spacing.xs)
                )
            }
        }
        Spacer(modifier = Modifier.height(tokens.spacing.sm))
        Text(
            text = vodInfo.vodName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (vodInfo.vodYear.isNotEmpty() || vodInfo.vodClass.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            val sub = buildString {
                if (vodInfo.vodYear.isNotEmpty()) append(vodInfo.vodYear)
                if (vodInfo.vodYear.isNotEmpty() && vodInfo.vodClass.isNotEmpty()) append(" · ")
                if (vodInfo.vodClass.isNotEmpty()) append(vodInfo.vodClass.take(8))
            }
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============== 影视网格 ==============

@Composable
fun VodGrid(
    items: List<VodInfo>,
    columns: Int = 3,
    onClick: (VodInfo) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    scrollEnabled: Boolean = true
) {
    val tokens = tvTokens()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = if (scrollEnabled) modifier.fillMaxSize() else modifier.fillMaxWidth().wrapContentHeight(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
        userScrollEnabled = scrollEnabled
    ) {
        items(items, key = { it.vodId + it.sourceKey }) { vodInfo ->
            VodCard(vodInfo = vodInfo, onClick = onClick)
        }
    }
}

// ============== 加载指示器 ==============

@Composable
fun LoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============== 错误视图 ==============

@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(tokens.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = TVBoxIcons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(tokens.spacing.lg))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.lg))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

// ============== 空视图 ==============

@Composable
fun EmptyView(
    title: String = "暂无数据",
    description: String? = null,
    icon: ImageVector = TVBoxIcons.Outlined.Source,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(tokens.spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(tokens.spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.xl))
            OutlinedButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
}

// ============== 标题栏 Section Header ==============

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val tokens = tvTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(tokens.spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionHeader(
        title = title,
        modifier = modifier,
        action = {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text(text = actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

// ============== 搜索框组件 ==============

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "搜索影视、演员、导演…",
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.size.componentHeightLg)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TVBoxIcons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(tokens.size.iconMd)
        )
        Spacer(modifier = Modifier.width(tokens.spacing.sm))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        )
        if (value.isNotEmpty() && onClear != null) {
            Spacer(modifier = Modifier.width(tokens.spacing.xs))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TVBoxIcons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ============== 开关组件 ==============

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(tokens.size.iconMd)
                )
            }
            Spacer(modifier = Modifier.width(tokens.spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(tokens.spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ============== 设置项列表 ==============

@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = tvTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(tokens.size.iconMd)
                )
            }
            Spacer(modifier = Modifier.width(tokens.spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(tokens.spacing.sm))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============== 设置分组 ==============

@Composable
fun SettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = tvTokens()
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = tokens.elevation.sm
        ) {
            Column { content() }
        }
    }
}
