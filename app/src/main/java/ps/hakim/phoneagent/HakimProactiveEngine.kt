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
     * دورة خلفية آمنة: صيانة التعلم، فحص القيود، استعادة الاتصال، وفحص التحديثات الموثوقة.
     * لا تنقر واجهات المستخدم ولا ترسل/تحذف/تدفع من الخلفية.
     */
    fun runSafeBackground(context: Context, reason: String, healthStatus: String? = null): JSONObject {
        val app = context.applicationContext
        HakimQuranicInvariantKernel.requireInherited("proactive_background")
        HakimIntegrationFabric.requireCore(app, "proactive_background")
        initialize(app)

        val actions = JSONArray()
        if (!isEnabled(app)) return report(app, reason, "DISABLED_BY_USER", actions)

        runCatching {
            HakimLearning.maintenance(app)
            actions.put("learning_maintenance")
        }
        runCatching {
            HakimAdaptiveLearning.consolidate(app, healthStatus ?: lastHealthStatus(app))
            actions.put("adaptive_consolidation")
        }
        runCatching {
            val recovery = HakimConnectionResilience.recover(app, "proactive_${reason.take(48)}")
            actions.put("connection_recovery:${recovery.optString("state", "unknown")}")
        }
        runCatching {
            HakimConstraintDoctor.run(app, "proactive_${reason.take(48)}")
            actions.put("constraint_doctor")
        }
        runCatching {
            AutoUpdater.schedule(app)
            AutoUpdater.startRealtimeListener(app)
            AutoUpdater.checkAsync(app)
            actions.put("trusted_update_realtime_and_check")
        }

        val active = HakimMissionLedger.active(app)
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
     */
    fun foregroundOpportunity(context: Context): HakimMissionLedger.Mission? {
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

        val decision = HakimDecisionMatrix.evaluate(mission.goal)
        if (decision.mode != HakimDecisionMatrix.Mode.AUTO && decision.mode != HakimDecisionMatrix.Mode.AUTO_VERIFY) return null

        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastId = p.getString("last_auto_resume_mission_id", "")
        val lastAt = p.getLong("last_auto_resume_at", 0L)
        if (lastId == mission.id && now - lastAt < AUTO_RESUME_COOLDOWN_MS) return null
        return mission
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
            .put("enabled", p.getBoolean("enabled", true))
            .put("beneficial_safe_actions_auto", true)
            .put("background_safe_maintenance", true)
            .put("foreground_safe_resume", true)
            .put("realtime_update_reasserted", true)
            .put("high_impact_never_silently_authorized", true)
            .put("silence_not_consent", true)
            .put("no_secret_or_permission_escalation", true)
            .put("active_mission", mission != null)
            .put("active_phase", mission?.phase?.name ?: "IDLE")
            .put("last_run_at", p.getLong("last_run_at", 0L))
            .put("last_auto_resume_at", p.getLong("last_auto_resume_at", 0L))
    }

    fun promptContext(context: Context): String {
        val s = status(context)
        return buildString {
            appendLine("[المبادرة الذاتية المفيدة]")
            appendLine("مفعلة=${s.optBoolean("enabled")}. ابحث تلقائيًا عن كل مكسب مفيد وآمن داخل العقد ولا تنتظر طلبًا جديدًا إذا كان التنفيذ منخفض الأثر وقابلًا للتراجع ومصرحًا.")
            appendLine("نفذ تلقائيًا: الصيانة، التحقق، التعلم، تحسين الأداء، التعافي، استعادة الاتصال، فحص التحديثات الموثوقة، واستئناف المهمة الآمنة غير المكتملة عند توفر سياق كافٍ.")
            appendLine("حافظ على قناة التحديث الفوري، ومعها فحص مباشر ودوري كمسارات بديلة؛ انقطاع مسار واحد لا يلغي التطور أو التحديث.")
            appendLine("حضّر فقط وتوقف عند آخر بوابة: دفع/شراء، حذف نهائي، إرسال حساس أو علني، كشف سر أو بيانات محمية، منح صلاحية نظام، تغيير حق أو التزام، أو فعل غير قابل للتراجع.")
            appendLine("لا تعتبر الصمت أو الطيبة أو عبارة عامة تفويضًا جديدًا. المبادرة تزيد الفائدة داخل السلطة ولا توسع السلطة نفسها.")
        }.take(3600)
    }

    private fun lastHealthStatus(context: Context): String =
        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
            .getString("last_self_check_status", "UNKNOWN").orEmpty()

    private fun report(context: Context, reason: String, status: String, actions: JSONArray): JSONObject = JSONObject()
        .put("status", status)
        .put("reason", reason.take(100))
        .put("time", System.currentTimeMillis())
        .put("enabled", context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true))
        .put("actions", actions)
        .put("authority_not_expanded", true)
        .put("high_impact_requires_gate", true)
}
