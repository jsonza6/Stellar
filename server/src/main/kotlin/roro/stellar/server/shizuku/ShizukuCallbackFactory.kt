package roro.stellar.server.shizuku

import android.content.Intent
import android.os.Process
import android.system.Os
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.ServerConstants
import roro.stellar.server.service.StellarServiceCore
import roro.stellar.server.userservice.UserServiceManager
import roro.stellar.server.util.Logger
import roro.stellar.server.util.PackageManagerCompat

object ShizukuCallbackFactory {
    private val LOGGER = Logger("ShizukuCallbackFactory")

    fun create(
        clientManager: ClientManager,
        configManager: ConfigManager,
        userServiceManager: UserServiceManager,
        managerAppId: Int,
        serviceCore: StellarServiceCore,
        shizukuNotifier: ShizukuPermissionNotifier
    ): ShizukuServiceCallback {
        val cachedUid = Os.getuid()
        val cachedPid = Process.myPid()
        val cachedSeContext = try { android.os.SELinux.getContext() } catch (_: Throwable) { null }

        return object : ShizukuServiceCallback {
            override val serviceUid: Int = cachedUid
            override val serviceVersion: Int = StellarApiConstants.SERVER_VERSION
            override val serviceSeLinuxContext: String? = cachedSeContext

            override val clientManager: ClientManager get() = clientManager
            override val configManager: ConfigManager get() = configManager
            override val userServiceManager: UserServiceManager get() = userServiceManager
            override val managerAppId: Int get() = managerAppId
            override val servicePid: Int = cachedPid

            override fun getPackagesForUid(uid: Int): List<String> {
                return PackageManagerApis.getPackagesForUidNoThrow(uid).toList()
            }

            override fun getSystemProperty(name: String?, defaultValue: String?): String {
                return android.os.SystemProperties.get(name, defaultValue)
            }

            override fun setSystemProperty(name: String?, value: String?) {
                android.os.SystemProperties.set(name, value)
            }

            override fun newProcess(uid: Int, pid: Int, cmd: Array<String?>?, env: Array<String?>?, dir: String?): com.stellar.server.IRemoteProcess {
                return serviceCore.processManager.newProcess(uid, pid, cmd ?: emptyArray(), env, dir)
            }

            override fun requestPermission(uid: Int, pid: Int, requestCode: Int) {
                // 本分支不提供客户端授权：Shizuku 兼容层的权限申请同样一律拒绝，
                // 不再拉起管理器的确认界面。需要 Shizuku 权限的应用请使用官方 Stellar。
                LOGGER.i("Shizuku 权限申请一律拒绝: uid=$uid, pid=$pid, code=$requestCode")
                shizukuNotifier.notifyPermissionResult(uid, pid, requestCode, false)
            }
        }
    }
}
