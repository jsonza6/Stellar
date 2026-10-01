package roro.stellar.manager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 的调色板里没有 warning（琥珀色）语义色，而启动流程需要表达「端口未检测到 /
 * 需要配对」这类**非错误**的注意状态，因此由应用自定义，命名沿用 Miuix 的
 * `*Container` / `on*Container` 成对约定。
 *
 * 深浅判定直接从当前 Miuix 主题的背景亮度推导，这样 `ThemeMode` 覆盖系统深色时也正确。
 */
object StellarColors {

    private val warningContainerLight = Color(0xFFFFF3E0)
    private val onWarningContainerLight = Color(0xFF7A4F00)
    private val warningAccentLight = Color(0xFFE08A00)

    private val warningContainerDark = Color(0xFF33270D)
    private val onWarningContainerDark = Color(0xFFF5C77E)
    private val warningAccentDark = Color(0xFFF0B354)

    private val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = MiuixTheme.colorScheme.background.luminance() < 0.5f

    val warningContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) warningContainerDark else warningContainerLight

    val onWarningContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) onWarningContainerDark else onWarningContainerLight

    val warningAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = if (isDark) warningAccentDark else warningAccentLight
}
