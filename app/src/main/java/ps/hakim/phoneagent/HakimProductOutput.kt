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
            "internal reasoning",
            "technical limitations"
        )
        s = s.lines()
            .filterNot { line ->
                val l = line.trim().lowercase()
                internalHeadings.any { h -> l == h.lowercase() || l.startsWith(h.lowercase() + ":") }
            }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return s
    }

    fun containsRawMarkup(text: String): Boolean {
        val q = text.lowercase()
        return listOf(
            "<html", "</html", "<body", "</body", "<table", "</table",
            "<tr", "</tr", "<td", "</td", "<div", "</div", "class=\\\"",
            "\\n<tr", "\\n<td"
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
