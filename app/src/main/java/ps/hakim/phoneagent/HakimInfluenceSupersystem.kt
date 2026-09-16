package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * منظومة «كل ما هو مؤثر» العليا.
 *
 * الهدف ليس ادعاء حصر كل أسباب العالم، بل منع العمى المنهجي: كل عامل معروف ذي أثر
 * يدخل سجل التأثير، وأي عامل جديد/مجهول يظهر أثناء التنفيذ يُرفع فورًا إلى UNKNOWN_EMERGENT
 * ثم يتحول — إذا ثبت أثره — إلى قيد/حاجز/اختبار انحدار دائم.
 *
 * الحاكمية الشرعية لا تُستنبط آليًا من الكلمات. القرآن أصل الهداية والقيم والغاية
 * والحدود الشرعية، والسنة الصحيحة بيان؛ الحكم الدقيق يحتاج مصدرًا وسياقًا ودلالة،
 * وعند الشبهة أو الخلاف يُتحقق أولًا. الوسائل الدنيوية تبقى بالدليل العلمي والتجريبي.
 */
object HakimInfluenceSupersystem {
    const val VERSION = "HAKIM-INFLUENCE-SUPERSYSTEM-2026-09-16-v1"
    private const val PREFS = "hakim_influence_supersystem"

    enum class Factor(val title: String) {
        QURAN_SUNNAH_NORMATIVE("القرآن والسنة الصحيحة والقيم والحدود"),
        USER_INTENT("مقصد المستخدم وحدوده وقراره"),
        PLATFORM_SAFETY_RIGHTS_LAW("المنصة والسلامة والحقوق والقانون"),
        REALITY_EVIDENCE("الواقع والدليل ودرجة اليقين"),
        TIME_LOCATION_CONTEXT("الزمن والمكان والسياق المحلي عند الصلة"),
        HUMAN_IMPACT("الأثر على الإنسان والكرامة والعدل والرحمة"),
        DEVICE_RUNTIME("الهاتف والنظام وحالة التشغيل"),
        RESOURCE_PRESSURE("البطارية والحرارة والذاكرة والأداء"),
        NETWORK_CONNECTIVITY("الشبكة والاتصال والاستمرارية"),
        TOOLS_CAPABILITIES("الأدوات والخدمات والقدرات والبدائل"),
        AUTHORITY_PERMISSIONS("السلطة والصلاحيات والتفويض"),
        PRIVACY_SECRETS("الخصوصية والأسرار وتقليل البيانات"),
        COST_LOCKIN("الكلفة والارتهان وقابلية النقل"),
        SOURCES_FRESHNESS("المصادر والأصالة والحداثة"),
        DEPENDENCIES("الاعتماديات ونقاط الفشل"),
        MEMORY_CONTEXT("السياق والذاكرة وفقدان المعنى"),
        FAILURE_HISTORY("سجل الأعطال والأسباب الجذرية والحواجز"),
        LEARNING_FEEDBACK("التعلم والتغذية الراجعة وعدم الانحدار"),
        DELIVERY_VISIBLE_RESULT("المسلَّم والنتيجة المرئية الفعلية"),
        FIELD_EVIDENCE("الدليل الميداني من البيئة المستهدفة"),
        SECURITY_THREATS("التهديدات والحقن والتلاعب وسوء الاستخدام"),
        UNKNOWN_EMERGENT("عامل مجهول/ناشئ قد يصبح مؤثرًا")
    }

