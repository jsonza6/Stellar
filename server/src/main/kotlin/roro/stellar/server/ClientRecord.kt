package roro.stellar.server

import android.os.Bundle
import com.stellar.server.IStellarApplication
import roro.stellar.StellarApiConstants
import roro.stellar.server.util.Logger

open class ClientRecord(
    val uid: Int,
    val pid: Int,
    val client: IStellarApplication?,
    val packageName: String,
    val apiVersion: Int
) {
    val attachTime: Long = System.currentTimeMillis()

    val lastDenyTimeMap: MutableMap<String, Long> = mutableMapOf()

    val allowedMap: MutableMap<String, Boolean> = mutableMapOf()

    val onetimeMap: MutableMap<String, Boolean> = mutableMapOf()

    fun dispatchRequestPermissionResult(
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean,
        permission: String = StellarApiConstants.PERMISSION_STELLAR
    ) {
        if (!allowed) lastDenyTimeMap[permission] = System.currentTimeMillis()

        val reply = Bundle().apply {
            putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
            putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
            putString(StellarApiConstants.REQUEST_PERMISSION_REPLY_PERMISSION, permission)
        }

        try {
            client?.dispatchRequestPermissionResult(requestCode, reply)
        } catch (e: Throwable) {
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName)
        }
    }

    companion object {
        protected val LOGGER: Logger = Logger("ClientRecord")
    }
}
