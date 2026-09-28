package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * طبقة التحكم بالأجهزة المملوكة/المأذونة داخل LAN.
 * عنوان IP يبقى محليًا على الهاتف؛ السحابة ترى device_id مشتقًا محليًا فقط.
 * الاكتشاف قرائي، والاعتماد/التحكم يمران عبر بوابة موافقة HakimUnifiedRelay.
 */
object HakimAuthorizedLanControl {
    const val VERSION = "AUTHORIZED-LAN-CONTROL-2026-09-28-v1"
    private const val PREFS = "hakim_authorized_lan"
    private const val MAX_STALE_MS = 24L * 60L * 60L * 1000L
    private val DEVICE_ID = Regex("^lan-[0-9a-f]{16}$")
    private val ACTIONS = setOf(
        "home","back","up","down","left","right","enter",
        "play_pause","volume_up","volume_down","mute",
        "open_url","launch_package"
    )

    fun discover(context: Context, force: Boolean = false): JSONObject {
        val survey = HakimLanSurvey.inspect(context, force)
        if (!survey.optBoolean("ok", false)) {
            return JSONObject().put("ok", false).put("version", VERSION)
                .put("error", survey.optString("error", "lan_survey_failed"))
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        val hosts = survey.optJSONArray("hosts") ?: JSONArray()
        val devices = JSONArray()
        var authorizedCount = 0
        for (i in 0 until hosts.length()) {
            val host = hosts.optJSONObject(i) ?: continue
            val ip = host.optString("ip")
            if (!isPrivateIpv4(ip)) continue
            val id = deviceId(ip)
            val ports = host.optJSONArray("open_ports") ?: JSONArray()
            val adb = containsInt(ports, 5555)
            val adapters = JSONArray()
            if (adb) adapters.put("adb")
            editor.putString("$id.ip", ip)
                .putLong("$id.last_seen_ms", now)
                .putString("$id.role_hint", host.optString("role_hint"))
                .putString("$id.vendor_hint", host.optString("vendor_hint"))
                .putString("$id.model_hint", host.optString("model_hint"))
            val authorized = adb && prefs.getBoolean("$id.authorized.adb", false)
            if (authorized) authorizedCount += 1
            devices.put(JSONObject()
                .put("device_id", id)
                .put("role_hint", host.optString("role_hint"))
                .put("vendor_hint", host.optString("vendor_hint"))
                .put("model_hint", host.optString("model_hint"))
                .put("control_adapters", adapters)
                .put("authorized", authorized)
                .put("last_seen_ms", now))
        }
        editor.apply()
        return JSONObject().put("ok", true).put("version", VERSION)
            .put("devices", devices)
            .put("device_count", devices.length())
            .put("authorized_count", authorizedCount)
            .put("privacy", "ip_local_only")
    }

    fun authorize(context: Context, payload: JSONObject): JSONObject {
        val id = payload.optString("device_id").trim()
        val adapter = payload.optString("adapter", "adb").trim()
        if (!DEVICE_ID.matches(id)) return error("invalid_device_id")
        if (adapter != "adb") return error("unsupported_adapter")
        val fresh = discover(context, force = true)
        val device = findDevice(fresh.optJSONArray("devices"), id)
            ?: return error("device_not_currently_discovered")
        if (!jsonArrayContains(device.optJSONArray("control_adapters"), "adb"))
            return error("adb_not_available")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ip = prefs.getString("$id.ip", "").orEmpty()
        if (!isPrivateIpv4(ip)) return error("invalid_local_target")
        prefs.edit().putBoolean("$id.authorized.adb", true)
            .putLong("$id.authorized_at_ms", System.currentTimeMillis()).apply()
        return JSONObject().put("ok", true).put("device_id", id)
            .put("adapter", "adb").put("status", "authorized")
            .put("note", "target_adb_may_still_require_on_device_key_approval")
    }

    fun control(context: Context, payload: JSONObject): JSONObject {
        val id = payload.optString("device_id").trim()
        val action = payload.optString("action").trim()
        if (!DEVICE_ID.matches(id)) return error("invalid_device_id")
        if (!ACTIONS.contains(action)) return error("unsupported_action")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("$id.authorized.adb", false)) return error("device_not_authorized")
        val ip = prefs.getString("$id.ip", "").orEmpty()
        val seen = prefs.getLong("$id.last_seen_ms", 0L)
        if (!isPrivateIpv4(ip)) return error("invalid_local_target")
        if (seen <= 0L || System.currentTimeMillis() - seen > MAX_STALE_MS)
            return error("device_mapping_stale")
        val url = payload.optString("url").takeIf { it.isNotBlank() }
        val packageName = payload.optString("package").takeIf { it.isNotBlank() }
        val result = HakimAdbConnectionManager.remote(context)
            .executeRemoteAction(ip, 5555, action, url, packageName)
        return JSONObject().put("ok", result.ok).put("device_id", id)
            .put("action", action).put("adapter", "adb")
            .put("error", result.error ?: JSONObject.NULL)
            .put("output", result.output?.take(800) ?: "")
    }

    fun status(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val authorized = prefs.all.keys.count {
            it.endsWith(".authorized.adb") && prefs.getBoolean(it, false)
        }
        return JSONObject().put("version", VERSION)
            .put("authorized_adb_devices", authorized)
            .put("raw_ip_exposed", false)
            .put("arbitrary_shell_exposed", false)
    }

    private fun findDevice(devices: JSONArray?, id: String): JSONObject? {
        if (devices == null) return null
        for (i in 0 until devices.length()) {
            val item = devices.optJSONObject(i) ?: continue
            if (item.optString("device_id") == id) return item
        }
        return null
    }

    private fun deviceId(ip: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("HAKIM-LAN-v1\u0000" + ip).toByteArray(Charsets.UTF_8))
        val short = digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "lan-$short"
    }

    private fun containsInt(array: JSONArray, value: Int): Boolean {
        for (i in 0 until array.length()) if (array.optInt(i) == value) return true
        return false
    }

    private fun jsonArrayContains(array: JSONArray?, value: String): Boolean {
        if (array == null) return false
        for (i in 0 until array.length()) if (array.optString(i) == value) return true
        return false
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    private fun error(code: String): JSONObject =
        JSONObject().put("ok", false).put("version", VERSION).put("error", code)
}
