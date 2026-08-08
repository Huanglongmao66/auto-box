package com.tvbox.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * TVBox 全局主题色板
 *
 * 定义深色与浅色两套 Material3 配色方案，覆盖主色、次色、背景、表面等关键色值。
 */
object TVBoxColorScheme {
    // ===== 基础色值（浅色） =====
    val Primary = Color(0xFF1976D2)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFBBDEFB)
    val OnPrimaryContainer = Color(0xFF0D47A1)
    val Secondary = Color(0xFF03A9F4)
    val OnSecondary = Color(0xFFFFFFFF)
    val Background = Color(0xFFF5F5F5)
    val OnBackground = Color(0xFF212121)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF212121)
    val SurfaceVariant = Color(0xFFE0E0E0)
    val OnSurfaceVariant = Color(0xFF616161)
    val Error = Color(0xFFD32F2F)
    val OnError = Color(0xFFFFFFFF)

    // ===== 深色主题色值 =====
    val PrimaryDark = Color(0xFF82B1FF)
    val OnPrimaryDark = Color(0xFF000000)
    val PrimaryContainerDark = Color(0xFF1565C0)
    val OnPrimaryContainerDark = Color(0xFFBBDEFB)
    val BackgroundDark = Color(0xFF121212)
    val OnBackgroundDark = Color(0xFFEEEEEE)
    val SurfaceDark = Color(0xFF1E1E1E)
    val OnSurfaceDark = Color(0xFFEEEEEE)
    val SurfaceVariantDark = Color(0xFF2C2C2C)
    val OnSurfaceVariantDark = Color(0xFFBDBDBD)

    /** 浅色配色方案 */
    val lightScheme: ColorScheme = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        secondary = Secondary,
        onSecondary = OnSecondary,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        error = Error,
        onError = OnError
    )

    /** 深色配色方案 */
    val darkScheme: ColorScheme = darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = Secondary,
        onSecondary = OnSecondary,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        error = Error,
        onError = OnError
    )
}

/**
 * TVBox 全局字体配置
 *
 * 基于 Material3 Typography 定义各层级文字样式，TV 大屏场景下整体字号偏大。
 */
object TVBoxTypography {
    /** 全局 Material3 字体配置 */
    val typography: Typography = Typography(
        displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
        displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    )
}

/**
 * 应用主题 Composable
 *
 * 根据 [isDark] 切换深色/浅色配色方案，并应用全局字体配置。
 *
 * @param isDark 是否使用深色主题，默认跟随系统
 * @param content 子内容
 */
@Composable
fun AppTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) {
        TVBoxColorScheme.darkScheme
    } else {
        TVBoxColorScheme.lightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TVBoxTypography.typography,
        content = content
    )
}
