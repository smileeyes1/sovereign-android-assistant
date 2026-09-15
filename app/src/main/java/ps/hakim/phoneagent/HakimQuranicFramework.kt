package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * الإطار القرآني الحاكم لحكيم.
 * يرث جذر الثقة القرآني وسياسة القرآن كله؛ القرآن أصل الهداية والميزان القيمي والشرعي،
 * والسنة الصحيحة بيان وهدي، بينما تبقى العلوم والخبرة والتجربة أدوات لمعرفة الوسائل الدنيوية.
 * لا تُنسب تفاصيل تقنية أو طبية أو تجريبية إلى القرآن بلا دليل، ولا يُحوّل القرآن إلى آلية تقنية أو تعويذة.
 */
object HakimQuranicFramework {
    data class Assessment(
        val quranicNormativeDefault: Boolean,
        val religiousDecision: Boolean,
        val exactQuranTextRequired: Boolean,
        val worldlyMeansTask: Boolean,
        val wholeQuranCorpusRequested: Boolean,
        val reason: String
    )

    fun assess(raw: String): Assessment {
        HakimQuranicInvariantKernel.requireInherited("quranic_framework")
        val corpus = HakimQuranicCorpusPolicy.assess(raw)
        val s = raw.trim().lowercase()
        val religious = religiousDecisionRegex.containsMatchIn(s)
        val exact = exactQuranRegex.containsMatchIn(s) || corpus.exactTextRequested || corpus.wholeCorpusRequested
        val worldly = worldlyMeansRegex.containsMatchIn(s) && !religious
        return Assessment(
            quranicNormativeDefault = true,
            religiousDecision = religious,
            exactQuranTextRequired = exact,
            worldlyMeansTask = worldly,
            wholeQuranCorpusRequested = corpus.wholeCorpusRequested,
            reason = when {
                corpus.wholeCorpusRequested -> "المهمة تطلب استقراء القرآن كله؛ تغطية السور الـ١١٤ مطلوبة من مصدر قرآني متحقق، بلا انتقاء مسبق أو تكلف في الصلة"
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
            append(HakimQuranicCorpusPolicy.promptContext(raw))
            append(HakimIstikhlaafFramework.promptContext(raw))
            appendLine("[الإطار القرآني الحاكم — افتراضي]")
            appendLine("القرآن الكريم هو أصل الهداية والميزان الأعلى للقيم والمعنى والغاية والحدود الشرعية؛ والسنة الصحيحة بيانٌ وهديٌ متبع. تعمل هذه الحاكمية داخل قواعد المنصة والسلامة والحقوق.")
            appendLine("في كل قرار: احفظ التوحيد والعبودية لله، واطلب الحق والصدق والعدل والأمانة والرحمة والإحسان وحفظ الحقوق ومنع الظلم والفساد بقدر صلة ذلك بالمهمة، دون اختلاق حكم أو نسبة معنى إلى القرآن بلا دليل.")
            appendLine("في الحلال والحرام والعبادات والأحكام والمعاني الشرعية: لا تجعل المنفعة أو السرعة أو رأي النموذج يعلو على نص شرعي ثابت، وتحقق من النص والدلالة والسياق والخلاف المعتبر قبل الجزم.")
            appendLine("في الطب والهندسة والحساب والإدارة والبرمجة والفيزياء الذرية والنووية وسائر الوسائل الدنيوية: استخدم العقل والعلم والتجربة والخبرة والمصادر الموثوقة لاختيار السبب الأنسب؛ هذه أدوات تحت الهداية وليست بدائل عنها. لا تنسب نتيجة تقنية أو تجريبية إلى القرآن بلا دليل، ولا تنسب قانونًا ذريًا أو نوويًا إلى القرآن بلا دليل مستقل صالح.")
            appendLine("افصل دائمًا بين: الوحي، فهم البشر للوحي، الحكم الشرعي المستنبط، الحقيقة التجريبية، والقرار التنفيذي. لا تخلط بينها ولا تجعل أحدها يدّعي مقام الآخر.")
            appendLine("البركة والبسملة والدعاء معانٍ شرعية كريمة، لكنها لا تستبدل السبب المشروع والتحقق والعمل المتقن، ولا تُستخدم كخوارزمية أو ضمان نتيجة مادية.")
            if (a.exactQuranTextRequired) appendLine("هذه المهمة تتطلب تحققًا نصيًا من المصحف/مصدر موثوق قبل نقل الآية أو رقمها أو نسبتها أو ادعاء استقراء القرآن كله.")
            if (a.wholeQuranCorpusRequested) appendLine("نفّذ استقراءً على مجال السور الـ١١٤ كاملًا من corpus نصه متحقق، ثم استعمل فقط ما ثبتت صلته بالمقصد؛ لا تعتبر الاستقراء الشامل مكتملًا من سياسة التغطية وحدها، ولا تُجبر كل سورة على كل مسألة ولا تنتقِ الشواهد لموافقة نتيجة مسبقة.")
            if (a.worldlyMeansTask) appendLine("هذه مهمة وسائل دنيوية: طبّق الميزان القرآني على الغاية والأثر والحقوق، ودع اختيار الوسيلة للدليل الفني/التجريبي الأقوى.")
        }.take(12000)
    }

    fun requiresExactQuranVerification(raw: String): Boolean = assess(raw).exactQuranTextRequired

    fun status(): JSONObject = JSONObject()
        .put("quranic_normative_default", true)
        .put("quran_as_guidance_and_value_origin", true)
        .put("authentic_sunnah_as_explanation_and_guidance", true)
        .put("all_114_surahs_in_governed_corpus", true)
        .put("whole_quran_corpus_requires_verified_source", true)
        .put("no_cherry_picking", true)
        .put("no_forced_relevance", true)
        .put("worldly_means_use_reason_science_experience", true)
        .put("no_technical_mystification", true)
        .put("istikhlaaf_framework", HakimIstikhlaafFramework.status())
        .put("exact_quran_text_requires_verification", true)
        .put("quranic_corpus_policy", HakimQuranicCorpusPolicy.status())
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
