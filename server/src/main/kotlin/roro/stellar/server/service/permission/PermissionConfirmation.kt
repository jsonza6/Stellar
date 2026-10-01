package roro.stellar.server.service.permission

import roro.stellar.StellarApiConstants
import roro.stellar.server.ClientRecord
import roro.stellar.server.util.Logger

/**
 * 本分支不提供客户端授权界面：任何来自第三方应用的权限申请都直接拒绝，不再拉起
 * 管理器的确认 Activity。管理器自身不会走到这里 —— `PermissionEnforcer` 以宿主身份放行。
 *
 * 保留这个类只是为了让 `PermissionRequester` 的调用点不用改动；行为等同于
 * 「用户点了拒绝」，客户端会收到 allowed = false。
 */
class PermissionConfirmation {
    companion object {
        private val LOGGER = Logger("PermissionConfirmation")
    }

    fun showPermissionConfirmation(
        requestCode: Int,
        clientRecord: ClientRecord,
        callingUid: Int,
        callingPid: Int,
        userId: Int,
        permission: String = StellarApiConstants.PERMISSION_STELLAR
    ) {
        LOGGER.i(
            "拒绝权限申请：pkg=${clientRecord.packageName}, uid=$callingUid, " +
                    "pid=$callingPid, permission=$permission（本分支不提供客户端授权）"
        )
        clientRecord.dispatchRequestPermissionResult(
            requestCode,
            allowed = false,
            onetime = false,
            permission = permission
        )
    }
}
