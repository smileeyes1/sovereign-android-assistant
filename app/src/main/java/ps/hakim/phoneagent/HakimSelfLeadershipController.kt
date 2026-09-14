package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * القيادة الذاتية السيادية: المستخدم يحدد الغاية والحدود والقرارات الجوهرية،
 * وحكيم يقود «كيف» والتفويض والتعافي والتحقق داخل غلاف السلطة والقدرات المثبتة.
 */
object HakimSelfLeadershipController {
    enum class Mode {
        LEAD_AUTONOMOUSLY,
        LEAD_AND_VERIFY,
        RESEARCH_AND_REPLAN,
        RECOVER_CAPABILITY,
        WAIT_APPROVAL,
        WAIT_CREDENTIAL,
        WAIT_TRUST,
        CANCELLED,
        BLOCKED,
        IDLE
    }

    data class State(
        val mode: Mode,
        val reason: String,
        val missionPhase: String,
        val accessibilityReady: Boolean,
        val browserReady: Boolean,
        val inAppReasoningReady: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("mode", mode.name)
            .put("reason", reason)
            .put("mission_phase", missionPhase)
            .put("accessibility_ready", accessibilityReady)
            .put("browser_ready", browserReady)
            .put("in_app_reasoning_ready", inAppReasoningReady)
    }

    fun evaluate(context: Context, goal: String): State {
        val mission = HakimMissionLedger.active(context)
        val decision = HakimDecisionMatrix.evaluate(goal)
        val accessibility = HakimCapabilityRegistry.isReady(context, "accessibility_actions")
        val browser = HakimCapabilityRegistry.isReady(context, "browser")
        val inAppReasoning = HakimCapabilityRegistry.isReady(context, "in_app_reasoning")
        val phase = mission?.phase

        val interactivePathReady = accessibility || browser || inAppReasoning
        val mode = when {
            phase == HakimMissionLedger.Phase.CANCELLED -> Mode.CANCELLED
            phase == HakimMissionLedger.Phase.BLOCKED -> Mode.BLOCKED
            phase == HakimMissionLedger.Phase.WAITING_APPROVAL -> Mode.WAIT_APPROVAL
            phase == HakimMissionLedger.Phase.WAITING_CREDENTIAL -> Mode.WAIT_CREDENTIAL
            phase == HakimMissionLedger.Phase.WAITING_TRUST -> Mode.WAIT_TRUST
            decision.mode == HakimDecisionMatrix.Mode.BLOCK -> Mode.BLOCKED
            decision.mode == HakimDecisionMatrix.Mode.APPROVAL_GATE -> Mode.WAIT_APPROVAL
            decision.mode == HakimDecisionMatrix.Mode.RESEARCH_FIRST -> Mode.RESEARCH_AND_REPLAN
            looksLikeInteractiveTask(goal) && !interactivePathReady -> Mode.RECOVER_CAPABILITY
            decision.mode == HakimDecisionMatrix.Mode.AUTO -> Mode.LEAD_AUTONOMOUSLY
            else -> Mode.LEAD_AND_VERIFY
        }

        val reason = when (mode) {
            Mode.LEAD_AUTONOMOUSLY -> "المقصد واضح ومنخفض الأثر؛ حكيم يقود كيف تلقائيًا ويغلق المهمة بالدليل"
            Mode.LEAD_AND_VERIFY -> "يقود حكيم التنفيذ مع تحقق مباشر بعد الخطوات المؤثرة"
            Mode.RESEARCH_AND_REPLAN -> "الدليل/الحداثة/السلامة المعيارية تحتاج تحققًا ثم إعادة تخطيط"
            Mode.RECOVER_CAPABILITY -> "لا يوجد الآن مسار تنفيذي/استدلالي جاهز للمهمة؛ استعد الوصلة أو بدّل المسار دون توسيع السلطة"
            Mode.WAIT_APPROVAL -> "وصلت المهمة إلى قرار جوهري أو أثر عالٍ يحتاج موافقة المستخدم"
            Mode.WAIT_CREDENTIAL -> "وصلت المهمة إلى اعتماد سري يجب أن يبقى في قناة النظام الآمنة"
            Mode.WAIT_TRUST -> "إخراج بيانات الخزنة يحتاج ثقة وجهة دقيقة"
            Mode.CANCELLED -> "أوقف المستخدم المهمة؛ لا يجوز الاستئناف التلقائي لنفس الغاية"
            Mode.BLOCKED -> "حاجز سيادي يمنع التنفيذ حتى تتغير الحالة/الدليل/السلطة"
            Mode.IDLE -> "لا توجد مهمة نشطة"
        }
        return State(mode, reason, phase?.name ?: "IDLE", accessibility, browser, inAppReasoning)
    }

