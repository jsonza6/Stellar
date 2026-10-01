package roro.stellar.manager.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用根主题。
 *
 * 使用 Miuix（HyperOS / MIUI 设计语言）作为唯一的主题与组件来源。整体界面风格参考
 * SukiSU 管理器：底部导航 + 分组卡片 + 状态标签。
 *
 * [ThemeMode] 映射到 Miuix 的 [ColorSchemeMode]：开启动态取色时使用 Monet 变体
 * （Android 12+ 取系统壁纸色），否则回退到 Miuix 内置的浅色/深色配色。
 */
@Composable
fun StellarTheme(
    themeMode: ThemeMode = ThemePreferences.themeMode.value,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }

    val controller = remember(themeMode, dynamicColor) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                ThemeMode.LIGHT ->
                    if (dynamicColor) ColorSchemeMode.MonetLight else ColorSchemeMode.Light

                ThemeMode.DARK ->
                    if (dynamicColor) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark

                ThemeMode.AUTO ->
                    if (dynamicColor) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
            }
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 边到边模式下状态栏背景由 enableEdgeToEdge 处理，这里只同步图标明暗。
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MiuixTheme(controller = controller, content = content)
}
