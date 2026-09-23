package ps.hakim.phoneagent

import android.content.Context

/**
 * يحول كلام المستخدم إلى عقد تنفيذ قصير للمحرك المختار.
 * لا يطلب من النموذج إظهار سلسلة التفكير؛ بل أفضل نتيجة قابلة للتحقق فقط.
 */
object HakimIntentDirector {
    private const val MAX_INTERNAL_REVISIONS = 3

    data class Contract(
        val userIntent: String,
        val acceptance: String,
        val instruction: String
    )

    fun build(context: Context, raw: String, attachmentCount: Int = 0): Contract {
        val goal = raw.trim().take(4000)
        val acceptance = acceptanceFor(goal, attachmentCount)
        val cycle = HakimExecutiveLoop.current(context)?.cycle ?: 1

        val instruction = buildString {
            appendLine("أنت محرك متخصص يعمل تحت إشراف حكيم، ولست المدير النهائي للمهمة.")
            appendLine("افهم مقصد المستخدم قبل الإجابة، ثم صغ داخليًا لنفسك أفضل وأدق وأكفأ أمر عمل يحقق المقصد.")
            appendLine("نفّذ ذلك الأمر داخليًا، وافحص الناتج مقابل معيار الاكتمال، وحسّنه داخليًا عند الحاجة حتى 3 مراجعات.")
            appendLine("لا تعرض سلسلة التفكير أو المسودة الداخلية أو الأمر الذاتي الذي صغته لنفسك.")
            appendLine("لا تكتفِ بالخطة أو الاقتراح إذا كان بإمكانك إنتاج الناتج المطلوب فعليًا ضمن قدراتك.")
            appendLine("لا تدّعِ فعلًا خارجيًا أو نجاحًا لم يحدث.")
            appendLine("إذا كان المطلوب يحتاج أداة/بيانات/إذنًا غير متاح لك، أنجز كل الممكن أولًا ثم اذكر أصغر مانع محدد.")
            appendLine("أعد النتيجة النهائية للمستخدم مباشرة، ثم اذكر فقط الدليل أو القيود الضرورية للتحقق.")
            appendLine("معيار الاكتمال: " + acceptance)
            appendLine("دورة إشراف حكيم: " + cycle)
            appendLine("مقصد المستخدم:")
            append(goal)
        }.take(6000)

        return Contract(goal, acceptance, instruction)
    }

    fun acceptanceFor(raw: String, attachmentCount: Int = 0): String {
        val q = raw.trim().lowercase()
        val actionWords = listOf("نفذ", "نفّذ", "قم", "ثبت", "ثبّت", "اصلح", "أصلح", "انشئ", "أنشئ", "ارسل", "أرسل", "احذف", "احجز", "افتح")
        val creationWords = listOf("اكتب", "صمم", "صمّم", "أنشئ", "انشئ", "اصنع", "لخص", "لخّص")
        val questionWords = listOf("لماذا", "كيف", "ما ", "هل ", "متى", "أين", "من ")

        return when {
            actionWords.any { q.contains(it) } ->
                "أثر تنفيذي قابل للتحقق، لا مجرد شرح؛ وإن تعذر التنفيذ فسبب محدد وأصغر تدخل لازم فقط."
            creationWords.any { q.contains(it) } || attachmentCount > 0 ->
                "ناتج نهائي قابل للاستخدام يطابق المطلوب والمرفقات، مع تحقق من اكتماله قبل التسليم."
            questionWords.any { q.startsWith(it) || q.contains(" " + it) } ->
                "جواب مباشر ودقيق وكافٍ للمقصد، يميز المؤكد من غير المؤكد ولا يدعي تنفيذًا غير حاصل."
            else ->
                "نتيجة نهائية واضحة تحقق مقصد المستخدم بأقل عبء، مع عدم التوقف عند خطوة وسيطة."
        }
    }
}
