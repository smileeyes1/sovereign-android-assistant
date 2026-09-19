package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * ن★ التكيفية: عمق العمل ليس رقمًا ثابتًا. يستمر فقط ما دام الدور التالي
 * يحقق مكسبًا ماديًا آمنًا ومثبتًا، ويتوقف عند انعدام المكسب أو ظهور بوابة أعلى.
 */
object HakimAdaptiveNStarLoop {
    const val VERSION = "NSTAR-ADAPTIVE-2026-09-15-v1"
    private const val MIN_MATERIAL_GAIN = 5
    private const val MAX_STAGNANT_ROUNDS = 2
    private const val MAX_ROUNDS = 12

    enum class Stage { UNDERSTAND, RESEARCH, MODEL, PLAN, CRITIQUE, EXECUTE, VERIFY, REPAIR, LEARN, COMPLETE }

    data class Round(
        val stage: Stage,
        val gain: Int,
        val confidence: Int,
        val risk: Int,
        val blocked: Boolean = false,
        val reason: String = ""
    )

    data class Decision(
        val continueLoop: Boolean,
        val nextStage: Stage,
        val reason: String,
        val depth: Int
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("continue", continueLoop)
            .put("next_stage", nextStage.name)
            .put("reason", reason)
            .put("depth", depth)
    }

    fun decide(history: List<Round>): Decision {
        if (history.isEmpty()) return Decision(true, Stage.UNDERSTAND, "ابدأ بفهم المقصد والعقد", 0)
        val last = history.last()
        if (last.blocked) return Decision(false, last.stage, "بوابة حاكمة أوقفت الاستمرار: ${last.reason}", history.size)
        if (history.size >= MAX_ROUNDS) return Decision(false, Stage.VERIFY, "بلغت الحلقة حد العمق؛ تحقق وأغلق أو أعد التخطيط بدليل جديد", history.size)

        val stagnant = history.takeLast(MAX_STAGNANT_ROUNDS).count { it.gain < MIN_MATERIAL_GAIN }
        if (stagnant >= MAX_STAGNANT_ROUNDS) {
            return Decision(false, Stage.VERIFY, "لا يوجد مكسب مادي كافٍ في جولتين؛ KEEP_BASELINE/NO_OP ثم تحقق", history.size)
        }
        if (last.risk >= 80 && last.stage == Stage.EXECUTE) {
            return Decision(false, Stage.VERIFY, "الخطر مرتفع؛ لا تعمق التنفيذ، انتقل للتحقق/الموافقة", history.size)
        }
        if (last.confidence < 55 && last.stage in setOf(Stage.PLAN, Stage.EXECUTE)) {
            return Decision(true, Stage.RESEARCH, "الثقة أقل من حد الاعتماد؛ زد الدليل لا الجرأة", history.size)
        }

        val next = when (last.stage) {
            Stage.UNDERSTAND -> Stage.RESEARCH
            Stage.RESEARCH -> Stage.MODEL
            Stage.MODEL -> Stage.PLAN
            Stage.PLAN -> Stage.CRITIQUE
            Stage.CRITIQUE -> Stage.EXECUTE
            Stage.EXECUTE -> Stage.VERIFY
            Stage.VERIFY -> if (last.gain >= MIN_MATERIAL_GAIN && last.confidence >= 75) Stage.LEARN else Stage.REPAIR
            Stage.REPAIR -> Stage.VERIFY
            Stage.LEARN -> Stage.COMPLETE
            Stage.COMPLETE -> Stage.COMPLETE
        }
        return Decision(next != Stage.COMPLETE, next, "استمر لأن الدور التالي ما زال يملك مكسبًا ماديًا متوقعًا داخل البوابات", history.size)
    }

    fun promptContext(): String = buildString {
        appendLine("[ن★ التكيفية — $VERSION]")
        appendLine("العمق غير ثابت: افهم→ابحث→نمذج→خطط→انتقد→نفذ→تحقق→أصلح→تعلم→أكمل.")
        appendLine("زد عمق التحليل/البحث/التحقق فقط عندما يحقق الدور التالي مكسبًا ماديًا مثبتًا أو يسد مجهولًا جوهريًا.")
        appendLine("لا تكرر عند انعدام المكسب، ولا تعوض ضعف الدليل بزيادة الثقة أو الصلاحيات أو عدد الوكلاء.")
        appendLine("إذا فشلت وسيلة فابحث عن بديل مشروع ومصرح، وأصلح السبب الجذري. إذا بقيت الغاية ممكنة فلا تخلط فشل الوسيلة بفشل الغاية.")
        appendLine("الحدود الحقيقية للواقع والسلطة والسلامة والحقوق توقف الحلقة؛ لا يوجد ادعاء بقدرات غير موجودة.")
    }.take(2600)

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("adaptive_depth", true)
        .put("material_gain_threshold", MIN_MATERIAL_GAIN)
        .put("max_stagnant_rounds", MAX_STAGNANT_ROUNDS)
        .put("max_rounds", MAX_ROUNDS)
        .put("stages", JSONArray(Stage.values().map { it.name }))
        .put("no_infinite_loop", true)
        .put("no_privilege_escalation_as_progress", true)
        .put("failure_of_tool_not_failure_of_goal", true)
}
