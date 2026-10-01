package roro.stellar.manager

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import roro.stellar.Stellar
import roro.stellar.StellarApiConstants
import roro.stellar.manager.authorization.AuthorizationManager
import roro.stellar.manager.authorization.RequestPermissionActivity
import roro.stellar.manager.domain.apps.AppType
import roro.stellar.manager.domain.apps.AppsViewModel
import roro.stellar.manager.domain.apps.appsViewModel
import roro.stellar.manager.ui.components.AdaptiveLayoutProvider
import roro.stellar.manager.ui.features.apps.AppsScreen
import roro.stellar.manager.ui.features.home.HomeScreen
import roro.stellar.manager.ui.features.home.HomeViewModel
import roro.stellar.manager.ui.features.manager.ManagerActivity
import roro.stellar.manager.ui.features.passwordswitch.PasswordSwitchScreen
import roro.stellar.manager.ui.features.settings.SettingsScreen
import roro.stellar.manager.ui.features.terminal.TerminalScreen
import roro.stellar.manager.ui.navigation.components.LocalNavigationState
import roro.stellar.manager.ui.navigation.components.LocalTopAppBarState
import roro.stellar.manager.ui.navigation.components.NavigationState
import roro.stellar.manager.ui.navigation.components.StandardBottomNavigation
import roro.stellar.manager.ui.navigation.components.StandardNavigationRail
import roro.stellar.manager.ui.navigation.components.TopAppBarProvider
import roro.stellar.manager.ui.navigation.routes.MainScreen
import roro.stellar.manager.ui.navigation.safePopBackStack
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.ui.theme.StartPage
import roro.stellar.manager.util.BackgroundVisibilityUtils

class MainActivity : ComponentActivity() {

    private companion object {
        const val STATE_SOURCE_PACKAGE = "source_package"
    }

    private var pendingSourcePackage: String? = null
    private var sourceAuthorizationStarted = false

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        checkServerStatus()
        handlePendingSourceApp()
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        checkServerStatus()
    }

    private val homeModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        pendingSourcePackage = savedInstanceState?.getString(STATE_SOURCE_PACKAGE)
        rememberSourceApp()
        BackgroundVisibilityUtils.setHidden(
            this,
            StellarSettings.getPreferences().getBoolean(StellarSettings.HIDE_BACKGROUND, false)
        )
        
        enableEdgeToEdge()
        
        setContent {
            val themeMode = ThemePreferences.themeMode.value

            StellarTheme(themeMode = themeMode) {
                TopAppBarProvider {
                    MainScreenContent(homeViewModel = homeModel)
                }
            }
        }

        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        
        checkServerStatus()

        handlePendingSourceApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        rememberSourceApp()
        handlePendingSourceApp()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingSourcePackage?.let { outState.putString(STATE_SOURCE_PACKAGE, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        checkServerStatus()
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    private fun rememberSourceApp() {
        val sourcePackage = referrer?.host?.takeIf {
            it.isNotBlank() && it != packageName
        } ?: return

        pendingSourcePackage = sourcePackage
        sourceAuthorizationStarted = false
    }

    private fun handlePendingSourceApp() {
        val sourcePackage = pendingSourcePackage ?: return
        if (!Stellar.pingBinder() || sourceAuthorizationStarted) return

        val packages = runCatching { AuthorizationManager.getPackages() }.getOrElse { return }
        val packageInfo = packages.firstOrNull { it.packageName == sourcePackage }
        if (packageInfo == null) {
            clearSourceApp()
            return
        }
        val appType = AuthorizationManager.getAppType(packageInfo)
        val permission =
            if (appType == AppType.SHIZUKU) "shizuku" else StellarApiConstants.PERMISSION_STELLAR
        val grantedFlag = if (appType == AppType.SHIZUKU) 2 else AuthorizationManager.FLAG_GRANTED
        val uid = packageInfo.applicationInfo?.uid ?: run {
            clearSourceApp()
            return
        }
        val currentFlag = runCatching { Stellar.getFlagForUid(uid, permission) }.getOrElse { return }
        val isGranted = currentFlag == grantedFlag

        if (isGranted) {
            clearSourceApp()
        } else {
            // 本分支不提供客户端授权：被其他应用拉起时不再弹出授权界面，
            // 直接清掉 referrer 状态。第三方应用请使用官方 Stellar。
            clearSourceApp()
        }
    }

    private fun clearSourceApp() {
        pendingSourcePackage = null
        sourceAuthorizationStarted = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    homeViewModel: HomeViewModel
) {
    val topAppBarState = LocalTopAppBarState.current!!
    val navController = rememberNavController()

    // Fixed start destination: the start-page preference lived in the removed Settings screen,
    // and MainScreen now exposes only Home and PasswordSwitch.
    var selectedIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val context = navController.context

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        if (navController.previousBackStackEntry == null) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, context.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
            }
        } else {
            navController.safePopBackStack()
        }
    }

    val onNavigationItemClick: (Int) -> Unit = { index ->
        if (selectedIndex != index) {
            selectedIndex = index
            val route = MainScreen.entries[index].route
            navController.navigate(route) {
                popUpTo(0) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    val navigationState = NavigationState(
        selectedIndex = selectedIndex,
        onItemClick = onNavigationItemClick
    )

    val navHostContent: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = MainScreen.Home.route,
            modifier = modifier,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            navigation(
                startDestination = "home",
                route = MainScreen.Home.route
            ) {
                composable("home") {
                    HomeScreen(
                        topAppBarState = topAppBarState,
                        homeViewModel = homeViewModel,
                        onNavigateToStarter = { isRoot, host, port, hasSecureSettings ->
                            context.startActivity(ManagerActivity.createStarterIntent(context, isRoot, host, port, hasSecureSettings))
                        }
                    )
                }
            }

            navigation(
                startDestination = "password_switch",
                route = MainScreen.PasswordSwitch.route
            ) {
                composable("password_switch") {
                    PasswordSwitchScreen(
                        topAppBarState = topAppBarState
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalNavigationState provides navigationState) {
        AdaptiveLayoutProvider {
            if (isLandscape) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                ) {
                    StandardNavigationRail(
                        selectedIndex = selectedIndex,
                        onItemClick = onNavigationItemClick
                    )
                    navHostContent(Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            StandardBottomNavigation(
                                selectedIndex = selectedIndex,
                                onItemClick = onNavigationItemClick
                            )
                        },
                        contentWindowInsets = WindowInsets.navigationBars
                    ) {
                        navHostContent(Modifier.fillMaxSize().padding(it))
                    }
                }
            }
        }
    }
}
