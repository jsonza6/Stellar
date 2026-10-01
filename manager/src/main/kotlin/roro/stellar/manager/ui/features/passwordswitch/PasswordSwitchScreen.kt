@file:OptIn(ExperimentalMaterial3Api::class)

package roro.stellar.manager.ui.features.passwordswitch

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSwitchScreen(
    topAppBarState: TopAppBarState,
    viewModel: PasswordSwitchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val context = LocalContext.current

    var picker by remember { mutableStateOf<ProviderKind?>(null) }
    var confirmApply by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var rawExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StandardLargeTopAppBar(
                title = stringResource(R.string.nav_password_switch),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = viewModel::reconnect,
                        enabled = !state.busy,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding(),
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.busy) {
                    item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }

                item(key = "intro") {
                    Text(
                        "让密码与通行密钥，交给你选择的应用。",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            ListItem(
                                headlineContent = { Text("保留其他已启用的凭据提供者") },
                                supportingContent = {
                                    Text(
                                        if (state.keepOthers) "只将所选应用设为首选。"
                                        else "凭据提供者列表将仅保留所选服务。",
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = state.keepOthers,
                                        onCheckedChange = viewModel::keepOthers,
                                        enabled = !state.busy,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                    Column(Modifier.padding(horizontal = 16.dp)) {
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
                            Text(
                                it,
                                Modifier.padding(top = 8.dp, start = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item(key = "more") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "更多操作",
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Card(Modifier.padding(horizontal = 16.dp)) {
                            ActionRow(
                                title = "恢复上次配置",
                                summary = if (state.backup != null) "恢复到最近一次应用前的配置" else "暂无可恢复的配置",
                                enabled = state.connection == ConnectionState.READY &&
                                    state.snapshot != null && state.backup != null && !state.busy,
                                onClick = { confirmRestore = true },
                            )
                            ActionRow(
                                title = "打开系统设置",
                                onClick = { openSystemSettings(context) },
                            )
                            ActionRow(
                                title = "系统配置详情",
                                summary = "查看三项系统设置的原始值",
                                trailing = {
                                    Icon(
                                        if (rawExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { rawExpanded = !rawExpanded },
                            )
                            if (rawExpanded) {
                                Text(
                                    rawValues(state),
                                    Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "每次应用前保存配置，写入后重新读取核对。系统更新或其他应用仍可能改回设置，可随时刷新查看。",
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("确认更改默认服务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.credential?.let { selected ->
                        ChangeLine("首选凭据提供者", summary(state.snapshot, SettingKey.PRIMARY, viewModel), viewModel.label(selected))
                        Text(
                            if (state.keepOthers) "保留其他已启用的凭据提供者。"
                            else "已启用凭据提供者列表将仅保留 ${viewModel.label(selected)}。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        when (state.providers.firstOrNull { it.component == selected }?.supportsPasskeys) {
                            false -> Text(
                                "这个服务没有声明支持通行密钥；只能按它实际支持的凭据类型使用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )

                            null -> Text(
                                "无法确认这个服务的通行密钥支持情况。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )

                            true -> Unit
                        }
                    }
                    state.autofill?.let {
                        ChangeLine("自动填充服务", summary(state.snapshot, SettingKey.AUTOFILL, viewModel), viewModel.label(it))
                    }
                    Text(
                        "请仅选择你信任的密码管理器。未选择的项目保持原样，操作前的配置会保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmApply = false
                        viewModel.apply()
                    },
                    enabled = state.canApply,
                ) { Text("确认应用") }
            },
            dismissButton = { TextButton(onClick = { confirmApply = false }) { Text("取消") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复上次配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("首选凭据提供者", summary(state.backup, SettingKey.PRIMARY, viewModel))
                    LabeledValue("已启用凭据提供者", summary(state.backup, SettingKey.CREDENTIALS, viewModel))
                    LabeledValue("自动填充服务", summary(state.backup, SettingKey.AUTOFILL, viewModel))
                    Text(
                        "将恢复以上三项系统配置。如果原应用已卸载或停用，恢复配置不会使它重新可用。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restore()
                    },
                ) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ConnectionCard(state: PasswordSwitchState) {
    Card(Modifier.padding(horizontal = 16.dp)) {
        if (state.connection == ConnectionState.READY) {
            ListItem(
                headlineContent = { Text("Stellar 服务已连接") },
                supportingContent = { Text("仅修改系统服务选择，不读取密码或通行密钥内容。") },
                leadingContent = {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    state.checkedAt?.let {
                        Text(
                            "读回于 ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            return@Card
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (state.connection) {
                    ConnectionState.STOPPED -> "先启动 Stellar 服务"
                    else -> "正在连接 Stellar 服务…"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                when (state.connection) {
                    ConnectionState.STOPPED ->
                        "在 Stellar 首页通过无线调试或 Root 启动服务，返回后点刷新。"
                    else -> "连接成功后会读取当前系统配置。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.connection == ConnectionState.CONNECTING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在启动特权服务…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(state: PasswordSwitchState) {
    var expanded by rememberSaveable(state.message) { mutableStateOf(false) }
    Card(
        Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (state.error) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.detail.isNotEmpty()) {
                if (expanded) {
                    Text(
                        state.detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(if (expanded) "收起详情" else "查看详情") }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(Modifier.padding(horizontal = 16.dp)) {
            ListItem(
                headlineContent = { Text(value, maxLines = 2) },
                supportingContent = detail?.let { { Text(it) } },
                leadingContent = { AppIcon(icon) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(enabled = enabled, onClick = onChange),
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    disabledHeadlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            if (pending != null) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = {
                        Text("应用后改为 $pending", color = MaterialTheme.colorScheme.primary)
                    },
                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    trailingContent = {
                        TextButton(onClick = onUndo, enabled = enabled) { Text("撤销") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing ?: {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPickerSheet(
    kind: ProviderKind,
    state: PasswordSwitchState,
    currentLabel: String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    val entries = state.providers.filter { it.kind == kind }
    val matches = entries.filter {
        it.appName.contains(search, true) || it.packageName.contains(search, true) || it.component.contains(search, true)
    }
    val selected = if (kind == ProviderKind.CREDENTIAL) state.credential else state.autofill
    val key = if (kind == ProviderKind.CREDENTIAL) SettingKey.PRIMARY else SettingKey.AUTOFILL
    val inUse = runCatching { components(state.snapshot?.get(key)) }.getOrDefault(emptySet())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            if (kind == ProviderKind.CREDENTIAL) "选择凭据提供者" else "选择自动填充服务",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("搜索应用名称或包名") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                    }
                }
            },
        )
        Text(
            "只列出当前用户下已安装并启用、声明了对应服务的应用。",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headline, maxLines = 1)
                badge?.let {
                    Text(
                        it,
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        supportingContent = { Text(supporting) },
        leadingContent = { AppIcon(icon) },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        modifier = Modifier.selectable(selected = selected, enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChangeLine(label: String, from: String, to: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = buildAnnotatedString {
                append("$from → ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                    append(to)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun openSystemSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
    runCatching { context.startActivity(intent) }
}
