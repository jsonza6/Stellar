package roro.stellar.manager.ui.features.passwordswitch

import android.app.Application
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.passwordswitch.IPasswordSwitchService
import roro.stellar.manager.passwordswitch.PasswordSwitchService
import roro.stellar.manager.passwordswitch.ProviderCatalog
import roro.stellar.manager.passwordswitch.ProviderEntry
import roro.stellar.manager.passwordswitch.ProviderKind
import roro.stellar.manager.passwordswitch.SettingKey
import roro.stellar.manager.passwordswitch.SettingsFailure
import roro.stellar.manager.passwordswitch.SettingsSnapshot
import roro.stellar.manager.passwordswitch.components
import roro.stellar.manager.passwordswitch.scanProviders
import roro.stellar.manager.passwordswitch.toBundle
import roro.stellar.manager.passwordswitch.toSnapshot
import roro.stellar.userservice.StellarUserService
import roro.stellar.userservice.UserServiceArgs

enum class ConnectionState { STOPPED, CONNECTING, READY }

data class PasswordSwitchState(
    val connection: ConnectionState = ConnectionState.STOPPED,
    val busy: Boolean = false,
    val providers: List<ProviderEntry> = emptyList(),
    val scanned: Boolean = false,
    val snapshot: SettingsSnapshot? = null,
    val backup: SettingsSnapshot? = null,
    val credential: String? = null,
    val autofill: String? = null,
    val keepOthers: Boolean = false,
    val message: String = "",
    val detail: String = "",
    val error: Boolean = false,
    val checkedAt: Long? = null,
    val unconfirmedWrite: Boolean = false,
) {
    val canApply: Boolean get() = connection == ConnectionState.READY && !busy && snapshot != null &&
        (credential != null || autofill != null)
}

/**
 * Drives the password-manager switching screen.
 *
 * Everything privileged happens in [roro.stellar.manager.passwordswitch.PasswordSwitchService],
 * which Stellar starts as a user service with shell (uid 2000) or root identity. This ViewModel
 * only owns state and talks to that service over AIDL, so it never needs shell rights itself.
 */
class PasswordSwitchViewModel(application: Application) : AndroidViewModel(application) {
    // Android assigns a separate 100000-UID range per user. Never use shell's user (0).
    private val userId = Process.myUid() / 100000
    private val preferences = application.getSharedPreferences("password_switch_backup", 0)
    private val catalog = ProviderCatalog(application)
    private val mutableState = MutableStateFlow(PasswordSwitchState(backup = loadBackup()))
    val state = mutableState.asStateFlow()

    private var remote: IPasswordSwitchService? = null
    private var task: Job? = null
    private var bindingTimeout: Job? = null
    private var binding = false
    private var writing = false

    private val serviceArgs = UserServiceArgs.Builder(PasswordSwitchService::class.java)
        .processNameSuffix("passwordswitch")
        .versionCode(BuildConfig.VERSION_CODE.toLong())
        .debug(BuildConfig.DEBUG)
        .build()

    private val callback = object : StellarUserService.ServiceCallback {
        override fun onServiceConnected(service: IBinder) {
            viewModelScope.launch {
                bindingTimeout?.cancel()
                binding = false
                if (!service.pingBinder()) {
                    disconnect()
                    return@launch
                }
                remote = IPasswordSwitchService.Stub.asInterface(service)
                mutableState.update { it.copy(connection = ConnectionState.READY) }
                refresh()
            }
        }

        override fun onServiceDisconnected() {
            viewModelScope.launch { disconnect() }
        }

        override fun onServiceStartFailed(errorCode: Int, message: String) {
            viewModelScope.launch {
                bindingTimeout?.cancel()
                binding = false
                disconnect()
                notice("无法启动特权服务，请确认 Stellar 服务正在运行。", "$errorCode $message", true)
            }
        }
    }

    private val receivedListener = Stellar.OnBinderReceivedListener { viewModelScope.launch { reconnect() } }
    private val deadListener = Stellar.OnBinderDeadListener { viewModelScope.launch { disconnect() } }

