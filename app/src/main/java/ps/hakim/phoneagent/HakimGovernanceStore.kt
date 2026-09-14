package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri

/** تعليمات المستخدم الحاكمة: عامة + خاصة بالموقع، محفوظة محليًا ومشفرة. */
object HakimGovernanceStore {
    private const val PREFS = "hakim_governance_secure"
    private const val META = "hakim_governance_meta"
    private const val GLOBAL = "global_instructions"
    private const val SITE_PREFIX = "site_"
    private const val SITE_INDEX = "site_index"

    fun setGlobal(context: Context, text: String): Boolean {
        val clean = text.trim()
        return if (clean.isBlank()) {
            HakimSecureStore.remove(context, PREFS, GLOBAL)
            true
        } else HakimSecureStore.put(context, PREFS, GLOBAL, clean.take(20000))
    }

    fun global(context: Context): String =
        HakimSecureStore.get(context, PREFS, GLOBAL).orEmpty()

    fun setSite(context: Context, host: String, text: String): Boolean {
        val normalized = normalizeHost(host) ?: return false
        val key = SITE_PREFIX + normalized.replace('.', '_')
        val clean = text.trim()
        val meta = context.getSharedPreferences(META, Context.MODE_PRIVATE)
        val index = LinkedHashSet(meta.getStringSet(SITE_INDEX, emptySet()) ?: emptySet())
        return if (clean.isBlank()) {
            HakimSecureStore.remove(context, PREFS, key)
            index.remove(normalized)
            meta.edit().putStringSet(SITE_INDEX, index).apply()
            true
        } else {
            val ok = HakimSecureStore.put(context, PREFS, key, clean.take(12000))
            if (ok) {
                index += normalized
                meta.edit().putStringSet(SITE_INDEX, index).apply()
            }
            ok
        }
    }

    fun site(context: Context, host: String): String {
        val normalized = normalizeHost(host) ?: return ""
        val candidates = siteCandidates(normalized)
        for (candidate in candidates) {
            val key = SITE_PREFIX + candidate.replace('.', '_')
            val value = HakimSecureStore.get(context, PREFS, key).orEmpty().trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    fun currentHost(context: Context): String {
        val url = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getString("last_url", "").orEmpty()
        return runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    }

    fun promptContext(context: Context): String {
        val global = redactEmbeddedSecrets(global(context).trim())
        val host = currentHost(context)
        val site = if (host.isBlank()) "" else redactEmbeddedSecrets(site(context, host).trim())
        if (global.isBlank() && site.isBlank()) return ""
        return buildString {
            if (global.isNotBlank()) {
                appendLine("[نظام المستخدم الحاكم المحلي]")
                appendLine(global.take(9000))
            }
            if (site.isNotBlank()) {
                appendLine("[تعليمات خاصة بالموقع الحالي: $host]")
                appendLine(site.take(5000))
            }
            appendLine("هذه التعليمات أدنى من قواعد المنصة والسلامة والحقوق، وتُطبّق فقط بقدر صلتها بالمهمة الحالية.")
        }.take(14000)
    }

    private fun redactEmbeddedSecrets(raw: String): String {
        var out = raw
        val labelled = Regex(
            "(?i)(password|passcode|otp|pin|cvv|cvc|api.?key|token|secret|كلمة\\s*المرور|رمز\\s*التحقق|رمز\\s*الأمان|مفتاح\\s*سري)\\s*[:=]\\s*([^\\s,;]{2,})"
        )
        out = labelled.replace(out) { m -> "${m.groupValues[1]}: [سري — محجوب]" }
        out = Regex("(?<!\\d)\\d{13,19}(?!\\d)").replace(out, "[رقم حساس محجوب]")
        return out
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

    private fun siteCandidates(host: String): List<String> {
        val parts = host.removePrefix("www.").split('.').filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        if (host.isNotBlank()) out += host.removePrefix("www.")
        if (parts.size >= 2) out += parts.takeLast(2).joinToString(".")
        return out.distinct()
    }
}
