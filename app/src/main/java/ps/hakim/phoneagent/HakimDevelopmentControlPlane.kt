package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.security.MessageDigest

/**
 * طبقة تحكم تطويرية: تحوّل دليل التشغيل إلى طلب تطوير محدود وآمن.
 *
 * لا تكتب المصدر، لا تحمل أسرار GitHub، ولا تثبّت APK. دورها إصدار طلب
 * قابل للتحقق إلى طبقة التطوير المعزولة عبر القناة الآمنة القائمة.
 */
object HakimDevelopmentControlPlane {
    const val VERSION = "HAKIM-DEVELOPMENT-CONTROL-2026-09-30-v2"
    private const val PREFS = "hakim_development_control"
    private const val MIN_REPEAT_MS = 6L * 60L * 60L * 1000L

    private data class Need(
        val required: Boolean,
        val trigger: String,
        val severity: String,
        val evidenceCode: String,
        val failureCount: Int = 0,
        val selfCheckStatus: String = "",
        val candidateState: String = "",
        val actionCode: String = ""
    ) {
        fun fingerprintMaterial(): String = listOf(
            trigger, severity, evidenceCode, failureCount.toString(),
            selfCheckStatus, candidateState, actionCode
        ).joinToString("|")
    }

    fun shouldSignal(context: Context): Boolean {
        val need = evaluateNeed(context)
        if (!need.required) return false
        val p = prefs(context)
        val fingerprint = fingerprint(need.fingerprintMaterial())
        val lastFingerprint = p.getString("last_emitted_fingerprint", "").orEmpty()
        val lastAt = p.getLong("last_emitted_at", 0L)
        return fingerprint != lastFingerprint || System.currentTimeMillis() - lastAt >= MIN_REPEAT_MS
    }

    fun requestForBeacon(context: Context): JSONObject? {
        val need = evaluateNeed(context)
        if (!need.required || !shouldSignal(context)) return null
        val now = System.currentTimeMillis()
        val version = currentVersion(context)
        val fingerprint = fingerprint(need.fingerprintMaterial())
        return JSONObject()
            .put("schema_version", 1)
            .put("control_version", VERSION)
            .put("request_id", "dev-" + now.toString(36) + "-" + fingerprint.take(12))
            .put("requested_at_ms", now)
            .put("package", context.packageName)
            .put("current_version_code", version)
            .put("trigger", need.trigger)
            .put("severity", need.severity)
            .put("evidence", JSONObject()
                .put("code", need.evidenceCode)
                .put("failure_count", need.failureCount.coerceIn(0, 1000))
                .put("self_check_status", need.selfCheckStatus.take(32))
                .put("candidate_state", need.candidateState.take(48))
                .put("action_code", need.actionCode.take(48))
            )
            .put("fingerprint", fingerprint)
            .put("constraints", JSONObject()
                .put("source_mutation_on_device", false)
                .put("github_secret_on_device", false)
                .put("isolated_branch_required", true)
                .put("ci_required", true)
                .put("regression_test_required", true)
                .put("verified_baseline_required", true)
                .put("field_install_requires_separate_authorization", true)
                .put("same_artifact_field_evidence_required", true)
                .put("no_permission_expansion", true)
            )
    }

    fun markEmitted(context: Context, request: JSONObject?) {
        if (request == null) return
        val fingerprint = request.optString("fingerprint")
        val requestId = request.optString("request_id")
        if (fingerprint.isBlank() || requestId.isBlank()) return
        prefs(context).edit()
            .putString("last_emitted_fingerprint", fingerprint.take(64))
            .putString("last_request_id", requestId.take(96))
            .putLong("last_emitted_at", System.currentTimeMillis())
            .apply()
    }

    fun status(context: Context): JSONObject {
        val need = evaluateNeed(context)
        val p = prefs(context)
        return JSONObject()
            .put("development_control_plane", true)
            .put("version", VERSION)
            .put("request_required", need.required)
            .put("trigger", need.trigger)
            .put("severity", need.severity)
            .put("source_mutation_on_device", false)
            .put("github_secret_on_device", false)
            .put("isolated_branch_required", true)
            .put("ci_required", true)
            .put("regression_test_required", true)
            .put("field_install_separate_gate", true)
            .put("last_request_id", p.getString("last_request_id", ""))
            .put("last_emitted_at", p.getLong("last_emitted_at", 0L))
    }

    private fun evaluateNeed(context: Context): Need {
        val learning = HakimLearning.snapshot(context)
        val improvement = HakimSelfImprovementLoop.status(context)
        val selfStatus = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
            .getString("last_self_check_status", "NOT_TESTED").orEmpty()

        val consecutiveFailures = learning.optInt("consecutive_failures").coerceIn(0, 1000)
        val actionCode = lastFailureAction(learning)

        return when {
            improvement.optBoolean("rollback_forward_requested") ->
                Need(
                    required = true,
                    trigger = "field_candidate_regression",
                    severity = "critical",
                    evidenceCode = "rollback_forward_requested",
                    selfCheckStatus = selfStatus,
                    candidateState = improvement.optString("state").take(48)
                )
            selfStatus == "FAIL_CLOSED" ->
                Need(
                    required = true,
                    trigger = "self_check_failed",
                    severity = "critical",
                    evidenceCode = "self_check_fail_closed",
                    selfCheckStatus = "FAIL_CLOSED"
                )
            learning.optBoolean("improvement_needed") ->
                Need(
                    required = true,
                    trigger = "repeated_runtime_failure",
                    severity = "high",
                    evidenceCode = "consecutive_runtime_failures",
                    failureCount = consecutiveFailures,
                    actionCode = actionCode
                )
            else -> Need(false, "none", "none", "no_material_development_gap")
        }
    }

    private fun lastFailureAction(learning: JSONObject): String {
        val events = learning.optJSONArray("events") ?: return ""
        for (i in events.length() - 1 downTo 0) {
            val event = events.optJSONObject(i) ?: continue
            if (event.optString("kind") == "failure") {
                return event.optString("action")
                    .lowercase()
                    .replace(Regex("[^a-z0-9_\\-]"), "_")
                    .take(48)
            }
        }
        return ""
    }

    private fun fingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Suppress("DEPRECATION")
    private fun currentVersion(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) { 0L }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
