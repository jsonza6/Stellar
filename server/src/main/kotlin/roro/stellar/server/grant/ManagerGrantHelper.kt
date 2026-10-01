package roro.stellar.server.grant

import android.Manifest
import android.content.pm.PackageManager
import rikka.hidden.compat.PermissionManagerApis
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.Logger

object ManagerGrantHelper {
    private val LOGGER = Logger("ManagerGrantHelper")

    fun grantWriteSecureSettings(managerAppId: Int) {
        try {
            val pm = PermissionManagerApis.checkPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS,
                managerAppId
            )
            if (pm == PackageManager.PERMISSION_GRANTED) {
                LOGGER.i("Manager already has WRITE_SECURE_SETTINGS")
                return
            }

            LOGGER.i("Granting WRITE_SECURE_SETTINGS to manager...")
            Runtime.getRuntime().exec(
                arrayOf(
                    "pm", "grant", MANAGER_APPLICATION_ID,
                    Manifest.permission.WRITE_SECURE_SETTINGS
                )
            ).waitFor()
            LOGGER.i("WRITE_SECURE_SETTINGS grant completed")
        } catch (e: Throwable) {
            LOGGER.e(e, "Failed to grant WRITE_SECURE_SETTINGS")
        }
    }
}
