package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * طبقة تجميع سيادية: مقصد + قرآن كله + إنسان أولًا + مصفوفة قرار + تفوق شامل + قيادة ذاتية + نزاهة شرعية + سجل مهمة + نسيج تكامل.
 * الاستقلالية لا تتجاوز حدود السلطة أو الأمان أو الخصوصية؛ عند الفشل تعيد التخطيط ولا توسع الصلاحيات.
 */
object HakimSovereignEngine {
    data class Assessment(
        val mission: HakimMissionLedger.Mission,
        val decision: HakimDecisionMatrix.Decision,
        val quranic: HakimQuranicFramework.Assessment,
        val religious: HakimReligiousIntegrity.Assessment,
        val failureBudgetRemaining: Int,
        val route: String,
        val shouldResearchFirst: Boolean,
        val needsApproval: Boolean,
        val blocked: Boolean
    )

    private const val MAX_CONSECUTIVE_FAILURES = 3
    private const val HARD_FAILURE_LIMIT = 5

    fun assess(
        context: Context,
        goal: String,
        highImpact: Boolean = false,
        sensitive: Boolean = false
    ): Assessment {
        HakimQuranicInvariantKernel.requireInherited("sovereign_assess")
        HakimIntegrationFabric.requireCore(context, "sovereign_assess")
        val mission = HakimMissionLedger.beginOrResume(context, goal)
        val decision = HakimDecisionMatrix.evaluate(goal, highImpact, sensitive)
        val quranic = HakimQuranicFramework.assess(goal)
        val religious = HakimReligiousIntegrity.assess(goal)
        val failures = mission.failures
        val blockedByFailures = failures >= HARD_FAILURE_LIMIT
        val cancelledOrBlocked = mission.phase == HakimMissionLedger.Phase.CANCELLED ||
            mission.phase == HakimMissionLedger.Phase.BLOCKED
        val forceResearch = failures >= MAX_CONSECUTIVE_FAILURES ||
            decision.mode == HakimDecisionMatrix.Mode.RESEARCH_FIRST ||
            quranic.exactQuranTextRequired ||
            religious.exactSourceRequired
        val blocked = cancelledOrBlocked || blockedByFailures || decision.mode == HakimDecisionMatrix.Mode.BLOCK
        val approval = !blocked && decision.mode == HakimDecisionMatrix.Mode.APPROVAL_GATE
        val route = when {
            mission.phase == HakimMissionLedger.Phase.CANCELLED -> "cancelled"
            blocked -> "blocked"
            forceResearch -> "research_then_replan"
            decision.mode == HakimDecisionMatrix.Mode.APPROVAL_GATE -> "prepare_then_approval"
            decision.mode == HakimDecisionMatrix.Mode.AUTO -> "local_first"
            else -> "local_verify_then_reason"
        }
        HakimMissionLedger.progress(context, HakimMissionLedger.Phase.PLAN, "المسار=$route؛ القرار=${decision.mode}")
        return Assessment(
            mission = HakimMissionLedger.active(context) ?: mission,
            decision = decision,
            quranic = quranic,
            religious = religious,
            failureBudgetRemaining = (HARD_FAILURE_LIMIT - failures).coerceAtLeast(0),
            route = route,
            shouldResearchFirst = forceResearch,
            needsApproval = approval,
            blocked = blocked
        )
    }

