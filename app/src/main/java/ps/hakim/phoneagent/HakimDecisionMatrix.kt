package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * مصفوفة قرار حتمية متعددة الأبعاد. الغاية: تحويل «أفضل/أذكى» إلى قرار قابل للفحص.
 * لا توسع السلطة؛ ارتفاع القيمة لا يلغي حاجز الخطر أو الخصوصية أو الموافقة.
 */
object HakimDecisionMatrix {
    enum class Mode { AUTO, AUTO_VERIFY, RESEARCH_FIRST, APPROVAL_GATE, BLOCK }

    data class Signals(
        val benefit: Int,
        val evidence: Int,
        val reversibility: Int,
        val authority: Int,
        val privacy: Int,
        val safety: Int,
        val clarity: Int,
        val costFit: Int,
        val burdenReduction: Int,
        val freshness: Int
    ) {
        fun normalized(): Signals = Signals(
            benefit.coerceIn(0, 100), evidence.coerceIn(0, 100), reversibility.coerceIn(0, 100),
            authority.coerceIn(0, 100), privacy.coerceIn(0, 100), safety.coerceIn(0, 100),
            clarity.coerceIn(0, 100), costFit.coerceIn(0, 100), burdenReduction.coerceIn(0, 100),
            freshness.coerceIn(0, 100)
        )
    }

    data class Decision(
        val mode: Mode,
        val score: Int,
        val confidence: Int,
        val reason: String,
        val signals: Signals
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("mode", mode.name)
            .put("score", score)
            .put("confidence", confidence)
            .put("reason", reason)
            .put("signals", JSONObject()
                .put("benefit", signals.benefit)
                .put("evidence", signals.evidence)
                .put("reversibility", signals.reversibility)
                .put("authority", signals.authority)
                .put("privacy", signals.privacy)
                .put("safety", signals.safety)
                .put("clarity", signals.clarity)
                .put("cost_fit", signals.costFit)
                .put("burden_reduction", signals.burdenReduction)
                .put("freshness", signals.freshness))
    }

    fun evaluate(raw: String, highImpact: Boolean = false, sensitive: Boolean = false): Decision {
        val text = raw.trim()
        val s = text.lowercase()
        if (sensitive || sensitiveRegex.containsMatchIn(s)) {
            val sig = inferSignals(s, highImpact = true, sensitive = true)
            return Decision(Mode.BLOCK, 0, 100, "سر أو اعتماد حساس لا يمر عبر الاستدلال/التنفيذ النصي", sig)
        }
        val impact = highImpact || highImpactRegex.containsMatchIn(s)
        val sig = inferSignals(s, impact, false).normalized()
        val score = weightedScore(sig)
        val confidence = ((sig.evidence * 0.30) + (sig.clarity * 0.30) + (sig.freshness * 0.15) +
            (sig.authority * 0.15) + (sig.safety * 0.10)).roundToInt().coerceIn(0, 100)

        val mode = when {
            sig.authority < 45 || sig.safety < 35 || sig.privacy < 35 -> Mode.BLOCK
            impact || sig.reversibility < 35 -> Mode.APPROVAL_GATE
            sig.evidence < 45 || sig.clarity < 45 || sig.freshness < 35 -> Mode.RESEARCH_FIRST
            score >= 78 && confidence >= 72 && sig.reversibility >= 70 -> Mode.AUTO
            else -> Mode.AUTO_VERIFY
        }
        val reason = when (mode) {
            Mode.AUTO -> "قيمة مرتفعة مع دليل ووضوح وسلامة وقابلية تراجع كافية"
            Mode.AUTO_VERIFY -> "يمكن التنفيذ بخطوة قابلة للتراجع مع تحقق مباشر بعد كل فعل"
            Mode.RESEARCH_FIRST -> "الدليل/الوضوح/الحداثة غير كافية للتنفيذ المباشر؛ يلزم تحقق أو بحث أولًا"
            Mode.APPROVAL_GATE -> "الأثر أو عدم القابلية للتراجع يفرضان بوابة موافقة عند آخر خطوة جوهرية"
            Mode.BLOCK -> "السلطة أو السلامة أو الخصوصية دون الحد الأدنى المسموح"
        }
        return Decision(mode, score, confidence, reason, sig)
    }

