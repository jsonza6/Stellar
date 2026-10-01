package roro.stellar.manager

import roro.stellar.StellarProvider

/**
 * 管理器侧 ContentProvider（authority：`roro.stellar.manager.stellar`）。
 *
 * 服务端通过它把 Stellar Binder 投递到管理器。本分支已移除配置持久化、日志收集与
 * Shizuku 兼容开关，因此这里直接复用基类的 Binder 收发逻辑，不再做任何额外分发。
 */
class StellarManagerProvider : StellarProvider()
