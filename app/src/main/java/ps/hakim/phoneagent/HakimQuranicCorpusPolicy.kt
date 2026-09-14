package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * سياسة القرآن كله لحكيم.
 * التغطية افتراضيًا لجميع سور القرآن الـ١١٤، بلا انتقاء مريح للنتيجة.
 * لا يُحمَّل كلُّ سورة على كل مسألة قسرًا؛ عند الاستقراء الشامل تُفحص السور كلها،
 * وعند المهمة المحددة لا يُستعمل إلا ما ثبتت صلته بعد التحقق.
 */
object HakimQuranicCorpusPolicy {
    const val VERSION = "QURANIC-CORPUS-ALL-114-2026-09-14-v1"
    const val SURAH_COUNT = 114

    enum class Layer {
        REVELATION_TEXT,
        QIRAAT,
        TAFSIR,
        ASBAB_AL_NUZUL,
        SURAH_METADATA,
        SCHOLARLY_INFERENCE
    }

    data class Assessment(
        val wholeCorpusRequested: Boolean,
        val exactTextRequested: Boolean,
        val qiraatRequested: Boolean,
        val tafsirRequested: Boolean,
        val asbabRequested: Boolean,
        val surahMetadataRequested: Boolean,
        val requiresSourceVerification: Boolean,
        val reason: String
    )

    val allSurahNumbers: List<Int> = (1..SURAH_COUNT).toList()

    fun coversSurah(number: Int): Boolean = number in 1..SURAH_COUNT

    fun assess(raw: String): Assessment {
        HakimQuranicInvariantKernel.requireInherited("quranic_corpus")
        val s = raw.trim().lowercase()
        val whole = wholeCorpusRegex.containsMatchIn(s)
        val exact = exactTextRegex.containsMatchIn(s)
        val qiraat = qiraatRegex.containsMatchIn(s)
        val tafsir = tafsirRegex.containsMatchIn(s)
        val asbab = asbabRegex.containsMatchIn(s)
        val metadata = metadataRegex.containsMatchIn(s)
        val verify = exact || qiraat || tafsir || asbab || metadata || whole
        return Assessment(
            wholeCorpusRequested = whole,
            exactTextRequested = exact,
            qiraatRequested = qiraat,
            tafsirRequested = tafsir,
            asbabRequested = asbab,
            surahMetadataRequested = metadata,
            requiresSourceVerification = verify,
            reason = when {
                whole -> "المطلوب استقراء قرآني شامل؛ يجب تغطية السور الـ١١٤ بلا انتقاء مسبق ثم استعمال ما ثبتت صلته"
                exact -> "المطلوب نص قرآني دقيق؛ يلزم مصدر مصحفي موثوق قبل النقل"
                qiraat -> "المطلوب متعلق بالقراءات؛ يلزم مصدر متخصص موثوق وعدم خلط القراءة بالتفسير"
                tafsir -> "المطلوب تفسير؛ يجب نسبته لمصدره وعدم رفعه إلى مرتبة نص الوحي"
                asbab -> "المطلوب أسباب نزول؛ يلزم تثبت من الرواية والمصدر"
                metadata -> "المطلوب بيانات سورة؛ يلزم مصدر موثق مع فصل المشهور والتوقيفي والاجتهادي"
                else -> "القرآن كله داخل التغطية الحاكمة، مع استعمال الدلالة ذات الصلة فقط دون تكلف"
            }
        )
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            appendLine("[سياسة القرآن كله — السور الـ١١٤]")
            appendLine("التغطية الافتراضية تشمل جميع سور القرآن من ١ إلى ١١٤. لا تنتقِ سورة أو آية لمجرد موافقة نتيجة مسبقة، ولا تُسقط ما يخالف الترجيح البشري.")
            appendLine("عند طلب استقراء شامل: ابحث عبر corpus السور الـ١١٤ كلها، ثم استخرج فقط الدلالات ذات الصلة المثبتة مع بيان المصدر والسياق؛ لا تُجبر كل سورة على كل مسألة.")
            appendLine("افصل طبقات المعرفة دائمًا: نص الوحي ≠ القراءات ≠ التفسير ≠ أسباب النزول ≠ بيانات السورة ≠ الاستنباط البشري. كل طبقة تُوسم بمصدرها ولا تُرفع طبقة بشرية إلى مقام النص.")
            appendLine("نص الآية ورقمها واسم السورة والرسم والضبط والقراءة لا تُنقل جزمًا من الذاكرة غير المتحققة إذا كانت الدقة مؤثرة؛ استخدم مصحفًا/مصدرًا قرآنيًا موثوقًا ومراجعًا.")
            appendLine("في أسماء السور ومقاصدها والمكي والمدني ووجوه التسمية وأسباب النزول: ميّز المشهور والتوقيفي والاجتهادي والخلاف المنقول، ولا تدّع الإجماع بلا دليل.")
            appendLine("في العلوم الدنيوية، بما فيها الذرة والنواة والطب والهندسة: القرآن يحكم الهداية والغاية والقيم والحدود؛ أما القانون التجريبي والسبب الفني فيثبت بالعلم والتجربة والدليل المستقل.")
            if (a.requiresSourceVerification) appendLine("هذه المهمة تتطلب تحققًا مصدريًا قبل الجزم أو الاقتباس أو نسبة معنى محدد إلى سورة/آية.")
        }.take(5000)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("all_114_surahs_covered", allSurahNumbers.size == SURAH_COUNT)
        .put("surah_count", SURAH_COUNT)
        .put("surah_range", JSONArray(listOf(1, SURAH_COUNT)))
        .put("no_cherry_picking", true)
        .put("no_forced_relevance", true)
        .put("whole_corpus_scan_on_comprehensive_request", true)
        .put("exact_text_requires_verified_mushaf_source", true)
        .put("revelation_distinct_from_tafsir_and_inference", true)
        .put("qiraat_distinct_from_tafsir", true)
        .put("asbab_requires_source_verification", true)
        .put("surah_metadata_requires_documented_source", true)
        .put("worldly_science_requires_independent_evidence", true)
        .put("layers", JSONArray(Layer.values().map { it.name }))

    private val wholeCorpusRegex = Regex(
        "(?i)(كل\\s*سور|جميع\\s*سور|القرآن\\s*كله|القرءان\\s*كله|من\\s*كل\\s*سورة|استقراء\\s*القرآن|استقراء\\s*القرءان|على\\s*كل\\s*شيء\\s*من\\s*سور)"
    )
    private val exactTextRegex = Regex(
        "(?i)(نص\\s*الآية|نص\\s*الاية|اكتب\\s*الآية|قال\\s*الله|رقم\\s*الآية|اسم\\s*السورة|اقتباس\\s*قرآني|الرسم\\s*العثماني|ضبط\\s*المصحف)"
    )
    private val qiraatRegex = Regex("(?i)(قراءة|قراءات|رواية\\s*حفص|ورش|قالون|الدوري|شعبة|روايات\\s*القرآن)")
    private val tafsirRegex = Regex("(?i)(تفسير|المفسرون|معنى\\s*الآية|معنى\\s*السورة|تأويل)")
    private val asbabRegex = Regex("(?i)(سبب\\s*النزول|أسباب\\s*النزول|اسباب\\s*النزول|نزلت\\s*في)")
    private val metadataRegex = Regex("(?i)(مقاصد\\s*السورة|موضوعات\\s*السورة|مكية|مدنية|مكي\\s*ومدني|تسمية\\s*السورة|أسماء\\s*السورة|ترتيب\\s*السور)")
}
