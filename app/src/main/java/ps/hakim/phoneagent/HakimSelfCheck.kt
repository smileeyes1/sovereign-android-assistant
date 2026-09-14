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
    private const val PERIOD_MS = 60L * 60L * 1000L

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, HakimEvolutionJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
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
        HakimQuranicInvariantKernel.requireInherited("self_check")
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
        check("ن★ التكيفية مفعلة", governance.optBoolean("adaptive_nstar"))
        check("لا عدد تكرار ثابت", governance.optBoolean("no_fixed_iteration_count"))
        check("الدورة الإضافية تتطلب مكسبًا ماديًا", governance.optBoolean("material_gain_required"))
        check("الفجوة المادية تمنع الاكتمال", governance.optBoolean("material_gap_blocks_complete"))
        check("أفضل/أنسب/أعلى مفعلة", governance.optBoolean("best_fit_highest"))
        check("كل شيء/من كل شيء/في كل شيء مفيد", governance.optBoolean("all_from_all_in_all_useful"))
        check("كل ما يفيد افتراضي", governance.optBoolean("all_beneficial_default"))
        check("كل القواعد افتراضية", governance.optBoolean("all_rules_default"))
        check("التقاط القواعد تلقائي", governance.optBoolean("automatic_rule_capture"))
        check("الأحدث الصريح يعلو", governance.optBoolean("latest_explicit_rule_wins"))
        check("المهمة المؤقتة لا تصبح قاعدة عامة", governance.optBoolean("temporary_task_not_global"))
        check("البيانات الحساسة لا تُرقى لقاعدة", governance.optBoolean("sensitive_data_not_promoted"))
        check("الإكمال التلقائي الافتراضي", governance.optBoolean("default_auto_completion"))
        check("الاستمرار الآمن تلقائي", governance.optBoolean("safe_auto_continue"))
        check("التعلم الذاتي محكوم", governance.optBoolean("self_learning_guarded"))
        check("التطور الذاتي محكوم", governance.optBoolean("self_evolution_guarded"))

        val human = HakimHumanFirstPolicy.status()
        check("الإنسان أولًا مفعّل", human.optBoolean("human_first"))
        check("كرامة المستخدم قيد حاكم", human.optBoolean("dignity_is_hard_constraint"))
        check("صفر عبء تقني افتراضي", human.optBoolean("zero_technical_burden_default"))
        check("الطيبة لا تُستغل", human.optBoolean("kindness_must_not_be_exploited"))
        check("السكوت ليس موافقة", human.optBoolean("silence_is_not_consent"))
        check("حفظ سيادة المستخدم", human.optBoolean("preserve_user_agency"))
        check("التصميم يتحمل السهو والتعب", human.optBoolean("human_error_and_fatigue_tolerant"))

        val adaptive = HakimAdaptiveLearning.status(context)
        check("التعلم التكيفي المحلي موجود", adaptive.optBoolean("adaptive_learning"))
        check("التكيف محلي فقط", adaptive.optBoolean("local_only"))
        check("التكيف لا يعدل الكود تلقائيًا", !adaptive.optBoolean("changes_code_automatically"))
        check("التكيف لا يوسع السلطة", !adaptive.optBoolean("can_expand_authority"))
        check("التكيف يعيد ترتيب مرشحات آمنة فقط", adaptive.optBoolean("safe_candidates_only"))
        check("الرجوع إلى خط الأساس متاح", adaptive.optBoolean("baseline_fallback"))

        val proactive = HakimProactiveEngine.status(context)
        check("محرك المبادرة الذاتية موجود", proactive.optBoolean("proactive_engine"))
        check("الأعمال المفيدة الآمنة تلقائية", proactive.optBoolean("beneficial_safe_actions_auto"))
        check("المبادرة لا تفوض الأثر العالي بصمت", proactive.optBoolean("high_impact_never_silently_authorized"))
        check("السكوت لا يصبح موافقة عبر المبادرة", proactive.optBoolean("silence_not_consent"))
        check("المبادرة لا توسع سرًا أو صلاحية", proactive.optBoolean("no_secret_or_permission_escalation"))

        val integration = HakimIntegrationFabric.status(context)
        val structural = integration.optJSONObject("structural") ?: JSONObject()
        val runtime = integration.optJSONObject("runtime") ?: JSONObject()
        check("نسيج التكامل البنيوي سليم", structural.optBoolean("structural_integrity"), "fail")
        check("لا توجد طبقة حرجة معزولة", structural.optBoolean("no_isolated_critical_layer"), "fail")
        check("الجذر القرآني موروث داخل نسيج التكامل", structural.optBoolean("quranic_root_inherited"), "fail")
        check("الإنسان أولًا مدمج في نسيج التكامل", structural.optBoolean("human_first_integrated"), "fail")
        check("التعلم التكيفي مدمج في نسيج التكامل", structural.optBoolean("adaptive_learning_integrated"), "fail")
        check("المبادرة الذاتية مدمجة في نسيج التكامل", structural.optBoolean("proactive_engine_integrated"), "fail")
        check(
            "الجاهزية الخارجية مفصولة عن سلامة القلب",
            runtime.optBoolean("runtime_readiness_is_not_structural_integrity"),
            "fail"
        )

        val ledger = governance.optJSONObject("rule_ledger") ?: JSONObject()
        check("سجل القواعد مشفر محليًا", ledger.optBoolean("encrypted_local_ledger"))

        val intent = HakimIntentEngine.status(context)
        check("محرك النية فعّال", intent.optBoolean("intent_engine"))
        check("محرك النية يستخدم ن★", intent.optBoolean("adaptive_nstar"))
        check("الإكمال التلقائي افتراضي", intent.optBoolean("default_auto_completion"))
        check("الاستمرار الآمن تلقائي", intent.optBoolean("safe_auto_continue"))
        check("الفجوة المادية تمنع إغلاق النية", intent.optBoolean("material_gap_blocks_complete"))
        check("بوابة الأفعال عالية الأثر فعالة", intent.optBoolean("high_impact_gate"))

        val mainPrefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val userDisabled = mainPrefs.getBoolean("pairing_disabled_by_user", false)
        val paired = mainPrefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            mainPrefs.getString("result_topic", "").orEmpty().isNotBlank()
        check("حالة الاقتران منطقية", userDisabled || paired, "warn", if (userDisabled) "فصل المستخدم محترم" else if (paired) "مقترن" else "غير مقترن")

        val recovery = HakimConnectionResilience.status(context)
        check(
            "خدمة الاتصال قابلة للاستعادة",
            userDisabled || !paired || recovery.optBoolean("service_running") || recovery.optString("state") == "restart_requested",
            "warn",
            recovery.optString("state")
        )
        check(
            "الاتصال الحي متاح عند الاقتران",
            userDisabled || !paired || recovery.optBoolean("service_connected"),
            "warn",
            recovery.optString("state")
        )

        val scheduler = context.getSystemService(JobScheduler::class.java)
        val jobs = try { scheduler.allPendingJobs.map { it.id }.toSet() } catch (_: Exception) { emptySet() }
        check("التحديث الذاتي مجدول", jobs.contains(771204), "warn")
        check("الفحص/التطور/المبادرة الدورية مجدولة", jobs.contains(JOB_ID), "warn")
        check("حارس استعادة الاتصال مجدول", jobs.contains(HakimConnectionResilience.JOB_ID), "warn")

        val lastSocketError = mainPrefs.getString("last_socket_error", "").orEmpty()
        val lastAuthError = mainPrefs.getString("last_auth_error", "").orEmpty()
        val lastCommandError = mainPrefs.getString("last_command_error", "").orEmpty()
        val lastUpdateError = mainPrefs.getString("last_update_error", "").orEmpty()
        val lastRecoveryError = mainPrefs.getString("last_recovery_error", "").orEmpty()
        val recentErrors = listOf(lastSocketError, lastAuthError, lastCommandError, lastUpdateError, lastRecoveryError).count { it.isNotBlank() }
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
            .put("human_first", human)
            .put("adaptive_learning", adaptive)
            .put("proactive", proactive)
            .put("integration", integration)
            .put("intent", intent)
            .put("connection_recovery", recovery)
            .put("learning", HakimLearning.snapshot(context))

        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE).edit()
            .putString("last_self_check", report.toString().take(40000))
            .putLong("last_self_check_at", System.currentTimeMillis())
            .putString("last_self_check_status", status)
            .apply()
        return report
    }
}
