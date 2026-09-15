package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * طبقة الحكمة المهنية العليا.
 * القرآن أصل الميزان القيمي والشرعي، والسنة الصحيحة بيان وهدي؛ أما الوسائل الدنيوية
 * فتبنى على الدليل والعلم والخبرة والتحقق. لا سببية تقنية غيبية ولا ادعاء عصمة.
 */
object HakimEliteWisdomEngine {
    const val VERSION = "QURANIC-WISDOM-PRO-2026-09-15-v1"

    enum class Gate { PROCEED, VERIFY_FIRST, CONSULT, APPROVAL, BLOCK }

    data class Scorecard(
        val truth: Int,
        val evidence: Int,
        val justice: Int,
        val amanah: Int,
        val rights: Int,
        val harmAvoidance: Int,
        val reversibility: Int,
        val consultation: Int,
        val humility: Int,
        val clarity: Int,
        val efficiency: Int
    ) {
        fun normalized() = Scorecard(
            truth.coerceIn(0, 100), evidence.coerceIn(0, 100), justice.coerceIn(0, 100),
            amanah.coerceIn(0, 100), rights.coerceIn(0, 100), harmAvoidance.coerceIn(0, 100),
            reversibility.coerceIn(0, 100), consultation.coerceIn(0, 100), humility.coerceIn(0, 100),
            clarity.coerceIn(0, 100), efficiency.coerceIn(0, 100)
        )
    }

    data class Assessment(
        val gate: Gate,
        val score: Int,
        val confidenceCeiling: Int,
        val reason: String,
        val scorecard: Scorecard,
        val quranicVerificationRequired: Boolean,
        val highImpact: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("gate", gate.name)
            .put("score", score)
            .put("confidence_ceiling", confidenceCeiling)
            .put("reason", reason)
            .put("quranic_verification_required", quranicVerificationRequired)
            .put("high_impact", highImpact)
            .put("scorecard", JSONObject()
                .put("truth", scorecard.truth)
                .put("evidence", scorecard.evidence)
                .put("justice", scorecard.justice)
                .put("amanah", scorecard.amanah)
                .put("rights", scorecard.rights)
                .put("harm_avoidance", scorecard.harmAvoidance)
                .put("reversibility", scorecard.reversibility)
                .put("consultation", scorecard.consultation)
                .put("humility", scorecard.humility)
                .put("clarity", scorecard.clarity)
                .put("efficiency", scorecard.efficiency))
    }

    fun assess(raw: String, highImpact: Boolean = false, sensitive: Boolean = false): Assessment {
        val text = raw.trim()
        val s = text.lowercase()
        val quranic = HakimQuranicFramework.assess(raw)
        val impact = highImpact || highImpactRegex.containsMatchIn(s)
        val sensitiveDetected = sensitive || sensitiveRegex.containsMatchIn(s)
        val rightsRisk = rightsRiskRegex.containsMatchIn(s)
        val highStakesKnowledge = highStakesKnowledgeRegex.containsMatchIn(s)
        val freshKnowledge = freshnessRegex.containsMatchIn(s)
        val clear = text.length >= 14 && !minimalCueRegex.matches(s)

        val scorecard = Scorecard(
            truth = if (deceptionRegex.containsMatchIn(s)) 25 else 94,
            evidence = when {
                quranic.exactQuranTextRequired -> 45
                highStakesKnowledge || freshKnowledge -> 52
                clear -> 78
                else -> 60
            },
            justice = if (rightsRisk) 35 else 94,
            amanah = if (deceptionRegex.containsMatchIn(s)) 35 else 94,
            rights = if (rightsRisk) 30 else 95,
            harmAvoidance = if (impact) 64 else 92,
            reversibility = if (impact) 38 else 90,
            consultation = if (impact) 72 else 86,
            humility = if (certaintyRegex.containsMatchIn(s) && (freshKnowledge || highStakesKnowledge)) 55 else 92,
            clarity = if (clear) 84 else 58,
            efficiency = 90
        ).normalized()

        val score = listOf(
            scorecard.truth, scorecard.evidence, scorecard.justice, scorecard.amanah,
            scorecard.rights, scorecard.harmAvoidance, scorecard.reversibility,
            scorecard.consultation, scorecard.humility, scorecard.clarity, scorecard.efficiency
        ).average().roundToInt().coerceIn(0, 100)

        val gate = when {
            sensitiveDetected -> Gate.BLOCK
            scorecard.truth < 50 || scorecard.justice < 50 || scorecard.amanah < 50 || scorecard.rights < 50 -> Gate.BLOCK
            quranic.exactQuranTextRequired || quranic.religiousDecision -> Gate.VERIFY_FIRST
            highStakesKnowledge || freshKnowledge -> Gate.VERIFY_FIRST
            impact -> Gate.APPROVAL
            !clear -> Gate.CONSULT
            else -> Gate.PROCEED
        }

        val confidenceCeiling = when (gate) {
            Gate.BLOCK -> 100
            Gate.VERIFY_FIRST -> 65
            Gate.CONSULT -> 68
            Gate.APPROVAL -> 82
            Gate.PROCEED -> 92
        }

        val reason = when (gate) {
            Gate.BLOCK -> "فشلت بوابة من الحقيقة/الأمانة/العدل/الحقوق/الخصوصية؛ لا يبررها نفع أو سرعة"
            Gate.VERIFY_FIRST -> "يلزم تثبت من النص أو الواقع أو المصدر قبل الجزم أو التنفيذ"
            Gate.CONSULT -> "المقصد غير مكتمل بما يكفي؛ اجمع قرينة إضافية أو استعمل السياق قبل افتراض مراد المستخدم"
            Gate.APPROVAL -> "الأثر مرتفع أو صعب التراجع؛ نفذ ما قبل الحد فقط ثم اطلب الموافقة عند آخر خطوة جوهرية"
            Gate.PROCEED -> "المقصد واضح والبوابات الحاكمة سليمة؛ اختر أقل وسيلة تحقق الغاية ثم تحقق من الأثر"
        }
        return Assessment(gate, score, confidenceCeiling, reason, scorecard, quranic.exactQuranTextRequired, impact)
    }

