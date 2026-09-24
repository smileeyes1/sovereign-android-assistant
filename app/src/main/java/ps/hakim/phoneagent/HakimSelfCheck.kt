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

        val ledger = governance.optJSONObject("rule_ledger") ?: JSONObject()
        check("سجل القواعد مشفر محليًا", ledger.optBoolean("encrypted_local_ledger"))

        val intent = HakimIntentEngine.status(context)
        check("محرك النية فعّال", intent.optBoolean("intent_engine"))
        check("محرك النية يستخدم ن★", intent.optBoolean("adaptive_nstar"))
        check("الإكمال التلقائي افتراضي", intent.optBoolean("default_auto_completion"))
        check("الاستمرار الآمن تلقائي", intent.optBoolean("safe_auto_continue"))
        check("الفجوة المادية تمنع إغلاق النية", intent.optBoolean("material_gap_blocks_complete"))
        check("بوابة الأفعال عالية الأثر فعالة", intent.optBoolean("high_impact_gate"))

        val capability = HakimCapabilityKernel.status(context)
        check("نواة القدرات فعالة", capability.optBoolean("capability_kernel"))
        check("القدرات المجهولة مرفوضة", capability.optBoolean("unknown_capability_denied"))
        check("النواة تفشل مغلقة", capability.optBoolean("fail_closed"))

        val continuity = HakimValueContinuityEngine.status(context)
        check("التحكم بالقيمة مغلق الحلقة", continuity.optBoolean("closed_loop_value_control"))
        check("الاستمرارية ليست دورانًا مشغولًا", continuity.optBoolean("continuity_is_not_busy_loop"))
        check("القيمة الحدية الموجبة شرط للاستمرار", continuity.optBoolean("positive_marginal_value_required"))
        check("منع الدوران فعال", continuity.optBoolean("anti_loop"))
        check("الحالة قابلة للحفظ والاستئناف", continuity.optBoolean("checkpoint_resume"))
        check("آخر خط أساس مثبت محمي", continuity.optBoolean("verified_baseline_protected"))

        val supervisor = HakimGoalSupervisor.status(context)
        check("المقصد هو وحدة الإغلاق", supervisor.optBoolean("goal_is_unit_of_closure"))
        check("نجاح الأداة ليس نجاح المقصد", supervisor.optBoolean("tool_success_is_not_goal_success"))
        check("لا توقف عادي للمقصد", supervisor.optBoolean("no_normal_stop_state"))
        check("فشل الوسيلة لا يغلق المقصد", supervisor.optBoolean("failure_of_means_never_closes_goal"))
        check("تكرار الفشل يجبر تغيير المسار", supervisor.optBoolean("same_failure_forces_reroute"))
        check("الانتظار قابل للاستئناف", supervisor.optBoolean("wait_must_be_resumable"))
        check("البوابة الوهمية ممنوعة", supervisor.optBoolean("hypothetical_gate_forbidden"))

        val executor = HakimGoalExecutor.heartbeat(context)
        check("منفذ المقصد موجود", executor.optBoolean("executor"))
        check("نبض المنفذ قابل للرصد", executor.has("heartbeat_at"))

        val materialFactory = HakimMaterialFactory.status(context)
        check("مصنع حكيم للمادة فعّال", materialFactory.optBoolean("material_factory"))
        check("التصميم الرقمي لا يُعد منتجًا ماديًا", materialFactory.optBoolean("digital_design_is_not_physical_product"))
        check("التحقق المادي يتطلب نفس الأثر", materialFactory.optBoolean("same_artifact_required"))
        check("ترقية المصنع تفشل مغلقة", materialFactory.optBoolean("fail_closed_promotion"))

        val humanBiology = HakimHumanBiology.status(context)
        check("طبقة الأحياء والإنسان فعالة", humanBiology.optBoolean("human_biology"))
        check("القلب والدماغ ضمن التغطية", humanBiology.optBoolean("covers_heart") && humanBiology.optBoolean("covers_brain"))
        check("لا استقلال علاجي ذاتي", !humanBiology.optBoolean("autonomous_clinical_action"))
        check("التدخل المباشر خلف بوابة مختصة", humanBiology.optBoolean("direct_intervention_requires_qualified_gate"))

        val improvement = HakimSelfImprovementLoop.status(context)
        check("حلقة التحسين الذاتي محكومة", improvement.optBoolean("self_improvement_loop"))
        check("لا تعديل مصدر ذاتي على الهاتف", !improvement.optBoolean("source_mutation_on_device"))
        check("الرجوع أمامي من مصدر مثبت فقط", improvement.optString("rollback_strategy") == "FORWARD_ONLY_FROM_VERIFIED_BASELINE_SOURCE")
        check("توقيع D1 حاكم للترقية الميدانية", improvement.optBoolean("d1_required_for_field_update"))
        check("الجديد لا يرث النجاح", improvement.optBoolean("new_candidate_does_not_inherit_success"))

        val executionFabric = HakimExecutionFabric.status(context)
        check("نسيج التنفيذ فعّال", executionFabric.optBoolean("execution_fabric"))
        check("Online لا يُعلن بلا مسار حي", executionFabric.optBoolean("online_requires_live_path"))
        check("فشل مسار واحد لا يغلق المقصد", executionFabric.optBoolean("single_path_failure_does_not_close_goal"))

        val mainPrefs = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        val userDisabled = mainPrefs.getBoolean("pairing_disabled_by_user", false)
        val legacyPaired = mainPrefs.getString("command_topic", "").orEmpty().isNotBlank() &&
            mainPrefs.getString("result_topic", "").orEmpty().isNotBlank()
        val securePaired = HakimUnifiedRelay.isConfigured(context)
        val paired = legacyPaired || securePaired || mainPrefs.getBoolean("local_adb_paired", false)
        check("حالة الاقتران منطقية", userDisabled || paired, "warn", if (userDisabled) "فصل المستخدم محترم" else if (paired) "يوجد مسار مهيأ" else "غير مقترن")

        val recovery = HakimConnectionResilience.status(context)
        check(
            "خدمة الاتصال قابلة للاستعادة",
            userDisabled || !paired || recovery.optBoolean("service_running") || recovery.optString("state") == "restart_requested",
            "warn",
            recovery.optString("state")
        )
        check(
            "الاتصال الحي متاح عند الاقتران",
            userDisabled || !paired || recovery.optBoolean("online"),
            "warn",
            recovery.optString("state")
        )

        val scheduler = context.getSystemService(JobScheduler::class.java)
        val jobs = try { scheduler.allPendingJobs.map { it.id }.toSet() } catch (_: Exception) { emptySet() }
        check("الفحص/التطور الدوري مجدول", jobs.contains(JOB_ID), "warn")
        check("حارس استعادة الاتصال مجدول", jobs.contains(HakimConnectionResilience.JOB_ID), "warn")

        val lastSocketError = mainPrefs.getString("last_socket_error", "").orEmpty()
        val lastAuthError = mainPrefs.getString("last_auth_error", "").orEmpty()
        val lastCommandError = mainPrefs.getString("last_command_error", "").orEmpty()
        val lastRecoveryError = mainPrefs.getString("last_recovery_error", "").orEmpty()
        val recentErrors = listOf(lastSocketError, lastAuthError, lastCommandError, lastRecoveryError).count { it.isNotBlank() }
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
            .put("intent", intent)
            .put("capability_kernel", capability)
            .put("value_continuity", continuity)
            .put("goal_supervisor", supervisor)
            .put("goal_executor", executor)
            .put("material_factory", materialFactory)
            .put("human_biology", humanBiology)
            .put("execution_fabric", executionFabric)
            .put("self_improvement", improvement)
            .put("connection_recovery", recovery)
            .put("learning", HakimLearning.snapshot(context))

        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE).edit()
            .putString("last_self_check", report.toString().take(24000))
            .putLong("last_self_check_at", System.currentTimeMillis())
            .putString("last_self_check_status", status)
            .apply()
        return report
    }
}
