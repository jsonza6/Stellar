package roro.stellar.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import roro.stellar.manager.util.EmptySharedPreferencesImpl
import roro.stellar.manager.util.PortBlacklistUtils

/**
 * 管理器本地偏好设置。
 *
 * 已移除开机自启（BootMode）、Shizuku 兼容开关与进程守护开关等条目：本分支只保留
 * Root / ADB 手动启动与密码管理器切换所需的最小设置。
 */
object StellarSettings {
    const val NAME = "settings"
    const val TCPIP_PORT = "tcpip_port"
    const val TCPIP_PORT_ENABLED = "tcpip_port_enabled"
    const val THEME_MODE = "theme_mode"
    const val START_PAGE = "start_page"
    const val DROP_PRIVILEGES = "drop_privileges"
    const val WIRELESS_DEBUGGING_SU = "wireless_debugging_su"
    const val LAST_VERSION_CODE = "last_version_code"
    const val HIDE_BACKGROUND = "hide_background"

    enum class LaunchMethod { UNKNOWN, ROOT, ADB }
    const val LAST_LAUNCH_METHOD = "last_launch_method"

    fun getLastLaunchMethod(): LaunchMethod {
        val name = getPreferences().getString(LAST_LAUNCH_METHOD, LaunchMethod.UNKNOWN.name)
            ?: LaunchMethod.UNKNOWN.name
        return runCatching { LaunchMethod.valueOf(name) }.getOrDefault(LaunchMethod.UNKNOWN)
    }

    fun setLastLaunchMethod(method: LaunchMethod) {
        getPreferences().edit().putString(LAST_LAUNCH_METHOD, method.name).apply()
    }

    private var preferences: SharedPreferences? = null

    fun getPreferences(): SharedPreferences = preferences ?: EmptySharedPreferencesImpl()

    private fun getSettingsStorageContext(context: Context): Context {
        val storageContext = context.createDeviceProtectedStorageContext()
        return object : ContextWrapper(storageContext) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return try {
                    super.getSharedPreferences(name, mode)
                } catch (_: IllegalStateException) {
                    EmptySharedPreferencesImpl()
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (preferences == null) {
            preferences = getSettingsStorageContext(context)
                .getSharedPreferences(NAME, Context.MODE_PRIVATE)

            preferences?.let { prefs ->
                if (!prefs.contains(TCPIP_PORT_ENABLED)) {
                    prefs.edit().putBoolean(TCPIP_PORT_ENABLED, true).apply()
                }
                if (!prefs.contains(TCPIP_PORT)) {
                    var randomPort = PortBlacklistUtils.generateSafeRandomPort(1000, 9999, 100)
                    if (randomPort == -1) {
                        randomPort = 8765
                    }
                    prefs.edit().putString(TCPIP_PORT, randomPort.toString()).apply()
                }
            }
        }
    }
}
