package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import org.json.JSONArray

/** لا يسمح بإخراج بيانات الخزنة إلا داخل متصفح حكيم وعلى المضيف الدقيق الذي اعتمده المستخدم. */
object HakimSiteTrust {
    private const val PREFS = "hakim_site_trust"
    private const val TRUSTED_HOSTS = "trusted_profile_hosts"

    fun setTrusted(context: Context, rawHost: String, trusted: Boolean): Boolean {
        val host = normalizeHost(rawHost) ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = LinkedHashSet(prefs.getStringSet(TRUSTED_HOSTS, emptySet()) ?: emptySet())
        if (trusted) set += host else set.remove(host)
        prefs.edit().putStringSet(TRUSTED_HOSTS, set).apply()
        return true
    }

    fun isTrusted(context: Context, rawHost: String): Boolean {
        val host = normalizeHost(rawHost) ?: return false
        return host in trustedHosts(context)
    }

    fun trustedHosts(context: Context): Set<String> =
        LinkedHashSet(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(TRUSTED_HOSTS, emptySet()) ?: emptySet())

    fun replaceTrustedHosts(context: Context, rawHosts: Set<String>): Boolean {
        val normalized = linkedSetOf<String>()
        for (raw in rawHosts) {
            val host = normalizeHost(raw) ?: return false
            normalized += host
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(TRUSTED_HOSTS, normalized)
            .apply()
        return true
    }

    fun currentHost(context: Context): String {
        val live = HakimRuntime.visibleWebView()?.url.orEmpty()
        normalizeHost(live)?.let { return it }
        val stored = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getString("last_url", "").orEmpty()
        return normalizeHost(stored).orEmpty()
    }

    fun canUseProfile(context: Context, snapshot: JSONArray): Boolean {
        val packages = linkedSetOf<String>()
        for (i in 0 until snapshot.length()) {
            val pkg = snapshot.optJSONObject(i)?.optString("package").orEmpty().trim()
            if (pkg.isNotBlank()) packages += pkg
        }
        if (packages.isNotEmpty() && packages.any { !it.startsWith("ps.hakim.stable") }) return false
        val host = currentHost(context)
        return host.isNotBlank() && isTrusted(context, host)
    }

    private fun normalizeHost(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isBlank()) return null
        if (s.startsWith("http://") || s.startsWith("https://")) {
            s = runCatching { Uri.parse(s).host.orEmpty() }.getOrDefault("")
        }
        s = s.removePrefix("www.").trim('.').trim()
        if (!Regex("^[a-z0-9.-]{3,253}$").matches(s) || !s.contains('.')) return null
        return s
    }
}
