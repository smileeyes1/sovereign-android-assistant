package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/**
 * حلقة التحسين الذاتي المحكومة.
 *
 * لا تعدّل المصدر أو الصلاحيات على الهاتف. مهمتها رصد انتقالات الإصدار،
 * التحقق بعد التثبيت، وحفظ طلب رجوع أمامي عند تدهور المرشح.
 * الترقية الميدانية تبقى خلف توقيع D1 ودليل نفس القطعة.
 */
object HakimSelfImprovementLoop {
    const val VERSION = "SELF-IMPROVEMENT-LOOP-2026-09-24-v1"
    private const val PREFS = "hakim_self_improvement"
    private const val GRACE_MS = 45_000L
    private const val MAX_POST_INSTALL_OBSERVE_MS = 5L * 60L * 1000L
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var evaluationScheduled = false

    fun install(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        val current = currentVersion(app)
        val previous = p.getLong("last_started_version", 0L)
        val mainPrefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val committedVersion = mainPrefs.getLong("last_update_commit_version", -1L)
        val committedAt = mainPrefs.getLong("last_update_commit_at", 0L)
        val versionChanged = previous > 0L && current > previous
        val alreadyHealthy = p.getLong("last_field_observed_healthy_version", -1L) == current
        val committedCandidateStarted = committedVersion == current && committedAt > 0L && !alreadyHealthy

        if (versionChanged || committedCandidateStarted) {
            markCandidate(
                app,
                current,
                if (previous > 0L && previous != current) previous else -1L,
                if (versionChanged) "version_changed" else "committed_candidate_started"
            )
        }

        p.edit()
            .putString("version", VERSION)
            .putLong("last_started_version", current)
            .putLong("last_seen_at", System.currentTimeMillis())
            .apply()

        scheduleEvaluation(app, if (versionChanged) "version_changed" else if (committedCandidateStarted) "committed_candidate_started" else "startup")
    }

    fun onPackageReplaced(context: Context) {
        val app = context.applicationContext
        val current = currentVersion(app)
        val p = prefs(app)
        val previous = p.getLong("post_install_previous_version", -1L)
        if (p.getLong("last_field_observed_healthy_version", -1L) != current) {
            markCandidate(app, current, previous, "package_replaced")
            scheduleEvaluation(app, "package_replaced")
        }
    }

    private fun markCandidate(context: Context, current: Long, previous: Long, reason: String) {
        val p = prefs(context)
        val alreadySamePending = p.getBoolean("post_install_pending", false) &&
            p.getLong("post_install_candidate_version", -1L) == current
        val edit = p.edit()
            .putBoolean("post_install_pending", true)
            .putLong("post_install_candidate_version", current)
            .putString("state", "POST_INSTALL_OBSERVING")
            .putString("last_reason", reason.take(80))
        if (!alreadySamePending) {
            edit.putLong("post_install_previous_version", previous)
                .putLong("post_install_first_seen_at", System.currentTimeMillis())
        }
        edit.apply()
    }

    fun scheduleEvaluation(context: Context, reason: String) {
        val app = context.applicationContext
        if (evaluationScheduled) return
        evaluationScheduled = true
        handler.postDelayed({
            evaluationScheduled = false
            evaluate(app, reason)
        }, GRACE_MS)
    }

    fun evaluate(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        val p = prefs(app)
        val current = currentVersion(app)
        val pending = p.getBoolean("post_install_pending", false)
        val candidate = p.getLong("post_install_candidate_version", -1L)
        val fabric = HakimExecutionFabric.status(app)
        val selfStatus = app.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
            .getString("last_self_check_status", "NOT_TESTED").orEmpty()
        val online = fabric.optBoolean("online", false)
        val selfHealthy = selfStatus == "PASS" || selfStatus == "PASS_WITH_WARNINGS"
        val healthy = online && selfHealthy
        val previousState = p.getString("state", "OBSERVING").orEmpty()
        val firstSeenAt = p.getLong("post_install_first_seen_at", 0L)
        val observationAge = if (firstSeenAt > 0L) (System.currentTimeMillis() - firstSeenAt).coerceAtLeast(0L) else 0L

        val nextState = when {
            pending && candidate == current && healthy -> "POST_INSTALL_HEALTHY"
            pending && candidate == current && observationAge < MAX_POST_INSTALL_OBSERVE_MS -> "POST_INSTALL_WAITING_EVIDENCE"
            pending && candidate == current -> "ROLLBACK_FORWARD_REQUIRED"
            healthy -> "BASELINE_HEALTHY"
            else -> "OBSERVING"
        }

        val edit = p.edit()
            .putString("state", nextState)
            .putString("last_reason", reason.take(80))
            .putLong("last_evaluated_at", System.currentTimeMillis())
            .putLong("current_version", current)
            .putBoolean("last_online", online)
            .putString("last_self_check_status", selfStatus)

        if (nextState == "POST_INSTALL_HEALTHY") {
            edit.putBoolean("post_install_pending", false)
                .putBoolean("rollback_forward_requested", false)
                .putLong("last_field_observed_healthy_version", current)
                .putLong("last_field_observed_healthy_at", System.currentTimeMillis())
                .remove("rollback_forward_reason")
        } else if (nextState == "ROLLBACK_FORWARD_REQUIRED") {
            edit.putBoolean("rollback_forward_requested", true)
                .putLong("rollback_forward_from_version", current)
                .putLong("rollback_forward_target_previous_version", p.getLong("post_install_previous_version", -1L))
                .putString("rollback_forward_reason", "post_install_not_healthy:online=$online,self=$selfStatus")
        }
        edit.apply()

        if (previousState != nextState) {
            HakimHealthBeacon.sendAsync(app, "self_improvement_$nextState")
        }
        if (nextState == "POST_INSTALL_WAITING_EVIDENCE") {
            scheduleEvaluation(app, "post_install_retry")
        }
        return status(app)
    }

    fun status(context: Context): JSONObject {
        val p = prefs(context)
        return JSONObject()
            .put("self_improvement_loop", true)
            .put("version", VERSION)
            .put("state", p.getString("state", "OBSERVING"))
            .put("current_version", currentVersion(context))
            .put("last_started_version", p.getLong("last_started_version", 0L))
            .put("post_install_pending", p.getBoolean("post_install_pending", false))
            .put("post_install_candidate_version", p.getLong("post_install_candidate_version", -1L))
            .put("post_install_previous_version", p.getLong("post_install_previous_version", -1L))
            .put("rollback_forward_requested", p.getBoolean("rollback_forward_requested", false))
            .put("rollback_forward_from_version", p.getLong("rollback_forward_from_version", -1L))
            .put("rollback_forward_target_previous_version", p.getLong("rollback_forward_target_previous_version", -1L))
            .put("rollback_forward_reason", p.getString("rollback_forward_reason", ""))
            .put("last_field_observed_healthy_version", p.getLong("last_field_observed_healthy_version", -1L))
            .put("last_evaluated_at", p.getLong("last_evaluated_at", 0L))
            .put("max_post_install_observe_ms", MAX_POST_INSTALL_OBSERVE_MS)
            .put("source_mutation_on_device", false)
            .put("automatic_downgrade", false)
            .put("rollback_strategy", "FORWARD_ONLY_FROM_VERIFIED_BASELINE_SOURCE")
            .put("verified_baseline_source_required", true)
            .put("d1_required_for_field_update", true)
            .put("new_candidate_does_not_inherit_success", true)
            .put("same_artifact_field_evidence_required", true)
    }

    @Suppress("DEPRECATION")
    private fun currentVersion(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) { 0L }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
