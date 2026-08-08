package com.tvbox.core.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 布局模式
 *
 * 根据屏幕尺寸自动选择，决定间距、列数、字号及是否启用焦点导航等显示策略。
 */
enum class LayoutMode {
    /** TV 大屏模式：大间距、大字体、启用焦点导航 */
    TV,

    /** 移动端模式：小间距、触摸适配 */
    MOBILE,

    /** 桌面小窗模式：中等间距 */
    DESKTOP
}

/**
 * 自适应布局配置
 *
 * @property mode 当前布局模式
 * @property gridColumns 网格列数
 * @property spacing 元素间距
 * @property cardWidth 卡片宽度
 * @property titleSize 标题字号
 * @property bodySize 正文字号
 * @property enableFocusNavigation 是否启用焦点导航
 * @property contentPadding 内容内边距
 */
@Stable
data class AdaptiveConfig(
    val mode: LayoutMode,
    val gridColumns: Int,
    val spacing: Dp,
    val cardWidth: Dp,
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val enableFocusNavigation: Boolean,
    val contentPadding: Dp
)

/**
 * 根据可用宽度计算自适应布局配置
 *
 * - 宽度 >= 1280dp：TV 大屏模式（大间距、大字体、启用焦点导航）
 * - 宽度 >= 720dp：桌面模式（中等间距）
 * - 宽度 < 720dp：移动端模式（小间距、触摸适配）
 *
 * @param width 可用宽度
 */
fun computeAdaptiveConfig(width: Dp): AdaptiveConfig {
    return when {
        width >= 1280.dp -> AdaptiveConfig(
            mode = LayoutMode.TV,
            gridColumns = 6,
            spacing = 20.dp,
            cardWidth = 200.dp,
            titleSize = 20.sp,
            bodySize = 16.sp,
            enableFocusNavigation = true,
            contentPadding = 32.dp
        )
        width >= 720.dp -> AdaptiveConfig(
            mode = LayoutMode.DESKTOP,
            gridColumns = 4,
            spacing = 12.dp,
            cardWidth = 160.dp,
            titleSize = 16.sp,
            bodySize = 14.sp,
            enableFocusNavigation = false,
            contentPadding = 20.dp
        )
        else -> AdaptiveConfig(
            mode = LayoutMode.MOBILE,
            gridColumns = 3,
            spacing = 8.dp,
            cardWidth = 120.dp,
            titleSize = 14.sp,
            bodySize = 12.sp,
            enableFocusNavigation = false,
            contentPadding = 12.dp
        )
    }
}

/**
 * 根据当前屏幕尺寸记忆自适应布局配置
 *
 * 内部使用 [BoxWithConstraints] 读取实际约束宽度并据此选择布局模式。
 * 首帧使用默认配置，约束确定后自动 recomposition 切换到正确模式。
 */
@Composable
fun rememberAdaptiveConfig(): AdaptiveConfig {
    var config by remember { mutableStateOf(computeAdaptiveConfig(0.dp)) }
    BoxWithConstraints {
        config = computeAdaptiveConfig(maxWidth)
    }
    return config
}
