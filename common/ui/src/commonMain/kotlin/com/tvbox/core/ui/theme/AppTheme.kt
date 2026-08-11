package com.tvbox.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape

/**
 * TVBox 全局主题色板
 *
 * 定义深色与浅色两套 Material3 配色方案，配合 TV/移动端使用场景。
 */
@Immutable
object TVBoxColorScheme {
    // ===== 主题预设（6 款） =====
    data class ThemeSeed(
        val name: String,
        val primaryLight: Color,
        val primaryContainerLight: Color,
        val onPrimaryContainerLight: Color,
        val secondaryLight: Color,
        val primaryDark: Color,
        val primaryContainerDark: Color,
        val onPrimaryContainerDark: Color,
        val secondaryDark: Color,
    )

    val themePresets: List<ThemeSeed> = listOf(
        // 0 - 靛紫
        ThemeSeed(
            name = "靛紫",
            primaryLight = Color(0xFF4F46E5),
            primaryContainerLight = Color(0xFFE0E7FF),
            onPrimaryContainerLight = Color(0xFF1E1B4B),
            secondaryLight = Color(0xFF0EA5E9),
            primaryDark = Color(0xFF818CF8),
            primaryContainerDark = Color(0xFF3730A3),
            onPrimaryContainerDark = Color(0xFFC7D2FE),
            secondaryDark = Color(0xFF38BDF8)
        ),
        // 1 - 湖蓝
        ThemeSeed(
            name = "湖蓝",
            primaryLight = Color(0xFF0284C7),
            primaryContainerLight = Color(0xFFBAE6FD),
            onPrimaryContainerLight = Color(0xFF0C4A6E),
            secondaryLight = Color(0xFF06B6D4),
            primaryDark = Color(0xFF38BDF8),
            primaryContainerDark = Color(0xFF0369A1),
            onPrimaryContainerDark = Color(0xFFE0F2FE),
            secondaryDark = Color(0xFF22D3EE)
        ),
        // 2 - 樱粉
        ThemeSeed(
            name = "樱粉",
            primaryLight = Color(0xFFDB2777),
            primaryContainerLight = Color(0xFFFBCFE8),
            onPrimaryContainerLight = Color(0xFF831843),
            secondaryLight = Color(0xFFF472B6),
            primaryDark = Color(0xFFF472B6),
            primaryContainerDark = Color(0xFFBE185D),
            onPrimaryContainerDark = Color(0xFFFCE7F3),
            secondaryDark = Color(0xFFF9A8D4)
        ),
        // 3 - 森绿
        ThemeSeed(
            name = "森绿",
            primaryLight = Color(0xFF059669),
            primaryContainerLight = Color(0xFFA7F3D0),
            onPrimaryContainerLight = Color(0xFF064E3B),
            secondaryLight = Color(0xFF10B981),
            primaryDark = Color(0xFF34D399),
            primaryContainerDark = Color(0xFF047857),
            onPrimaryContainerDark = Color(0xFFD1FAE5),
            secondaryDark = Color(0xFF6EE7B7)
        ),
        // 4 - 暖橙
        ThemeSeed(
            name = "暖橙",
            primaryLight = Color(0xFFEA580C),
            primaryContainerLight = Color(0xFFFED7AA),
            onPrimaryContainerLight = Color(0xFF7C2D12),
            secondaryLight = Color(0xFFF59E0B),
            primaryDark = Color(0xFFFB923C),
            primaryContainerDark = Color(0xFFC2410C),
            onPrimaryContainerDark = Color(0xFFEDD5BB),
            secondaryDark = Color(0xFFFBBF24)
        ),
        // 5 - 朱砂
        ThemeSeed(
            name = "朱砂",
            primaryLight = Color(0xFFDC2626),
            primaryContainerLight = Color(0xFFFECACA),
            onPrimaryContainerLight = Color(0xFF7F1D1D),
            secondaryLight = Color(0xFFEF4444),
            primaryDark = Color(0xFFF87171),
            primaryContainerDark = Color(0xFFB91C1C),
            onPrimaryContainerDark = Color(0xFFFEE2E2),
            secondaryDark = Color(0xFFFCA5A5)
        )
    )

