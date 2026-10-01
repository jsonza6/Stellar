package roro.stellar.manager.ui.navigation.routes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import roro.stellar.manager.R

/**
 * Destinations shown in the bottom bar / navigation rail.
 *
 * This fork keeps only the two pages it needs: starting the service and switching the password
 * manager. The Apps, Terminal and Settings screens still exist in the source tree because other
 * packages depend on symbols they declare (for example `CommandItem` and `AppType`), but they are
 * no longer reachable from the UI, so R8 drops them from a release build.
 */
enum class MainScreen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconFilled: ImageVector
) {
    Home(
        route = "home_graph",
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.PlayArrow,
        iconFilled = Icons.Filled.PlayArrow
    ),

    PasswordSwitch(
        route = "password_switch_graph",
        labelRes = R.string.nav_password_switch,
        icon = Icons.Outlined.Key,
        iconFilled = Icons.Filled.Key
    )
}
