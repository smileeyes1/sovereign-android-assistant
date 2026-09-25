package ps.hakim.phoneagent

import android.content.Context

/**
 * خط إنتاج عام للملفات:
 * مقصد -> محتوى فقط -> تصيير محلي -> تحقق -> تسليم.
 *
 * لا يُطلب من أي نموذج إنشاء PDF أو binary؛ حكيم مسؤول عن الملف نفسه.
 */
object HakimArtifactPipeline {
    const val VERSION = "UNIVERSAL-ARTIFACT-PIPELINE-2026-09-25-v1"

    data class PreparedContent(
        val text: String,
        val source: Source
    )

    enum class Source {
        DETERMINISTIC_LOCAL,
        RECENT_CONVERSATION,
        MODEL_GENERATED
    }

    fun deterministicSpec(
        context: Context,
        prompt: String
    ): HakimTeacherArtifactSpec? =
        HakimLocalArtifactFactory.resolveSpec(context, prompt)

    fun recentUsableContent(
        context: Context,
        request: HakimArtifactRequest
    ): PreparedContent? {
        val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()

        if (recent.isBlank()) return null

        val assistantBlocks = recent
            .split(Regex("(?m)^حكيم:\s*"))
            .drop(1)
            .map { it.substringBefore(Regex("(?m)^أنت:\s*")) }
            .map { HakimProductOutput.clean(it).trim() }
            .filter { it.length >= 120 }
            .filterNot { HakimProductOutput.looksLikeCapabilityRefusal(it) }
            .filterNot { HakimProductOutput.looksLikeManualConversionInstructions(it) }

        val topicTokens = request.topic
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

        val best = assistantBlocks
            .asReversed()
            .firstOrNull { block ->
                topicTokens.isEmpty() || topicTokens.any { token -> block.contains(token, ignoreCase = true) }
            }
            ?: return null

        val validated = validateContent(request, best).getOrNull() ?: return null
        return PreparedContent(validated, Source.RECENT_CONVERSATION)
    }

    fun contentInstruction(
        context: Context,
        request: HakimArtifactRequest
    ): String {
        val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
            .takeLast(6_000)

        val kindInstruction = when (request.kind) {
            HakimArtifactRequest.Kind.WORKSHEET ->
                "أنشئ محتوى ورقة عمل تعليمية مناسبة للطالب: عنوان، تعليمات قصيرة، أنشطة/أسئلة واضحة ومساحات إجابة مفهومة."
            HakimArtifactRequest.Kind.LESSON ->
                "أنشئ محتوى درس تعليمي منظم: هدف واحد واضح، تمهيد، نشاط رئيسي، تقويم سريع وخلاصة مناسبة."
            HakimArtifactRequest.Kind.TEST ->
                "أنشئ محتوى تقويم/اختبار متوازن، واضح، قابل للطباعة، مع أسئلة مناسبة للمستوى دون إجابات ظاهرة للطالب."
            HakimArtifactRequest.Kind.PLAN ->
                "أنشئ محتوى خطة عملية منظمة وجاهزة للاستخدام، بعناوين قصيرة وخطوات واضحة."
            HakimArtifactRequest.Kind.DOCUMENT ->
                "أنشئ محتوى المستند النهائي بصورة منظمة وجاهزة للقراءة والطباعة."
        }

        return buildString {
            appendLine("أنت مولّد محتوى فقط داخل مصنع حكيم، ولست مسؤولًا عن إنشاء الملف.")
            appendLine("مهم جدًا: لا تقل إنك لا تستطيع إنشاء PDF أو binary؛ لا تنشئ PDF أصلًا.")
            appendLine("حكيم سيتولى تحويل النص الناتج محليًا إلى PDF حقيقي بعد عودتك.")
            appendLine("لا تعرض Markdown خامًا أو HTML أو JSON أو تعليمات من نوع Save as PDF / انسخ والصق / افتح Word.")
            appendLine("أعد محتوى الوثيقة النهائي فقط، بالعربية الفصحى الواضحة، دون شرح تقني.")
            appendLine("استخدم الأرقام الشرقية ٠١٢٣٤٥٦٧٨٩ في محتوى الصفوف الأولى عندما تكون أرقامًا ظاهرة للطالب.")
            appendLine(kindInstruction)
            appendLine("العنوان المقصود: " + request.title)
            appendLine("الموضوع: " + request.topic)
            if (recent.isNotBlank()) {
                appendLine("سياق حديث للاستفادة فقط، لا تنسخ منه أخطاء أو اعتذارات:")
                appendLine(recent)
            }
            appendLine("طلب المستخدم الأصلي:")
            append(request.originalPrompt)
        }.take(12_000)
    }

    fun repairInstruction(
        request: HakimArtifactRequest,
        failedText: String
    ): String = buildString {
        appendLine("أعد المحاولة كمحتوى وثيقة فقط.")
        appendLine("لا تتحدث عن القدرة على إنشاء ملفات ولا عن PDF ولا عن أدوات التحويل.")
        appendLine("لا تستخدم HTML أو Markdown خامًا أو جداول pipes.")
        appendLine("سلّم النص التعليمي النهائي الجاهز للتصيير المحلي.")
        appendLine("العنوان: " + request.title)
        appendLine("الموضوع: " + request.topic)
        if (failedText.isNotBlank()) {
            appendLine("النص السابق كان غير صالح؛ أصلح مضمونه ولا تكرر اعتذاره:")
            append(failedText.take(3_000))
        }
    }.take(6_000)

    fun validateContent(
        request: HakimArtifactRequest,
        raw: String
    ): Result<String> = runCatching {
        var content = HakimProductOutput.clean(raw)
            .trim()

        require(content.length >= 80) {
            "المحتوى أقصر من الحد الأدنى لمستند قابل للاستخدام."
        }
        require(!HakimProductOutput.looksLikeCapabilityRefusal(content)) {
            "النص اعتذار عن إنشاء الملف وليس محتوى وثيقة."
        }
        require(!HakimProductOutput.looksLikeManualConversionInstructions(content)) {
            "النص تعليمات تحويل يدوي وليس محتوى نهائيًا."
        }
        require(!HakimProductOutput.containsRawMarkup(content)) {
            "بقيت وسوم خام في المحتوى."
        }

        // الأرقام الغربية تُحوّل إلى شرقية في المخرجات العربية الافتراضية.
        content = HakimArtifactRequest.toEasternDigits(content)

        // لا نسمح بتسريب عبارات تشغيلية للمستند النهائي.
        val forbidden = listOf(
            "openrouter", "oauth", "binary", "binaries",
            "save as pdf", "ctrl+p", "ctrl + p",
            "لا أستطيع إنتاج ملف", "لا استطيع انتاج ملف",
            "القيود التقنية", "technical limitations"
        )
        require(forbidden.none { content.contains(it, ignoreCase = true) }) {
            "تسربت عبارات تشغيلية إلى المستند."
        }

        val titleToken = request.topic.split(Regex("\\s+"))
            .firstOrNull { it.length >= 2 }
        if (titleToken != null && request.kind != HakimArtifactRequest.Kind.DOCUMENT) {
            require(
                content.contains(titleToken, ignoreCase = true) ||
                    request.title.contains(titleToken, ignoreCase = true)
            ) {
                "المحتوى لا يبدو مرتبطًا بموضوع الطلب."
            }
        }

        content
    }

    fun needsModel(
        context: Context,
        request: HakimArtifactRequest
    ): Boolean =
        deterministicSpec(context, request.originalPrompt) == null &&
            recentUsableContent(context, request) == null
}
