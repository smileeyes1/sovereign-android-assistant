package ps.hakim.phoneagent

import android.content.Context

/**
 * مصفوفة الحكمة: واجهة مستقرة لقرار المحرك.
 *
 * لا تحتوي تقييمات ثابتة لمزود بعينه. الترتيب يأتي من سياسة الإدراك الاحترافية
 * اعتمادًا على حالة المهمة والقياس المحلي الفعلي، مع بقاء المجانية والصحة بوابات صلبة.
 */
object HakimWisdomMatrix {
    data class Ranked(
        val engine: HakimInferenceEngine,
        val score: Int,
        val reason: String
    )

    fun rank(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        excluded: Set<String> = emptySet()
    ): List<Ranked> =
        HakimCognitivePolicy.rank(context, prompt, attachments, excluded)
            .map { Ranked(it.engine, it.score, it.reason) }

    fun choose(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        excluded: Set<String> = emptySet()
    ): Ranked? = rank(context, prompt, attachments, excluded).firstOrNull()
}