    init {
        Stellar.addBinderReceivedListenerSticky(receivedListener)
        Stellar.addBinderDeadListener(deadListener)
    }

    fun reconnect() {
        try {
            if (!Stellar.pingBinder()) {
                disconnect()
                scanOnly()
                return
            }
            if (remote?.asBinder()?.pingBinder() == true) {
                refresh()
                return
            }
            if (binding) return
            binding = true
            mutableState.update { it.copy(connection = ConnectionState.CONNECTING) }
            StellarUserService.bindUserService(serviceArgs, callback)
            bindingTimeout = viewModelScope.launch {
                delay(12_000)
                if (binding) {
                    StellarUserService.unbindUserService(serviceArgs)
                    binding = false
                    mutableState.update { it.copy(connection = ConnectionState.STOPPED) }
                    notice("连接超时，请确认 Stellar 服务正在运行，再点刷新。", error = true)
                }
            }
        } catch (failure: Exception) {
            disconnect()
            notice("连接失败，请检查 Stellar 服务状态。", failure.toString(), true)
        }
    }

    private fun scanOnly() {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { catalog.scan() }
                mutableState.update { it.copy(providers = entries, scanned = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notice("无法读取已安装服务，请重试。", failure.toString(), true)
            } finally {
                // If the service came up while scanning, finish the deferred read now.
                task = null
                if (remote != null && state.value.snapshot == null) refresh()
            }
        }
    }

    fun select(kind: ProviderKind, component: String?) {
        if (state.value.busy) return
        if (component != null && state.value.providers.none { it.kind == kind && it.component == component }) return
        mutableState.update {
            if (kind == ProviderKind.CREDENTIAL) it.copy(credential = component)
            else it.copy(autofill = component)
        }
    }

    fun keepOthers(value: Boolean) {
        if (!state.value.busy) mutableState.update { it.copy(keepOthers = value) }
    }

    fun label(component: String): String = catalog.label(component, state.value.providers)

    fun refresh() = execute(false) { service, _ -> service.read(userId) }

    fun apply() {
        if (!state.value.canApply) return
        val draft = state.value
        execute(true) action@{ service, before ->
            val entries = service.scanProviders(userId)
            require(
                draft.credential == null ||
                    entries.any { it.kind == ProviderKind.CREDENTIAL && it.component == draft.credential }
            ) { "选中的凭据服务已不可用，请刷新后重新选择。" }
            require(
                draft.autofill == null ||
                    entries.any { it.kind == ProviderKind.AUTOFILL && it.component == draft.autofill }
            ) { "选中的自动填充服务已不可用，请刷新后重新选择。" }
            val expected = requireNotNull(before)
            val current = service.read(userId)
            if (!current.getBoolean("ok")) return@action current
            if (!current.toSnapshot().equivalentTo(expected)) {
                return@action current.apply {
                    putBoolean("ok", false)
                    putString("message", "系统配置已变化，请检查当前配置后重新应用。未写入设置，原备份已保留。")
                }
            }
            val desired = expected.withSelection(draft.credential, draft.autofill, draft.keepOthers)
            if (!expected.equivalentTo(desired)) saveBackup(expected)
            service.apply(userId, draft.credential, draft.autofill, draft.keepOthers, expected.toBundle())
        }
    }

    fun restore() {
        val target = state.value.backup ?: return
        if (state.value.snapshot == null || state.value.busy) return
        // Keep this backup until restore is confirmed; retry is possible after interruption.
        execute(true, restoring = true) { service, before ->
            service.restore(userId, target.toBundle(), requireNotNull(before).toBundle())
        }
    }

