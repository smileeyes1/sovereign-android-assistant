package ps.hakim.phoneagent

import android.app.Activity
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * مصنع مهارات محلي مقيد: يولد DSL تصريحيًا من العمليات المعتمدة فقط، لا كودًا أصليًا حرًا.
 * أي حاجة تتطلب Kotlin/صلاحية/مكتبة جديدة تتحول إلى NEEDS_SIGNED_BUILD ولا تُنفذ على الجهاز ذاتيًا.
 */
object HakimSkillFactory {
    const val VERSION = "SKILL-FACTORY-2026-09-16-v1"
    private const val PREFS = "hakim_skill_factory"
    private const val KEY_SKILLS = "verified_skills"
    private const val MAX_SKILLS = 24
    private val allowedActions = setOf("navigate", "click", "type", "select", "scroll", "verify_text", "back", "reload", "wait")
    private val sensitive = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)")

    data class Validation(val ok: Boolean, val status: String, val reason: String)

    fun proposeAndRegister(context: Context, title: String, need: String, steps: JSONArray): Validation {
        val validation = sandboxValidate(steps)
        if (!validation.ok) return validation
        val id = "skill." + sha256((title + "\n" + need + "\n" + steps.toString()).take(16000)).take(16)
        val record = JSONObject()
            .put("id", id)
            .put("title", title.take(100))
            .put("need", redact(need).take(500))
            .put("steps", steps)
            .put("status", "VERIFIED")
            .put("verified_at", System.currentTimeMillis())
            .put("permission_expansion", false)
            .put("native_code_execution", false)
        val skills = read(context).toMutableList().filterNot { it.optString("id") == id }.toMutableList()
        skills += record
        write(context, skills.takeLast(MAX_SKILLS))
        return Validation(true, "VERIFIED", id)
    }

    fun sandboxValidate(steps: JSONArray): Validation {
        if (steps.length() !in 1..24) return Validation(false, "REJECTED", "عدد الخطوات خارج الحد الآمن")
        for (i in 0 until steps.length()) {
            val s = steps.optJSONObject(i) ?: return Validation(false, "REJECTED", "الخطوة $i ليست كائنًا")
            val action = s.optString("action").lowercase()
            if (action !in allowedActions) return Validation(false, "REJECTED", "فعل غير مسموح: $action")
            val target = s.optString("target")
            val valueRef = s.optString("value_ref")
            if (s.has("value") && s.optString("value").isNotBlank()) return Validation(false, "REJECTED", "القيم الخام لا تُحفظ داخل المهارة؛ استخدم value_ref")
            if (sensitive.containsMatchIn(target) || sensitive.containsMatchIn(valueRef)) return Validation(false, "REJECTED", "المهارة لا تخزن أو تستهدف أسرارًا")
            if (action == "navigate") {
                val url = s.optString("url")
                val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return Validation(false, "REJECTED", "URL غير صالح")
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return Validation(false, "REJECTED", "التنقل محصور في http/https")
                val query = uri.query.orEmpty()
                if (sensitive.containsMatchIn(query)) return Validation(false, "REJECTED", "URL يحتوي معاملًا قد يكون سريًا")
            }
            if (action in setOf("type", "select") && valueRef.isBlank()) return Validation(false, "REJECTED", "الكتابة/التحديد يتطلب value_ref عابرًا")
            if (action == "wait" && s.optLong("ms", 0L) !in 50L..5000L) return Validation(false, "REJECTED", "زمن الانتظار خارج الحد")
        }
        return Validation(true, "SANDBOX_PASS", "كل الخطوات ضمن DSL المأذون وقابلة للإيقاف والتراجع")
    }

    fun run(
        activity: Activity,
        id: String,
        transientValues: Map<String, String> = emptyMap(),
        onProgress: (String) -> Unit = {},
        onComplete: (Boolean, String) -> Unit
    ) {
        val skill = read(activity).firstOrNull { it.optString("id") == id && it.optString("status") == "VERIFIED" }
        if (skill == null) { onComplete(false, "المهارة غير موجودة أو غير متحققة"); return }
        val steps = skill.optJSONArray("steps") ?: JSONArray()
        val validation = sandboxValidate(steps)
        if (!validation.ok) { onComplete(false, validation.reason); return }

        var index = 0
        fun next() {
            if (HakimMissionLedger.isCancelled(activity)) { onComplete(false, "المهمة متوقفة بأمر المستخدم"); return }
            if (index >= steps.length()) { onComplete(true, "اكتملت المهارة المتحققة"); return }
            val s = steps.optJSONObject(index++) ?: return next()
            val action = s.optString("action")
            if (action == "wait") {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ next() }, s.optLong("ms", 250L))
                return
            }
            val value = s.optString("value_ref").takeIf { it.isNotBlank() }?.let { transientValues[it] }.orEmpty()
            val request = HakimSovereignBrowserAgent.Action(
                type = HakimSovereignBrowserAgent.ActionType.fromDsl(action),
                target = s.optString("target"),
                value = value,
                url = s.optString("url"),
                dx = s.optInt("dx", 0),
                dy = s.optInt("dy", 0)
            )
            onProgress("المهارة: ${skill.optString("title")} — خطوة $index/${steps.length()}")
            HakimSovereignBrowserAgent.execute(activity, request) { result ->
                if (!result.success) onComplete(false, result.evidence) else next()
            }
        }
        next()
    }

    fun verifiedToolSpecs(context: Context): List<HakimSovereignToolRegistry.ToolSpec> = read(context)
        .filter { it.optString("status") == "VERIFIED" }
        .map { skill ->
            HakimSovereignToolRegistry.ToolSpec(
                id = skill.optString("id"),
                title = skill.optString("title").ifBlank { "مهارة متحققة" },
                capabilities = listOf("declarative_browser_skill", "verified_before_use", "transient_values_only"),
                permissionRequirements = listOf("ترث صلاحيات الأدوات الأساسية فقط؛ لا توسعها"),
                successCondition = "كل خطوات المهارة تحقق postcondition",
                verification = "إعادة فحص الحالة بعد كل خطوة",
                rollback = "Stop ثم Back/إعادة التخطيط حسب الخطوة",
                impact = HakimSovereignToolRegistry.Impact.REVERSIBLE_LOCAL,
                localFirst = true,
                freeFirst = true,
                available = true,
                source = "verified_skill"
            )
        }

    fun unregister(context: Context, id: String): Boolean {
        val old = read(context)
        val newer = old.filterNot { it.optString("id") == id }
        if (newer.size == old.size) return false
        write(context, newer)
        return true
    }

    fun nativeGapDisposition(requirement: String): JSONObject = JSONObject()
        .put("requirement", redact(requirement).take(600))
        .put("status", "NEEDS_SIGNED_BUILD")
        .put("reason", "Kotlin/مكتبة/صلاحية جديدة لا تُحمّل أو تُنفذ ذاتيًا على الجهاز؛ تُبنى وتُختبر وتُوقّع كتحديث مستقل")
        .put("automatic_permission_expansion", false)
        .put("arbitrary_javascript_execution", false)

    private fun read(context: Context): List<JSONObject> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SKILLS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.takeLast(MAX_SKILLS)
    }

    private fun write(context: Context, skills: List<JSONObject>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SKILLS, JSONArray(skills).toString())
            .apply()
    }

    private fun redact(v: String): String = sensitive.replace(v) { "[محجوب]" }
    private fun sha256(v: String): String = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }

    fun status(context: Context): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("verified_skill_count", read(context).count { it.optString("status") == "VERIFIED" })
        .put("dsl_only_on_device", true)
        .put("arbitrary_native_code_execution", false)
        .put("arbitrary_javascript_execution", false)
        .put("test_before_register", true)
        .put("permission_expansion_automatic", false)
        .put("native_gap_requires_signed_build", true)
}