    fun promptContext(
        context: Context,
        goal: String,
        highImpact: Boolean = false,
        sensitive: Boolean = false
    ): String {
        HakimQuranicInvariantKernel.requireInherited("sovereign_prompt")
        HakimIntegrationFabric.requireCore(context, "sovereign_prompt")
        val a = assess(context, goal, highImpact, sensitive)
        return buildString {
            appendLine("[المحرك السيادي لحكيم]")
            appendLine("مهمة واحدة نشطة فقط WIP=1. المرحلة=${a.mission.phase}، المسار=${a.route}، ميزانية الفشل المتبقية=${a.failureBudgetRemaining}.")
            append(HakimQuranicFramework.promptContext(goal))
            append(HakimHumanFirstPolicy.promptContext())
            append(HakimIntegrationFabric.promptContext(context))
            append(HakimDecisionMatrix.promptContext(goal, highImpact, sensitive))
            append(HakimExcellenceOptimizer.promptContext())
            append(HakimSelfLeadershipController.promptContext(context, goal))
            append(HakimAuthorityEnvelope.promptContext())
            append(HakimReligiousIntegrity.promptContext(goal))
            appendLine("سلسلة الاستقلالية المتكاملة: اعرض الغاية والأثر على الميزان القرآني→احفظ كرامة الإنسان وحقوقه→افهم المقصد→ثبّت العقد→افحص التكامل والقدرات والسلطة→ولّد البدائل اللازمة→رشّحها بالبوابات والترتيب الأعلى→فوّض الوكلاء→نفّذ أقل خطوة كافية→تحقق من الأثر→أصلح السبب→استعد الوصل/تعافَ/أعد التخطيط→تعلم محكومًا→أغلق بالدليل.")
            appendLine("صمم للإنسان الحقيقي: لا تفترض خبرة تقنية، لا تستغل الطيبة أو الرحمة، لا تفسر السكوت أو الإشارة العامة كموافقة عالية الأثر، وخفف العبء المعرفي والإجرائي ما دام ذلك لا يسلب القرار الجوهري.")
            appendLine("عند فشل وسيلة أو وصلة لا تعتبر الغاية فاشلة؛ بدّل إلى بديل مشروع ومصرح أو استعد الوصلة. بعد ثلاثة إخفاقات متتابعة أعد البحث/التخطيط، وبعد خمسة أوقف التكرار حتى يتغير الدليل أو الحالة.")
            appendLine("لا تُنشئ نشاطًا لمجرد النشاط؛ إذا لم يبق مكسب مادي آمن ومثبت فأغلق المهمة. لا تعيد خطوة ثبت نجاحها، ولا تغيّر خط الأساس المثبت لتحسين شكلي.")
            appendLine("الاستمرارية والتكامل لا يعنيان التحكم الخفي أو تجاوز موافقة؛ وكلمة إلغاء/توقف من المستخدم توقف المهمة وتعلو على الاستئناف.")
        }.take(30000)
    }

    fun recordExecution(context: Context, evidence: String) {
        HakimQuranicInvariantKernel.requireInherited("sovereign_execute")
        HakimIntegrationFabric.requireCore(context, "sovereign_execute")
        HakimMissionLedger.progress(context, HakimMissionLedger.Phase.EXECUTE, evidence, attempted = true)
    }

    fun recordVerification(context: Context, success: Boolean, evidence: String) {
        HakimQuranicInvariantKernel.requireInherited("sovereign_verify")
        HakimIntegrationFabric.requireCore(context, "sovereign_verify")
        if (HakimMissionLedger.isCancelled(context)) return
        if (success) {
            HakimMissionLedger.progress(context, HakimMissionLedger.Phase.VERIFY, evidence)
            HakimLearning.recordResult(context, "sovereign_mission", true)
        } else {
            HakimMissionLedger.failure(context, evidence)
            HakimLearning.recordResult(context, "sovereign_mission", false)
        }
    }

    fun complete(context: Context, evidence: String) {
        HakimQuranicInvariantKernel.requireInherited("sovereign_complete")
        HakimIntegrationFabric.requireCore(context, "sovereign_complete")
        if (HakimMissionLedger.isCancelled(context)) return
        HakimMissionLedger.complete(context, evidence)
        HakimLearning.recordResult(context, "sovereign_mission", true)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("sovereign_engine", true)
        .put("wip_one", true)
        .put("closed_loop", true)
        .put("failure_replan_threshold", MAX_CONSECUTIVE_FAILURES)
        .put("hard_failure_limit", HARD_FAILURE_LIMIT)
        .put("mission", HakimMissionLedger.status(context))
        .put("decision_dimensions", HakimDecisionMatrix.dimensions())
        .put("quranic_framework", HakimQuranicFramework.status())
        .put("quranic_invariant_kernel", HakimQuranicInvariantKernel.status())
        .put("human_first", HakimHumanFirstPolicy.status())
        .put("integration_fabric", HakimIntegrationFabric.status(context))
        .put("excellence_optimizer", HakimExcellenceOptimizer.status())
        .put("self_leadership", HakimSelfLeadershipController.status(context))
        .put("authority_envelope", HakimAuthorityEnvelope.status())
        .put("capability_registry", HakimCapabilityRegistry.status(context))
        .put("religious_integrity", true)
        .put("independence_policy", "قيادة ذاتية سيادية كاملة ومتكاملة وإنسانية داخل غلاف السلطة: المستخدم يملك WHAT/WHY/الحدود، وحكيم يملك HOW والتفويض والوصل والتعافي والتحقق؛ لا توسع صلاحيات ولا نجاح بلا دليل ولا استغلال للطيبة أو الجهل التقني")
}
