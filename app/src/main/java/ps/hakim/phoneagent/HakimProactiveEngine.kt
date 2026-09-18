package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * محرك المبادرة الذاتية المفيدة.
 * ينفذ من تلقاء نفسه فقط ما هو منخفض الأثر، قابل للتراجع، داخل صلاحيات مثبتة، ولا يحتاج سرًا أو موافقة جديدة.
 * الأفعال الخارجية عالية الأثر لا تصبح تلقائية مهما كانت منفعتها المتوقعة؛ يحضّرها حكيم ويتوقف عند البوابة الأخيرة.
 */
object HakimProactiveEngine {
    private const val PREFS = "hakim_proactive"
    private const val AUTO_RESUME_COOLDOWN_MS = 15L * 60L * 1000L

    fun initialize(context: Context) {
        HakimQuranicInvariantKernel.requireInherited("proactive_initialize")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("enabled")) {
            p.edit()
                .putBoolean("enabled", true)
                .putLong("created_at", System.currentTimeMillis())
                .apply()
        }
    }

    fun isEnabled(context: Context): Boolean {
        initialize(context)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .putLong("changed_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * دورة خلفية آمنة ومتكيفة مع الهاتف: في ضغط الموارد تُؤجل الصيانة غير العاجلة بدل منافسة المستخدم.
     * لا تنقر واجهات المستخدم ولا ترسل/تحذف/تدفع من الخلفية.
     */
    fun runSafeBackground(context: Context, reason: String, healthStatus: String? = null): JSONObject {
        val app = context.applicationContext
        HakimQuranicInvariantKernel.requireInherited("proactive_background")
        HakimIntegrationFabric.requireCore(app, "proactive_background")
        initialize(app)
        HakimSovereignOneKernel.recordSignal(
            app,
            HakimSovereignOneKernel.SignalKind.EVOLUTION,
            "proactive_background",
            "reason=${reason.take(60)};health=${healthStatus?.take(40) ?: "UNKNOWN"}",
            90
        )

        val actions = JSONArray()
        if (!isEnabled(app)) return report(app, reason, "DISABLED_BY_USER", actions)

        val resources = HakimResourceGovernor.snapshot(app)
        if (resources.mode == HakimResourceGovernor.Mode.PRESSURE) {
            actions.put("deferred_nonessential_background_due_to_resource_pressure")
            return report(app, reason, "DEFERRED_RESOURCE_PRESSURE", actions)
        }

        // هذه العمليات محلية وخفيفة وتحافظ على التعلم دون شبكة أو نموذج محلي ثقيل.
        runCatching {
            HakimLearning.maintenance(app)
            actions.put("learning_maintenance")
        }
        runCatching {
            HakimAdaptiveLearning.consolidate(app, healthStatus ?: lastHealthStatus(app))
            actions.put("adaptive_consolidation")
        }

        val active = HakimMissionLedger.active(app)
        if (active != null) {
            runCatching {
                val recovery = HakimConnectionResilience.recover(app, "proactive_${reason.take(48)}")
                actions.put("connection_recovery:${recovery.optString("state", "unknown")}")
            }
        }

        // الفحوص الأثقل لا تعمل في وضع الاقتصاد إلا عند الحاجة؛ جودة المهمة الحالية لا تتأثر.
        if (resources.mode != HakimResourceGovernor.Mode.CONSERVE) {
            runCatching {
                HakimConstraintDoctor.run(app, "proactive_${reason.take(48)}")
                actions.put("constraint_doctor")
            }
        } else {
            actions.put("constraint_doctor_deferred_conserve_mode")
        }

        // تأسيس النص القرآني الموثق عمل صيانة نادر ومنخفض الأثر، لكنه قد ينقل قرابة ١٠–٢٢ م.ب.
        // لذلك لا يبدأ تلقائيًا على شبكة محسوبة/وضع اقتصاد/ضغط موارد، ولا يعتمد الملف إلا بعد
        // بصمة المصدر الرسمي وفحص السور الـ١١٤ والآيات كاملة داخل HakimVerifiedQuranCorpus.
        if (!HakimVerifiedQuranCorpus.isReady(app)) {
            if (resources.mode != HakimResourceGovernor.Mode.CONSERVE &&
                HakimResourceGovernor.canUseRealtimeBackgroundNetwork(app)
            ) {
                runCatching {
                    val quran = HakimQuranBootstrap.syncIfNeeded(app)
                    actions.put(
                        when {
                            quran.success -> "verified_quran_bootstrap:ready"
                            quran.deferred -> "verified_quran_bootstrap:deferred"
                            else -> "verified_quran_bootstrap:rejected_or_failed"
                        }
                    )
                }.onFailure {
                    HakimFaultLedger.record(app, "proactive_quran_bootstrap", it, severity = HakimFaultLedger.Severity.WARNING)
                    actions.put("verified_quran_bootstrap:failed_recorded")
                }
            } else {
                actions.put("verified_quran_bootstrap:deferred_by_resources_or_network")
            }
        } else {
            actions.put("verified_quran_bootstrap:already_ready")
        }

        runCatching {
            AutoUpdater.schedule(app)
            if (HakimResourceGovernor.canUseRealtimeBackgroundNetwork(app)) {
                AutoUpdater.startRealtimeListener(app)
                AutoUpdater.checkAsync(app)
                actions.put("trusted_update_realtime_and_check")
            } else {
                actions.put("trusted_update_scheduled_background_network_deferred")
            }
        }

        if (active != null) {
            val opportunity = foregroundOpportunity(app)
            actions.put(if (opportunity != null) "safe_mission_ready_for_foreground_resume" else "active_mission_not_auto_resumable")
        }

        val result = report(app, reason, "PASS", actions)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_report", result.toString().take(12000))
            .putLong("last_run_at", System.currentTimeMillis())
            .apply()
        return result
    }

    /**
     * يعيد مهمة لاستئنافها فقط في واجهة حكيم عندما تكون منخفضة الأثر وغير منتظرة لسر/ثقة/موافقة.
     * لا ينفذ شيئًا هنا؛ التنفيذ لاحقًا يمر مجددًا عبر المحرك السيادي وغلاف السلطة.
     * أي خلل غير حاكم في فحص الاستئناف يسجل محليًا ويعيد null بدل إسقاط واجهة المستخدم.
     */
    fun foregroundOpportunity(context: Context): HakimMissionLedger.Mission? {
        if (HakimCrashShield.shouldSuppressProactiveResume(context)) return null
        return try {
            if (!isEnabled(context)) return null
            val mission = HakimMissionLedger.active(context) ?: return null
            if (mission.phase in setOf(
                    HakimMissionLedger.Phase.COMPLETE,
                    HakimMissionLedger.Phase.CANCELLED,
                    HakimMissionLedger.Phase.BLOCKED,
                    HakimMissionLedger.Phase.WAITING_APPROVAL,
                    HakimMissionLedger.Phase.WAITING_CREDENTIAL,
                    HakimMissionLedger.Phase.WAITING_TRUST
                )) return null
            if (mission.failures >= 3) return null

            val one = HakimSovereignOneKernel.frame(context, mission.goal)
            val decision = one.decision
            if (decision.mode != HakimDecisionMatrix.Mode.AUTO && decision.mode != HakimDecisionMatrix.Mode.AUTO_VERIFY) return null
            if (one.blocked || one.needsApproval || one.shouldResearchFirst) return null

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastId = p.getString("last_auto_resume_mission_id", "")
            val lastAt = p.getLong("last_auto_resume_at", 0L)
            if (lastId == mission.id && now - lastAt < AUTO_RESUME_COOLDOWN_MS) return null
            mission
        } catch (t: Throwable) {
            HakimCrashShield.recordNonFatal(context, "proactive_foreground_opportunity", t)
            null
        }
    }

    fun markForegroundResume(context: Context, mission: HakimMissionLedger.Mission) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_auto_resume_mission_id", mission.id)
            .putLong("last_auto_resume_at", System.currentTimeMillis())
            .apply()
    }

    fun status(context: Context): JSONObject {
        initialize(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mission = HakimMissionLedger.active(context)
        return JSONObject()
            .put("proactive_engine", true)
            .put("one_sovereign_kernel_integrated", true)
            .put("enabled", p.getBoolean("enabled", true))
            .put("beneficial_safe_actions_auto", true)
            .put("background_safe_maintenance", true)
            .put("resource_adaptive_background", true)
            .put("verified_quran_bootstrap_integrated", true)
            .put("verified_quran_bootstrap_unmetered_and_resource_guarded", true)
            .put("foreground_safe_resume", true)
            .put("crash_safe_recovery_suppresses_auto_resume", HakimCrashShield.shouldSuppressProactiveResume(context))
            .put("realtime_update_reasserted", true)
            .put("high_impact_never_silently_authorized", true)
            .put("silence_not_consent", true)
            .put("no_secret_or_permission_escalation", true)
            .put("active_mission", mission != null)
            .put("active_phase", mission?.phase?.name ?: "IDLE")
            .put("resource_governor", HakimResourceGovernor.status(context))
            .put("verified_quran", HakimVerifiedQuranCorpus.status(context))
            .put("quran_bootstrap", HakimQuranBootstrap.status(context))
            .put("last_run_at", p.getLong("last_run_at", 0L))
            .put("last_auto_resume_at", p.getLong("last_auto_resume_at", 0L))
    }

    fun promptContext(context: Context): String {
        val s = status(context)
        val resource = s.optJSONObject("resource_governor") ?: JSONObject()
        return buildString {
            appendLine("[المبادرة الذاتية المفيدة]")
            appendLine("مفعلة=${s.optBoolean("enabled")}. ابحث تلقائيًا عن كل مكسب مفيد وآمن داخل العقد ولا تنتظر طلبًا جديدًا إذا كان التنفيذ منخفض الأثر وقابلًا للتراجع ومصرحًا.")
            appendLine("حالة موارد الهاتف=${resource.optString("mode", "UNKNOWN")}. الأولوية دائمًا لسرعة المهمة الحالية؛ عند ضغط الموارد تُؤجل الصيانة غير العاجلة ولا تُخفَّض جودة القرار أو الحاكمية.")
            appendLine("نفذ تلقائيًا: الصيانة، التحقق، التعلم، تحسين الأداء، التعافي، استعادة الاتصال، فحص التحديثات الموثوقة، واستئناف المهمة الآمنة غير المكتملة عند توفر سياق كافٍ.")
            appendLine("إذا لم تكن قاعدة القرآن المتحققة جاهزة، يجوز تأسيسها تلقائيًا فقط عند موارد وشبكة مناسبة؛ التنزيل قناة نقل لا مصدر ثقة، والاعتماد يبقى رهين البصمة الرسمية وفحص الـ١١٤ سورة.")
            appendLine("حافظ على قناة التحديث الفوري عندما تسمح الموارد والشبكة، ومعها الفحص الدوري كمسار بديل؛ انقطاع مسار واحد لا يلغي التطور أو التحديث.")
            appendLine("حضّر فقط وتوقف عند آخر بوابة: دفع/شراء، حذف نهائي، إرسال حساس أو علني، كشف سر أو بيانات محمية، منح صلاحية نظام، تغيير حق أو التزام، أو فعل غير قابل للتراجع.")
            appendLine("لا تعتبر الصمت أو الطيبة أو عبارة عامة تفويضًا جديدًا. المبادرة تزيد الفائدة داخل السلطة ولا توسع السلطة نفسها.")
        }.take(4800)
    }

    private fun lastHealthStatus(context: Context): String =
        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
            .getString("last_self_check_status", "UNKNOWN").orEmpty()

    private fun report(context: Context, reason: String, status: String, actions: JSONArray): JSONObject = JSONObject()
        .put("status", status)
        .put("reason", reason.take(100))
        .put("time", System.currentTimeMillis())
        .put("enabled", context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true))
        .put("resource_mode", HakimResourceGovernor.snapshot(context).mode.name)
        .put("actions", actions)
        .put("authority_not_expanded", true)
        .put("quality_not_downgraded_for_background_savings", true)
        .put("high_impact_requires_gate", true)
}
