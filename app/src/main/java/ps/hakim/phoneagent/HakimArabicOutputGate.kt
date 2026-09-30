package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * بوابة قبول للمخرجات العربية.
 *
 * هذه البوابة تفحص النص/HTML قبل التسليم حين يكون حكيم هو المنتج المباشر
 * للمخرج. المخرجات الصادرة من تطبيق خارجي لا تُعد مفحوصة ما لم تمر فعليًا هنا.
 */
object HakimArabicOutputGate {
    const val VERSION = "AR-PS-OUTPUT-GATE-2026-09-30-v1"

    enum class Audience {
        GENERAL,
        EARLY_GRADE_STUDENT,
        TECHNICAL
    }

    data class Check(val code: String, val ok: Boolean, val detail: String = "")

    data class Result(val passed: Boolean, val checks: List<Check>) {
        fun toJson(): JSONObject = JSONObject()
            .put("passed", passed)
            .put("checks", JSONArray().apply {
                checks.forEach { c ->
                    put(JSONObject().put("code", c.code).put("ok", c.ok).put("detail", c.detail))
                }
            })
    }

    private val englishUiLeak = Regex(
        """\b(loading|submit|failed|error|cancel|settings|download|upload|retry|continue|back|next)\b""",
        RegexOption.IGNORE_CASE
    )

    fun validateText(
        text: String,
        audience: Audience = Audience.GENERAL,
        explicitNonArabic: Boolean = false
    ): Result {
        val checks = mutableListOf<Check>()
        val trimmed = text.trim()
        checks += Check("NON_EMPTY", trimmed.isNotEmpty(), "يجب ألا يكون المخرج فارغًا")

        if (!explicitNonArabic && audience != Audience.TECHNICAL) {
            val hasArabic = trimmed.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
            checks += Check("ARABIC_PRESENT", hasArabic, "العربية هي لغة المخرج الافتراضية")
            val leak = englishUiLeak.find(trimmed)?.value.orEmpty()
            checks += Check("NO_COMMON_ENGLISH_UI_LEAK", leak.isEmpty(), leak)
        }

        val equalsAtLineStart = trimmed.lineSequence().any { it.trimStart().startsWith("=") }
        checks += Check("NO_EQUALS_AT_LINE_START", !equalsAtLineStart, "لا تبدأ العملية الرياضية بعلامة =")

        if (audience == Audience.EARLY_GRADE_STUDENT) {
            val westernDigit = Regex("""[0-9]""").find(trimmed)?.value.orEmpty()
            checks += Check("EASTERN_DIGITS_ONLY", westernDigit.isEmpty(), westernDigit)
        }

        return Result(checks.all { it.ok }, checks)
    }

    fun validateHtml(html: String, earlyGradeStudent: Boolean = false): Result {
        val base = validateText(
            html,
            audience = if (earlyGradeStudent) Audience.EARLY_GRADE_STUDENT else Audience.GENERAL
        )
        val lower = html.lowercase()
        val htmlLocaleOk = Regex("""<html\b[^>]*\blang\s*=\s*["']ar-ps["'][^>]*>""").containsMatchIn(lower)
        val rtlOk = Regex("""<html\b[^>]*\bdir\s*=\s*["']rtl["'][^>]*>""").containsMatchIn(lower)
        val directionOk = Regex("""direction\s*:\s*rtl""").containsMatchIn(lower)
        val alignOk = Regex("""text-align\s*:\s*(right|start)""").containsMatchIn(lower)
        val extra = listOf(
            Check("HTML_LANG_AR_PS", htmlLocaleOk, "يجب أن يكون lang=ar-PS"),
            Check("HTML_DIR_RTL", rtlOk, "يجب أن يكون dir=rtl"),
            Check("CSS_DIRECTION_RTL", directionOk, "يجب تثبيت direction:rtl"),
            Check("CSS_ARABIC_ALIGNMENT", alignOk, "يجب تثبيت محاذاة عربية مناسبة")
        )
        val all = base.checks + extra
        return Result(all.all { it.ok }, all)
    }
}
