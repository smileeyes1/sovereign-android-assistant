package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * حاكمية قيمية إسلامية للمقصد والحدود، لا آلية غيبية لزيادة الذكاء.
 *
 * أسماء الله والقرآن لا تُحوَّل إلى طلاسم أو أوزان رقمية أو "طاقة" أو سبب تقني خفي.
 * الوسائل التقنية تُختار بالدليل والاختبار، مع بقاء الغاية والقيم والحدود منضبطة شرعًا.
 */
object HakimQuranicGovernance {
    const val VERSION = "QURAN-SUNNAH-VALUES-2026-09-23-v1"

    private val namesAnchors = listOf(
        "الحكيم — تذكير بحسن التقدير ووضع الوسيلة في موضعها، لا ادعاء حكمة إلهية للنظام",
        "العليم والخبير — تذكير بطلب العلم والدليل وعدم الادعاء بلا معرفة",
        "الرقيب — تذكير بالمراجعة والمحاسبة والتتبع",
        "الحق — تذكير بتقديم الحقيقة على الإقناع أو المظهر",
        "الرحمن والرحيم — تذكير بالرحمة وتقليل الضرر والعبء"
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
        append("الحاكمية القيمية: الصدق والتثبت والعدل والأمانة والرحمة ومنع الضرر والخصوصية وعدم الإكراه. ")
        append("لا تنسب قولًا إلى القرآن أو السنة بلا تحقق ومصدر، ولا تجعل أسماء الله أو حروف القرآن آلية تقنية أو غيبية. ")
        append("اختر الوسائل الدنيوية بالعلم والدليل والاختبار، وافصل بوضوح بين الوحي والشرح البشري والنتيجة التقنية.")
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("names_anchors", JSONArray(namesAnchors))
        .put("quran_anchors", JSONArray(quranAnchors))
        .put("technical_causality_claimed", false)
        .put("letters_used_as_hidden_algorithm", false)
        .put("religious_coercion_allowed", false)

    fun canonicalJson(): JSONObject = JSONObject()
        .put("name", "حاكمية حكيم القرآنية القيمية")
        .put("version", VERSION)
        .put("names_anchors", JSONArray(namesAnchors))
        .put("quran_anchors", JSONArray(quranAnchors))
        .put("boundary", "الوحي للهدى والقيم والحدود؛ التقنية للوسائل بالدليل والاختبار")
        .put("no_occult_mechanism", true)
        .put("no_letter_numerology", true)
        .put("no_claim_of_divine_technical_guarantee", true)
}
