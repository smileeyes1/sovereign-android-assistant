package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * سياسة القرآن كله لحكيم.
 * التغطية افتراضيًا لجميع سور القرآن الـ١١٤، بلا انتقاء مريح للنتيجة.
 * لا يُحمَّل كلُّ سورة على كل مسألة قسرًا؛ عند الاستقراء الشامل تُفحص السور كلها،
 * وعند المهمة المحددة لا يُستعمل إلا ما ثبتت صلته بعد التحقق.
 *
 * «كل ما يخص القرآن» يعني اكتمال نطاق علومه مع فصل صارم بين نص الوحي وبين العلوم البشرية
 * الخادمة له. اكتمال النطاق لا يعني امتلاك corpus محلي شامل لكل تفسير أو قراءة أو علم.
 */
object HakimQuranicCorpusPolicy {
    const val VERSION = "QURANIC-CORPUS-ALL-114-2026-09-15-v4"
    const val SURAH_COUNT = 114

    enum class Layer {
        REVELATION_TEXT,
        RASM_DABT_AYAH_NUMBERING,
        QIRAAT,
        TAJWEED_WAQF_IBTIDA,
        TAFSIR,
        ASBAB_AL_NUZUL,
        MAKKI_MADANI_REVELATION_CHRONOLOGY,
        SURAH_METADATA_THEMES_MAQASID,
        MUHKAM_MUTASHABIH,
        NASIKH_MANSUKH_CLAIMS,
        GHARIB_LEXICON,
        IRAB_BALAGHA_NAZM,
        AHKAM_FIQH_INFERENCE,
        MUNASABAT_SURAH_AYAH_COHERENCE,
        MUSHAF_COMPILATION_TRANSMISSION_HISTORY,
        TRANSLATIONS_AND_EXPLANATORY_RENDERINGS,
        SCHOLARLY_INFERENCE,
        WORLDLY_SCIENCE_BOUNDARY
    }

