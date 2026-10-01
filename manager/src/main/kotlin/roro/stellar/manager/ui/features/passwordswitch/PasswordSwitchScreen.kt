package roro.stellar.manager.ui.features.passwordswitch

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import roro.stellar.manager.R
import roro.stellar.manager.passwordswitch.ProviderEntry
import roro.stellar.manager.passwordswitch.ProviderKind
import roro.stellar.manager.passwordswitch.SettingKey
import roro.stellar.manager.passwordswitch.components
import roro.stellar.manager.ui.components.StellarDialog
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.text.DateFormat
import java.util.Date

@Composable
fun PasswordSwitchScreen(
    scrollBehavior: ScrollBehavior,
    viewModel: PasswordSwitchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var picker by remember { mutableStateOf<ProviderKind?>(null) }
    var confirmApply by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var rawExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StandardLargeTopAppBar(
                title = stringResource(R.string.password_switch_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = viewModel::reconnect,
                        enabled = !state.busy,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) {
                item(key = "progress") {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            item(key = "intro") {
                Text(
                    "让密码与通行密钥，交给你选择的应用。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            item(key = "status") {
                ConnectionCard(state)
            }

            if (state.message.isNotEmpty()) {
                item(key = "message") {
                    MessageCard(state)
                }
            }

            item(key = "credential") {
                ServiceSection(
                    title = "凭据提供者",
                    description = "用于密码与通行密钥的创建和登录",
                    value = summary(state.snapshot, SettingKey.PRIMARY, viewModel),
                    icon = state.current(SettingKey.PRIMARY, ProviderKind.CREDENTIAL)?.icon,
                    detail = state.enabledProviders(viewModel),
                    pending = state.credential?.let(viewModel::label),
                    enabled = !state.busy && state.scanned,
                    onChange = { picker = ProviderKind.CREDENTIAL },
                    onUndo = { viewModel.select(ProviderKind.CREDENTIAL, null) },
                ) {
                    if (state.credential != null) {
                        HorizontalDivider()
                        BasicComponent(
                            title = "保留其他已启用的凭据提供者",
                            summary = if (state.keepOthers) "只将所选应用设为首选。"
                            else "凭据提供者列表将仅保留所选服务。",
                            endActions = {
                                Switch(
                                    checked = state.keepOthers,
                                    onCheckedChange = viewModel::keepOthers,
                                    enabled = !state.busy,
                                )
                            },
                        )
                    }
                }
            }

            item(key = "autofill") {
                ServiceSection(
                    title = "自动填充服务",
                    description = "用于登录表单中的账号和密码填充",
                    value = summary(state.snapshot, SettingKey.AUTOFILL, viewModel),
                    icon = state.current(SettingKey.AUTOFILL, ProviderKind.AUTOFILL)?.icon,
                    detail = null,
                    pending = state.autofill?.let(viewModel::label),
                    enabled = !state.busy && state.scanned,
                    onChange = { picker = ProviderKind.AUTOFILL },
                    onUndo = { viewModel.select(ProviderKind.AUTOFILL, null) },
                )
            }

            item(key = "apply") {
                Column {
                    Button(
                        onClick = { confirmApply = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canApply,
                    ) {
                        Text("应用更改")
                    }
                    val hint = when {
                        state.busy -> null
                        state.connection != ConnectionState.READY -> "Stellar 服务运行并成功读取配置后，即可应用更改。"
                        state.credential == null && state.autofill == null -> "点按上方服务选择新的应用，确认后才会写入系统设置。"
                        else -> null
                    }
                    hint?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item(key = "more_title") {
                SmallTitle(text = "更多操作")
            }

            item(key = "more") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ActionRow(
                        title = "恢复上次配置",
                        summary = if (state.backup != null) "恢复到最近一次应用前的配置" else "暂无可恢复的配置",
                        enabled = state.connection == ConnectionState.READY &&
                            state.snapshot != null && state.backup != null && !state.busy,
                        onClick = { confirmRestore = true },
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    ActionRow(
                        title = "打开系统设置",
                        onClick = { openSystemSettings(context) },
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    ActionRow(
                        title = "系统配置详情",
                        summary = "查看三项系统设置的原始值",
                        endActions = {
                            Icon(
                                imageVector = if (rawExpanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { rawExpanded = !rawExpanded },
                    )
                    if (rawExpanded) {
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                        Text(
                            rawValues(state),
                            Modifier.padding(16.dp),
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item(key = "more_note") {
                Text(
                    "每次应用前保存配置，写入后重新读取核对。系统更新或其他应用仍可能改回设置，可随时刷新查看。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    val pickerKind = picker
    if (pickerKind != null) {
        ProviderPickerSheet(
            kind = pickerKind,
            state = state,
            currentLabel = summary(
                state.snapshot,
                if (pickerKind == ProviderKind.CREDENTIAL) SettingKey.PRIMARY else SettingKey.AUTOFILL,
                viewModel,
            ),
            onDismiss = { picker = null },
            onSelect = { value ->
                viewModel.select(pickerKind, value)
                picker = null
            },
        )
    }

    if (confirmApply) {
        StellarDialog(
            onDismissRequest = { confirmApply = false },
            title = "确认更改默认服务",
            confirmText = "确认应用",
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = state.canApply,
            onConfirm = {
                confirmApply = false
                viewModel.apply()
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.credential?.let { selected ->
                    ChangeLine("首选凭据提供者", summary(state.snapshot, SettingKey.PRIMARY, viewModel), viewModel.label(selected))
                    Text(
                        if (state.keepOthers) "保留其他已启用的凭据提供者。"
                        else "已启用凭据提供者列表将仅保留 ${viewModel.label(selected)}。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    when (state.providers.firstOrNull { it.component == selected }?.supportsPasskeys) {
                        false -> Text(
                            "这个服务没有声明支持通行密钥；只能按它实际支持的凭据类型使用。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                        )

                        null -> Text(
                            "无法确认这个服务的通行密钥支持情况。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                        )

                        true -> Unit
                    }
                }
                state.autofill?.let {
                    ChangeLine("自动填充服务", summary(state.snapshot, SettingKey.AUTOFILL, viewModel), viewModel.label(it))
                }
                Text(
                    "请仅选择你信任的密码管理器。未选择的项目保持原样，操作前的配置会保存在本机。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    if (confirmRestore) {
        StellarDialog(
            onDismissRequest = { confirmRestore = false },
            title = "恢复上次配置",
            confirmText = "确认恢复",
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                confirmRestore = false
                viewModel.restore()
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue("首选凭据提供者", summary(state.backup, SettingKey.PRIMARY, viewModel))
                LabeledValue("已启用凭据提供者", summary(state.backup, SettingKey.CREDENTIALS, viewModel))
                LabeledValue("自动填充服务", summary(state.backup, SettingKey.AUTOFILL, viewModel))
                Text(
                    "将恢复以上三项系统配置。如果原应用已卸载或停用，恢复配置不会使它重新可用。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: PasswordSwitchState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (state.connection == ConnectionState.READY) {
            BasicComponent(
                title = "Stellar 服务已连接",
                summary = "仅修改系统服务选择，不读取密码或通行密钥内容。",
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                },
                endActions = {
                    state.checkedAt?.let {
                        Text(
                            "读回于 ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                },
            )
            return@Card
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when (state.connection) {
                    ConnectionState.STOPPED -> "先启动 Stellar 服务"
                    else -> "正在连接 Stellar 服务…"
                },
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary,
            )
            Text(
                when (state.connection) {
                    ConnectionState.STOPPED ->
                        "在 Stellar 首页通过无线调试或 Root 启动服务，返回后点刷新。"
                    else -> "连接成功后会读取当前系统配置。"
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (state.connection == ConnectionState.CONNECTING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在启动特权服务…", style = MiuixTheme.textStyles.body2)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(state: PasswordSwitchState) {
    var expanded by rememberSaveable(state.message) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (state.error) MiuixTheme.colorScheme.errorContainer
            else MiuixTheme.colorScheme.secondaryContainer,
            contentColor = if (state.error) MiuixTheme.colorScheme.onErrorContainer
            else MiuixTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (state.error) MiuixIcons.Blocklist else MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(state.message, style = MiuixTheme.textStyles.body2)
            }
            if (state.detail.isNotEmpty()) {
                if (expanded) {
                    Text(
                        state.detail,
                        style = MiuixTheme.textStyles.footnote2,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    text = if (expanded) "收起详情" else "查看详情",
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun ServiceSection(
    title: String,
    description: String,
    value: String,
    icon: android.graphics.Bitmap?,
    detail: String?,
    pending: String?,
    enabled: Boolean,
    onChange: () -> Unit,
    onUndo: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallTitle(text = title)
        Text(
            description,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = value,
                summary = detail,
                startAction = { AppIcon(icon) },
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                },
                onClick = onChange,
                enabled = enabled,
            )
            if (pending != null) {
                HorizontalDivider(Modifier.padding(start = 16.dp))
                BasicComponent(
                    title = "应用后改为 $pending",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    endActions = {
                        TextButton(
                            text = "撤销",
                            onClick = onUndo,
                            enabled = enabled,
                        )
                    },
                )
            }
            extra()
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = endActions ?: {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
private fun AppIcon(icon: android.graphics.Bitmap?) {
    if (icon != null) {
        val image = remember(icon) { icon.asImageBitmap() }
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(40.dp))
        return
    }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MiuixTheme.colorScheme.secondaryContainerVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = MiuixIcons.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProviderPickerSheet(
    kind: ProviderKind,
    state: PasswordSwitchState,
    currentLabel: String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val entries = state.providers.filter { it.kind == kind }
    val matches = entries.filter {
        it.appName.contains(search, true) || it.packageName.contains(search, true) || it.component.contains(search, true)
    }
    val selected = if (kind == ProviderKind.CREDENTIAL) state.credential else state.autofill
    val key = if (kind == ProviderKind.CREDENTIAL) SettingKey.PRIMARY else SettingKey.AUTOFILL
    val inUse = runCatching { components(state.snapshot?.get(key)) }.getOrDefault(emptySet())

    WindowBottomSheet(
        show = true,
        title = if (kind == ProviderKind.CREDENTIAL) "选择凭据提供者" else "选择自动填充服务",
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = "搜索应用名称或包名",
                useLabelAsPlaceholder = true,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                },
                trailingIcon = if (search.isNotEmpty()) {
                    {
                        IconButton(onClick = { search = "" }) {
                            Icon(
                                imageVector = MiuixIcons.Clear,
                                contentDescription = "清除搜索",
                                tint = MiuixTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                } else null,
            )

            Text(
                "只列出当前用户下已安装并启用、声明了对应服务的应用。",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "keep") {
                    PickerRow(
                        selected = selected == null,
                        enabled = !state.busy,
                        onClick = { onSelect(null) },
                        headline = "保持当前配置",
                        supporting = if (state.snapshot == null) "本次不修改这一项" else "本次不修改这一项 · 当前为 $currentLabel",
                    )
                }
                items(matches, key = { it.component }) { entry ->
                    PickerRow(
                        selected = selected == entry.component,
                        enabled = !state.busy,
                        onClick = { onSelect(entry.component) },
                        headline = entry.appName,
                        badge = if (entry.component in inUse) "当前使用" else null,
                        icon = entry.icon,
                        supporting = buildList {
                            add(entry.packageName)
                            if (entries.count { it.packageName == entry.packageName } > 1) {
                                add(entry.component.substringAfter('/'))
                            }
                            if (kind == ProviderKind.CREDENTIAL) {
                                add(
                                    when (entry.supportsPasskeys) {
                                        true -> "声明支持通行密钥"
                                        false -> "未声明通行密钥支持"
                                        null -> "通行密钥支持情况未知"
                                    },
                                )
                            }
                        }.joinToString("\n"),
                    )
                }
                if (matches.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (entries.isEmpty()) {
                                "未发现可用服务。请先安装并打开密码管理器，完成初始化后返回刷新；该应用也需要支持此类系统服务。"
                            } else "没有匹配的应用。",
                            Modifier.padding(24.dp),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    headline: String,
    supporting: String,
    badge: String? = null,
    icon: android.graphics.Bitmap? = null,
) {
    BasicComponent(
        title = headline,
        summary = supporting,
        startAction = { AppIcon(icon) },
        endActions = {
            if (badge != null) {
                Text(
                    badge,
                    Modifier.padding(end = 8.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

private fun summary(
    snapshot: roro.stellar.manager.passwordswitch.SettingsSnapshot?,
    key: SettingKey,
    viewModel: PasswordSwitchViewModel,
): String {
    if (snapshot == null) return "尚未读取"
    val entries = components(snapshot[key])
    return if (entries.isEmpty()) "未设置" else entries.joinToString("、") { viewModel.label(it) }
}

/** The installed service behind the first component of a setting, used only for its icon. */
private fun PasswordSwitchState.current(key: SettingKey, kind: ProviderKind): ProviderEntry? {
    val component = runCatching { components(snapshot?.get(key)).firstOrNull() }.getOrNull() ?: return null
    return providers.firstOrNull { it.kind == kind && it.component == component }
}

/** The enabled list only adds information when it differs from the preferred provider. */
private fun PasswordSwitchState.enabledProviders(viewModel: PasswordSwitchViewModel): String? {
    val snapshot = snapshot ?: return null
    val enabled = runCatching { components(snapshot[SettingKey.CREDENTIALS]) }.getOrNull()
    val primary = runCatching { components(snapshot[SettingKey.PRIMARY]) }.getOrNull()
    if (enabled != null && enabled == primary) return null
    return "已启用：${summary(snapshot, SettingKey.CREDENTIALS, viewModel)}"
}

private fun rawValues(state: PasswordSwitchState): String = buildString {
    appendLine("Android 用户：${state.snapshot?.userId ?: "尚未读取"}")
    SettingKey.entries.forEach {
        append("\n${it.wireName}\n${if (state.snapshot == null) "尚未读取" else state.snapshot[it] ?: "未设置"}\n")
    }
}.trimEnd()

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(value, style = MiuixTheme.textStyles.body2)
    }
}

@Composable
private fun ChangeLine(label: String, from: String, to: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = buildAnnotatedString {
                append("$from → ")
                withStyle(SpanStyle(color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                    append(to)
                }
            },
            style = MiuixTheme.textStyles.body2,
        )
    }
}

private fun openSystemSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
    runCatching { context.startActivity(intent) }
}
