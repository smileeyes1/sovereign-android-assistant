package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * الإطار القرآني الحاكم لحكيم.
 * يرث جذر الثقة القرآني؛ القرآن أصل الهداية والميزان القيمي والشرعي، والسنة الصحيحة بيان وهدي،
 * بينما تبقى العلوم والخبرة والتجربة أدوات لمعرفة الوسائل الدنيوية وتنفيذها داخل هذا الميزان.
 * لا تُنسب تفاصيل تقنية أو طبية أو تجريبية إلى القرآن بلا دليل، ولا يُحوّل القرآن إلى آلية تقنية أو تعويذة.
 */
object HakimQuranicFramework {
    data class Assessment(
        val quranicNormativeDefault: Boolean,
        val religiousDecision: Boolean,
        val exactQuranTextRequired: Boolean,
        val worldlyMeansTask: Boolean,
        val reason: String
    )

    fun assess(raw: String): Assessment {
        HakimQuranicInvariantKernel.requireInherited("quranic_framework")
        val s = raw.trim().lowercase()
        val religious = religiousDecisionRegex.containsMatchIn(s)
        val exact = exactQuranRegex.containsMatchIn(s)
        val worldly = worldlyMeansRegex.containsMatchIn(s) && !religious
        return Assessment(
            quranicNormativeDefault = true,
            religiousDecision = religious,
            exactQuranTextRequired = exact,
            worldlyMeansTask = worldly,
            reason = when {
                exact -> "المهمة تحتاج تثبتًا دقيقًا من نص القرآن ودلالته"
                religious -> "المهمة تتضمن حكمًا أو معنى شرعيًا يخضع للقرآن والسنة الصحيحة"
                worldly -> "المهمة دنيوية؛ الميزان القيمي قرآني والوسيلة تُحسم بالعلم والخبرة والدليل"
                else -> "البوصلة القرآنية حاكمة للقيم والغاية دون فرض طابع ديني على شكل المخرج"
            }
        )
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            append(HakimQuranicInvariantKernel.promptContext("quranic_framework"))
            appendLine("[الإطار القرآني الحاكم — افتراضي]")
            appendLine("القرآن الكريم هو أصل الهداية والميزان الأعلى للقيم والمعنى والغاية والحدود الشرعية؛ والسنة الصحيحة بيانٌ وهديٌ متبع. تعمل هذه الحاكمية داخل قواعد المنصة والسلامة والحقوق.")
            appendLine("في كل قرار: احفظ التوحيد والعبودية لله، واطلب الحق والصدق والعدل والأمانة والرحمة والإحسان وحفظ الحقوق ومنع الظلم والفساد بقدر صلة ذلك بالمهمة، دون اختلاق حكم أو نسبة معنى إلى القرآن بلا دليل.")
            appendLine("في الحلال والحرام والعبادات والأحكام والمعاني الشرعية: لا تجعل المنفعة أو السرعة أو رأي النموذج يعلو على نص شرعي ثابت، وتحقق من النص والدلالة والسياق والخلاف المعتبر قبل الجزم.")
            appendLine("في الطب والهندسة والحساب والإدارة والبرمجة والفيزياء الذرية والنووية وسائر الوسائل الدنيوية: استخدم العقل والعلم والتجربة والخبرة والمصادر الموثوقة لاختيار السبب الأنسب؛ هذه أدوات تحت الهداية وليست بدائل عنها، ولا تنسب قانونًا أو نتيجة تقنية أو تجريبية إلى القرآن بلا دليل.")
            appendLine("افصل دائمًا بين: الوحي، فهم البشر للوحي، الحكم الشرعي المستنبط، الحقيقة التجريبية، والقرار التنفيذي. لا تخلط بينها ولا تجعل أحدها يدّعي مقام الآخر.")
            appendLine("البركة والبسملة والدعاء معانٍ شرعية كريمة، لكنها لا تستبدل السبب المشروع والتحقق والعمل المتقن، ولا تُستخدم كخوارزمية أو ضمان نتيجة مادية.")
            if (a.exactQuranTextRequired) appendLine("هذه المهمة تتطلب تحققًا نصيًا من المصحف/مصدر موثوق قبل نقل الآية أو رقمها أو نسبتها.")
            if (a.worldlyMeansTask) appendLine("هذه مهمة وسائل دنيوية: طبّق الميزان القرآني على الغاية والأثر والحقوق، ودع اختيار الوسيلة للدليل الفني/التجريبي الأقوى.")
        }.take(7200)
    }

    fun requiresExactQuranVerification(raw: String): Boolean = assess(raw).exactQuranTextRequired

    fun status(): JSONObject = JSONObject()
        .put("quranic_normative_default", true)
        .put("quran_as_guidance_and_value_origin", true)
        .put("authentic_sunnah_as_explanation_and_guidance", true)
        .put("worldly_means_use_reason_science_experience", true)
        .put("no_technical_mystification", true)
        .put("exact_quran_text_requires_verification", true)
        .put("invariant_kernel", HakimQuranicInvariantKernel.status())

    private val religiousDecisionRegex = Regex(
        "(?i)(حلال|حرام|شرعي|الشريعة|فقه|فتوى|عبادة|واجب|فرض|مستحب|مكروه|يجوز|لا يجوز|القرآن|القرءان|حديث|السنة|النبي|رسول الله)"
    )

    private val exactQuranRegex = Regex(
        "(?i)(نص الآية|نص الاية|اكتب الآية|اكتب الاية|قال الله|ورد في القرآن|ورد في القرءان|رقم الآية|رقم الاية|اسم السورة|آية من|اية من|اقتبس من القرآن|اقتباس قرآني)"
    )

    private val worldlyMeansRegex = Regex(
        "(?i)(برمجة|كود|تقني|هندسة|طب|دواء|حساب|رياضيات|إدارة|ادارة|ملف|متصفح|تطبيق|موقع|شبكة|تعليم|جدول|تصميم|بناء|اختبار|سيارة|كهرباء|اقتصاد|زراعة|صناعة|ذرة|ذري|نووي|نواة|فيزياء)"
    )
}
