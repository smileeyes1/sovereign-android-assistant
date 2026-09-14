package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import org.json.JSONArray

/** لا يسمح بإخراج بيانات الخزنة إلا داخل متصفح حكيم وعلى موقع اعتمده المستخدم. */
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
        val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(TRUSTED_HOSTS, emptySet()) ?: emptySet()
        return candidates(host).any { it in set }
    }

    fun currentHost(context: Context): String {
        val raw = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getString("last_url", "").orEmpty()
        return normalizeHost(raw).orEmpty()
    }

    fun canUseProfile(context: Context, snapshot: JSONArray): Boolean {
        val packages = linkedSetOf<String>()
        for (i in 0 until snapshot.length()) {
            val pkg = snapshot.optJSONObject(i)?.optString("package").orEmpty().trim()
            if (pkg.isNotBlank()) packages += pkg
        }
        // تعبئة البيانات محصورة في واجهة حكيم نفسها. دعم تطبيقات خارجية يحتاج ثقة منفصلة لاحقًا.
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

    private fun candidates(host: String): List<String> {
        val parts = host.split('.').filter { it.isNotBlank() }
        val out = mutableListOf(host)
        if (parts.size >= 2) out += parts.takeLast(2).joinToString(".")
        return out.distinct()
    }
}