    fun promptContext(context: Context, goal: String): String {
        val s = evaluate(context, goal)
        return buildString {
            appendLine("[القيادة الذاتية السيادية]")
            appendLine("الوضع=${s.mode}؛ ${s.reason}")
            appendLine("المستخدم يملك WHAT/WHY/الحدود والقرارات الجوهرية. حكيم يملك HOW: الفهم، التفكيك، توليد البدائل، اختيار الوكلاء والأدوات، ترتيب الخطوات، التنفيذ منخفض الأثر، التحقق، الإصلاح، التعافي، والاستئناف.")
            appendLine("الاستدلال الداخلي=${s.inAppReasoningReady}، المتصفح الحاضر=${s.browserReady}، الوصول الشامل=${s.accessibilityReady}. لا تجعل Accessibility شرطًا إذا كان مسار حكيم الداخلي يحقق الغاية بأقل صلاحية.")
            appendLine("لا تسأل عن اختيار وسيط يمكن حسمه بالدليل والسياق. اسأل/توقف فقط عند مجهول جوهري أو بوابة سلطة/سر/ثقة/أثر عالٍ.")
            appendLine("لا توسع السلطة ذاتيًا، لا تثبت صلاحية لنفسك، ولا تعتبر عبارة عامة إذنًا جديدًا. استخدم أقل صلاحية وأقل كشف وأقصر مسار يحقق الغاية.")
            appendLine("افحص القدرات الفعلية قبل التخطيط؛ غياب أداة يغيّر المسار ولا يغيّر الغاية. لا تدّع اكتمالًا إلا بدليل من الناتج الفعلي.")
            appendLine("الأولوية التشغيلية: PREVENT→PLAN→EXECUTE→VERIFY→RECOVER→LEARN→FREEZE، مع WIP=1 ومنع الدوران وإعادة ما نجح.")
            appendLine("حق المستخدم في STOP/CANCEL أعلى من الاستمرارية: عند الإلغاء أوقف الحلقات ولا تستأنف نفس الغاية تلقائيًا.")
            append(HakimAuthorityEnvelope.promptContext())
            append(HakimCapabilityRegistry.promptContext(context))
        }.take(7800)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("sovereign_self_leadership", true)
        .put("user_owns_what_why_boundaries", true)
        .put("hakim_owns_how_within_authority", true)
        .put("auto_delegation", true)
        .put("in_app_reasoning_is_first_class_path", true)
        .put("accessibility_not_required_when_lower_authority_path_suffices", true)
        .put("capability_awareness", HakimCapabilityRegistry.status(context))
        .put("authority_envelope", HakimAuthorityEnvelope.status())
        .put("user_cancel_supremacy", true)
        .put("no_self_privilege_escalation", true)
        .put("evidence_required_for_completion", true)

    private fun looksLikeInteractiveTask(goal: String): Boolean = Regex(
        "(?i)(افتح|موقع|متصفح|اضغط|اكبس|انقر|اكتب|عبئ|املأ|سجل|تسجيل|أرسل|ارسل|احذف|ادفع|شراء|chatgpt|gemini|http|www\\.)"
    ).containsMatchIn(goal)
}
