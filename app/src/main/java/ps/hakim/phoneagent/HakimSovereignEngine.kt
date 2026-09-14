package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * طبقة تجميع سيادية: مقصد + إطار قرآني + مصفوفة قرار + نزاهة شرعية + سجل مهمة + ميزانية فشل.
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
        val mission = HakimMissionLedger.beginOrResume(context, goal)
        val decision = HakimDecisionMatrix.evaluate(goal, highImpact, sensitive)
        val quranic = HakimQuranicFramework.assess(goal)
        val religious = HakimReligiousIntegrity.assess(goal)
        val failures = mission.failures
        val blockedByFailures = failures >= HARD_FAILURE_LIMIT
        val forceResearch = failures >= MAX_CONSECUTIVE_FAILURES ||
            decision.mode == HakimDecisionMatrix.Mode.RESEARCH_FIRST ||
            quranic.exactQuranTextRequired ||
            religious.exactSourceRequired
        val blocked = blockedByFailures || decision.mode == HakimDecisionMatrix.Mode.BLOCK
        val approval = !blocked && decision.mode == HakimDecisionMatrix.Mode.APPROVAL_GATE
        val route = when {
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
        val a = assess(context, goal, highImpact, sensitive)
        return buildString {
            appendLine("[المحرك السيادي لحكيم]")
            appendLine("مهمة واحدة نشطة فقط WIP=1. المرحلة=${a.mission.phase}، المسار=${a.route}، ميزانية الفشل المتبقية=${a.failureBudgetRemaining}.")
            append(HakimQuranicFramework.promptContext(goal))
            append(HakimDecisionMatrix.promptContext(goal, highImpact, sensitive))
            append(HakimReligiousIntegrity.promptContext(goal))
            appendLine("سلسلة الاستقلالية: اعرض الغاية والأثر على الميزان القرآني→افهم→ثبّت العقد→قدّر الواقع→اختر المسار→نفّذ أقل خطوة كافية→تحقق من الأثر→أصلح السبب→أعد التقدير→أغلق أو توقف عند بوابة لازمة.")
            appendLine("عند فشل وسيلة لا تعتبر الغاية فاشلة؛ بدّل إلى بديل مشروع ومصرح. بعد ثلاثة إخفاقات متتابعة أعد البحث/التخطيط، وبعد خمسة أوقف التكرار حتى يتغير الدليل أو الحالة.")
            appendLine("لا تُنشئ نشاطًا لمجرد النشاط؛ إذا لم يبق مكسب مادي آمن ومثبت فأغلق المهمة. لا تعيد خطوة ثبت نجاحها.")
            appendLine("الاستمرارية تعني حفظ الحالة والتعافي وإعادة الاتصال واستئناف المهمة، ولا تعني التحكم الخفي أو تجاوز موافقة مطلوبة.")
        }.take(10400)
    }

    fun recordExecution(context: Context, evidence: String) {
        HakimMissionLedger.progress(context, HakimMissionLedger.Phase.EXECUTE, evidence, attempted = true)
    }

    fun recordVerification(context: Context, success: Boolean, evidence: String) {
        if (success) {
            HakimMissionLedger.progress(context, HakimMissionLedger.Phase.VERIFY, evidence)
            HakimLearning.recordResult(context, "sovereign_mission", true)
        } else {
            HakimMissionLedger.failure(context, evidence)
            HakimLearning.recordResult(context, "sovereign_mission", false)
        }
    }

    fun complete(context: Context, evidence: String) {
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
        .put("religious_integrity", true)
        .put("independence_policy", "أقصى استقلالية آمنة ومشروعة ومصرح بها: القرآن ميزان الغاية والقيم، والدليل العلمي/التجريبي يحكم الوسائل الدنيوية")
}
