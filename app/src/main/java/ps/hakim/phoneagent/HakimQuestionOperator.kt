package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * قاعدة «؟ / و؟»: تحوّل الاستفهام إلى بوابة جودة قابلة للفحص لا إلى دوران فكري.
 * لا تطلب سلسلة التفكير الداخلية؛ تحفظ فقط أسئلة القرار المادية ونتيجتها المختصرة.
 */
object HakimQuestionOperator {
    const val VERSION = "QUESTION-OPERATOR-2026-09-15-v1"

    enum class QuestionKind {
        WHAT_IS_UNKNOWN, WHAT_IS_EVIDENCE, WHAT_CAN_FAIL, WHAT_IS_IMPACT,
        WHAT_IS_ALTERNATIVE, WHY_THIS, WHAT_NEXT, WHAT_STOPS_US
    }

    data class Gate(
        val proceed: Boolean,
        val unresolvedMaterialQuestions: Int,
        val reason: String,
        val requiredKinds: List<QuestionKind>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("proceed", proceed)
            .put("unresolved_material_questions", unresolvedMaterialQuestions)
            .put("reason", reason)
            .put("required_kinds", JSONArray(requiredKinds.map { it.name }))
    }

    fun gate(plan: HakimReasoningProtocol.Plan): Gate {
        val unresolved = (plan.unknowns.size + plan.evidenceNeeded.size).coerceAtMost(12)
        val required = mutableListOf<QuestionKind>()
        if (plan.unknowns.isNotEmpty()) required += QuestionKind.WHAT_IS_UNKNOWN
        if (plan.evidenceNeeded.isNotEmpty()) required += QuestionKind.WHAT_IS_EVIDENCE
        if (plan.actions.isNotEmpty()) {
            required += QuestionKind.WHAT_CAN_FAIL
            required += QuestionKind.WHAT_IS_IMPACT
            required += QuestionKind.WHAT_IS_ALTERNATIVE
            required += QuestionKind.WHY_THIS
            required += QuestionKind.WHAT_NEXT
        }
        val blockedByUnknown = plan.phase == "execute" && unresolved > 0
        val proceed = !blockedByUnknown
        val reason = when {
            blockedByUnknown -> "قاعدة ؟ أوقفت التنفيذ: ما زالت هناك مجهولات/أدلة مطلوبة مؤثرة؛ أغلقها أولًا"
            plan.phase == "research" -> "قاعدة ؟ تعمل لجمع الدليل وسد المجهولات دون تغيير الحالة"
            plan.phase == "verify" -> "قاعدة و؟ تبحث عن أي فجوة متبقية قبل إعلان الاكتمال"
            else -> "قاعدة ؟/و؟ اجتازت: لا يوجد مجهول مادي مسجل يمنع هذه الجولة"
        }
        return Gate(proceed, unresolved, reason, required.distinct())
    }

    fun promptContext(): String = buildString {
        appendLine("[قاعدة ؟ / و؟ — $VERSION]")
        appendLine("قبل الاعتماد اسأل سؤال القرار المادي: ما المجهول المؤثر؟ ما الدليل؟ ما الذي قد يفشل؟ ما الأثر والحقوق؟ ما البديل؟ لماذا هذا المسار؟")
        appendLine("بعد كل جواب/خطوة طبّق «و؟»: ما السؤال التالي الأعلى قيمة الذي قد يغيّر القرار أو يسد فجوة حقيقية؟")
        appendLine("لا تعرض سلسلة تفكير داخلية؛ سجّل فقط المجهول/الدليل/البديل/معيار النجاح اللازم للمراجعة.")
        appendLine("لا تبدأ execute ومجهول مادي أو evidence_needed ما زال مفتوحًا. حوّله إلى research ثم أعد التخطيط.")
        appendLine("توقف عن «و؟» عندما لا يبقى سؤال يحقق مكسبًا ماديًا أو يقلل خطرًا/عدم يقين جوهريًا؛ لا دوران بلا قيمة.")
    }.take(2600)

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("question_gate", true)
        .put("recursive_what_next", true)
        .put("execution_requires_material_unknowns_closed", true)
        .put("no_private_chain_of_thought", true)
        .put("no_infinite_questioning", true)
        .put("question_kinds", JSONArray(QuestionKind.values().map { it.name }))
}
