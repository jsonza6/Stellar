package roro.stellar.manager.ui.features.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import roro.stellar.Stellar
import roro.stellar.manager.R
import roro.stellar.manager.compat.BuildUtils.atLeast30
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.ui.components.StellarDialog
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.util.EnvironmentUtils
import roro.stellar.manager.util.UserHandleCompat
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HomeScreen(
    scrollBehavior: ScrollBehavior,
    homeViewModel: HomeViewModel,
    onNavigateToStarter: (isRoot: Boolean, host: String?, port: Int, hasSecureSettings: Boolean) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val serviceStatusResource by homeViewModel.serviceStatus.observeAsState()
    val serviceStatus = serviceStatusResource?.data

    val isRunning = serviceStatus?.isRunning ?: false
    val isRoot = serviceStatus?.uid == 0
    val isPrimaryUser = UserHandleCompat.myUserId() == 0
    val hasRoot = EnvironmentUtils.isRooted()

    var showPowerDialog by remember { mutableStateOf(false) }
    var showAdbCommandDialog by remember { mutableStateOf(false) }
    var showAdbRestrictedFeaturesDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            StandardLargeTopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Miuix 的滚动手感：越界回弹 + 滚到底部/顶部的轻触反馈。
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status") {
                ServerStatusCard(
                    isRunning = isRunning,
                    isRoot = isRoot,
                    apiVersion = serviceStatus?.apiVersion ?: 0,
                    onStopClick = { showPowerDialog = true }
                )
            }

            if (isRunning && !serviceStatus.permission) {
                item(key = "restricted_hint") {
                    AdbRestrictedHintCard(
                        onViewClick = { showAdbRestrictedFeaturesDialog = true }
                    )
                }
            }

            if (isPrimaryUser) {
                item(key = "start_title") {
                    SmallTitle(text = stringResource(R.string.home_start_section))
                }

                if (hasRoot) {
                    item(key = "root") {
                        StartRootCard(
                            isRestart = isRunning && isRoot,
                            onStartClick = { onNavigateToStarter(true, null, 0, false) }
                        )
                    }
                }

                if (atLeast30 || EnvironmentUtils.getAdbTcpPort() > 0) {
                    item(key = "wireless") {
                        StartWirelessAdbCard(
                            onStartClick = { onNavigateToStarter(false, "127.0.0.1", 0, false) }
                        )
                    }
                }

                item(key = "wired") {
                    StartWiredAdbCard(
                        onButtonClick = { showAdbCommandDialog = true }
                    )
                }

                if (!hasRoot) {
                    item(key = "root_disabled") {
                        StartRootCard(
                            isRestart = isRunning && isRoot,
                            onStartClick = {
                                Toast.makeText(context, context.getString(R.string.no_root_permission), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPowerDialog) {
        StellarDialog(
            onDismissRequest = { showPowerDialog = false },
            title = stringResource(R.string.stop_service),
            confirmText = stringResource(R.string.stop),
            dismissText = stringResource(R.string.restart),
            onConfirm = {
                if (Stellar.pingBinder()) {
                    runCatching { Stellar.exit() }
                }
                showPowerDialog = false
            },
            onDismiss = {
                homeViewModel.restartService()
                showPowerDialog = false
            }
        ) {
            Text(
                text = stringResource(R.string.stop_service_message),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    if (showAdbCommandDialog) {
        StellarDialog(
            onDismissRequest = { showAdbCommandDialog = false },
            title = stringResource(R.string.view_command),
            confirmText = stringResource(R.string.copy),
            dismissText = stringResource(R.string.close),
            onConfirm = {
                ClipboardUtils.put(context, Starter.adbCommand)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAdbCommandDialog = false }
        ) {
            Text(
                text = Starter.adbCommand,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    if (showAdbRestrictedFeaturesDialog) {
        StellarDialog(
            onDismissRequest = { showAdbRestrictedFeaturesDialog = false },
            title = stringResource(R.string.adb_restricted_features_title),
            confirmText = stringResource(R.string.close),
            onConfirm = { showAdbRestrictedFeaturesDialog = false },
            showDismissButton = false
        ) {
            RestrictedFeatureList(
                features = serviceStatus?.featureStates ?: emptyList()
            )
        }
    }
}
