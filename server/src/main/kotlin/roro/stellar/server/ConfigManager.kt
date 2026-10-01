package roro.stellar.server

import android.content.pm.PackageManager
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import roro.stellar.StellarApiConstants.PERMISSIONS
import roro.stellar.StellarApiConstants.PERMISSION_KEY
import roro.stellar.StellarApiConstants.PERMISSION_STELLAR
import roro.stellar.server.StellarConfig.PackageEntry
import roro.stellar.server.util.Logger
import roro.stellar.server.util.PackageManagerCompat
import roro.stellar.server.util.ProviderDiscovery
import roro.stellar.server.util.UserHandleCompat

/**
 * 进程内配置：记录哪些 uid 声明了 Stellar 支持、以及它们各自的权限标记。
 *
 * 本分支不再向第三方应用授权，也不再与管理器做配置持久化同步，因此这里只保留内存态，
 * 供 Binder 分发与调用级鉴权使用。
 */
class ConfigManager {

    private val config: StellarConfig

    val packages: MutableMap<Int, PackageEntry>
        get() = LinkedHashMap(config.packages)

    init {
        this.config = StellarConfig()

        for (entry in LinkedHashMap(config.packages)) {
            val packages = PackageManagerApis.getPackagesForUidNoThrow(entry.key)
            if (packages.isEmpty()) {
                LOGGER.i("remove config for uid %d since it has gone", entry.key)
                config.packages.remove(entry.key)
                continue
            }

            var needRemoving = true
            for (packageName in entry.value.packages) {
                if (packages.contains(packageName)) {
                    needRemoving = false
                    break
                }
            }

            val rawSize = entry.value.packages.size
            val s = LinkedHashSet(entry.value.packages)
            entry.value.packages.clear()
            entry.value.packages.addAll(s)
            val shrunkSize = entry.value.packages.size
            if (shrunkSize < rawSize) {
                LOGGER.w("entry.packages has duplicate! Shrunk. (%d -> %d)", rawSize, shrunkSize)
            }

            if (needRemoving) {
                LOGGER.i("remove config for uid %d since the packages for it changed", entry.key)
                config.packages.remove(entry.key)
            }
        }

        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            for (pi in PackageManagerCompat.getInstalledPackagesNoThrow(
                (PackageManager.MATCH_ALL or PackageManager.GET_META_DATA or PackageManager.GET_PROVIDERS).toLong(),
                userId
            )) {
                if (
                    pi == null ||
                    pi.applicationInfo == null ||
                    (
                        pi.applicationInfo!!.metaData
                            ?.getString(PERMISSION_KEY, "")
                            ?.split(",")
                            ?.map { it.trim() }
                            ?.any { PERMISSIONS.contains(it) } != true &&
                            !ProviderDiscovery.hasStellarProvider(pi)
                    ) ||
                    pi.packageName == ServerConstants.MANAGER_APPLICATION_ID
                ) {
                    continue
                }

                val uid = pi.applicationInfo!!.uid
                val packages = ArrayList<String>()
                packages.add(pi.packageName)
                updateLocked(uid, packages)
            }
        }

