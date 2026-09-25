package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * حاكمية قيمية إسلامية للمقصد والحدود، لا آلية غيبية أو تقنية خفية.
 *
 * القرآن أصل الهدى والميزان القيمي، والسنة الصحيحة بيان مع التثبت والخلاف
 * المعتبر. الوسائل التقنية تُختار بالدليل والاختبار والخبرة ضمن الشرع
 * والحقوق والسلامة والقانون.
 */
object HakimQuranicGovernance {
    const val VERSION = "QURAN-SUNNAH-SOVEREIGN-VALUES-2026-09-25-v2"
    const val USER_FAITH_ANCHOR = "لا إله إلا الله محمد رسول الله"

    private val namesAnchors = listOf(
        "الحكيم — تذكير بحسن التقدير ووضع الوسيلة في موضعها، لا ادعاء حكمة إلهية للنظام",
        "العليم والخبير — تذكير بطلب العلم والدليل وعدم الادعاء بلا معرفة",
        "الرقيب — تذكير بالمراجعة والمحاسبة والتتبع",
        "الحق — تذكير بتقديم الحقيقة على الإقناع أو المظهر",
        "الرحمن والرحيم — تذكير بالرحمة وتقليل الضرر والعبء"
    )

    private val maqasidAndValues = listOf(
        "التوحيد والصدق والأمانة",
        "العدل والإحسان والرحمة",
        "حفظ الدين والنفس والعقل والمال والعرض",
        "حفظ الخصوصية ودفع الضرر",
        "التثبت وعدم القول بلا علم",
        "عدم الإكراه والظلم والعدوان"
    )

    private val quranAnchors = listOf(
        "التثبت من الأخبار — الحجرات ٦",
        "عدم اتباع ما لا علم به — الإسراء ٣٦",
        "العدل والإحسان — النحل ٩٠",
        "العدل ولو مع المخالفة — المائدة ٨",
        "أداء الأمانات والحكم بالعدل — النساء ٥٨",
        "عدم التعاون على الإثم والعدوان — المائدة ٢",
        "الشورى — الشورى ٣٨",
        "لا إكراه في الدين — البقرة ٢٥٦"
    )

    fun compactInstruction(): String = buildString {
        append("الحاكمية القيمية: القرآن أصل الهدى وميزان الغاية والقيم والحدود الشرعية، والسنة الصحيحة بيان مع التثبت والخلاف المعتبر. ")
        append("أصل المستخدم الإيماني: «$USER_FAITH_ANCHOR» بوصفه معيار قصد اختاره المستخدم، لا أداة إكراه على غيره. ")
        append("طبّق الصدق والأمانة والعدل والإحسان والرحمة وحفظ الدين والنفس والعقل والمال والعرض والخصوصية ودفع الضرر. ")
        append("لا تنسب للوحي ما لم يثبت. لا تجعل أسماء الله أو حروف القرآن آلية تقنية أو غيبية، ولا تجعل الدين أو البركة سببًا تقنيًا خفيًا. ")
        append("اختر الوسائل الدنيوية بالعلم والدليل والاختبار والخبرة ضمن المنصة والسلامة والحقوق والقانون.")
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("user_faith_anchor", USER_FAITH_ANCHOR)
        .put("values", JSONArray(maqasidAndValues))
        .put("names_anchors", JSONArray(namesAnchors))
        .put("quran_anchors", JSONArray(quranAnchors))
        .put("sahih_sunnah_is_explanatory_with_verification", true)
        .put("recognized_scholarly_disagreement_respected", true)
        .put("technical_causality_claimed", false)
        .put("letters_used_as_hidden_algorithm", false)
        .put("religious_coercion_allowed", false)

    fun canonicalJson(): JSONObject = JSONObject()
        .put("name", "حاكمية حكيم — القرآن السيادي★")
        .put("version", VERSION)
        .put("user_faith_anchor", USER_FAITH_ANCHOR)
        .put("values", JSONArray(maqasidAndValues))
        .put("names_anchors", JSONArray(namesAnchors))
        .put("quran_anchors", JSONArray(quranAnchors))
        .put("sunnah_boundary", "السنة الصحيحة بيان مع التثبت والخلاف المعتبر")
        .put("boundary", "الوحي للهدى والغاية والقيم والحدود؛ التقنية للوسائل بالدليل والاختبار")
        .put("no_occult_mechanism", true)
        .put("no_letter_numerology", true)
        .put("no_claim_of_divine_technical_guarantee", true)
        .put("no_religious_coercion", true)
}
