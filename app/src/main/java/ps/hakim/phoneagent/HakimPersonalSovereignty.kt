package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * عقد السيادة الشخصية: يحفظ ما يصرّح به المستخدم عن قصده وقيمه وأهدافه محليًا ومشفّرًا.
 * لا يدّعي حفظ الروح/الدماغ حرفيًا، ولا يصدّر السمات الحساسة إلى مزود خارجي دون إذن صريح.
 */
object HakimPersonalSovereignty {
    const val VERSION = "PERSONAL-SOVEREIGNTY-2026-09-15-v1"
    private const val SECURE_PREFS = "hakim_personal_sovereignty_secure"
    private const val KEY_CHARTER = "personal_charter_json"
    private const val META_PREFS = "hakim_personal_sovereignty_meta"
    private const val MAX_CHARTER_CHARS = 24_000

    enum class Source { EXPLICIT_CURRENT, EXPLICIT_STABLE, CORRECTION, CONTEXT, INFERENCE }
    enum class Certainty { CERTAIN, HIGH, MEDIUM, LOW }

    data class Understanding(
        val source: Source,
        val certainty: Certainty,
        val mayExecuteReversible: Boolean,
        val mayExecuteHighImpact: Boolean,
        val reason: String
    )

    fun saveExplicitCharter(context: Context, charter: JSONObject): Boolean {
        val safe = sanitizeCharter(charter) ?: return false
        val ok = HakimSecureStore.put(context, SECURE_PREFS, KEY_CHARTER, safe.toString())
        if (ok) {
            context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit()
                .putLong("updated_at", System.currentTimeMillis())
                .putInt("schema_version", 1)
                .putBoolean("encrypted_local_only", true)
                .putBoolean("external_export_default", false)
                .apply()
        }
        return ok
    }

    fun loadCharter(context: Context): JSONObject? {
        val raw = HakimSecureStore.get(context, SECURE_PREFS, KEY_CHARTER).orEmpty()
        if (raw.isBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun clearCharter(context: Context): Boolean {
        HakimSecureStore.remove(context, SECURE_PREFS, KEY_CHARTER)
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit()
            .putLong("cleared_at", System.currentTimeMillis())
            .apply()
        return loadCharter(context) == null
    }

    fun classifyUnderstanding(source: Source, highImpact: Boolean): Understanding {
        val certainty = when (source) {
            Source.EXPLICIT_CURRENT, Source.CORRECTION -> Certainty.CERTAIN
            Source.EXPLICIT_STABLE -> Certainty.HIGH
            Source.CONTEXT -> Certainty.MEDIUM
            Source.INFERENCE -> Certainty.LOW
        }
        val reversible = certainty != Certainty.LOW || !highImpact
        val highImpactAllowed = !highImpact || certainty == Certainty.CERTAIN
        val reason = when {
            highImpact && certainty != Certainty.CERTAIN -> "الأثر مرتفع ولا يكفي فيه الاستنتاج؛ يلزم قصد صريح حالي أو تصحيح صريح"
            certainty == Certainty.LOW -> "هذا استنتاج فقط؛ يجوز استخدامه لخطوة قابلة للتراجع بعد التحقق ولا يغيّر قرارًا سياديًا"
            else -> "درجة الفهم مناسبة لنوع الأثر مع بقاء التحقق من النتيجة"
        }
        return Understanding(source, certainty, reversible, highImpactAllowed, reason)
    }

    fun externalSafeContext(context: Context): String {
        val charter = loadCharter(context) ?: return ""
        val publicKeys = listOf("purpose", "goals", "dreams", "service", "preferences", "boundaries")
        val lines = mutableListOf<String>()
        for (key in publicKeys) {
            val value = charter.optString(key).trim()
            if (value.isNotBlank()) lines += "• $key: ${value.take(1200)}"
        }
        if (lines.isEmpty()) return ""
        return buildString {
            appendLine("[مقتطف اختياري آمن من عقد المستخدم]")
            appendLine("هذا ليس ذاكرة كاملة للإنسان ولا تمثيلًا للروح/الدماغ؛ هو نص صرّح به المستخدم ويظل تصحيحه الصريح أعلى منه.")
            lines.forEach(::appendLine)
        }.take(5000)
    }

    fun status(context: Context): JSONObject {
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val present = loadCharter(context) != null
        return JSONObject()
            .put("version", VERSION)
            .put("charter_present", present)
            .put("encrypted_local_only", meta.getBoolean("encrypted_local_only", true))
            .put("external_export_default", meta.getBoolean("external_export_default", false))
            .put("explicit_over_inference", true)
            .put("correction_over_previous", true)
            .put("high_impact_requires_explicit_current_intent", true)
            .put("sensitive_traits_not_exported_by_default", true)
            .put("human_not_reduced_to_memory_model", true)
            .put("soul_or_brain_literal_copy_claim", false)
            .put("updated_at", meta.getLong("updated_at", 0L))
    }

    private fun sanitizeCharter(raw: JSONObject): JSONObject? {
        val out = JSONObject()
        val allowed = setOf(
            "purpose", "intent", "will", "resolve", "dignity", "heart_values",
            "protection", "service", "dreams", "goals", "humility", "righteousness",
            "guidance", "good", "memory_preferences", "reasoning_preferences",
            "faith_commitments", "boundaries", "preferences"
        )
        for (key in raw.keys()) {
            if (key !in allowed) continue
            val value = raw.optString(key).trim()
            if (value.isBlank()) continue
            val safe = HakimGovernanceStore.exportSafeText(value).take(4000)
            if (safe.isNotBlank()) out.put(key, safe)
        }
        val encoded = out.toString()
        if (encoded.length > MAX_CHARTER_CHARS) return null
        return out
    }
    fun captureExplicitDeclaration(context: Context, raw: String, source: String = "hakim_chat"): Boolean {
        val text = raw.trim()
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val persistence = listOf("احفظ", "حفظ", "ثبت", "ثبّت", "قاعدة", "ذاكرة", "محفوظ")
        val personal = listOf("مقصدي", "نيتي", "إرادتي", "ارادتي", "عزمي", "قلبي", "أحلامي", "احلامي", "تواضعي", "هدايتي", "صالحي", "ذاكرتي", "عقلي", "روحي")
        if (persistence.none { lower.contains(it) } || personal.none { lower.contains(it) }) return false
        val existing = loadCharter(context) ?: JSONObject()
        val safeText = HakimGovernanceStore.exportSafeText(text).take(12_000)
        if (safeText.isBlank()) return false
        existing.put("intent", safeText)
        val ok = saveExplicitCharter(context, existing)
        if (ok) {
            context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_capture_source", source.take(60))
                .putLong("last_capture_at", System.currentTimeMillis())
                .apply()
        }
        return ok
    }

}