    // ===== 品牌主色（沉稳影视深色主题） =====
    val Brand = Color(0xFF6366F1)          // 靛紫主色，强调选中/高亮
    val BrandDim = Color(0xFF818CF8)
    val BrandContainer = Color(0xFF312E81) // 靛紫深容器
    val OnBrandContainer = Color(0xFFC7D2FE)

    // ===== 浅色主题 =====
    val PrimaryLight = Color(0xFF4F46E5)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFE0E7FF)
    val OnPrimaryContainerLight = Color(0xFF1E1B4B)
    val SecondaryLight = Color(0xFF0EA5E9)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val TertiaryLight = Color(0xFFF59E0B)
    val BackgroundLight = Color(0xFFF8FAFC)
    val OnBackgroundLight = Color(0xFF0F172A)
    val SurfaceLight = Color(0xFFFFFFFF)
    val OnSurfaceLight = Color(0xFF0F172A)
    val SurfaceVariantLight = Color(0xFFF1F5F9)
    val OnSurfaceVariantLight = Color(0xFF475569)
    val SurfaceContainerHighestLight = Color(0xFFE2E8F0)
    val OutlineLight = Color(0xFFCBD5E1)
    val OutlineVariantLight = Color(0xFFE2E8F0)
    val ErrorLight = Color(0xFFDC2626)
    val OnErrorLight = Color(0xFFFFFFFF)

    // ===== 深色主题（影视APP主用） =====
    val PrimaryDark = Color(0xFF818CF8)
    val OnPrimaryDark = Color(0xFF1E1B4B)
    val PrimaryContainerDark = Color(0xFF3730A3)
    val OnPrimaryContainerDark = Color(0xFFC7D2FE)
    val SecondaryDark = Color(0xFF38BDF8)
    val OnSecondaryDark = Color(0xFF082F49)
    val TertiaryDark = Color(0xFFFBBF24)
    val BackgroundDark = Color(0xFF0A0A0F)          // 深蓝黑背景
    val OnBackgroundDark = Color(0xFFE2E8F0)
    val SurfaceDark = Color(0xFF121218)             // 深色卡片
    val OnSurfaceDark = Color(0xFFE2E8F0)
    val SurfaceVariantDark = Color(0xFF1E1E2E)
    val OnSurfaceVariantDark = Color(0xFF94A3B8)
    val SurfaceContainerHighestDark = Color(0xFF27273A)
    val OutlineDark = Color(0xFF334155)
    val OutlineVariantDark = Color(0xFF1E293B)
    val ErrorDark = Color(0xFFF87171)
    val OnErrorDark = Color(0xFF450A0A)
    val ScrimDark = Color(0xAA000000)

    // ===== 语义增强色 =====
    val SuccessLight = Color(0xFF10B981)
    val SuccessDark = Color(0xFF34D399)
    val WarningLight = Color(0xFFF59E0B)
    val WarningDark = Color(0xFFFBBF24)

    val lightScheme: ColorScheme = lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        secondary = SecondaryLight,
        onSecondary = OnSecondaryLight,
        tertiary = TertiaryLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        error = ErrorLight,
        onError = OnErrorLight
    )

    val darkScheme: ColorScheme = darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = SecondaryDark,
        onSecondary = OnSecondaryDark,
        tertiary = TertiaryDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        error = ErrorDark,
        onError = OnErrorDark,
        scrim = ScrimDark
    )

    /** 根据主题种子索引构建浅色配色 */
    fun lightSchemeFor(seedIndex: Int): ColorScheme {
        val seed = themePresets.getOrNull(seedIndex.coerceIn(0, themePresets.size - 1)) ?: themePresets[0]
        return lightColorScheme(
            primary = seed.primaryLight,
            onPrimary = Color.White,
            primaryContainer = seed.primaryContainerLight,
            onPrimaryContainer = seed.onPrimaryContainerLight,
            secondary = seed.secondaryLight,
            onSecondary = Color.White,
            tertiary = TertiaryLight,
            background = BackgroundLight,
            onBackground = OnBackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = OnSurfaceVariantLight,
            surfaceContainerHighest = SurfaceContainerHighestLight,
            outline = OutlineLight,
            outlineVariant = OutlineVariantLight,
            error = ErrorLight,
            onError = OnErrorLight
        )
    }

    /** 根据主题种子索引构建深色配色 */
    fun darkSchemeFor(seedIndex: Int): ColorScheme {
        val seed = themePresets.getOrNull(seedIndex.coerceIn(0, themePresets.size - 1)) ?: themePresets[0]
        return darkColorScheme(
            primary = seed.primaryDark,
            onPrimary = seed.onPrimaryContainerDark,
            primaryContainer = seed.primaryContainerDark,
            onPrimaryContainer = seed.onPrimaryContainerDark,
            secondary = seed.secondaryDark,
            onSecondary = Color(0xFF082F49),
            tertiary = TertiaryDark,
            background = BackgroundDark,
            onBackground = OnBackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = OnSurfaceVariantDark,
            surfaceContainerHighest = SurfaceContainerHighestDark,
            outline = OutlineDark,
            outlineVariant = OutlineVariantDark,
            error = ErrorDark,
            onError = OnErrorDark,
            scrim = ScrimDark
        )
    }
}

