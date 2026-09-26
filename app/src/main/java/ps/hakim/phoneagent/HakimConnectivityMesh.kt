package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-path connectivity fabric.
 *
 * It does not open inbound sockets or bypass Android/network policy. It scores
 * only configured, authorized paths and exposes the best live route while
 * keeping independent fallbacks available.
 */
object HakimConnectivityMesh {
    const val VERSION = "CONNECTIVITY-MESH-2026-09-26-v1"

    private const val PREFS = "hakim_connectivity_mesh"
    private const val RECOVERY_DEBOUNCE_MS = 10_000L
    private const val PATH_STALE_MS = 120_000L

    data class NetworkProfile(
        val available: Boolean,
        val validated: Boolean,
        val wifi: Boolean,
        val cellular: Boolean,
        val vpn: Boolean,
        val unmetered: Boolean
    )

    fun networkProfile(context: Context): NetworkProfile {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        return NetworkProfile(
            available = network != null && caps != null,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        )
    }

    fun mayRecover(context: Context, reason: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = p.getLong("last_recovery_requested_at", 0L)
        if (now - last < RECOVERY_DEBOUNCE_MS) {
            p.edit()
                .putString("last_suppressed_reason", reason.take(80))
                .putLong("last_suppressed_at", now)
                .apply()
            return false
        }
        p.edit()
            .putLong("last_recovery_requested_at", now)
            .putString("last_recovery_reason", reason.take(80))
            .apply()
        return true
    }

    fun status(context: Context): JSONObject {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val network = networkProfile(app)
        val now = System.currentTimeMillis()

        val secureConfigured = HakimUnifiedRelay.isConfigured(app)
        val secureSeenAt = prefs.getLong("secure_relay_seen_at", 0L)
        val secureOnline = secureConfigured &&
            HakimUnifiedRelay.isConnected() &&
            (secureSeenAt == 0L || now - secureSeenAt <= PATH_STALE_MS)

        val legacyConfigured =
            prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            prefs.getString("result_topic", "").orEmpty().isNotBlank()
        val legacyOnline = legacyConfigured && HakimService.connected

        val adbPaired = prefs.getBoolean("local_adb_paired", false)
        val adbOnline = adbPaired &&
            network.wifi &&
            prefs.getBoolean("local_adb_connected", false)

        val paths = JSONArray()
        paths.put(path(
            name = "secure_relay",
            configured = secureConfigured,
            online = secureOnline,
            priority = 100,
            role = "primary_remote",
            transport = "outbound_tls",
            mutableActionsNeedApproval = true
        ))
        paths.put(path(
            name = "local_adb",
            configured = adbPaired,
            online = adbOnline,
            priority = 80,
            role = "local_maintenance",
            transport = "paired_local_wifi",
            mutableActionsNeedApproval = true
        ))
        paths.put(path(
            name = "legacy_websocket",
            configured = legacyConfigured,
            online = legacyOnline,
            priority = 40,
            role = "last_resort_compatibility",
            transport = "authenticated_legacy",
            mutableActionsNeedApproval = true
        ))

        val preferred = when {
            secureOnline -> "secure_relay"
            adbOnline -> "local_adb"
            legacyOnline -> "legacy_websocket"
            else -> ""
        }

        val onlineCount = listOf(secureOnline, adbOnline, legacyOnline).count { it }
        val configuredCount = listOf(secureConfigured, adbPaired, legacyConfigured).count { it }
        val state = when {
            preferred.isNotBlank() && onlineCount > 1 -> "REDUNDANT_ONLINE"
            preferred.isNotBlank() -> "ONLINE"
            configuredCount > 0 && network.available -> "RECOVERING"
            configuredCount > 0 -> "WAITING_NETWORK"
            else -> "UNCONFIGURED"
        }

        if (preferred.isNotBlank()) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_good_path", preferred)
                .putLong("last_good_path_at", now)
                .apply()
        }

        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", VERSION)
            .put("state", state)
            .put("preferred_path", preferred)
            .put("online_path_count", onlineCount)
            .put("configured_path_count", configuredCount)
            .put("hot_redundancy", onlineCount > 1)
            .put("paths", paths)
            .put("network", JSONObject()
                .put("available", network.available)
                .put("validated", network.validated)
                .put("wifi", network.wifi)
                .put("cellular", network.cellular)
                .put("vpn", network.vpn)
                .put("unmetered", network.unmetered)
            )
            .put("last_good_path", p.getString("last_good_path", ""))
            .put("last_good_path_at", p.getLong("last_good_path_at", 0L))
            .put("last_recovery_requested_at", p.getLong("last_recovery_requested_at", 0L))
            .put("outbound_cloud_only", true)
            .put("opens_inbound_listener", false)
            .put("local_adb_maintenance_only", true)
            .put("legacy_last_resort", true)
            .put("single_path_failure_does_not_close_goal", true)
    }

    private fun path(
        name: String,
        configured: Boolean,
        online: Boolean,
        priority: Int,
        role: String,
        transport: String,
        mutableActionsNeedApproval: Boolean
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("configured", configured)
        .put("online", online)
        .put("priority", priority)
        .put("role", role)
        .put("transport", transport)
        .put("mutable_actions_need_approval", mutableActionsNeedApproval)
}
