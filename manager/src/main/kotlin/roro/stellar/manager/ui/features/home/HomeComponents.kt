package roro.stellar.manager.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import roro.stellar.manager.R
import roro.stellar.manager.model.FeatureAvailability
import roro.stellar.manager.model.RestrictedFeature
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 圆角方块图标底座，HyperOS 风格的状态/入口图标容器。 */
@Composable
private fun LeadingIconBadge(
    icon: ImageVector,
    tint: Color,
    container: Color,
    size: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.6f))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * 服务状态卡：运行中显示为高亮色卡片，并附带版本与运行模式。
 */
@Composable
fun ServerStatusCard(
    isRunning: Boolean,
    isRoot: Boolean,
    apiVersion: Int,
    onStopClick: () -> Unit
) {
    val container = if (isRunning) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.errorContainer
    val onContainer = if (isRunning) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LeadingIconBadge(
                    icon = if (isRunning) MiuixIcons.Ok else MiuixIcons.Info,
                    tint = onContainer,
                    container = onContainer.copy(alpha = 0.14f),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.service_status),
                        style = MiuixTheme.textStyles.title3,
                        color = onContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isRunning) stringResource(R.string.service_running) else stringResource(R.string.service_not_running),
                        style = MiuixTheme.textStyles.footnote1,
                        color = onContainer.copy(alpha = 0.72f),
                    )
                }

                if (isRunning) {
                    IconButton(onClick = onStopClick) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.stop_service),
                            tint = onContainer,
                        )
                    }
                }
            }

            if (isRunning) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = onContainer.copy(alpha = 0.18f))
                Spacer(Modifier.height(12.dp))

                InfoRow(
                    label = stringResource(R.string.version),
                    value = "${apiVersion / 100}.${(apiVersion % 100) / 10}.${apiVersion % 10}",
                    contentColor = onContainer,
                )
                InfoRow(
                    label = stringResource(R.string.run_mode),
                    value = if (isRoot) "Root" else "ADB",
                    contentColor = onContainer,
                )
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor.copy(alpha = 0.72f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
        )
    }
}

/**
 * ADB 受限能力提示卡。
 */
@Composable
fun AdbRestrictedHintCard(
    onViewClick: () -> Unit
) {
    val container = MiuixTheme.colorScheme.errorContainer
    val onContainer = MiuixTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = container, contentColor = onContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LeadingIconBadge(
                icon = MiuixIcons.Blocklist,
                tint = onContainer,
                container = onContainer.copy(alpha = 0.14f),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.adb_restricted_hint_title),
                    style = MiuixTheme.textStyles.subtitle,
                    color = onContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.adb_restricted_hint_message),
                    style = MiuixTheme.textStyles.footnote1,
                    color = onContainer.copy(alpha = 0.78f),
                )
            }

            Button(
                onClick = onViewClick,
            ) {
                Text(stringResource(R.string.view))
            }
        }
    }
}

@Composable
fun RestrictedFeatureList(
    features: List<FeatureAvailability>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (features.isEmpty()) {
            Text(
                text = stringResource(R.string.no_restricted_features),
                modifier = Modifier.padding(16.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            features.forEachIndexed { index, feature ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                RestrictedFeatureRow(feature)
            }
        }
    }
}

@Composable
private fun RestrictedFeatureRow(
    feature: FeatureAvailability
) {
    val available = feature.available

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(feature.feature.titleRes()),
            summary = if (available) stringResource(R.string.feature_status_available)
            else stringResource(R.string.feature_status_restricted),
            startAction = {
                Icon(
                    imageVector = if (available) MiuixIcons.Ok else MiuixIcons.Blocklist,
                    contentDescription = null,
                    tint = if (available) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                )
            },
        )

        feature.children.forEach { child ->
            BasicComponent(
                modifier = Modifier.padding(start = 24.dp),
                title = stringResource(child.feature.titleRes()),
                summary = stringResource(R.string.feature_status_restricted),
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.Blocklist,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

private fun RestrictedFeature.titleRes(): Int = when (this) {
    RestrictedFeature.SHELL_ID_COMMAND -> R.string.feature_shell_id_command
    RestrictedFeature.PROPERTY_READ_COMMAND -> R.string.feature_property_read_command
    RestrictedFeature.SETTINGS_READ_COMMAND -> R.string.feature_settings_read_command
    RestrictedFeature.PACKAGE_LIST_COMMAND -> R.string.feature_package_list_command
    RestrictedFeature.SERVICE_LIST_COMMAND -> R.string.feature_service_list_command
    RestrictedFeature.PROCESS_LIST_COMMAND -> R.string.feature_process_list_command
    RestrictedFeature.FILESYSTEM_READ_COMMAND -> R.string.feature_filesystem_read_command
    RestrictedFeature.SELINUX_STATUS_COMMAND -> R.string.feature_selinux_status_command
    RestrictedFeature.APPOPS_MANAGE -> R.string.feature_appops_manage
    RestrictedFeature.RUNTIME_PERMISSION_MANAGE -> R.string.feature_runtime_permission_manage
    RestrictedFeature.SECURE_SETTINGS_WRITE -> R.string.feature_secure_settings_write
}

/**
 * 启动入口卡片：图标 + 标题 + 说明 + 操作按钮。
 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LeadingIconBadge(
                icon = icon,
                tint = MiuixTheme.colorScheme.primary,
                container = MiuixTheme.colorScheme.primaryContainer,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            Button(
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun StartRootCard(
    isRestart: Boolean,
    onStartClick: () -> Unit = {}
) {
    ActionCard(
        icon = MiuixIcons.Unlock,
        title = if (isRestart) stringResource(R.string.root_restart) else stringResource(R.string.root_start),
        subtitle = stringResource(R.string.root_start_subtitle),
        actionText = if (isRestart) stringResource(R.string.restart) else stringResource(R.string.start),
        onAction = onStartClick,
    )
}

@Composable
fun StartWirelessAdbCard(
    onStartClick: () -> Unit
) {
    ActionCard(
        icon = MiuixIcons.SearchDevice,
        title = stringResource(R.string.wireless_debugging),
        subtitle = stringResource(R.string.wireless_debugging_subtitle),
        actionText = stringResource(R.string.start),
        onAction = onStartClick,
    )
}

@Composable
fun StartWiredAdbCard(
    onButtonClick: () -> Unit
) {
    ActionCard(
        icon = MiuixIcons.Link,
        title = stringResource(R.string.wired_adb),
        subtitle = stringResource(R.string.wired_adb_subtitle),
        actionText = stringResource(R.string.view),
        onAction = onButtonClick,
    )
}