    /** نطاق ثابت يمنع اختزال «كل ما يخص القرآن» في النص والتفسير فقط. */
    val QURAN_KNOWLEDGE_DOMAINS: List<String> = Layer.values().map { it.name }

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
        val sciences = quranSciencesRegex.containsMatchIn(s)
        val verify = exact || qiraat || tafsir || asbab || metadata || sciences || whole
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
                asbab -> "المطلوب أسباب نزول؛ يلزم تثت من الرواية والمصدر"
                metadata -> "المطلوب بيانات سورة؛ يلزم مصدر موثق مع فصل المشهور والتوقيفي والاجتهادي"
                sciences -> "المطلوب من علوم القرآن؛ يلزم تحديد الطبقة العلمية ومصدرها وعدم رفعها إلى مرتبة نص الوحي"
                else -> "القرآن كله داخل التغطية الحاكمة، مع استعمال الدلالة ذات الصلة فقط دون تكلف"
            }
        )
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        return buildString {
            append(HakimQuranicSourceAuthority.promptContext())
            appendLine("[سياسة القرآن كله — السور الـ١١٤]")
            appendLine("التغطية الافتراضية تشمل جميع سور القرآن من ١ إلى ١١٤. لا تنتقِ سورة أو آية لمجرد موافقة نتيجة مسبقة، ولا تُسقط ما يخالف الترجيح البشري.")
            appendLine("عند طلب استقراء شامل: ابحث عبر corpus السور الـ١١٤ كلها، ثم استخرج فقط الدلالات ذات الصلة المثبتة مع بيان المصدر والسياق؛ لا تُجبر كل سورة على كل مسألة.")
            appendLine("افصل طبقات المعرفة دائمًا: نص الوحي ≠ الرسم والضبط وعدّ الآي ≠ القراءات ≠ التجويد والوقف والابتداء ≠ التفسير ≠ أسباب النزول ≠ المكي والمدني وتاريخ النزول ≠ بيانات السور ومقاصدها ≠ المحكم والمتشابه ≠ دعاوى النسخ ≠ غريب القرآن واللغة ≠ الإعراب والبلاغة والنظم ≠ أحكام القرآن والاستنباط الفقهي ≠ المناسبات ≠ تاريخ جمع المصحف ونقله ≠ الترجمات والتقريبات التفسيرية ≠ الاستنباط البشري.")
            appendLine("نص الآية ورقمها واسم السورة والرسم والضبط والقراءة لا تُنقل جزمًا من الذاكرة غير المتحققة إذا كانت الدقة مؤثرة؛ استخدم مصحفًا/مصدرًا قرآنيًا موثوقًا ومراجعًا.")
            appendLine("في أسماء السور ومقاصدها والمكي والمدني ووجوه التسمية وأسباب النزول والناسخ والمنسوخ والمحكم والمتشابه: ميّز المشهور والتوقيفي والاجتهادي والخلاف المنقول، ولا تدّع الإجماع بلا دليل.")
            appendLine("التجويد والوقف والابتداء والقراءات والرسم والضبط وعدّ الآي علوم تخصصية؛ لا تستنتج حكمًا فيها من النص المجرد دون مرجع مناسب.")
            appendLine("الترجمة ليست قرآنًا، وإنما تقريب لمعناه؛ والتفسير والاستنباط والبلاغة والإعراب علوم بشرية خادمة للنص ولا تُساوى به.")
            appendLine("في العلوم الدنيوية، بما فيها الذرة والنواة والطب والهندسة: القرآن يحكم الهداية والغاية والقيم والحدود؛ أما القانون التجريبي والسبب الفني فيثبت بالعلم والتجربة والدليل المستقل.")
            appendLine("لا تدّع أن كل علوم القرآن «مكتملة محليًا» لمجرد تحقق نص حفص المحلي؛ اكتمال نص المصحف شيء، واكتمال مصادر القراءات والتفسير وسائر العلوم شيء آخر.")
            if (a.requiresSourceVerification) appendLine("هذه المهمة تتطلب تحققًا مصدريًا قبل الجزم أو الاقتباس أو نسبة معنى/حكم/خاصية علمية إلى سورة أو آية.")
        }.take(11000)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("all_114_surahs_covered", allSurahNumbers.size == SURAH_COUNT)
        .put("surah_count", SURAH_COUNT)
        .put("surah_range", JSONArray(listOf(1, SURAH_COUNT)))
        .put("quran_knowledge_scope_schema_complete", QURAN_KNOWLEDGE_DOMAINS.size == 18)
        .put("quran_knowledge_domain_count", QURAN_KNOWLEDGE_DOMAINS.size)
        .put("quran_knowledge_domains", JSONArray(QURAN_KNOWLEDGE_DOMAINS))
        .put("local_verified_hafs_text_can_be_complete_independently", true)
        .put("local_exhaustive_secondary_quranic_sciences_corpus_verified", false)
        .put("secondary_sciences_completeness_claim_fail_closed", true)
        .put("no_cherry_picking", true)
        .put("no_forced_relevance", true)
        .put("whole_corpus_scan_on_comprehensive_request", true)
        .put("natural_arabic_whole_corpus_phrasing", true)
        .put("exact_text_requires_verified_mushaf_source", true)
        .put("revelation_distinct_from_tafsir_and_inference", true)
        .put("qiraat_distinct_from_tafsir", true)
        .put("asbab_requires_source_verification", true)
        .put("surah_metadata_requires_documented_source", true)
        .put("tajweed_waqf_qiraat_require_specialist_sources", true)
        .put("translation_is_not_quran_text", true)
        .put("nasikh_mansukh_claims_require_documented_scholarly_source", true)
        .put("worldly_science_requires_independent_evidence", true)
        .put("source_authority", HakimQuranicSourceAuthority.status())
        .put("layers", JSONArray(Layer.values().map { it.name }))

    private val wholeCorpusRegex = Regex(
        "(?i)(كل\\s*(?:ال)?سور|جميع\\s*(?:ال)?سور|كل\\s*(?:ال)?سورة|جميع\\s*(?:ال)?سورة|القرآن\\s*كله|القرءان\\s*كله|كل\\s*(?:ال)?قرآن|كل\\s*(?:ال)?قرءان|من\\s*كل\\s*(?:ال)?سورة|استقراء\\s*(?:ال)?قرآن|استقراء\\s*(?:ال)?قرءان|على\\s*كل\\s*شيء\\s*من\\s*(?:ال)?سور)"
    )
    private val exactTextRegex = Regex(
        "(?i)(نص\\s*الآية|نص\\s*الاية|اكتب\\s*الآية|قال\\s*الله|رقم\\s*الآية|اسم\\s*السورة|اقتباس\\s*قرآني|الرسم\\s*العثماني|ضبط\\s*المصحف|عد\\s*الآي|عدد\\s*الآيات)"
    )
    private val qiraatRegex = Regex("(?i)(قراءة|قراءات|رواية\\s*حفص|ورش|قالون|الدوري|شعبة|روايات\\s*القرآن)")
    private val tafsirRegex = Regex("(?i)(تفسير|المفسرون|معنى\\s*الآية|معنى\\s*السورة|تأويل)")
    private val asbabRegex = Regex("(?i)(سبب\\s*النزول|أسباب\\s*النزول|اسباب\\s*النزول|نزلت\\s*في)")
    private val metadataRegex = Regex("(?i)(مقاصد\\s*السورة|موضوعات\\s*السورة|مكية|مدنية|مكي\\s*ومدني|تسمية\\s*السورة|أسماء\\s*السورة|ترتيب\\s*السور)")
    private val quranSciencesRegex = Regex(
        "(?i)(علوم\\s*القرآن|علوم\\s*القرءان|تجويد|وقف\\s*وابتداء|الوقف\\s*والابتداء|رسم\\s*المصحف|ضبط\\s*المصحف|عد\\s*الآي|المحكم\\s*والمتشابه|ناسخ|منسوخ|غريب\\s*القرآن|إعراب\\s*القرآن|بلاغة\\s*القرآن|نظم\\s*القرآن|أحكام\\s*القرآن|مناسبات\\s*السور|جمع\\s*المصحف|تاريخ\\s*المصحف|ترجمة\\s*القرآن)"
    )
}