    enum class Worker(val title: String, val duty: String) {
        ORCHESTRATOR("المنسق الأعلى", "يربط المقصد والعوامل والوكلاء والأنظمة ويمنع تضاربها"),
        QURAN_SUNNAH_GUARD("حارس النزاهة الشرعية", "يتحقق قبل نسبة حكم للوحي ويمنع الإعانة على محرم ثابت ويطلب التثبت عند الشبهة"),
        INTENT_GUARD("حارس المقصد", "يحفظ مقصد المستخدم وحدوده ولا يستبدله باجتهاد النظام"),
        INFLUENCE_SCOUT("كشاف المؤثرات", "يبحث عن العامل المفقود أو الناشئ وأضعف حلقة وأعلى رافعة"),
        EVIDENCE_RESEARCH("وكيل الدليل والبحث", "يفصل الحقيقة عن الاستنتاج ويحدث المصادر والواقع"),
        PLANNER("وكيل التخطيط", "يحوّل الغاية إلى عقد ومسار واختبارات وقبول وتراجع"),
        EXECUTOR("العامل التنفيذي", "ينفذ فقط الأعمال المأذونة منخفضة الأثر والقابلة للتراجع تلقائيًا"),
        PHONE_BROWSER("عامل الهاتف والمتصفح", "ينفذ على الهاتف/المتصفح ضمن الصلاحيات ويقيس الأثر الفعلي"),
        EDUCATION_TEACHER("الوكيل المعلم", "يبني التعليم المناسب للمنهاج والعمر والسياق ويقيس الفهم"),
        SELF_LEARNER("وكيل التعلم الذاتي", "يتعلم من النتائج والأخطاء ويحسن الترتيب والحواجز دون تعديل كود ذاتي أو توسيع سلطة"),
        PROACTIVE_SCOUT("الوكيل الاستباقي", "يبحث عن مكسب آمن قبل طلب جديد دون تحويل الصمت إلى تفويض"),
        CRITIC("الناقد المستقل", "يحاول إسقاط الخطة بأمثلة مضادة وفشل معلوم قبل الاعتماد"),
        VERIFIER("وكيل التحقق", "يفحص المسلَّم نفسه والمرئي والميدان والانحدار"),
        SAFETY_SECURITY("وكيل السلامة والأمن", "يحمي الحقوق والأسرار ويقاوم الحقن والتلاعب ويطبق أقل امتياز"),
        RESILIENCE("وكيل التعافي", "يحفظ الصحيح ويبدل الوسيلة ويصلح الجذر ويمنع التكرار"),
        RESOURCE_GOVERNOR("وكيل الموارد", "يحمي أداء الهاتف ويمنع الاستقلال الظاهري على حساب الاستقرار"),
        FIELD_AUDITOR("مدقق الميدان", "يفصل SOURCE/CI/SIGNED عن FIELD ويمنع إعلان الأثر بلا دليل فعلي")
    }

    data class Snapshot(
        val goal: String,
        val factors: List<Factor>,
        val workers: List<Worker>,
        val agents: List<String>,
        val units: List<String>,
        val resourceMode: String,
        val decisionMode: String,
        val religiousReview: String,
        val executionGate: String,
        val weakestLinkRule: String,
        val unknownFactorRule: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("version", VERSION)
            .put("goal", goal)
            .put("factors", JSONArray(factors.map { it.name }))
            .put("workers", JSONArray(workers.map { it.name }))
            .put("agents", JSONArray(agents))
            .put("system_units", JSONArray(units))
            .put("resource_mode", resourceMode)
            .put("decision_mode", decisionMode)
            .put("religious_review", religiousReview)
            .put("execution_gate", executionGate)
            .put("weakest_link_rule", weakestLinkRule)
            .put("unknown_factor_rule", unknownFactorRule)
            .put("quranic_inheritance", true)
            .put("authentic_sunnah_guidance", true)
            .put("worldly_means_evidence_based", true)
            .put("no_automated_fatwa_from_keywords", true)
            .put("verified_prohibition_blocks_assistance", true)
            .put("doubt_requires_verification_before_material_effect", true)
            .put("user_intent_not_replaced", true)
            .put("authority_never_self_expands", true)
            .put("silence_not_consent", true)
            .put("self_learning_not_self_modifying_code", true)
            .put("no_autonomous_merge_or_signing", true)
            .put("delivered_output_must_be_tested", true)
            .put("field_requires_real_evidence", true)
    }

