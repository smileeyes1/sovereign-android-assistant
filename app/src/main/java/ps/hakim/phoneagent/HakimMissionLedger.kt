package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * سجل مهمة واحدة نشطة فقط WIP=1. يحفظ الغاية المنقحة مشفرة ويحتفظ ببيانات الحالة فقط دون أسرار.
 * الاستدامة هنا تعني الاستعادة بعد الانقطاع وإعادة التقدير، لا تنفيذًا خفيًا بلا حدود.
 */
object HakimMissionLedger {
    enum class Phase {
        UNDERSTAND, PLAN, EXECUTE, VERIFY, RECOVER,
        WAITING_APPROVAL, WAITING_CREDENTIAL, WAITING_TRUST,
        COMPLETE, CANCELLED, BLOCKED
    }

    data class Mission(
        val id: String,
        val goal: String,
        val goalHash: String,
        val phase: Phase,
        val startedAt: Long,
        val updatedAt: Long,
        val attempts: Int,
        val failures: Int,
        val evidence: String
    )

    private const val PREFS = "hakim_mission_meta"
    private const val SECURE_PREFS = "hakim_mission_secure"
    private const val GOAL_KEY = "active_goal"

    fun beginOrResume(context: Context, rawGoal: String): Mission {
        val clean = sanitizeGoal(rawGoal).ifBlank { "استمرار المهمة الحالية" }.take(5000)
        val hash = sha256(clean)
        val current = active(context)
        // BLOCKED/CANCELLED يبقيان حاجزًا لنفس الغاية؛ لا تعيد إنشاء المهمة لتصفير الحالة.
        // رفع CANCELLED لا يحدث إلا بطلب استمرار صريح عبر resumeCancelledByUser.
        if (current != null && current.goalHash == hash && current.phase != Phase.COMPLETE) return current

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        HakimSecureStore.put(context, SECURE_PREFS, GOAL_KEY, clean)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("id", id)
            .putString("goal_hash", hash)
            .putString("phase", Phase.UNDERSTAND.name)
            .putLong("started_at", now)
            .putLong("updated_at", now)
            .putInt("attempts", 0)
            .putInt("failures", 0)
            .putString("evidence", "")
            .putBoolean("active", true)
            .apply()
        return active(context)!!
    }

    /**
     * يعكس إلغاء المستخدم فقط عندما يطلب المستخدم نفسه الاستمرار صراحةً لاحقًا.
     * لا تستعمله الخلفية أو المبادرة الذاتية، ولذلك يبقى CANCELLED غير قابل للاستئناف التلقائي.
     */
    fun resumeCancelledByUser(context: Context, evidence: String = "استأنف المستخدم المهمة صراحة"): Mission? {
        val current = active(context) ?: return null
        if (current.phase != Phase.CANCELLED) return current
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("phase", Phase.RECOVER.name)
            .putString("evidence", sanitizeEvidence(evidence))
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
        return active(context)
    }

    fun active(context: Context): Mission? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return null
        val id = p.getString("id", "").orEmpty()
        val goal = HakimSecureStore.get(context, SECURE_PREFS, GOAL_KEY).orEmpty()
        if (id.isBlank() || goal.isBlank()) return null
        val phase = runCatching { Phase.valueOf(p.getString("phase", Phase.UNDERSTAND.name).orEmpty()) }
            .getOrDefault(Phase.UNDERSTAND)
        return Mission(
            id = id,
            goal = goal,
            goalHash = p.getString("goal_hash", sha256(goal)).orEmpty(),
            phase = phase,
            startedAt = p.getLong("started_at", 0L),
            updatedAt = p.getLong("updated_at", 0L),
            attempts = p.getInt("attempts", 0),
            failures = p.getInt("failures", 0),
            evidence = p.getString("evidence", "").orEmpty()
        )
    }

    fun progress(context: Context, phase: Phase, evidence: String = "", attempted: Boolean = false) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return
        if (p.getString("phase", "") == Phase.CANCELLED.name && phase != Phase.CANCELLED) return
        val edit = p.edit()
            .putString("phase", phase.name)
            .putLong("updated_at", System.currentTimeMillis())
        if (evidence.isNotBlank()) edit.putString("evidence", sanitizeEvidence(evidence))
        if (attempted) edit.putInt("attempts", p.getInt("attempts", 0) + 1)
        edit.apply()
    }

    fun failure(context: Context, reason: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return
        if (p.getString("phase", "") == Phase.CANCELLED.name) return
        p.edit()
            .putString("phase", Phase.RECOVER.name)
            .putInt("failures", p.getInt("failures", 0) + 1)
            .putString("evidence", sanitizeEvidence(reason))
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun complete(context: Context, evidence: String) {
        if (active(context)?.phase == Phase.CANCELLED) return
        progress(context, Phase.COMPLETE, evidence)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("active", false).apply()
    }

    fun cancel(context: Context, reason: String = "ألغى المستخدم المهمة") {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return
        p.edit()
            .putString("phase", Phase.CANCELLED.name)
            .putString("evidence", sanitizeEvidence(reason))
            .putLong("updated_at", System.currentTimeMillis())
            .putBoolean("active", true)
            .apply()
    }

    fun isCancelled(context: Context): Boolean = active(context)?.phase == Phase.CANCELLED

    fun block(context: Context, reason: String) {
        progress(context, Phase.BLOCKED, reason)
    }

    fun status(context: Context): JSONObject {
        val m = active(context)
        return JSONObject()
            .put("wip_limit", 1)
            .put("active", m != null)
            .put("id", m?.id ?: "")
            .put("phase", m?.phase?.name ?: "IDLE")
            .put("attempts", m?.attempts ?: 0)
            .put("failures", m?.failures ?: 0)
            .put("updated_at", m?.updatedAt ?: 0L)
            .put("encrypted_goal", true)
            .put("secret_redaction", true)
            .put("blocked_same_goal_persists", true)
            .put("user_cancel_is_sovereign", true)
            .put("cancel_never_auto_resumes", true)
            .put("explicit_user_continue_can_resume_cancelled", true)
    }

    private fun sanitizeGoal(v: String): String {
        var out = v
        out = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|api.?key|secret|كلمة\\s*المرور|رمز\\s*التحقق|رمز\\s*الأمان|مفتاح\\s*سري)\\s*[:=]?\\s*\\S+")
            .replace(out) { "${it.groupValues[1]}: [سري محذوف]" }
        out = Regex("(?<!\\d)\\d{13,19}(?!\\d)").replace(out, "[رقم حساس محذوف]")
        return out.trim()
    }

    private fun sanitizeEvidence(v: String): String = sanitizeGoal(v).replace(Regex("\\s+"), " ").take(1200)

    private fun sha256(v: String): String = MessageDigest.getInstance("SHA-256")
        .digest(v.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
