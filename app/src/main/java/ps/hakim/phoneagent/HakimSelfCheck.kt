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
        HakimFaultContainment.guard(context, "self_check", "schedule") {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, HakimEvolutionJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            scheduler.schedule(job)
        }
    }

    fun runAsync(context: Context) {
        Thread {
            val app = context.applicationContext
            HakimFaultContainment.guard(app, "self_check", "run_async") {
                val report = run(app)
                HakimLearning.recordHealth(app, report)
            }
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
        check("الدستور السيادي v5 مثبت", governance.optString("version").startsWith("SOVEREIGN-QURAN-V5"))
        check("فض التعارض بأضيق تغيير", governance.optBoolean("conflict_resolution_narrow_change"))
        check("ميزانية عدم اليقين مفعلة", governance.optBoolean("uncertainty_budget_enforced"))
        check("عقد المطلوب/الدليل/الاختبار مفعّل", governance.optBoolean("required_expected_evidence_test_state"))
        check("الوقاية أولًا مفعلة", governance.optBoolean("prevent_first"))
        check("التصعيد لأصغر تدخل فقط", governance.optBoolean("minimal_escalation_only"))
        check("حاكمية القرآن والسنة القيمية مفعلة", governance.optBoolean("quran_sunnah_values_governance"))
        check("سيادة مقصد المستخدم مفعلة", governance.optBoolean("user_goal_sovereignty"))
        check("حدود السلطة مفعلة", governance.optBoolean("authority_boundary_enforced"))
        check("المحتوى الخارجي بيانات لا أوامر", governance.optBoolean("external_content_data_not_commands"))
        check("لا عدد تكرار ثابت", governance.optBoolean("no_fixed_iteration_count"))
        check("الدورة الإضافية تتطلب مكسبًا ماديًا", governance.optBoolean("material_gain_required"))
        check("نفس القطعة المختبرة مطلوبة", governance.optBoolean("same_artifact_required"))
        check("الجديد لا يرث النجاح", governance.optBoolean("newer_does_not_inherit_success"))
        check("أقل صلاحية وبيانات وكلفة", governance.optBoolean("least_privilege_data_cost"))
        check("الفجوة المادية تمنع الاكتمال", governance.optBoolean("material_gap_blocks_complete"))
        check("أفضل/أنسب/أعلى مفعلة", governance.optBoolean("best_fit_highest"))
        check("التقاط القواعد الصريحة فقط", governance.optBoolean("automatic_rule_capture"))
        check("الأحدث الصريح يعلو", governance.optBoolean("latest_explicit_rule_wins"))
        check("المهمة المؤقتة لا تصبح قاعدة عامة", governance.optBoolean("temporary_task_not_global"))
        check("البيانات الحساسة لا تُرقى لقاعدة", governance.optBoolean("sensitive_data_not_promoted"))
        check("التعلم الذاتي محكوم", governance.optBoolean("self_learning_guarded"))
        check("التطور الذاتي محكوم", governance.optBoolean("self_evolution_guarded"))

        val quran = governance.optJSONObject("quranic_governance") ?: JSONObject()
        check("السنة الصحيحة بيان مع التثبت", quran.optBoolean("sahih_sunnah_is_explanatory_with_verification"))
        check("الخلاف المعتبر محفوظ", quran.optBoolean("recognized_scholarly_disagreement_respected"))
        check("لا سببية تقنية غيبية منسوبة للوحي", !quran.optBoolean("technical_causality_claimed"))
        check("لا إكراه ديني تقني", !quran.optBoolean("religious_coercion_allowed"))

        val arabic = governance.optJSONObject("arabic_policy") ?: JSONObject()
        check("العربية الفلسطينية ar-PS افتراضية", arabic.optString("locale") == HakimPalestinianArabicProfile.LOCALE_TAG)
        check("السياق الفلسطيني الافتراضي مفعّل", arabic.optBoolean("palestinian_context_default"))
        check("بوابة قبول العربية مطلوبة", arabic.optBoolean("output_gate_required"))

        val ledger = governance.optJSONObject("rule_ledger") ?: JSONObject()
        check("سجل القواعد مشفر محليًا", ledger.optBoolean("encrypted_local_ledger"))
        check("المهام المؤقتة لا تُحفظ كنص", !ledger.optBoolean("temporary_tasks_persisted"))
        check("تنقية الأسرار قبل الحفظ", ledger.optBoolean("secret_redaction_enabled"))

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
        check("القدرة لا تساوي التوفر", capability.optBoolean("capability_is_not_availability"))
        check("التوفر لا يساوي التفويض", capability.optBoolean("availability_is_not_authorization"))
        check("مصفوفة القدرات الحية موجودة", (capability.optJSONArray("runtime_matrix")?.length() ?: 0) > 0)

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

        val autonomous = HakimAutonomousContinuation.status(context)
        check("الاستمرار الذاتي الحدثي مفعّل", autonomous.optBoolean("autonomous_continuation"))
        check("الاستئناف الحدثي مفعّل", autonomous.optBoolean("event_driven_resume"))
        check("النسخ الاحتياطي الدوري للاستئناف مفعّل", autonomous.optBoolean("periodic_resume_backup"))
        check("الاستمرار الذاتي لا يستخدم دورانًا مشغولًا", autonomous.optBoolean("no_busy_loop"))
        check("بوابة الأثر العالي محفوظة أثناء الاستئناف", autonomous.optBoolean("high_impact_still_gated"))

        val runner = HakimAutonomousGoalRunner.status(context)
        check("منفذ المقاصد النصية الذاتي موجود", runner.optBoolean("autonomous_goal_runner"))
        check("التنفيذ الخلفي محصور في المسارات الآمنة", runner.optBoolean("safe_background_channels_only"))
        check("الأثر العالي لا ينفذ في الخلفية", !runner.optBoolean("high_impact_background_execution"))
        check("إغلاق الهدف ينتظر رؤية النتيجة", runner.optBoolean("ui_observation_required_before_close"))

        val executor = HakimGoalExecutor.heartbeat(context)
        check("منفذ المقصد موجود", executor.optBoolean("executor"))
        check("نبض المنفذ قابل للرصد", executor.has("heartbeat_at"))

        val cognitive = HakimCognitivePolicy.status(context)
        check("سياسة الإدراك الاحترافية مفعّلة", cognitive.optBoolean("professional_cognitive_policy"))
        check("التوجيه المعرفي قائم على الدليل", cognitive.optBoolean("evidence_driven_routing"))
        check("عمق التفكير متكيف مع المهمة", cognitive.optBoolean("adaptive_depth"))
        check("لا ترتيب ثابت لجودة المزودين", !cognitive.optBoolean("provider_quality_static_ranking"))

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

        val development = HakimDevelopmentControlPlane.status(context)
        check("طبقة طلب التطوير المحكومة فعالة", development.optBoolean("development_control_plane"))
        check("لا تعديل مصدر على الهاتف", !development.optBoolean("source_mutation_on_device"))
        check("لا سر GitHub على الهاتف", !development.optBoolean("github_secret_on_device"))
        check("فرع معزول مطلوب للتطوير", development.optBoolean("isolated_branch_required"))
        check("CI واختبار الانحدار إلزاميان", development.optBoolean("ci_required") && development.optBoolean("regression_test_required"))
        check("التثبيت الميداني خلف بوابة مستقلة", development.optBoolean("field_install_separate_gate"))

        val executionFabric = HakimExecutionFabric.status(context)
        check("نسيج التنفيذ فعّال", executionFabric.optBoolean("execution_fabric"))
        check("Online لا يُعلن بلا مسار حي", executionFabric.optBoolean("online_requires_live_path"))
        check("فشل مسار واحد لا يغلق المقصد", executionFabric.optBoolean("single_path_failure_does_not_close_goal"))

        val termux = HakimTermuxControl.status(context)
        check("قناة Termux المحلية معرفة", termux.optBoolean("termux_local_control"))
        check("Termux لا يكشف shell حرًا", !termux.optBoolean("arbitrary_shell_exposed"))
        check("Termux محصور بملفات تنفيذ ثابتة", termux.optBoolean("fixed_profiles_only"))
        check("Termux لا يحتاج حصة تحكم بعيدة", !termux.optBoolean("external_remote_quota_required"))
        check("Termux لا يحتاج مزودًا مدفوعًا", !termux.optBoolean("paid_provider_required"))
        check("الأثر العالي يبقى خلف بوابة مستقلة", termux.optBoolean("high_impact_requires_separate_gate"))
        check("لا ادعاء بلا حدود حرفيًا", !termux.optBoolean("unlimited_claim"))
        check("تطبيق Termux موجود", termux.optBoolean("termux_installed"), "warn")
        check("بوابة إذن RUN_COMMAND للمستخدم محفوظة", termux.optBoolean("permission_user_gate"))
        check("طلب الإذن من داخل حكيم مدعوم", termux.optBoolean("permission_request_supported"))
        check("صلاحية RUN_COMMAND مفعلة", termux.optBoolean("run_command_permission"), "warn")
        check("قناة Termux المحلية حيّة", termux.optBoolean("online"), "warn")

        val containment = HakimFaultContainment.status(context)
        check("حاجز الأعطال المركزي فعّال", containment.optBoolean("fault_containment"))
        check("الفشل الصامت ممنوع في المسارات الحرجة المحروسة", containment.optBoolean("critical_path_silent_failures_forbidden"))
        check("منع التكرار الأعمى فعال", containment.optBoolean("bounded_retry") && containment.optBoolean("circuit_breaker"))
        check("الأفعال عالية الأثر تفشل مغلقة عند العطل الحرج", containment.optBoolean("high_impact_fail_closed"))
        check("لا حظر أمان حرج نشط", !containment.optBoolean("high_impact_blocked"), "fail", "critical_blocks=" + containment.optInt("critical_blocks"))

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
        check("هوية الحزمة الصحيحة", context.packageName == BuildConfig.APPLICATION_ID)
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
            .put("autonomous_continuation", autonomous)
            .put("autonomous_goal_runner", runner)
            .put("goal_executor", executor)
            .put("cognitive_policy", cognitive)
            .put("material_factory", materialFactory)
            .put("human_biology", humanBiology)
            .put("execution_fabric", executionFabric)
            .put("termux_control", termux)
            .put("fault_containment", containment)
            .put("self_improvement", improvement)
            .put("development_control", development)
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
