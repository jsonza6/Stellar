package roro.stellar.manager.ui.navigation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import roro.stellar.manager.ui.navigation.routes.MainScreen

/**
 * 底部导航（竖屏）。Miuix [NavigationBar] 自带选中态着色与按压反馈。
 */
@Composable
fun StandardBottomNavigation(
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    NavigationBar {
        MainScreen.entries.forEachIndexed { index, screen ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemClick(index) },
                icon = screen.icon,
                label = stringResource(screen.labelRes),
            )
        }
    }
}

/**
 * 侧边导航（横屏）。
 */
@Composable
fun StandardNavigationRail(
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(modifier = modifier) {
        MainScreen.entries.forEachIndexed { index, screen ->
            NavigationRailItem(
                selected = selectedIndex == index,
                onClick = { onItemClick(index) },
                icon = screen.icon,
                label = stringResource(screen.labelRes),
            )
        }
    }
}