/**
 * 设计系统尺寸令牌（间距、圆角、阴影强度等）
 */
@Immutable
data class TVBoxSpacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxl: androidx.compose.ui.unit.Dp = 32.dp,
    val xxxl: androidx.compose.ui.unit.Dp = 48.dp,
)

@Immutable
data class TVBoxRadius(
    val sm: Shape = RoundedCornerShape(6.dp),
    val md: Shape = RoundedCornerShape(12.dp),
    val lg: Shape = RoundedCornerShape(16.dp),
    val xl: Shape = RoundedCornerShape(24.dp),
    val full: Shape = CircleShape,
)

@Immutable
data class TVBoxElevation(
    val sm: androidx.compose.ui.unit.Dp = 2.dp,
    val md: androidx.compose.ui.unit.Dp = 6.dp,
    val lg: androidx.compose.ui.unit.Dp = 12.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
)

@Immutable
data class TVBoxSize(
    val iconSm: androidx.compose.ui.unit.Dp = 16.dp,
    val iconMd: androidx.compose.ui.unit.Dp = 24.dp,
    val iconLg: androidx.compose.ui.unit.Dp = 32.dp,
    val componentHeightSm: androidx.compose.ui.unit.Dp = 36.dp,
    val componentHeightMd: androidx.compose.ui.unit.Dp = 48.dp,
    val componentHeightLg: androidx.compose.ui.unit.Dp = 56.dp,
    val componentHeightXl: androidx.compose.ui.unit.Dp = 64.dp,
)

@Immutable
data class TVBoxTokens(
    val spacing: TVBoxSpacing = TVBoxSpacing(),
    val radius: TVBoxRadius = TVBoxRadius(),
    val elevation: TVBoxElevation = TVBoxElevation(),
    val size: TVBoxSize = TVBoxSize(),
)

val LocalTVBoxTokens = staticCompositionLocalOf { TVBoxTokens() }
val LocalTVBoxIsDark = staticCompositionLocalOf { true }

/**
 * TVBox 全局字体配置
 */
object TVBoxTypography {
    val typography: Typography = Typography(
        displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
        displaySmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
    )
}

/**
 * TVBox Shapes
 */
object TVBoxShapes {
    val shapes: Shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
}

/**
 * 应用主题 Composable
 */
@Composable
fun AppTheme(
    isDark: Boolean = true,
    themeSeed: Int = 0,
    content: @Composable () -> Unit
) {
    val actualDark = isDark
    val seed = themeSeed.coerceIn(0, TVBoxColorScheme.themePresets.size - 1)
    val colorScheme = if (actualDark) TVBoxColorScheme.darkSchemeFor(seed) else TVBoxColorScheme.lightSchemeFor(seed)
    CompositionLocalProvider(
        LocalTVBoxTokens provides TVBoxTokens(),
        LocalTVBoxIsDark provides actualDark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TVBoxTypography.typography,
            shapes = TVBoxShapes.shapes,
            content = content
        )
    }
}

/** 便捷扩展：从 CompositionLocal 获取 tokens */
@Stable
@Composable
fun tvTokens(): TVBoxTokens = LocalTVBoxTokens.current
