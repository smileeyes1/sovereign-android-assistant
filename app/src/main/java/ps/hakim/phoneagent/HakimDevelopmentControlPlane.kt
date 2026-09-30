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
    const val VERSION = "HAKIM-DEVELOPMENT-CONTROL-2026-09-30-v1"
    private const val PREFS = "hakim_development_control"
    private const val MIN_REPEAT_MS = 6L * 60L * 60L * 1000L

    private data class Need(
        val required: Boolean,
        val trigger: String,
        val severity: String,
        val evidence: String
    )

    fun shouldSignal(context: Context): Boolean {
        val need = evaluateNeed(context)
        if (!need.required) return false
        val p = prefs(context)
        val fingerprint = fingerprint(need.trigger + "|" + need.severity + "|" + need.evidence)
        val lastFingerprint = p.getString("last_emitted_fingerprint", "").orEmpty()
        val lastAt = p.getLong("last_emitted_at", 0L)
        return fingerprint != lastFingerprint || System.currentTimeMillis() - lastAt >= MIN_REPEAT_MS
    }

    fun requestForBeacon(context: Context): JSONObject? {
        val need = evaluateNeed(context)
        if (!need.required || !shouldSignal(context)) return null
        val now = System.currentTimeMillis()
        val version = currentVersion(context)
        val fingerprint = fingerprint(need.trigger + "|" + need.severity + "|" + need.evidence)
        return JSONObject()
            .put("schema_version", 1)
            .put("control_version", VERSION)
            .put("request_id", "dev-" + now.toString(36) + "-" + fingerprint.take(12))
            .put("requested_at_ms", now)
            .put("package", context.packageName)
            .put("current_version_code", version)
            .put("trigger", need.trigger)
            .put("severity", need.severity)
            .put("evidence_summary", need.evidence.take(500))
            .put("fingerprint", fingerprint)
            .put("goal", "تشخيص السبب الجذري وإنشاء إصلاح آمن قابل للرجوع مع اختبار يمنع الانحدار")
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

        return when {
            improvement.optBoolean("rollback_forward_requested") ->
                Need(true, "field_candidate_regression", "critical",
                    "rollback_forward_requested=true;state=" + improvement.optString("state"))
            selfStatus == "FAIL_CLOSED" ->
                Need(true, "self_check_failed", "critical", "self_check=FAIL_CLOSED")
            learning.optBoolean("improvement_needed") ->
                Need(true, "repeated_runtime_failure", "high",
                    "consecutive_failures=" + learning.optInt("consecutive_failures"))
            else -> Need(false, "none", "none", "no_material_development_gap")
        }
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
