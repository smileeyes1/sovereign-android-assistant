package ps.hakim.phoneagent

import org.json.JSONObject

/**
 * آخر بوابة قبل أن يصل أي نص إلى عين المستخدم.
 * تنظف تنسيق النماذج، تمنع تسريب HTML/Markdown الخام، وتكشف مخرجات الملفات.
 */
object HakimProductOutput {

    fun clean(raw: String): String {
        if (raw.isBlank()) return raw

        var s = unwrapStructuredPayload(raw)
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\r\n", "\n")

        s = s
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</?(p|div|section|article|header|footer|h[1-6]|table|thead|tbody|tr)\\b[^>]*>"), "\n")
            .replace(Regex("(?i)<li\\b[^>]*>"), "• ")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("(?i)</?(td|th)\\b[^>]*>"), " | ")
            .replace(Regex("(?s)<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("__", "")
            .replace("\u0060", "")
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        val internalHeadings = listOf(
            "الدليل والقيود",
            "القيود التقنية",
            "التحقق الداخلي",
            "سلسلة التفكير",
            "القياس",
            "ملاحظة أخيرة",
            "internal reasoning",
            "technical limitations"
        )
        s = stripInternalDiagnosticBlocks(s, internalHeadings)
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return s
    }

    private fun stripInternalDiagnosticBlocks(text: String, headings: List<String>): String {
        val out = mutableListOf<String>()
        var skipping = false
        for (line in text.lines()) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()

            val startsInternal = headings.any { h ->
                val hh = h.lowercase()
                lower == hh ||
                    lower.startsWith(hh + ":") ||
                    lower.startsWith(hh + " ")
            } || lower.matches(
                Regex("^(🔎\\s*)?(الدليل والقيود|القيود|التحقق|القياس|internal reasoning|technical limitations)\\b.*")
            )

            if (startsInternal) {
                skipping = true
                continue
            }

            if (skipping) {
                if (trimmed == "---" || trimmed == "—" || trimmed == "___") {
                    skipping = false
                    continue
                }
                if (trimmed == "أنت:" || trimmed == "حكيم:") {
                    skipping = false
                    out += line
                }
                continue
            }

            val looksLikeInternalBullet = lower.matches(
                Regex("^([•\\-*]\\s*)?(القياس|القيود|التحقق|المحرك|المزود|binary|binaries|joining|addition|subtraction)\\s*[:：].*")
            )
            if (!looksLikeInternalBullet) out += line
        }
        return out.joinToString("\n")
    }

    fun containsRawMarkup(text: String): Boolean {
        val q = text.lowercase()
        return listOf(
            "<html", "</html", "<body", "</body", "<table", "</table",
            "<tr", "</tr", "<td", "</td", "<div", "</div", "class=\\\"",
            "\\n<tr", "\\n<td",
            "###", "**", "```",
            "الدليل والقيود", "القيود التقنية", "التحقق الداخلي",
            "internal reasoning", "technical limitations"
        ).any { q.contains(it) }
    }

    fun looksLikeHtmlArtifact(text: String): Boolean {
        val q = text.lowercase()
        val hits = listOf("<html", "<body", "<table", "<tr", "<td", "<div", "<style", "class=\\\"")
            .count { q.contains(it) }
        return hits >= 2 || (q.contains("</") && q.contains("<"))
    }

    fun requestsPdfArtifact(text: String): Boolean {
        val q = normalize(text)
        val pdf = listOf("pdf", "بي دي اف", "بى دى اف", "ملف pdf", "للتحميل", "للطباعة", "طباعة").any { q.contains(it) }
        val artifact = listOf("ورقة عمل", "ورقه عمل", "الورقة", "الورقه", "ملف", "نموذج", "مستند").any { q.contains(it) }
        return pdf && artifact
    }

    fun looksLikeCapabilityRefusal(text: String): Boolean {
        val q = normalize(text)
        return listOf(
            "لا أستطيع إنتاج ملف",
            "لا استطيع انتاج ملف",
            "لا أملك القدرة على توليد ملفات",
            "لا املك القدرة على توليد ملفات",
            "cannot generate",
            "can't generate",
            "binaries",
            "binary"
        ).any { q.contains(it.lowercase()) }
    }


    fun looksLikeManualConversionInstructions(text: String): Boolean {
        val q = normalize(text)
        return listOf(
            "كيف تحوله إلى pdf",
            "كيف تحوله الى pdf",
            "save as pdf",
            "ctrl + p",
            "ctrl+p",
            "انسخ الكود",
            "الصق الكود",
            "افتح محرر نصوص",
            "احفظ الملف باسم",
            "لا أستطيع إنتاج ملف",
            "لا استطيع انتاج ملف"
        ).any { q.contains(it) }
    }

    fun looksLikeBrokenWorksheetDump(text: String): Boolean {
        val q = text.lowercase()
        val pipeCount = text.count { it == '|' }
        val westernDigitCount = text.count { it in '0'..'9' }
        val foreignLeak = listOf(
            "collection within 10",
            "joining within 10",
            "addition subtraction",
            "notepad",
            "chrome",
            "firefox",
            "edge"
        ).any { q.contains(it) }
        val markdownLeak = q.contains("###") || q.contains("**") || q.contains("```")
        return looksLikeManualConversionInstructions(text) ||
            foreignLeak ||
            markdownLeak ||
            pipeCount >= 8 ||
            westernDigitCount >= 12
    }

    fun worksheetMustUseStructuredFactory(prompt: String): Boolean =
        HakimTeacherArtifactSpec.looksLikeWorksheetArtifactRequest(prompt) ||
            HakimTeacherArtifactSpec.looksLikeArtifactFollowUp(prompt)

    private fun unwrapStructuredPayload(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("{") || !t.endsWith("}")) return raw
        return runCatching {
            val obj = JSONObject(t)
            listOf("html", "content", "text", "body")
                .asSequence()
                .map { obj.optString(it, "") }
                .firstOrNull { it.isNotBlank() }
                ?: raw
        }.getOrDefault(raw)
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")
}
