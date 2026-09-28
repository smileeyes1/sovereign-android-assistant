package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Read-only mirror of the bridge continuity checkpoint for the paired phone.
 * Stores only bounded checkpoint metadata; never stores relay credentials.
 */
object HakimCloudContinuity {
    private const val PREFS = "hakim_cloud_continuity"
    private const val MIN_REFRESH_MS = 60_000L

    fun refreshIfDue(
        context: Context,
        bridgeBase: String,
        topic: String,
        relayKey: String
    ) {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_attempt_at", 0L) < MIN_REFRESH_MS) return
        p.edit().putLong("last_attempt_at", now).apply()

        var conn: HttpURLConnection? = null
        try {
            val encodedTopic = URLEncoder.encode(topic, Charsets.UTF_8.name())
            val url = URL(bridgeBase.trimEnd('/') + "/device/v1/continuity?topic=" + encodedTopic)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer " + relayKey)
                setRequestProperty("Accept", "application/json")
                useCaches = false
            }
            val code = conn.responseCode
            if (code != 200) {
                p.edit()
                    .putString("state", "unavailable")
                    .putInt("last_http_status", code)
                    .apply()
                return
            }

            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val work = root.optJSONObject("work")
            val edit = p.edit()
                .putString("state", "ready")
                .putLong("last_success_at", now)
                .putLong("cloud_updated_at", root.optLong("updated_at_ms", 0L))
                .putInt("pending_count", root.optInt("pending_count", 0).coerceAtLeast(0))

            if (work == null || work.length() == 0) {
                edit.remove("goal_id")
                    .remove("goal_label")
                    .remove("stage")
                    .remove("last_verified")
                    .remove("next_step")
                    .remove("blocker")
                    .remove("work_status")
            } else {
                edit.putString("goal_id", clean(work.optString("goal_id"), 96))
                    .putString("goal_label", clean(work.optString("goal_label"), 240))
                    .putString("stage", clean(work.optString("stage"), 120))
                    .putString("last_verified", clean(work.optString("last_verified"), 280))
                    .putString("next_step", clean(work.optString("next_step"), 280))
                    .putString("blocker", clean(work.optString("blocker"), 220))
                    .putString("work_status", clean(work.optString("status"), 20))
            }
            edit.apply()
        } catch (_: Exception) {
            p.edit().putString("state", "unavailable").apply()
        } finally {
            conn?.disconnect()
        }
    }

    fun panelLines(context: Context): List<String> {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("state", "") != "ready") return emptyList()
        val goal = p.getString("goal_label", "").orEmpty()
        val stage = p.getString("stage", "").orEmpty()
        val verified = p.getString("last_verified", "").orEmpty()
        val next = p.getString("next_step", "").orEmpty()
        val blocker = p.getString("blocker", "").orEmpty()
        val status = p.getString("work_status", "").orEmpty()
        val pending = p.getInt("pending_count", 0).coerceAtLeast(0)

        if (goal.isBlank() && stage.isBlank() && pending == 0) return emptyList()
        val lines = mutableListOf<String>()
        if (goal.isNotBlank()) lines += "مقصد الاستمرارية: " + goal
        if (stage.isNotBlank()) lines += "مرحلة الاستمرارية: " + stage
        if (verified.isNotBlank()) lines += "آخر نجاح سحابي مثبت: " + verified
        if (blocker.isNotBlank()) lines += "مانع الاستمرارية: " + blocker
        if (next.isNotBlank()) lines += "الخطوة التالية المحفوظة: " + next
        if (status.isNotBlank()) lines += "حالة الاستمرارية: " + status
        if (pending > 0) lines += "عمليات معلقة قابلة للاستئناف: " + pending
        return lines
    }

    fun publicStatus(context: Context): JSONObject {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("state", p.getString("state", "unknown"))
            .put("last_success_at", p.getLong("last_success_at", 0L))
            .put("cloud_updated_at", p.getLong("cloud_updated_at", 0L))
            .put("pending_count", p.getInt("pending_count", 0).coerceAtLeast(0))
    }

    private fun clean(raw: String, max: Int): String =
        raw.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(max)
}
