package ps.hakim.phoneagent

import android.content.Context

/**
 * يحول كلام المستخدم إلى عقد تنفيذ قصير للمحرك المختار.
 * لا يطلب من النموذج إظهار سلسلة التفكير؛ بل أفضل نتيجة قابلة للتحقق فقط.
 */
object HakimIntentDirector {
    data class Contract(
        val userIntent: String,
        val acceptance: String,
        val instruction: String
    )

    fun build(context: Context, raw: String, attachmentCount: Int = 0): Contract {
        val goal = raw.trim().take(4000)
        val acceptance = acceptanceFor(goal, attachmentCount)
        val cycle = HakimExecutiveLoop.current(context)?.cycle ?: 1
        val recentContext = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
            .takeLast(8_000)

        val instruction = buildString {
            append(HakimConstitution.promptPrefix(context))
            appendLine("أنت محرك متخصص يعمل تحت إشراف حكيم، ولست المدير النهائي للمهمة.")
            appendLine("افهم مقصد المستخدم قبل الإجابة، ثم صغ داخليًا لنفسك أفضل وأدق وأكفأ أمر عمل يحقق المقصد.")
            appendLine("نفّذ ذلك الأمر داخليًا، وافحص الناتج مقابل معيار الاكتمال، وحسّنه ما دام هناك عيب مادي قابل للإصلاح دون تكرار غير منتج.")
            appendLine("لا تعرض سلسلة التفكير أو المسودة الداخلية أو الأمر الذاتي الذي صغته لنفسك.")
            appendLine("لا تكتفِ بالخطة أو الاقتراح إذا كان بإمكانك إنتاج الناتج المطلوب فعليًا ضمن قدراتك.")
            appendLine("اعتبر المتصفح المدمج والأدوات والخدمات وسائل داخلية ضمن التنفيذ، لا محطات تسليم للمستخدم.")
            appendLine("نفّذ صامتًا قدر الإمكان، ثم سلّم النتيجة أو الملف الجاهز نفسه؛ فتح صفحة أو تطبيق وحده ليس نجاحًا.")
            appendLine("استخدم أقل أداة وصلاحية وبيانات تحقق الغاية، ولا ترسل محتوى خاصًا من رابط مباشر إلى نموذج خارجي تلقائيًا.")
            appendLine("لا تدّعِ فعلًا خارجيًا أو نجاحًا لم يحدث.")
            appendLine("إذا كان المطلوب يحتاج أداة/بيانات/إذنًا غير متاح لك، أنجز كل الممكن أولًا ثم اذكر أصغر مانع محدد.")
            appendLine("أعد النتيجة النهائية للمستخدم مباشرة.")
            appendLine("اكتب بالعربية الفصحى الواضحة ما لم يطلب المستخدم لغة أخرى، ولا تعرض Markdown خامًا مثل ### أو ** أو عناوين إنجليزية غير لازمة.")
            appendLine("لا تعرض أسماء المحركات أو المزودين أو تفاصيل OAuth أو القيود التقنية الداخلية في الرد النهائي إلا إذا سأل المستخدم عنها صراحة.")
            appendLine("إذا كان المطلوب ملفًا أو أثرًا ملموسًا رقميًا، فلا تعتبر الشرح أو الاعتذار نجاحًا؛ سلّم الملف نفسه إن كانت أداة حكيم قادرة على إنشائه.")
            appendLine("معيار الاكتمال: " + acceptance)
            appendLine("دورة إشراف حكيم: " + cycle)
            if (recentContext.isNotBlank()) {
                appendLine(
                    HakimAuthorityBoundary.externalData(
                        "سياق محادثة سابق داخل حكيم",
                        recentContext,
                        8_000
                    )
                )
            }
            appendLine("مقصد المستخدم الحالي:")
            append(goal)
        }.take(12_000)

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
