package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * يحول «الأعلى بكل شيء» إلى تحسين متعدد الأهداف محكوم، لا إلى مجموع نقاط يسمح
 * لبعد منخفض (مثل السرعة أو الجمال) بشراء تراجع في الحقيقة أو الشرع أو السلامة.
 */
object HakimExcellenceOptimizer {
    const val VERSION = "LEXICOGRAPHIC-PARETO-2026-09-14-v1"
    private const val HARD_GATE = 70
    private const val MATERIAL_GAIN = 5
    private const val MAX_PROTECTED_REGRESSION = 3

    enum class Verdict { ACCEPT_CANDIDATE, KEEP_BASELINE, RESEARCH_FIRST, REJECT_CANDIDATE }

    data class Scorecard(
        val truth: Int,
        val normativeIntegrity: Int,
        val safety: Int,
        val rights: Int,
        val authority: Int,
        val privacy: Int,
        val correctness: Int,
        val evidence: Int,
        val intentFit: Int,
        val completeness: Int,
        val reliability: Int,
        val recoverability: Int,
        val reversibility: Int,
        val burdenReduction: Int,
        val costFit: Int,
        val speed: Int,
        val usability: Int,
        val presentation: Int
    ) {
        fun normalized() = Scorecard(
            truth.coerceIn(0, 100), normativeIntegrity.coerceIn(0, 100), safety.coerceIn(0, 100),
            rights.coerceIn(0, 100), authority.coerceIn(0, 100), privacy.coerceIn(0, 100),
            correctness.coerceIn(0, 100), evidence.coerceIn(0, 100), intentFit.coerceIn(0, 100),
            completeness.coerceIn(0, 100), reliability.coerceIn(0, 100), recoverability.coerceIn(0, 100),
            reversibility.coerceIn(0, 100), burdenReduction.coerceIn(0, 100), costFit.coerceIn(0, 100),
            speed.coerceIn(0, 100), usability.coerceIn(0, 100), presentation.coerceIn(0, 100)
        )
    }

    data class Comparison(val verdict: Verdict, val reason: String, val decisiveTier: String) {
        fun toJson(): JSONObject = JSONObject()
            .put("verdict", verdict.name)
            .put("reason", reason)
            .put("decisive_tier", decisiveTier)
    }

    /**
     * ترتيب معجمي محكوم + منع انحدار: لا تُقارن طبقة أدنى قبل سلامة الأعلى.
     * إذا لم يوجد مكسب مادي مثبت فالحكم KEEP_BASELINE/NO_OP.
     */
    fun compare(baselineRaw: Scorecard, candidateRaw: Scorecard): Comparison {
        val b = baselineRaw.normalized()
        val c = candidateRaw.normalized()

        val gates = linkedMapOf(
            "الحقيقة" to c.truth,
            "السلامة المعيارية/الشرعية" to c.normativeIntegrity,
            "السلامة" to c.safety,
            "الحقوق" to c.rights,
            "السلطة" to c.authority,
            "الخصوصية" to c.privacy
        )
        val failed = gates.entries.firstOrNull { it.value < HARD_GATE }
        if (failed != null) {
            return Comparison(Verdict.REJECT_CANDIDATE, "فشل بوابة حاكمة: ${failed.key}=${failed.value}", "البوابات الحاكمة")
        }
        if (c.evidence < 55 || c.correctness < 55) {
            return Comparison(Verdict.RESEARCH_FIRST, "الدليل/الصحة غير كافيين لاعتماد تحسين جديد", "الصحة والدليل")
        }

        val tiers = listOf(
            "الصحة والدليل" to pairAvg(c.correctness, c.evidence) - pairAvg(b.correctness, b.evidence),
            "مطابقة المقصد والاكتمال" to pairAvg(c.intentFit, c.completeness) - pairAvg(b.intentFit, b.completeness),
            "الموثوقية والتعافي والتراجع" to tripleAvg(c.reliability, c.recoverability, c.reversibility) - tripleAvg(b.reliability, b.recoverability, b.reversibility),
            "خفض العبء والكلفة والوقت" to tripleAvg(c.burdenReduction, c.costFit, c.speed) - tripleAvg(b.burdenReduction, b.costFit, b.speed),
            "قابلية الاستخدام والعرض" to pairAvg(c.usability, c.presentation) - pairAvg(b.usability, b.presentation)
        )

        for ((name, delta) in tiers) {
            if (delta <= -MAX_PROTECTED_REGRESSION) {
                return Comparison(Verdict.REJECT_CANDIDATE, "انحدار مادي في طبقة محمية: $name ($delta)", name)
            }
            if (delta >= MATERIAL_GAIN) {
                return Comparison(Verdict.ACCEPT_CANDIDATE, "مكسب مادي مثبت في أعلى طبقة متمايزة بلا انحدار أعلى", name)
            }
        }
        return Comparison(Verdict.KEEP_BASELINE, "لا يوجد مكسب مادي صافٍ يبرر تغيير خط الأساس", "NO_OP")
    }

    fun promptContext(): String = buildString {
        appendLine("[محسن التفوق الشامل — الأعلى المثبت للمهمة]")
        appendLine("لا تختزل الجودة في مجموع نقاط واحد. استخدم ترتيبًا معجميًا محكومًا: البوابات الحاكمة أولًا، ثم الصحة/الدليل، ثم مطابقة المقصد/الاكتمال، ثم الموثوقية/التعافي/قابلية التراجع، ثم خفض العبء/الكلفة/الوقت، ثم قابلية الاستخدام/الجمال.")
        appendLine("ولّد عند الحاجة بدائل قليلة متمايزة، استبعد كل بديل يفشل بوابة أعلى، ثم اختر من جبهة الأفضل غير المهيمن عليه. لا تسمح بتحسن طبقة أدنى مقابل انحدار مادي في طبقة أعلى.")
        appendLine("الحقيقة والميزان القرآني/الشرعي والسلامة والحقوق والسلطة والخصوصية بوابات لا تُشترى بالمنفعة أو السرعة أو الجمال.")
        appendLine("أي تحسين للقلب يحتاج دليلًا واختبارًا وانحدارًا؛ وإذا لم يظهر مكسب مادي صافٍ فاحفظ LAST_VERIFIED_BASELINE ونفذ NO_OP.")
        appendLine("لا تعظّم الحجم أو عدد الأدوات أو عدد الوكلاء لذاته؛ عظّم القيمة الصافية المثبتة للنتيجة الفعلية.")
    }.take(3400)

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("lexicographic_priority", true)
        .put("pareto_filter", true)
        .put("hard_gates", JSONArray(listOf("الحقيقة", "السلامة المعيارية/الشرعية", "السلامة", "الحقوق", "السلطة", "الخصوصية")))
        .put("ordered_objectives", JSONArray(listOf(
            "الصحة والدليل",
            "مطابقة المقصد والاكتمال",
            "الموثوقية والتعافي وقابلية التراجع",
            "خفض العبء والكلفة والوقت",
            "قابلية الاستخدام والعرض"
        )))
        .put("material_gain_threshold", MATERIAL_GAIN)
        .put("max_protected_regression", MAX_PROTECTED_REGRESSION)
        .put("no_material_gain_means_no_op", true)

    private fun pairAvg(a: Int, b: Int): Int = ((a + b) / 2.0).roundToInt()
    private fun tripleAvg(a: Int, b: Int, c: Int): Int = ((a + b + c) / 3.0).roundToInt()
}
