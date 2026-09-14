package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * تعلم تكيفي محلي محكوم.
 * لا يولد أفعالًا أو صلاحيات جديدة، ولا يعدل الدستور أو الكود ذاتيًا.
 * وظيفته ترتيب البدائل الآمنة الموجودة أصلًا وفق دليل النجاح/الفشل الفعلي،
 * مع تعطيل ذاتي للتكيف إذا انخفض الأداء عن آخر خط أساس مثبت.
 */
object HakimAdaptiveLearning {
    private const val PREFS = "hakim_adaptive_learning"
    private const val MAX_RECENT_MISSIONS = 20
    private const val MIN_ACTION_TRIALS = 2
    private const val MIN_MISSION_TRIALS_FOR_PROMOTION = 5
    private const val REGRESSION_MARGIN = 0.15
    private const val COOLDOWN_MS = 6L * 60L * 60L * 1000L

    data class CandidateScore(
        val label: String,
        val trials: Int,
        val successes: Int,
        val score: Double
    )

    fun initialize(context: Context) {
        HakimQuranicInvariantKernel.requireInherited("adaptive_learning_initialize")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("created_at")) {
            p.edit()
                .putLong("created_at", System.currentTimeMillis())
                .putLong("epoch", 1L)
                .putBoolean("adaptive_enabled", true)
                .putDoubleCompat("best_mission_rate", -1.0)
                .putString("recent_missions", "[]")
                .apply()
        }
    }

    /**
     * يعيد ترتيب المرشحات نفسها فقط. لا يضيف زرًا ولا يزيل بوابة سلطة.
     * المرشح قليل الدليل يبقى قريبًا من ترتيب خط الأساس.
     */
    fun rankSafeCandidates(context: Context, baseline: List<String>): List<String> {
        initialize(context)
        if (baseline.size <= 1 || !isAdaptiveActive(context)) return baseline
        return baseline.withIndex()
            .map { indexed ->
                val stat = actionStat(context, indexed.value)
                val evidenceWeight = (stat.trials.coerceAtMost(8) / 8.0)
                val blended = (1.0 - evidenceWeight) * 0.5 + evidenceWeight * stat.score
                Triple(indexed.value, blended, indexed.index)
            }
            .sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    fun recordActionOutcome(context: Context, label: String, success: Boolean) {
        initialize(context)
        val key = actionKey(label)
        if (key.isBlank()) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trialsKey = "a_${key}_trials"
        val successKey = "a_${key}_success"
        val trials = p.getInt(trialsKey, 0) + 1
        val successes = p.getInt(successKey, 0) + if (success) 1 else 0
        p.edit()
            .putInt(trialsKey, trials.coerceAtMost(5000))
            .putInt(successKey, successes.coerceAtMost(5000))
            .putLong("last_action_outcome_at", System.currentTimeMillis())
            .apply()
    }

    /** يسجل نتيجة المهمة مرة واحدة فقط؛ WIP=1 يجعل منع التكرار واضحًا. */
    fun recordMissionOutcome(context: Context, missionId: String, success: Boolean, route: String) {
        initialize(context)
        if (missionId.isBlank()) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("last_outcome_mission_id", "") == missionId) return
        val recent = safeArray(p.getString("recent_missions", "[]").orEmpty())
        recent.put(
            JSONObject()
                .put("time", System.currentTimeMillis())
                .put("ok", success)
                .put("route", sanitizeRoute(route))
        )
        while (recent.length() > MAX_RECENT_MISSIONS) recent.remove(0)
        p.edit()
            .putString("last_outcome_mission_id", missionId)
            .putString("recent_missions", recent.toString())
            .putLong("last_mission_outcome_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * تثبيت/تراجع السياسة بناءً على نجاح المهمات وصحة النظام.
     * عند الانحدار لا نمسح الخبرة؛ نعطل أثرها مؤقتًا ونعود لترتيب خط الأساس.
     */
    fun consolidate(context: Context, healthStatus: String) {
        initialize(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (healthStatus == "FAIL_CLOSED") {
            p.edit()
                .putBoolean("adaptive_enabled", false)
                .putLong("cooldown_until", System.currentTimeMillis() + COOLDOWN_MS)
                .putString("last_adaptation_decision", "DISABLED_BY_HEALTH")
                .apply()
            return
        }

        val recent = safeArray(p.getString("recent_missions", "[]").orEmpty())
        if (recent.length() < MIN_MISSION_TRIALS_FOR_PROMOTION) return
        var ok = 0
        for (i in 0 until recent.length()) if (recent.optJSONObject(i)?.optBoolean("ok", false) == true) ok++
        val rate = ok.toDouble() / recent.length().toDouble()
        val best = p.getDoubleCompat("best_mission_rate", -1.0)
        val edit = p.edit()
        when {
            best < 0.0 || rate > best -> {
                edit.putDoubleCompat("best_mission_rate", rate)
                    .putBoolean("adaptive_enabled", true)
                    .putLong("cooldown_until", 0L)
                    .putLong("epoch", p.getLong("epoch", 1L) + 1L)
                    .putString("last_adaptation_decision", "PROMOTED")
            }
            best - rate >= REGRESSION_MARGIN -> {
                edit.putBoolean("adaptive_enabled", false)
                    .putLong("cooldown_until", System.currentTimeMillis() + COOLDOWN_MS)
                    .putString("last_adaptation_decision", "ROLLED_BACK_TO_BASELINE")
            }
            else -> {
                edit.putBoolean("adaptive_enabled", true)
                    .putString("last_adaptation_decision", "KEEP_CURRENT")
            }
        }
        edit.putDoubleCompat("last_mission_rate", rate)
            .putLong("last_consolidated_at", System.currentTimeMillis())
            .apply()
    }

    fun status(context: Context): JSONObject {
        initialize(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recent = safeArray(p.getString("recent_missions", "[]").orEmpty())
        return JSONObject()
            .put("adaptive_learning", true)
            .put("local_only", true)
            .put("changes_code_automatically", false)
            .put("can_expand_authority", false)
            .put("safe_candidates_only", true)
            .put("baseline_fallback", true)
            .put("adaptive_enabled", isAdaptiveActive(context))
            .put("epoch", p.getLong("epoch", 1L))
            .put("best_mission_rate", nullableRate(p.getDoubleCompat("best_mission_rate", -1.0)))
            .put("last_mission_rate", nullableRate(p.getDoubleCompat("last_mission_rate", -1.0)))
            .put("recent_mission_count", recent.length())
            .put("last_adaptation_decision", p.getString("last_adaptation_decision", "COLLECTING_EVIDENCE"))
            .put("cooldown_until", p.getLong("cooldown_until", 0L))
    }

    private fun actionStat(context: Context, label: String): CandidateScore {
        val key = actionKey(label)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trials = p.getInt("a_${key}_trials", 0)
        val successes = p.getInt("a_${key}_success", 0).coerceAtMost(trials)
        // Beta(1,1): يمنع القفز من عينة واحدة إلى ثقة زائفة.
        val score = (successes + 1.0) / (trials + 2.0)
        return CandidateScore(label, trials, successes, if (trials >= MIN_ACTION_TRIALS) score else 0.5)
    }

    private fun isAdaptiveActive(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cooldown = p.getLong("cooldown_until", 0L)
        if (cooldown > System.currentTimeMillis()) return false
        if (cooldown != 0L) {
            p.edit().putLong("cooldown_until", 0L).putBoolean("adaptive_enabled", true).apply()
        }
        return p.getBoolean("adaptive_enabled", true)
    }

    private fun actionKey(label: String): String {
        val normalized = label.lowercase().replace(Regex("\\s+"), " ").trim().take(160)
        if (normalized.isBlank()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeRoute(route: String): String = route.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_").take(48)
    private fun safeArray(raw: String): JSONArray = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    private fun nullableRate(v: Double): Any = if (v < 0.0) JSONObject.NULL else v

    private fun android.content.SharedPreferences.getDoubleCompat(key: String, default: Double): Double =
        java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToLongBits(default)))

    private fun android.content.SharedPreferences.Editor.putDoubleCompat(key: String, value: Double): android.content.SharedPreferences.Editor =
        putLong(key, java.lang.Double.doubleToLongBits(value))
}
