package ps.hakim.phoneagent

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject

/**
 * هوية المنتج في 20338.
 *
 * فصل Consumer عن Advanced قرار بناء، لا مفتاح صلاحيات وقت تشغيل.
 * لا يجوز لنسخة Consumer تشغيل طبقات Termux/VPN/ADB/relay automation المتقدمة.
 */
object HakimProductEdition {
    const val FOUNDATION_VERSION = "PRODUCT-FOUNDATION-20338-v1"

    val isAdvanced: Boolean
        get() = BuildConfig.HAKIM_ADVANCED

    val isConsumer: Boolean
        get() = !BuildConfig.HAKIM_ADVANCED

    fun name(): String = BuildConfig.HAKIM_EDITION

    fun status(context: Context): JSONObject {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val versionCode = if (info == null) 0L else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }

        return JSONObject()
            .put("foundation_version", FOUNDATION_VERSION)
            .put("edition", name())
            .put("advanced", isAdvanced)
            .put("consumer", isConsumer)
            .put("package_name", context.packageName)
            .put("version_code", versionCode)
            .put("target_sdk", context.applicationInfo.targetSdkVersion)
            .put("termux_surface_enabled", isAdvanced)
            .put("vpn_surface_enabled", isAdvanced)
            .put("adb_surface_enabled", isAdvanced)
            .put("side_load_update_surface_enabled", isAdvanced)
            .put("play_ready_claimed", false)
            .put("field_verified", false)
    }
}
