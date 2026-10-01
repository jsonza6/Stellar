package roro.stellar.manager

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import roro.stellar.manager.ui.components.AdaptiveLayoutProvider
import roro.stellar.manager.ui.features.home.HomeScreen
import roro.stellar.manager.ui.features.home.HomeViewModel
import roro.stellar.manager.ui.features.manager.ManagerActivity
import roro.stellar.manager.ui.features.passwordswitch.PasswordSwitchScreen
import roro.stellar.manager.ui.navigation.components.LocalNavigationState
import roro.stellar.manager.ui.navigation.components.LocalTopAppBarScrollBehavior
import roro.stellar.manager.ui.navigation.components.NavigationState
import roro.stellar.manager.ui.navigation.components.StandardBottomNavigation
import roro.stellar.manager.ui.navigation.components.StandardNavigationRail
import roro.stellar.manager.ui.navigation.components.TopAppBarProvider
import roro.stellar.manager.ui.navigation.routes.MainScreen
import roro.stellar.manager.ui.navigation.safePopBackStack
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.util.BackgroundVisibilityUtils
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单一用途管理器：启动特权服务，并驱动密码管理器切换。
 *
 * 界面采用 Miuix（HyperOS / MIUI）设计语言，整体结构参考 SukiSU 管理器：
 * 竖屏底部导航 / 横屏侧边导航 + 分组卡片。
 *
 * 本分支不提供客户端授权，因此不再处理来自第三方应用的 referrer，也不再拉起授权界面。
 */
class MainActivity : ComponentActivity() {

    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        checkServerStatus()
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        checkServerStatus()
    }

    private val homeModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

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
    }

    override fun onResume() {
        super.onResume()
        checkServerStatus()
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
    }
}

@Composable
private fun MainScreenContent(
    homeViewModel: HomeViewModel
) {
    val scrollBehavior = LocalTopAppBarScrollBehavior.current!!
    val navController = rememberNavController()

    // 固定起始页：MainScreen 只暴露 Home 与 PasswordSwitch 两个页面。
    var selectedIndex by remember { mutableIntStateOf(0) }

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
                        scrollBehavior = scrollBehavior,
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
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalNavigationState provides navigationState) {
        AdaptiveLayoutProvider {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface)
                ) {
                    StandardNavigationRail(
                        selectedIndex = selectedIndex,
                        onItemClick = onNavigationItemClick
                    )
                    navHostContent(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        StandardBottomNavigation(
                            selectedIndex = selectedIndex,
                            onItemClick = onNavigationItemClick
                        )
                    }
                ) { padding ->
                    navHostContent(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                }
            }
        }
    }
}