        for (entry in LinkedHashMap(config.packages)) {
            val permissions = LinkedHashSet<String>()
            val packages = PackageManagerApis.getPackagesForUidNoThrow(entry.key)
            for (packageName in packages) {
                val applicationInfo = PackageManagerCompat.getApplicationInfo(
                    packageName, PackageManager.GET_META_DATA.toLong(),
                    UserHandleCompat.getUserId(entry.key)
                ) ?: continue
                for (permission in (applicationInfo.metaData?.getString(PERMISSION_KEY, "")
                    ?: "").split(",").map { it.trim() }) {
                    if (PERMISSIONS.contains(permission)) {
                        permissions.add(permission)
                    }
                }
            }
            val packageEntry = findLocked(entry.key)!!
            val permissionsToRemove = mutableListOf<String>()
            for (permission in entry.value.permissions) {
                if (!permissions.contains(permission.key)) {
                    permissionsToRemove.add(permission.key)
                }
            }
            for (permissionKey in permissionsToRemove) {
                packageEntry.permissions.remove(permissionKey)
            }
            for (permission in permissions) {
                if (packageEntry.permissions[permission] == null) {
                    packageEntry.permissions[permission] = FLAG_ASK
                }
            }
        }
    }

    private fun findLocked(uid: Int): PackageEntry? {
        return config.packages[uid]
    }

    fun find(uid: Int): PackageEntry? {
        synchronized(this) {
            return findLocked(uid)
        }
    }

    fun getPermissionFlag(uid: Int, permission: String): Int {
        synchronized(this) {
            return findLocked(uid)?.permissions?.get(permission) ?: FLAG_ASK
        }
    }

    fun findOldConfigByPackageName(currentUid: Int, packageName: String): Pair<Int, PackageEntry>? {
        synchronized(this) {
            for ((uid, entry) in config.packages) {
                if (uid == currentUid) {
                    continue
                }
                if (entry.packages.contains(packageName)) {
                    return Pair(uid, entry)
                }
            }
            return null
        }
    }

    fun createConfigWithAllPermissions(uid: Int, packageName: String) {
        synchronized(this) {
            val userId = UserHandleCompat.getUserId(uid)
            val applicationInfo = PackageManagerCompat.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA.toLong(),
                userId
            )

            if (applicationInfo == null) {
                LOGGER.w("无法获取应用信息: %s, 使用默认权限", packageName)
                val packages = mutableListOf(packageName)
                updateLocked(uid, packages)
                return
            }

            val declaredPermissions = LinkedHashSet<String>()
            val metaDataPermissions = applicationInfo.metaData?.getString(PERMISSION_KEY, "") ?: ""
            for (permission in metaDataPermissions.split(",").map { it.trim() }) {
                if (PERMISSIONS.contains(permission)) {
                    declaredPermissions.add(permission)
                }
            }

            LOGGER.i("应用 %s 声明的权限: %s", packageName, declaredPermissions.toString())

            val entry = PackageEntry()
            entry.packages.add(packageName)

            for (permission in declaredPermissions) {
                entry.permissions[permission] = FLAG_ASK
            }

            if (entry.permissions.isEmpty()) {
                entry.permissions[PERMISSION_STELLAR] = FLAG_ASK
            }

            config.packages[uid] = entry

            LOGGER.i("已创建配置: uid=%d, package=%s, permissions=%s",
                uid, packageName, entry.permissions.toString())
        }
    }

    private fun updateLocked(
        uid: Int,
        packages: MutableList<String>?
    ) {
        var entry = findLocked(uid)
        if (entry == null) {
            entry = PackageEntry()
            entry.permissions[PERMISSION_STELLAR] = FLAG_ASK
            config.packages[uid] = entry
        }
        if (packages != null) {
            for (packageName in packages) {
                if (entry.packages.contains(packageName)) {
                    continue
                }
                entry.packages.add(packageName)
            }
        }
    }

    fun update(
        uid: Int,
        packages: MutableList<String>?
    ) {
        synchronized(this) {
            updateLocked(uid, packages)
        }
    }

    private fun updatePermissionLocked(uid: Int, permission: String, newFlag: Int) {
        var entry = findLocked(uid)
        if (entry == null) {
            entry = PackageEntry()
            val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
            entry.packages.addAll(packages)
            config.packages[uid] = entry
            LOGGER.i("为 uid=%d 创建新配置以保存权限 %s", uid, permission)
        } else if (entry.packages.isEmpty()) {
            entry.packages.addAll(PackageManagerApis.getPackagesForUidNoThrow(uid))
            LOGGER.i("为 uid=%d 的权限配置补全包名: %s", uid, entry.packages.toString())
        }
        entry.permissions[permission] = newFlag
    }

    fun updatePermission(uid: Int, permission: String, newFlag: Int) {
        synchronized(this) {
            updatePermissionLocked(uid, permission, newFlag)
        }
    }

    private fun removeLocked(uid: Int) {
        config.packages.remove(uid)
    }

    fun remove(uid: Int) {
        synchronized(this) {
            removeLocked(uid)
        }
    }

    companion object {
        private val LOGGER: Logger = Logger("ConfigManager")

        const val FLAG_ASK: Int = 0
        const val FLAG_GRANTED: Int = 1
        const val FLAG_DENIED: Int = 2
    }
}
