package ps.hakim.phoneagent

import android.content.Context

/**
 * وصف عام لأي منتج رقمي يطلبه المستخدم.
 *
 * لا يربط نوع الملف بموضوع بعينه. الموضوع والمحتوى شيء، وصيغة التسليم شيء آخر.
 */
data class HakimArtifactRequest(
    val kind: Kind,
    val format: Format,
    val topic: String,
    val title: String,
    val fileStem: String,
    val originalPrompt: String
) {
    enum class Kind { WORKSHEET, LESSON, TEST, PLAN, DOCUMENT }
    enum class Format { PDF }

    companion object {
        const val VERSION = "UNIVERSAL-ARTIFACT-REQUEST-2026-09-25-v1"
        private const val PREFS = "hakim_artifact_pipeline"
        private const val LAST_TOPIC = "last_topic"
        private const val LAST_KIND = "last_kind"

        fun resolve(context: Context, prompt: String): HakimArtifactRequest? {
            val q = normalize(prompt)
            val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
                .getString("recent", "")
                .orEmpty()
                .takeLast(10_000)

            val explicitArtifact = requestsFile(q)
            val followUp = looksLikeFileFollowUp(q)
            if (!explicitArtifact && !followUp) return null

            val recentNormalized = normalize(recent)
            val kind = detectKind(q)
                ?: detectKind(recentNormalized)
                ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(LAST_KIND, "")
                    .orEmpty()
                    .let { saved -> Kind.entries.firstOrNull { it.name == saved } }
                ?: Kind.DOCUMENT

            val topic = extractTopic(prompt)
                .takeIf { it.isNotBlank() }
                ?: extractTopic(recent)
                    .takeIf { it.isNotBlank() }
                ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(LAST_TOPIC, "")
                    .orEmpty()
                    .ifBlank { defaultTopic(kind) }

            val title = when (kind) {
                Kind.WORKSHEET -> "ورقة عمل: $topic"
                Kind.LESSON -> "درس: $topic"
                Kind.TEST -> "تقويم: $topic"
                Kind.PLAN -> "خطة: $topic"
                Kind.DOCUMENT -> topic
            }.trim().take(90)

            val stem = sanitizeFileStem(title)

            val request = HakimArtifactRequest(
                kind = kind,
                format = Format.PDF,
                topic = topic,
                title = title,
                fileStem = stem,
                originalPrompt = prompt
            )
            remember(context, request)
            return request
        }

        fun requestsFile(text: String): Boolean {
            val q = normalize(text)
            val format = listOf(
                "pdf", "بي دي اف", "بى دى اف", "ملف", "مستند",
                "للتحميل", "تحميل", "للطباعة", "طباعة"
            ).any { q.contains(it) }
            val product = detectKind(q) != null ||
                listOf("وثيقة", "نموذج", "ورقة", "ملف").any { q.contains(it) }
            val action = listOf(
                "أنشئ", "انشئ", "اصنع", "صمم", "صمّم", "جهز", "جهّز",
                "حول", "حوّل", "اريد", "أريد", "اعمل"
            ).any { q.contains(it) }
            return format && (product || action)
        }

        fun looksLikeFileFollowUp(text: String): Boolean {
            val q = normalize(text)
            val output = listOf(
                "pdf", "بي دي اف", "بى دى اف", "للتحميل", "تحميل",
                "للطباعة", "طباعة", "الملف", "المستند"
            ).any { q.contains(it) }
            val referent = listOf(
                "هذه", "هذي", "نفسها", "نفسه", "حولها", "حوّلها",
                "اريدها", "أريدها", "ورقة العمل", "الورقة", "الدرس",
                "الخطة", "التقويم"
            ).any { q.contains(it) }
            return output && referent
        }

        private fun detectKind(q: String): Kind? = when {
            listOf("ورقة عمل", "ورقه عمل", "worksheet", "ورقة تدريبات", "تمارين").any { q.contains(it) } -> Kind.WORKSHEET
            listOf("تحضير درس", "درس", "lesson").any { q.contains(it) } -> Kind.LESSON
            listOf("اختبار", "تقويم", "امتحان", "quiz").any { q.contains(it) } -> Kind.TEST
            listOf("خطة", "plan").any { q.contains(it) } -> Kind.PLAN
            listOf("مستند", "وثيقة", "document").any { q.contains(it) } -> Kind.DOCUMENT
            else -> null
        }

        private fun extractTopic(raw: String): String {
            val lines = raw.lines()
                .map { HakimProductOutput.clean(it).trim() }
                .filter { it.isNotBlank() }
            val joined = lines.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val explicit = listOf(
                Regex("(?:عن|حول|موضوع|درس)\\s+([^،.\\n]{1,80})"),
                Regex("(?:الجمع|الطرح)\\s+ضمن\\s+[٠-٩0-9]+"),
                Regex("العدد\\s+[٠-٩0-9]+")
            ).asSequence()
                .mapNotNull { it.find(joined)?.value }
                .firstOrNull()
                ?.trim()
                .orEmpty()

            if (explicit.isNotBlank()) return cleanupTopic(explicit)

            val cleaned = joined
                .replace(Regex("(?i)pdf|بي دي اف|بى دى اف"), " ")
                .replace(Regex("ورقة\\s+عمل|ورقه\\s+عمل|تحضير\\s+درس|مستند|وثيقة|ملف|للتحميل|للطباعة|طباعة"), " ")
                .replace(Regex("أنشئ|انشئ|اصنع|صمم|صمّم|جهز|جهّز|أريد|اريد|اعمل|حوّل|حول"), " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '،', '.', ':', '؛')

            return cleanupTopic(cleaned.take(80))
        }

        private fun cleanupTopic(value: String): String =
            value
                .replace(Regex("^(عن|حول|موضوع|درس)\\s+"), "")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '،', '.', ':', '؛')
                .ifBlank { "مستند" }

        private fun defaultTopic(kind: Kind): String = when (kind) {
            Kind.WORKSHEET -> "تدريبات"
            Kind.LESSON -> "الموضوع المطلوب"
            Kind.TEST -> "تقويم"
            Kind.PLAN -> "الخطة المطلوبة"
            Kind.DOCUMENT -> "مستند حكيم"
        }

        private fun sanitizeFileStem(title: String): String =
            toEasternDigits(title)
                .replace(Regex("[\\\\/:*?\"<>|]"), "")
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .take(70)
                .ifBlank { "مستند_حكيم" }

        fun toEasternDigits(text: String): String =
            text.map { ch ->
                when (ch) {
                    '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
                    '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
                    else -> ch
                }
            }.joinToString("")

        private fun remember(context: Context, request: HakimArtifactRequest) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(LAST_TOPIC, request.topic)
                .putString(LAST_KIND, request.kind.name)
                .putLong("last_requested_at", System.currentTimeMillis())
                .apply()
        }

        private fun normalize(text: String): String =
            text.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
