package ps.hakim.phoneagent

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

object HakimSelfCheck {
    const val JOB_ID = 771207
    private const val PERIOD_MS = 6L * 60L * 60L * 1000L

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, HakimEvolutionJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            scheduler.schedule(job)
        } catch (_: Exception) {}
    }

    fun runAsync(context: Context) {
        Thread {
            try {
                val report = run(context.applicationContext)
                HakimLearning.recordHealth(context.applicationContext, report)
            } catch (_: Exception) {}
        }.start()
    }

    fun run(context: Context): JSONObject {
        val checks = JSONArray()
        var failed = 0
        var warned = 0

        fun check(name: String, ok: Boolean, severity: String = "fail", detail: String = "") {
            checks.put(JSONObject().put("name", name).put("ok", ok).put("severity", severity).put("detail", detail.take(240)))
            if (!ok) {
                if (severity == "warn") warned++ else failed++
            }
        }

        val governance = HakimConstitution.status(context)
        check("الذكاء ×٧", governance.optBoolean("intelligence_x7"))
        check("التلقائية ×٧", governance.optBoolean("automatic_x7"))
        check("الفائدة ×٧", governance.optBoolean("benefit_x7"))
        check("بوابات الاكتمال ×٧", governance.optBoolean("completion_x7"))
        check("قاعدة السبع افتراضية", governance.optBoolean("sevenfold_default"))
        check("التعلم الذاتي محكوم", governance.optBoolean("self_learning_guarded"))
        check("التطور الذاتي محكوم", governance.optBoolean("self_evolution_guarded"))

        val mainPrefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val userDisabled = mainPrefs.getBoolean("pairing_disabled_by_user", false)
        val paired = mainPrefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            mainPrefs.getString("result_topic", "").orEmpty().isNotBlank()
        check("حالة الاقتران منطقية", userDisabled || paired, "warn", if (userDisabled) "فصل المستخدم محترم" else if (paired) "مقترن" else "غير مقترن")

        val scheduler = context.getSystemService(JobScheduler::class.java)
        val jobs = try { scheduler.allPendingJobs.map { it.id }.toSet() } catch (_: Exception) { emptySet() }
        check("التحديث الذاتي مجدول", jobs.contains(771204), "warn")
        check("الفحص/التطور الدوري مجدول", jobs.contains(JOB_ID), "warn")

        val lastSocketError = mainPrefs.getString("last_socket_error", "").orEmpty()
        val lastAuthError = mainPrefs.getString("last_auth_error", "").orEmpty()
        val lastCommandError = mainPrefs.getString("last_command_error", "").orEmpty()
        val lastUpdateError = mainPrefs.getString("last_update_error", "").orEmpty()
        val recentErrors = listOf(lastSocketError, lastAuthError, lastCommandError, lastUpdateError).count { it.isNotBlank() }
        check("لا أخطاء تشغيلية مسجلة", recentErrors == 0, "warn", "الأخطاء المسجلة: $recentErrors")

        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: Exception) { 0L }
        check("هوية الحزمة الصحيحة", context.packageName == "ps.hakim.stable")
        check("رقم إصدار صالح", version > 0)

        val status = when {
            failed > 0 -> "FAIL_CLOSED"
            warned > 0 -> "PASS_WITH_WARNINGS"
            else -> "PASS"
        }
        val report = JSONObject()
            .put("status", status)
            .put("time", System.currentTimeMillis())
            .put("package", context.packageName)
            .put("version_code", version)
            .put("failed", failed)
            .put("warnings", warned)
            .put("checks", checks)
            .put("governance", governance)
            .put("learning", HakimLearning.snapshot(context))

        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE).edit()
            .putString("last_self_check", report.toString().take(16000))
            .putLong("last_self_check_at", System.currentTimeMillis())
            .putString("last_self_check_status", status)
            .apply()
        return report
    }
}
