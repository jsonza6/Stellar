package roro.stellar.server.communication

import android.os.Binder
import android.system.Os
import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientManager
import roro.stellar.server.ConfigManager
import roro.stellar.server.util.Logger
import roro.stellar.server.util.OsUtils
import roro.stellar.server.util.UserHandleCompat.getAppId

class PermissionEnforcer(
    private val clientManager: ClientManager,
    private val configManager: ConfigManager,
    private val managerAppId: Int
) {
    companion object {
        private val LOGGER = Logger("PermissionEnforcer")
    }

    fun isManager(caller: CallerContext): Boolean = getAppId(caller.uid) == managerAppId

    fun isSelf(caller: CallerContext): Boolean = caller.uid == OsUtils.uid || caller.pid == Os.getpid()

    fun hasPermission(
        caller: CallerContext,
        permission: String = StellarApiConstants.PERMISSION_STELLAR
    ): Boolean {
        if (isSelf(caller) || isManager(caller)) return true
        // 本分支不向第三方应用授予任何权限，因此这里恒为 false。
        return false
    }

    fun enforceManager(caller: CallerContext, func: String) {
        if (isSelf(caller) || isManager(caller)) return

        val msg = "Permission Denial: $func from pid=${caller.pid}, uid=${caller.uid} is not manager"
        LOGGER.w(msg)
        throw SecurityException(msg)
    }

    fun enforcePermission(
        caller: CallerContext,
        func: String,
        permission: String = StellarApiConstants.PERMISSION_STELLAR
    ) {
        if (isSelf(caller) || isManager(caller)) return

        // 本分支不向第三方应用授予权限：无论此前是否被授予过，一律拒绝。
        val msg = "Permission Denial: $func from pid=${caller.pid}, uid=${caller.uid}; " +
                "this build does not authorize client apps"
        LOGGER.w(msg)
        throw SecurityException(msg)
    }
}