    /** سجل قرار موجز قابل للمراجعة؛ لا يطلب ولا يخزن سلسلة التفكير الخاصة. */
    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            append(HakimQuranicFramework.promptContext(raw))
            appendLine("[محرك الحكمة المهنية العليا — $VERSION]")
            appendLine("الغاية: حق نافع بأمانة وعدل وإحسان، مع حفظ الحقوق ومنع الضرر والفساد، ثم اختيار السبب الدنيوي الأقوى بالدليل.")
            appendLine("مراجع الميزان: الأمانة والعدل (النساء 4:58)، العدل حتى مع المخالفة (المائدة 5:8)، التثبت من الخبر (الحجرات 49:6)، عدم اتباع ما لا علم به (الإسراء 17:36)، الشورى (الشورى 42:38)، ومنع الفساد (الأعراف 7:56). لا تنقل نص آية حرفيًا هنا إلا بعد تحقق المصدر.")
            appendLine("قبل القرار افصل: حقائق مثبتة / افتراضات / مجهولات / قيود / حقوق متأثرة. لا تملأ فراغًا مهمًا بالتخمين.")
            appendLine("ولّد بدائل قليلة متمايزة عندما يوجد اختيار حقيقي، وانقدها من جهة الحقيقة والدليل والعدل والأمانة والخصوصية والضرر وقابلية التراجع والكلفة والعبء.")
            appendLine("اختر أقل تدخل يحقق المقصد، ولا تجعل القوة أو الذكاء أو السرعة أو الجمال تعوّض نقص الحق أو الأمانة أو السلامة.")
            appendLine("إذا كانت المسألة دينية/شرعية أو تحتاج نصًا قرآنيًا دقيقًا: تحقق من النص والسياق والمصدر ولا تنسب للوحي ما لم يثبت. وإذا كانت دنيوية فاستعمل العلم والهندسة والخبرة؛ القرآن ليس خوارزمية تقنية ولا ضمانًا ماديًا.")
            appendLine("لا تعرض سلسلة تفكير خاصة. أعط فقط سجل قرار موجزًا: النتيجة، أهم الحقائق، أهم الافتراضات/المجهولات، البدائل التي فُحصت، سبب الاختيار، مستوى الثقة، معيار النجاح، وخطة التحقق/التراجع.")
            appendLine("بوابة الحكمة الحالية: ${a.gate} • سقف الثقة قبل مزيد من الدليل: ${a.confidenceCeiling}/100 • ${a.reason}")
        }.take(14000)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("quranic_value_governance", true)
        .put("authentic_sunnah_guidance", true)
        .put("worldly_means_by_evidence", true)
        .put("no_mystical_technical_causation", true)
        .put("truth_gate", true)
        .put("justice_gate", true)
        .put("amanah_gate", true)
        .put("rights_gate", true)
        .put("evidence_gate", true)
        .put("uncertainty_calibration", true)
        .put("minimal_intervention", true)
        .put("reversibility_first", true)
        .put("no_private_chain_of_thought_required", true)
        .put("principle_refs", JSONArray(listOf("4:58", "5:8", "49:6", "17:36", "42:38", "7:56")))

    private val sensitiveRegex = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|card.?number|api.?key|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة|مفتاح.?سري)")
    private val highImpactRegex = Regex("(?i)(pay|purchase|buy|delete|transfer|publish|submit|sign contract|ادفع|شراء|احذف|تحويل|نشر|إرسال نهائي|توقيع|عقد|مسح البيانات|الغاء الحساب|إلغاء الحساب)")
    private val rightsRiskRegex = Regex("(?i)(انتحل|تجسس|راقب شخص|اكشف خصوص|ابتز|اخدع|خداع|تحايل على موافق|impersonat|spy on|bypass consent|blackmail)")
    private val deceptionRegex = Regex("(?i)(اكذب|كذب عليه|اخدعه|زوّر|زور|fake identity|deceive|forge)")
    private val highStakesKnowledgeRegex = Regex("(?i)(تشخيص|دواء|جرعة|قانون|محكمة|استثمار|ضريبة|فتوى|حلال|حرام|medical|diagnosis|dose|legal|tax|investment)")
    private val freshnessRegex = Regex("(?i)(اليوم|الآن|الان|أحدث|احدث|حالي|سعر|خبر|طقس|انتخابات|current|today|latest|price|weather|news)")
    private val certaintyRegex = Regex("(?i)(أكيد|قطعًا|قطعا|بالتأكيد|100%|certain|definitely|guaranteed)")
    private val minimalCueRegex = Regex("(?i)^(كمل|كمّل|اكمل|أكمل|تابع|نفذ|نفّذ|هاي|هذا|هذه|هون|هنا|تمام|يلا|هيا|دبرها|دبّرها|قم بكل شيء|قم بكل شيئ)$")
}
