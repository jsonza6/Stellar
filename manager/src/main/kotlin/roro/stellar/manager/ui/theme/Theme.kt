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
 * 使用 **Miuix 自带配色**（HyperOS / MIUI 设计语言），不要接 Monet：
 * `ColorSchemeMode.Monet*` 走的是系统 Material You 壁纸取色，会让界面看起来就是 MD3。
 *
 * Miuix 默认调色板（见 Miuix `Colors.kt`）：
 * - 浅色：背景 `#F7F7F7`、卡片纯白、主色 HyperOS 蓝 `#3482FF`
 * - 深色：背景纯黑、卡片 `#242424`、主色 `#277AF7`
 *
 * [ThemeMode] 只决定浅色 / 深色 / 跟随系统。
 */
@Composable
fun StellarTheme(
    themeMode: ThemeMode = ThemePreferences.themeMode.value,
    content: @Composable () -> Unit
) {
    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                ThemeMode.LIGHT -> ColorSchemeMode.Light
                ThemeMode.DARK -> ColorSchemeMode.Dark
                ThemeMode.AUTO -> ColorSchemeMode.System
            }
        )
    }

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
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