    fun promptContext(raw: String, highImpact: Boolean = false, sensitive: Boolean = false): String {
        val d = evaluate(raw, highImpact, sensitive)
        return buildString {
            appendLine("[مصفوفة القرار السيادي]")
            appendLine("الوضع: ${d.mode} • القيمة: ${d.score}/100 • ثقة القرار: ${d.confidence}/100")
            appendLine("السبب: ${d.reason}")
            appendLine("الأبعاد: منفعة=${d.signals.benefit}، دليل=${d.signals.evidence}، تراجع=${d.signals.reversibility}، سلطة=${d.signals.authority}، خصوصية=${d.signals.privacy}، سلامة=${d.signals.safety}، وضوح=${d.signals.clarity}، ملاءمة كلفة=${d.signals.costFit}، خفض عبء=${d.signals.burdenReduction}، حداثة=${d.signals.freshness}.")
            appendLine("لا يجوز أن يرفع مجموع النقاط فعلًا محظورًا أو يتجاوز بوابة السلطة/الخصوصية/الأثر العالي؛ القيود الحاكمة بوابات لا أوزان تعويضية.")
        }.take(2600)
    }

    fun dimensions(): JSONArray = JSONArray(listOf(
        "المنفعة", "قوة الدليل", "قابلية التراجع", "حدود السلطة", "الخصوصية", "السلامة",
        "وضوح المقصد", "ملاءمة الكلفة", "خفض عبء المستخدم", "حداثة الواقع/المعلومة"
    ))

    private fun inferSignals(s: String, highImpact: Boolean, sensitive: Boolean): Signals {
        val explicit = s.length >= 12 && !minimalCueRegex.matches(s)
        val asksResearch = researchRegex.containsMatchIn(s)
        val timeSensitive = freshnessRegex.containsMatchIn(s)
        val destructive = highImpact
        return Signals(
            benefit = if (s.isBlank()) 45 else 82,
            evidence = when { asksResearch -> 42; explicit -> 72; else -> 55 },
            reversibility = if (destructive) 25 else 88,
            authority = if (sensitive) 20 else if (destructive) 65 else 90,
            privacy = if (sensitive) 10 else 92,
            safety = if (destructive) 55 else 90,
            clarity = if (explicit) 82 else 58,
            costFit = if (paidRegex.containsMatchIn(s)) 45 else 90,
            burdenReduction = 92,
            freshness = if (timeSensitive) 45 else 78
        )
    }

    private fun weightedScore(s: Signals): Int = (
        s.benefit * 0.17 + s.evidence * 0.14 + s.reversibility * 0.13 + s.authority * 0.10 +
            s.privacy * 0.10 + s.safety * 0.13 + s.clarity * 0.08 + s.costFit * 0.05 +
            s.burdenReduction * 0.06 + s.freshness * 0.04
        ).roundToInt().coerceIn(0, 100)

    private val sensitiveRegex = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|card.?number|api.?key|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة|مفتاح.?سري)")
    private val highImpactRegex = Regex("(?i)(pay|purchase|buy|delete|send|submit|publish|transfer|sign contract|ادفع|شراء|احذف|إرسال|ارسل|أرسل|نشر|تحويل|توقيع|عقد)")
    private val researchRegex = Regex("(?i)(ابحث|تحقق|قارن|مصدر|أحدث|احدث|اليوم|الآن|الان|research|verify|latest)")
    private val freshnessRegex = Regex("(?i)(اليوم|الآن|الان|أحدث|احدث|حالي|current|today|latest|سعر|طقس|خبر|انتخابات|قانون)")
    private val paidRegex = Regex("(?i)(مدفوع|اشتراك|شراء|pay|paid|subscription)")
    private val minimalCueRegex = Regex("(?i)^(كمل|كمّل|اكمل|أكمل|تابع|نفذ|نفّذ|هاي|هذا|هذه|هون|هنا|تمام|يلا|هيا|دبرها|دبّرها)$")
}
