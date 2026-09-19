package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * سجل أعطال سببي محلي. الغاية ليست الادعاء أن البرمجيات لا تخطئ، بل منع الخطأ الصامت:
 * كشف → تصنيف → عدّ التكرار → تغذية التعافي/الفحص → منع إعلان الاكتمال عند عطل مادي متكرر.
 * لا يحفظ أسرارًا أو نصوص شاشة خامًا.
 */
object HakimFaultLedger {
    private const val PREFS = "hakim_fault_ledger"
    private const val MAX_EVENTS = 40

    enum class Kind { NETWORK, RESOURCE, AUTHORITY, SOURCE, STORAGE, EXECUTION, LOGIC, UNKNOWN }
    enum class Severity { INFO, WARNING, MATERIAL, CRITICAL }

    fun record(
        context: Context,
        scope: String,
        throwable: Throwable? = null,
        message: String = "",
        severity: Severity = Severity.MATERIAL
    ) {
        runCatching {
            val app = context.applicationContext
            val safeScope = sanitize(scope).take(96).ifBlank { "unknown" }
            val safeMessage = sanitize(message.ifBlank { throwable?.message.orEmpty() }).take(260)
            val kind = classify(safeScope, safeMessage, throwable)
            val fingerprint = fingerprint("$safeScope|${throwable?.javaClass?.simpleName.orEmpty()}|$safeMessage")
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString("events", "[]").orEmpty()
            val events = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            val counts = runCatching { JSONObject(prefs.getString("counts", "{}").orEmpty()) }.getOrElse { JSONObject() }
            val count = counts.optInt(fingerprint, 0) + 1
            counts.put(fingerprint, count)
            events.put(
                JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("scope", safeScope)
                    .put("kind", kind.name)
                    .put("severity", severity.name)
                    .put("fingerprint", fingerprint)
                    .put("repeat_count", count)
                    .put("exception", throwable?.javaClass?.simpleName.orEmpty().take(80))
                    .put("message", safeMessage)
            )
            while (events.length() > MAX_EVENTS) events.remove(0)
            prefs.edit()
                .putString("events", events.toString())
                .putString("counts", counts.toString())
                .putLong("last_fault_at", System.currentTimeMillis())
                .putString("last_fault_scope", safeScope)
                .putString("last_fault_fingerprint", fingerprint)
                .apply()
        }
    }

    fun resolve(context: Context, scope: String, evidence: String = "") {
        runCatching {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit()
                .putLong("last_resolution_at", System.currentTimeMillis())
                .putString("last_resolution_scope", sanitize(scope).take(96))
                .putString("last_resolution_evidence", sanitize(evidence).take(220))
                .apply()
        }
    }

    fun repeatedMaterialFault(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]").orEmpty()) }.getOrElse { JSONArray() }
        for (i in (events.length() - 1) downTo 0) {
            val e = events.optJSONObject(i) ?: continue
            val severity = e.optString("severity")
            if ((severity == Severity.MATERIAL.name || severity == Severity.CRITICAL.name) && e.optInt("repeat_count", 0) >= 3) return true
        }
        return false
    }

    fun status(context: Context): JSONObject {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("events", "[]").orEmpty()) }.getOrElse { JSONArray() }
        var material = 0
        var critical = 0
        for (i in 0 until events.length()) {
            when (events.optJSONObject(i)?.optString("severity")) {
                Severity.MATERIAL.name -> material++
                Severity.CRITICAL.name -> critical++
            }
        }
        return JSONObject()
            .put("fault_ledger", true)
            .put("silent_failure_forbidden", true)
            .put("root_cause_required_on_repeat", true)
            .put("secret_redaction", true)
            .put("recent_event_count", events.length())
            .put("material_event_count", material)
            .put("critical_event_count", critical)
            .put("repeated_material_fault", repeatedMaterialFault(context))
            .put("last_fault_at", p.getLong("last_fault_at", 0L))
            .put("last_fault_scope", p.getString("last_fault_scope", ""))
            .put("last_resolution_at", p.getLong("last_resolution_at", 0L))
            .put("last_resolution_scope", p.getString("last_resolution_scope", ""))
    }

    private fun classify(scope: String, message: String, throwable: Throwable?): Kind {
        val s = "$scope $message ${throwable?.javaClass?.simpleName.orEmpty()}".lowercase()
        return when {
            listOf("network", "socket", "http", "dns", "connect").any { s.contains(it) } -> Kind.NETWORK
            listOf("memory", "thermal", "battery", "resource", "oom").any { s.contains(it) } -> Kind.RESOURCE
            listOf("permission", "authority", "approval", "securityexception").any { s.contains(it) } -> Kind.AUTHORITY
            listOf("quran", "source", "corpus", "mushaf", "مصحف", "قرآن", "قرءان").any { s.contains(it) } -> Kind.SOURCE
            listOf("store", "database", "sqlite", "keystore", "storage").any { s.contains(it) } -> Kind.STORAGE
            listOf("execute", "action", "webview", "browser", "adb").any { s.contains(it) } -> Kind.EXECUTION
            listOf("state", "logic", "invariant", "assert").any { s.contains(it) } -> Kind.LOGIC
            else -> Kind.UNKNOWN
        }
    }

    private fun sanitize(raw: String): String {
        var out = raw.replace(Regex("[\\r\\n\\t]+"), " ").trim()
        out = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|api.?key|token|secret|كلمة\\s*المرور|رمز\\s*التحقق|رمز\\s*الأمان|مفتاح\\s*سري)\\s*[:=]?\\s*[^ ,;]{2,}")
            .replace(out, "[سر محجوب]")
        out = Regex("(?<!\\d)\\d{13,19}(?!\\d)").replace(out, "[رقم حساس محجوب]")
        return out
    }

    private fun fingerprint(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .take(10)
        .joinToString("") { "%02x".format(it) }
}
