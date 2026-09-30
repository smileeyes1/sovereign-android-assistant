package ps.hakim.phoneagent

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/**
 * العقد العربي الافتراضي لحكيم.
 *
 * الهدف: جعل العربية هي اللغة والاتجاه والتنسيق الافتراضيان في واجهة حكيم
 * ومخرجاته، مع استثناءات مقصودة للمحتوى التقني والرياضيات حتى لا يفسد BiDi
 * المعنى المرئي.
 */
object HakimArabicPolicy {
    const val VERSION = "ARABIC-FIRST-RTL-AR-PS-2026-09-30-v2"
    private const val EASTERN_DIGITS = "٠١٢٣٤٥٦٧٨٩"
    private val arabicLocale: Locale = Locale.forLanguageTag(HakimPalestinianArabicProfile.LOCALE_TAG)

    private val invariants = listOf(
        "العربية الفلسطينية ar-PS هي المحلية الافتراضية لواجهة حكيم ومخرجاته ما لم يطلب المستخدم لغة أخرى صراحة",\n        "العربية الفصحى الطبيعية هي الأصل، والسياق الفلسطيني يُطبق عندما يكون ذا صلة دون اختلاق تفاصيل محلية",
        "الجذر المرئي للنص العربي RTL، وبداية الفقرة ومحاذاتها من اليمين",
        "اتجاه الوثيقة والجداول العربية من اليمين إلى اليسار، مع حفظ ترتيب القراءة الطبيعي",
        "الأرقام الشرقية ٠١٢٣٤٥٦٧٨٩ هي الافتراضية في المواد العربية الموجهة للطالب، وبخاصة الصفوف الأولى",
        "الرموز الرياضية لا تُترك لقواعد BiDi؛ تُعزل بصريًا وتُبنى وفق المعنى الرياضي المقصود",
        "عناوين المواقع والكود والمعرّفات التقنية تبقى باتجاهها الطبيعي ولا تُقلب قسرًا",
        "أي HTML عربي يبدأ بـ lang=ar وdir=rtl ويضبط direction:rtl وtext-align:right",
        "أي PDF/Word عربي يجب أن يستخدم تشكيلًا وخطًا يدعم العربية وأن يخضع لفحص بصري قبل إعلان النجاح",
        "لا نجاح لمخرج عربي إذا ظهرت محاذاة خاطئة أو انعكاس رموز أو أرقام غربية غير مقصودة أو قص/تداخل"
    )

    fun install(context: Context) {
        val canonical = canonicalJson().toString()
        context.getSharedPreferences("hakim_arabic_policy", Context.MODE_PRIVATE)
            .edit()
            .putString("version", VERSION)
            .putString("locale", HakimPalestinianArabicProfile.LOCALE_TAG)\n            .putString("country_context", HakimPalestinianArabicProfile.COUNTRY_CONTEXT)
            .putBoolean("arabic_default", true)\n            .putBoolean("palestinian_context_default", true)\n            .putBoolean("output_gate_required", true)
            .putBoolean("rtl_default", true)
            .putBoolean("right_alignment_default", true)
            .putBoolean("eastern_digits_student_default", true)
            .putBoolean("math_bidi_isolation_required", true)
            .putBoolean("technical_ltr_exception", true)
            .putBoolean("html_ar_rtl_required", true)
            .putBoolean("document_visual_qa_required", true)
            .putString("policy_json", canonical)
            .putString("sha256", sha256(canonical))
            .apply()
    }

    fun promptContract(): String = buildString {
        appendLine("[العربية الافتراضية في حكيم]")
        appendLine("العربية الفلسطينية ar-PS هي المحلية الافتراضية لكل واجهة ونص ووثيقة ومادة تعليمية ما لم يطلب المستخدم غير ذلك صراحة.")
        appendLine("للنثر العربي: ابدأ من اليمين، استخدم RTL ومحاذاة يمين وتسلسل قراءة عربي صحيح، واضبط علامات الترقيم والمسافات بما يلائم العربية.")
        appendLine("لـ HTML: استخدم <html lang=\"ar-PS\" dir=\"rtl\">، واجعل direction:rtl وtext-align:right افتراضيين، وطبّق RTL على الجداول والنماذج دون قلب المحتوى التقني.")
        appendLine("لـ PDF/Word/الطباعة: استخدم خطًا وتشكيلًا يدعمان العربية، احفظ اتصال الحروف، واضبط الجداول والترويسات من اليمين، ثم افحص الناتج بصريًا قبل اعتماده.")
        appendLine("الأرقام الشرقية ٠١٢٣٤٥٦٧٨٩ هي الافتراضية في المحتوى العربي الموجّه للطالب، خصوصًا الصفوف الأولى؛ لا تستبدل أرقامًا داخل URL أو كود أو معرّف تقني.")
        appendLine("الرياضيات مستقلة عن اتجاه النثر: حافظ على الترتيب الدلالي والمرئي المقصود، واعزل التعبير الرياضي LTR/BiDi عند الحاجة حتى لا تنقلب المعادلة. عين الطالب هي الحكم.")
        appendLine("في مواد الصفوف الأولى: لا تظهر 0-9 الغربية دون ضرورة صريحة، ولا يبدأ سطر العملية بعلامة =، ولا تتحرك = أو خانة الإجابة بسبب RTL.")
        appendLine("عناوين المواقع، الأكواد، أسماء الحزم، المسارات، المعرّفات والسلاسل التقنية تُعرض باتجاهها الطبيعي، ولا تُجبر على RTL إذا أفسد ذلك قراءتها.")
        appendLine("فحص القبول العربي: LANGUAGE/AR-PS/RTL/ALIGNMENT/SHAPING/DIGITS/BIDI/MATH/ENGLISH-LEAK/OVERLAP/CLIP/TABLES/STUDENT-EYE. أي فشل مادي يمنع إعلان الاكتمال.")\n        appendLine("كل مخرج ينتجه حكيم مباشرة يجب أن يمر عبر HakimArabicOutputGate قبل اعتباره صالحًا للتسليم.")
    }

