package ps.hakim.phoneagent

import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسيج تنفيذ حكيم: يجمع قنوات التنفيذ المتاحة تحت حالة واحدة.
 *
 * القاعدة: ONLINE لا تعني أن التطبيق موجود؛ تعني وجود مسار تنفيذ حي مثبت الآن.
 * فشل مسار واحد لا يغلق المقصد ما دام مسار آخر حيًا أو يمكن إنعاشه.
 */
object HakimExecutionFabric {
    const val VERSION = "EXECUTION-FABRIC-2026-09-26-v2"

    fun recover(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        PairingDefaults.ensure(prefs)

        val now = System.currentTimeMillis()
        val meshProfile = HakimConnectivityMesh.networkProfile(app)
        if (prefs.getBoolean("pairing_disabled_by_user", false)) {
            prefs.edit()
                .putString("execution_fabric_state", "DISABLED_BY_USER")
                .putLong("execution_fabric_checked_at", now)
                .putString("execution_fabric_reason", reason.take(80))
                .apply()
            return status(app)
        }

        val legacyConfigured =
            prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val secureConfigured = HakimUnifiedRelay.isConfigured(app)
        val adbPaired = prefs.getBoolean("local_adb_paired", false)

        if (secureConfigured && meshProfile.available) {
            HakimUnifiedRelay.start(app)
        }
        if (adbPaired && meshProfile.wifi) {
            HakimLocalPairing.reconnectAsync(app)
        }

        var serviceStart = "not_required"
        if (legacyConfigured || secureConfigured) {
            serviceStart = try {
                val intent = Intent(app, HakimService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
                else app.startService(intent)
                "requested"
            } catch (e: Exception) {
                prefs.edit()
                    .putString("execution_fabric_last_start_error", e.javaClass.simpleName + ":" + e.message.orEmpty().take(180))
                    .apply()
                "blocked"
            }
        }

        val configuredCount = listOf(legacyConfigured, secureConfigured, adbPaired).count { it }
        val meshState = HakimConnectivityMesh.status(app).optString("state", "RECOVERING")
        prefs.edit()
            .putString("execution_fabric_version", VERSION)
            .putString("execution_fabric_state", if (configuredCount == 0) "UNCONFIGURED" else meshState)
            .putString("execution_fabric_reason", reason.take(80))
            .putString("execution_fabric_service_start", serviceStart)
            .putLong("execution_fabric_recover_at", now)
            .putLong("execution_fabric_checked_at", now)
            .apply()

        return status(app)
    }

    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val legacyConfigured =
            prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val secureConfigured = HakimUnifiedRelay.isConfigured(app)
        val adbPaired = prefs.getBoolean("local_adb_paired", false)

        val legacyOnline = legacyConfigured && HakimService.connected
        val secureOnline = secureConfigured && HakimUnifiedRelay.isConnected()
        val adbOnline = adbPaired && prefs.getBoolean("local_adb_connected", false)

        val onlinePaths = JSONArray()
        if (secureOnline) onlinePaths.put("secure_relay")
        if (legacyOnline) onlinePaths.put("legacy_websocket")
        if (adbOnline) onlinePaths.put("local_adb")

        val configuredPaths = JSONArray()
        if (secureConfigured) configuredPaths.put("secure_relay")
        if (legacyConfigured) configuredPaths.put("legacy_websocket")
        if (adbPaired) configuredPaths.put("local_adb")

        val online = onlinePaths.length() > 0
        val state = when {
            prefs.getBoolean("pairing_disabled_by_user", false) -> "DISABLED_BY_USER"
            online -> "ONLINE"
            configuredPaths.length() == 0 -> "UNCONFIGURED"
            else -> "RECOVERING"
        }

        prefs.edit()
            .putString("execution_fabric_state", state)
            .putLong("execution_fabric_checked_at", System.currentTimeMillis())
            .apply()

        val mesh = HakimConnectivityMesh.status(app)

        return JSONObject()
            .put("execution_fabric", true)
            .put("version", VERSION)
            .put("state", state)
            .put("online", online)
            .put("online_paths", onlinePaths)
            .put("configured_paths", configuredPaths)
            .put("mesh", mesh)
            .put("preferred_path", mesh.optString("preferred_path", ""))
            .put("hot_redundancy", mesh.optBoolean("hot_redundancy", false))
            .put("secure_relay_configured", secureConfigured)
            .put("secure_relay_running", HakimUnifiedRelay.isRunning())
            .put("secure_relay_connected", secureOnline)
            .put("legacy_configured", legacyConfigured)
            .put("legacy_service_running", HakimService.running)
            .put("legacy_connected", legacyOnline)
            .put("local_adb_paired", adbPaired)
            .put("local_adb_connected", adbOnline)
            .put("last_recover_at", prefs.getLong("execution_fabric_recover_at", 0L))
            .put("last_reason", prefs.getString("execution_fabric_reason", ""))
            .put("service_start", prefs.getString("execution_fabric_service_start", ""))
            .put("last_start_error", prefs.getString("execution_fabric_last_start_error", ""))
            .put("online_requires_live_path", true)
            .put("single_path_failure_does_not_close_goal", true)
    }
}
