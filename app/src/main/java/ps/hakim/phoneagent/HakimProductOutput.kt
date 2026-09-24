package ps.hakim.phoneagent

/**
 * آخر بوابة قبل أن يصل نص إلى عين المستخدم.
 * لا تغيّر حقيقة النتيجة؛ تنظف تنسيق النموذج الخام وتمنع تسريب تفاصيل التنفيذ.
 */
object HakimProductOutput {

    fun clean(raw: String): String {
        if (raw.isBlank()) return raw
        var s = raw
            .replace("\r\n", "\n")
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

    fun requestsPdfArtifact(text: String): Boolean {
        val q = normalize(text)
        val pdf = listOf("pdf", "بي دي اف", "بى دى اف", "ملف pdf", "للتحميل", "للطباعة").any { q.contains(it) }
        val artifact = listOf("ورقة عمل", "ورقه عمل", "ملف", "نموذج", "مستند").any { q.contains(it) }
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

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")
}
