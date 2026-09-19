package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نقل دليل التعلم المحلي دون نقل مهمة جارية أو أسرار أو صلاحيات.
 * يفصل الاستمرارية عن محرك التعلم المثبت حتى لا يغير سلوكه الحاكم.
 */
object HakimAdaptiveLearningPortability {
    private const val PREFS = "hakim_adaptive_learning"
    private const val MAX_ACTIONS = 128
    private const val MAX_RECENT_MISSIONS = 20
    private val ACTION_KEY = Regex("^a_([0-9a-f]{16})_(trials|success)$")
    private val HASH = Regex("^[0-9a-f]{16}$")

    fun exportEvidence(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val grouped = linkedMapOf<String, Pair<Int, Int>>()
        p.all.forEach { (key, value) ->
            val match = ACTION_KEY.matchEntire(key) ?: return@forEach
            val hash = match.groupValues[1]
            val old = grouped[hash] ?: (0 to 0)
            val n = (value as? Int ?: 0).coerceIn(0, 5000)
            grouped[hash] = if (match.groupValues[2] == "trials") n to old.second else old.first to n
        }
        val actions = JSONObject()
        grouped.entries.take(MAX_ACTIONS).forEach { (hash, pair) ->
            val trials = pair.first
            val success = pair.second.coerceAtMost(trials)
            actions.put(hash, JSONObject().put("trials", trials).put("successes", success))
        }

        val recent = safeRecent(p.getString("recent_missions", "[]").orEmpty())
        return JSONObject()
            .put("schema", 1)
            .put("anonymous_action_evidence", actions)
            .put("recent_missions", recent)
            .put("epoch", p.getLong("epoch", 1L).coerceIn(1L, 1_000_000L))
            .put("best_mission_rate", nullableRate(p.getDoubleCompat("best_mission_rate", -1.0)))
            .put("last_mission_rate", nullableRate(p.getDoubleCompat("last_mission_rate", -1.0)))
            .put("contains_pending_mission", false)
            .put("contains_credentials", false)
    }

    /** يستبدل دليل التعلم فقط؛ لا يستورد pending mission/cooldown/سلطة. */
    fun replaceEvidence(context: Context, root: JSONObject): Boolean {
        if (root.optInt("schema", -1) != 1) return false
        if (root.optBoolean("contains_pending_mission", false) || root.optBoolean("contains_credentials", false)) return false
        val actions = root.optJSONObject("anonymous_action_evidence") ?: JSONObject()
        if (actions.length() > MAX_ACTIONS) return false
        val normalized = linkedMapOf<String, Pair<Int, Int>>()
        actions.keys().forEach { hash ->
            if (!HASH.matches(hash)) return false
            val item = actions.optJSONObject(hash) ?: return false
            val trials = item.optInt("trials", -1)
            val successes = item.optInt("successes", -1)
            if (trials !in 0..5000 || successes !in 0..trials) return false
            normalized[hash] = trials to successes
        }
        val recent = normalizeRecent(root.optJSONArray("recent_missions") ?: JSONArray()) ?: return false
        val epoch = root.optLong("epoch", 1L).coerceIn(1L, 1_000_000L)
        val best = parseRate(root, "best_mission_rate") ?: return false
        val last = parseRate(root, "last_mission_rate") ?: return false

        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = p.edit()
        p.all.keys.filter { ACTION_KEY.matches(it) }.forEach(edit::remove)
        normalized.forEach { (hash, pair) ->
            edit.putInt("a_${hash}_trials", pair.first)
            edit.putInt("a_${hash}_success", pair.second)
        }
        edit.putString("recent_missions", recent.toString())
            .putLong("epoch", epoch)
            .putDoubleCompat("best_mission_rate", best)
            .putDoubleCompat("last_mission_rate", last)
            .putBoolean("adaptive_enabled", true)
            .putLong("cooldown_until", 0L)
            .remove("pending_mission_id")
            .remove("pending_route")
            .putString("last_adaptation_decision", "RESTORED_EVIDENCE_PENDING_REEVALUATION")
        return edit.commit()
    }

    private fun safeRecent(raw: String): JSONArray = normalizeRecent(
        try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    ) ?: JSONArray()

    private fun normalizeRecent(input: JSONArray): JSONArray? {
        if (input.length() > MAX_RECENT_MISSIONS) return null
        val out = JSONArray()
        for (i in 0 until input.length()) {
            val item = input.optJSONObject(i) ?: return null
            val route = item.optString("route", "unknown").lowercase()
                .replace(Regex("[^a-z0-9_\\-]"), "_").take(48)
            out.put(
                JSONObject()
                    .put("time", item.optLong("time", 0L).coerceAtLeast(0L))
                    .put("ok", item.optBoolean("ok", false))
                    .put("route", route.ifBlank { "unknown" })
            )
        }
        return out
    }

    /** null هنا يعني قيمة غير صالحة؛ -1 هو «لا يوجد معدل بعد». */
    private fun parseRate(root: JSONObject, key: String): Double? {
        if (root.isNull(key) || !root.has(key)) return -1.0
        val value = root.optDouble(key, Double.NaN)
        if (!value.isFinite() || value < 0.0 || value > 1.0) return null
        return value
    }

    private fun nullableRate(v: Double): Any = if (!v.isFinite() || v < 0.0) JSONObject.NULL else v.coerceIn(0.0, 1.0)

    private fun android.content.SharedPreferences.getDoubleCompat(key: String, default: Double): Double =
        java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToLongBits(default)))

    private fun android.content.SharedPreferences.Editor.putDoubleCompat(key: String, value: Double): android.content.SharedPreferences.Editor =
        putLong(key, java.lang.Double.doubleToLongBits(value))
}