    fun initialize(context: Context) {
        HakimQuranicInvariantKernel.requireInherited("influence_supersystem_initialize")
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("enabled")) {
            p.edit()
                .putBoolean("enabled", true)
                .putString("version", VERSION)
                .putLong("created_at", System.currentTimeMillis())
                .apply()
        }
    }

    fun snapshot(context: Context, rawGoal: String): Snapshot {
        initialize(context)
        val app = context.applicationContext
        val activeMission = HakimMissionLedger.active(app)
        val goal = rawGoal.trim().ifBlank { activeMission?.goal.orEmpty().ifBlank { "استمرارية حكيم وتطويره الآمن" } }
        val agentPlan = HakimAgentSystem.plan(app, goal)
        val system = HakimSystemOfSystems.compose(app, goal)
        val resource = HakimResourceGovernor.snapshot(app)
        val religious = HakimReligiousIntegrity.assess(goal)
        val shubha = HakimHalalShubuhatGuard.assessTask(goal, agentPlan.highImpact)

        val factors = Factor.entries.toMutableList()
        val workers = linkedSetOf(
            Worker.ORCHESTRATOR,
            Worker.QURAN_SUNNAH_GUARD,
            Worker.INTENT_GUARD,
            Worker.INFLUENCE_SCOUT,
            Worker.EVIDENCE_RESEARCH,
            Worker.PLANNER,
            Worker.EXECUTOR,
            Worker.SELF_LEARNER,
            Worker.PROACTIVE_SCOUT,
            Worker.CRITIC,
            Worker.VERIFIER,
            Worker.SAFETY_SECURITY,
            Worker.RESILIENCE,
            Worker.RESOURCE_GOVERNOR,
            Worker.FIELD_AUDITOR
        )
        if (agentPlan.agents.any { it == HakimAgentSystem.Agent.BROWSER || it == HakimAgentSystem.Agent.FORMS }) {
            workers += Worker.PHONE_BROWSER
        }
        if (agentPlan.agents.any { it == HakimAgentSystem.Agent.EDUCATION }) workers += Worker.EDUCATION_TEACHER

        val religiousReview = when {
            shubha != null -> "${shubha.ruling}/${shubha.gate}"
            religious.exactSourceRequired -> "VERIFY_EXACT_SOURCE"
            religious.religious -> "RELIGIOUS_CONTEXT_ACTIVE"
            else -> "NOT_RELIGIOUS"
        }
        val gate = when {
            shubha?.gate == HakimHalalShubuhatGuard.Gate.BLOCK_AND_ALTERNATIVE -> "BLOCK_AND_PERMISSIBLE_ALTERNATIVE"
            shubha?.gate == HakimHalalShubuhatGuard.Gate.ABSTAIN -> "ABSTAIN_VERIFY_FIRST"
            shubha?.gate == HakimHalalShubuhatGuard.Gate.VERIFY_FIRST -> "VERIFY_FIRST"
            agentPlan.needsApproval -> "USER_APPROVAL_GATE"
            agentPlan.route == "blocked" -> "BLOCKED_BY_SOVEREIGN_GOVERNANCE"
            else -> "SAFE_AUTHORIZED_EXECUTION"
        }

        return Snapshot(
            goal = goal.take(1800),
            factors = factors,
            workers = workers.toList(),
            agents = agentPlan.agents.map { it.name },
            units = system.units.map { it.name },
            resourceMode = resource.mode.name,
            decisionMode = agentPlan.decisionMode,
            religiousReview = religiousReview,
            executionGate = gate,
            weakestLinkRule = "وجّه العمل إلى أضعف حلقة/أعلى رافعة ولا توزع الجهد بالتساوي",
            unknownFactorRule = "أي عامل جديد مادي الأثر⇒سجّل→اعزل/قيّد→اختبر→أضف حاجز انحدار→أعد التقييم"
        )
    }

    /**
     * دورة ذاتية آمنة: تنسق وتتعلم وتوثق ولا تنفذ فعلًا خارجيًا عالي الأثر.
     * التنفيذ الخارجي يبقى في محركات حكيم القائمة وتحت غلاف السلطة والموافقة.
     */
    fun runSafeCycle(context: Context, reason: String): JSONObject {
        val app = context.applicationContext
        val mission = HakimMissionLedger.active(app)
        val s = snapshot(app, mission?.goal.orEmpty())
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = p.getString("last_snapshot", "").orEmpty()
        val current = s.toJson()
            .put("reason", reason.take(120))
            .put("time", System.currentTimeMillis())
            .put("previous_snapshot_present", previous.isNotBlank())

        // تعلم ذاتي محافظ: يسجل الحالة للمقارنة اللاحقة ولا يعدّل الكود أو السلطة أو الأسرار.
        p.edit()
            .putString("last_snapshot", current.toString().take(24000))
            .putString("last_gate", s.executionGate)
            .putString("last_resource_mode", s.resourceMode)
            .putLong("last_cycle_at", System.currentTimeMillis())
            .apply()

        return current
    }

    fun promptContext(context: Context, rawGoal: String): String {
        val s = snapshot(context, rawGoal)
        return buildString {
            appendLine("[منظومة كل ما هو مؤثر — $VERSION]")
            appendLine("اعتبر المهمة نظامًا داخل بيئة من العوامل المؤثرة، ولا تفترض أن القائمة مغلقة. ابحث عن العامل المفقود وأضعف حلقة وأعلى رافعة قبل زيادة التعقيد.")
            appendLine("الغاية=${s.goal}")
            appendLine("البوابة=${s.executionGate}؛ المراجعة الشرعية=${s.religiousReview}؛ الموارد=${s.resourceMode}.")
            appendLine("العمال النشطون=${s.workers.joinToString("،") { it.title }}")
            appendLine("قاعدة الشرع: إذا ثبت تعارض الفعل مع حكم شرعي ثابت فلا تُعِن عليه وقدّم البديل المشروع الأقرب للغاية؛ إذا كان الحكم مشتبهًا/مجهولًا فلا تنسب التحريم أو الإباحة إلى الوحي، بل تحقق من مصدر معتبر وسياق ودلالة ثم أعد التقييم.")
            appendLine("قاعدة السيادة: لا يوسع أي وكيل سلطته أو بياناته أو صلاحياته أو كلفته بنفسه، ولا يحول الصمت إلى موافقة، ولا يكتب/يوقع/يدمج كودًا ذاتيًا خارج بوابات التغيير والاختبار والرجوع.")
            appendLine("قاعدة التعلم: تعلم من النتيجة والفشل والتغذية الراجعة بتحسين الترتيب والحواجز والاختبارات والذاكرة القابلة للرجوع؛ النجاح الجديد لا يرث صفة الميدان بلا دليل ميداني.")
            appendLine("قاعدة المجهول: ${s.unknownFactorRule}")
            appendLine("قاعدة المسلَّم: اختبر الناتج الذي سيسلَّم فعلًا؛ أي اختلاف جوهري بين المختبَر والمسلَّم يعيد الحالة إلى غير مثبت.")
        }.take(5200)
    }

    fun status(context: Context): JSONObject {
        initialize(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", VERSION)
            .put("enabled", p.getBoolean("enabled", true))
            .put("all_material_influences_open_world_registry", true)
            .put("unknown_emergent_factor_channel", true)
            .put("agent_orchestration", true)
            .put("proactive_workers", true)
            .put("education_teacher_worker", true)
            .put("self_learning_worker", true)
            .put("independent_verifier_and_critic", true)
            .put("quran_sunnah_normative_guard", true)
            .put("verified_prohibition_blocks_assistance", true)
            .put("doubt_verifies_before_material_effect", true)
            .put("self_learning_not_self_modifying_code", true)
            .put("authority_never_self_expands", true)
            .put("last_gate", p.getString("last_gate", "UNSEEN"))
            .put("last_resource_mode", p.getString("last_resource_mode", "UNSEEN"))
            .put("last_cycle_at", p.getLong("last_cycle_at", 0L))
    }
}