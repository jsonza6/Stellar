package roro.stellar.server

/**
 * 进程内配置容器：记录声明了 Stellar 支持的 uid 及其权限标记。
 *
 * 本分支已移除 Shizuku 兼容、无障碍自启与进程守护开关，也不再做持久化同步，
 * 因此这里只保留 Binder 分发与鉴权所需的最小字段。
 */
class StellarConfig {

    var version: Int = LATEST_VERSION

    var packages: MutableMap<Int, PackageEntry> = mutableMapOf()

    class PackageEntry {
        var packages: MutableList<String> = ArrayList()
        var permissions: MutableMap<String, Int> = mutableMapOf()
    }

    companion object {
        const val LATEST_VERSION: Int = 1
    }
}
