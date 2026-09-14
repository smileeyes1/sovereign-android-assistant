package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * يفهم أقل إشارة ممكنة دون تحويل الغموض إلى تخمين خطِر.
 * الرمز/الحرف/الصمت يكتسب معناه من سياق موثوق فقط؛ بلا سياق يبقى غير كافٍ للتنفيذ عالي الأثر.
 */
object HakimMicroCueEngine {
    const val VERSION = "MICRO-CUE-2026-09-14-v1"

    enum class Kind { SILENT, SYMBOL, SINGLE_GLYPH, SHORT_CUE, EXPLICIT }

    data class Signal(
        val raw: String,
        val kind: Kind,
        val contextRequired: Boolean,
        val normalizedCue: String,
        val mayFastContinue: Boolean
    )

    fun classify(raw: String): Signal {
        val s = raw.trim()
        if (s.isBlank()) return Signal(raw, Kind.SILENT, true, "أكمل", true)
        if (symbolOnly(s)) return Signal(raw, Kind.SYMBOL, true, s.take(8), true)
        if (s.codePointCount(0, s.length) == 1) return Signal(raw, Kind.SINGLE_GLYPH, true, s, true)
        if (HakimIntentContext.isKnownMinimalCue(s) || s.split(Regex("\\s+")).size <= 2) {
            return Signal(raw, Kind.SHORT_CUE, true, s, true)
        }
        return Signal(raw, Kind.EXPLICIT, false, s, false)
    }

    fun status(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("silent_signal", true)
        .put("symbol_signal", true)
        .put("single_glyph_signal", true)
        .put("short_cue_signal", true)
        .put("context_required_for_ambiguous_micro_cues", true)
        .put("high_impact_never_authorized_by_micro_cue", true)

    private fun symbolOnly(s: String): Boolean {
        if (s.length > 12) return false
        var saw = false
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val type = Character.getType(cp)
            val symbol = type == Character.MATH_SYMBOL.toInt() ||
                type == Character.CURRENCY_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.OTHER_SYMBOL.toInt() ||
                type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt()
            if (!symbol && !Character.isWhitespace(cp)) return false
            if (symbol) saw = true
            i += Character.charCount(cp)
        }
        return saw
    }
}