    fun applyUiDefaults(root: View) {
        if (root is WebView) {\n            root.layoutDirection = View.LAYOUT_DIRECTION_RTL\n            root.textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL\n            return\n        }
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (root is TextView) {
            root.textDirection = View.TEXT_DIRECTION_FIRST_STRONG_RTL
            root.setTextLocale(arabicLocale)
            if (root is EditText) {
                val vertical = root.gravity and Gravity.VERTICAL_GRAVITY_MASK
                val resolvedVertical = if (vertical == 0) Gravity.CENTER_VERTICAL else vertical
                root.gravity = Gravity.START or resolvedVertical
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) applyUiDefaults(root.getChildAt(i))
        }
    }

    fun toEasternDigits(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            append(if (ch in '0'..'9') EASTERN_DIGITS[ch - '0'] else ch)
        }
    }

    /**
     * عزل رياضي خفيف للنصوص المرئية عند الحاجة.
     * LRI/PDI يمنع سياق RTL المحيط من قلب ترتيب العملية.
     */
    fun isolateMath(expression: String, easternDigits: Boolean = true): String {
        val value = if (easternDigits) toEasternDigits(expression) else expression
        return "\u2066$value\u2069"
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences("hakim_arabic_policy", Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", p.getString("version", VERSION))
            .put("locale", p.getString("locale", HakimPalestinianArabicProfile.LOCALE_TAG))\n            .put("country_context", p.getString("country_context", HakimPalestinianArabicProfile.COUNTRY_CONTEXT))\n            .put("palestinian_context_default", p.getBoolean("palestinian_context_default", false))\n            .put("output_gate_required", p.getBoolean("output_gate_required", false))
            .put("arabic_default", p.getBoolean("arabic_default", false))
            .put("rtl_default", p.getBoolean("rtl_default", false))
            .put("right_alignment_default", p.getBoolean("right_alignment_default", false))
            .put("eastern_digits_student_default", p.getBoolean("eastern_digits_student_default", false))
            .put("math_bidi_isolation_required", p.getBoolean("math_bidi_isolation_required", false))
            .put("technical_ltr_exception", p.getBoolean("technical_ltr_exception", false))
            .put("html_ar_rtl_required", p.getBoolean("html_ar_rtl_required", false))
            .put("document_visual_qa_required", p.getBoolean("document_visual_qa_required", false))
            .put("sha256", p.getString("sha256", ""))
    }

    fun canonicalJson(): JSONObject = JSONObject()
        .put("name", "عقد حكيم العربي الافتراضي")
        .put("version", VERSION)
        .put("locale", HakimPalestinianArabicProfile.LOCALE_TAG)\n        .put("country_context", HakimPalestinianArabicProfile.COUNTRY_CONTEXT)
        .put("direction", "rtl")
        .put("alignment", "right")
        .put("student_digits", EASTERN_DIGITS)
        .put("invariants", JSONArray(invariants))
        .put("technical_exception", "URL/code/package/path/identifier keep natural direction")
        .put("math_rule", "semantic order first; explicit bidi isolation; student-eye visual QA")
        .put("html_root", "<html lang=\"ar-PS\" dir=\"rtl\">")
        .put("qa", "LANGUAGE/AR-PS/RTL/ALIGNMENT/SHAPING/DIGITS/BIDI/MATH/ENGLISH-LEAK/OVERLAP/CLIP/TABLES/STUDENT-EYE")\n        .put("output_gate", HakimArabicOutputGate.VERSION)\n        .put("palestinian_profile", HakimPalestinianArabicProfile.VERSION)

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
