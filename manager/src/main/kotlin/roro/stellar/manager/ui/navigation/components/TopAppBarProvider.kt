package roro.stellar.manager.ui.navigation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/**
 * 顶栏滚动行为。由 [TopAppBarProvider] 统一创建，页面通过
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` 与顶栏联动。
 */
val LocalTopAppBarScrollBehavior = compositionLocalOf<ScrollBehavior?> { null }

data class NavigationState(
    val selectedIndex: Int,
    val onItemClick: (Int) -> Unit
)

val LocalNavigationState = compositionLocalOf<NavigationState?> { null }

@Composable
fun TopAppBarProvider(
    content: @Composable () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()

    CompositionLocalProvider(
        LocalTopAppBarScrollBehavior provides scrollBehavior
    ) {
        content()
    }
}