    private fun execute(
        write: Boolean,
        restoring: Boolean = false,
        action: (IPasswordSwitchService, SettingsSnapshot?) -> Bundle,
    ) {
        val service = remote ?: return
        if (task?.isActive == true) return
        val before = state.value.snapshot
        writing = write
        mutableState.update { it.copy(busy = true, message = if (write) "正在写入并核对系统配置…" else it.message) }
        task = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action(service, before) }
                if (remote !== service) return@launch
                val snapshot = if (result.containsKey("userId")) result.toSnapshot() else null
                val ok = result.getBoolean("ok")
                mutableState.update {
                    it.copy(
                        snapshot = snapshot,
                        checkedAt = snapshot?.let { _ -> System.currentTimeMillis() },
                        backup = loadBackup(),
                        credential = if (write && ok) null else it.credential,
                        autofill = if (write && ok) null else it.autofill,
                        message = when {
                            !ok -> result.getString("message").orEmpty()
                            restoring -> "已恢复上次配置，并核对系统设置。"
                            write -> "系统设置已写入并核对。请在目标应用中验证通行密钥或自动填充。"
                            it.unconfirmedWrite -> "已重新读取当前配置。请核对中断操作的结果，需要时恢复上次配置。"
                            else -> it.message
                        },
                        error = if (write || !ok) !ok else it.error,
                        detail = if (write || !ok) result.getString("detail").orEmpty() else it.detail,
                        unconfirmedWrite = snapshot == null && (write || it.unconfirmedWrite),
                    )
                }
                val entries = withContext(Dispatchers.IO) { service.scanProviders(userId) }
                mutableState.update { it.copy(providers = entries, scanned = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (remote !== service) return@launch
                mutableState.update {
                    it.copy(
                        snapshot = null, checkedAt = null, backup = loadBackup(),
                        unconfirmedWrite = write || it.unconfirmedWrite,
                    )
                }
                notice(
                    if (write) "操作中断，结果尚未确认。请刷新检查；上次配置已保留。" else "读取失败，请刷新重试。",
                    failure.message ?: failure.toString(), true,
                )
            } finally {
                if (remote === service) {
                    writing = false
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun notice(message: String, detail: String = "", error: Boolean = false) {
        mutableState.update { it.copy(message = message, detail = detail, error = error) }
    }

    private fun saveBackup(snapshot: SettingsSnapshot) {
        val editor = preferences.edit().clear().putBoolean("valid", true).putInt("userId", userId)
        SettingKey.entries.forEach { key ->
            editor.putBoolean("${key.wireName}_present", snapshot[key] != null)
            snapshot[key]?.let { editor.putString(key.wireName, it) }
        }
        if (!editor.commit()) throw SettingsFailure("无法保存恢复配置，已取消本次写入。")
    }

    private fun loadBackup(): SettingsSnapshot? {
        if (!preferences.getBoolean("valid", false) || preferences.getInt("userId", -1) != userId) return null
        return runCatching {
            SettingsSnapshot(userId, SettingKey.entries.associateWith {
                if (preferences.getBoolean("${it.wireName}_present", false)) {
                    preferences.getString(it.wireName, null)
                } else null
            }).also { snapshot -> snapshot.values.values.forEach(::components) }
        }.getOrNull()
    }

    private fun disconnect() {
        val interrupted = writing
        writing = false
        remote = null
        binding = false
        bindingTimeout?.cancel()
        task?.cancel()
        task = null
        mutableState.update {
            it.copy(
                connection = ConnectionState.STOPPED, busy = false, snapshot = null, checkedAt = null,
                unconfirmedWrite = interrupted || it.unconfirmedWrite,
                backup = loadBackup(),
                message = if (interrupted) "写入时连接已中断，结果待确认。请重新连接并刷新，必要时恢复上次配置。" else it.message,
                error = interrupted || it.error,
            )
        }
    }

    override fun onCleared() {
        Stellar.removeBinderReceivedListener(receivedListener)
        Stellar.removeBinderDeadListener(deadListener)
        // Do not kill an in-flight transaction when the UI is destroyed.
        runCatching { StellarUserService.unbindUserService(serviceArgs) }
        super.onCleared()
    }
}
