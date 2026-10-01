package roro.stellar.manager.ui.navigation.routes

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Play
import roro.stellar.manager.R

/**
 * Destinations shown in the bottom bar / navigation rail.
 *
 * This fork keeps only the two pages it needs: starting the service and switching the password
 * manager.
 */
enum class MainScreen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    Home(
        route = "home_graph",
        labelRes = R.string.nav_home,
        icon = MiuixIcons.Play
    ),

    PasswordSwitch(
        route = "password_switch_graph",
        labelRes = R.string.nav_password_switch,
        icon = MiuixIcons.Lock
    )
}
