package ps.hakim.phoneagent

import android.content.Context

/**
 * سياسة تشغيل الأدوات: الأداة وسيلة داخل المهمة وليست محطة تسليم.
 * لا يعتبر فتح متصفح/تطبيق/صفحة نجاحًا. النجاح هو أثر نهائي قابل للاستخدام.
 */
object HakimSilentToolOrchestrator {
    enum class Tool {
        LOCAL_ARTIFACT,
        BACKGROUND_BROWSER,
        DIRECT_MODEL,
        LOCAL_DEVICE,
        PROVIDER_APP,
        SYSTEM_SHARE
    }

    data class Plan(
        val orderedTools: List<Tool>,
        val silentFirst: Boolean,
        val terminalHandoffAllowed: Boolean,
        val acceptance: String
    )

    fun plan(context: Context, prompt: String, hasAttachments: Boolean): Plan {
        val q = prompt.trim().lowercase()
        val artifact = HakimLocalArtifactFactory.canHandle(context, prompt)
        val freshWeb = needsFreshWeb(q) || isDirectUrl(q)

        val tools = mutableListOf<Tool>()
        if (artifact) tools += Tool.LOCAL_ARTIFACT
        if (freshWeb) tools += Tool.BACKGROUND_BROWSER
        if (HakimEngineRegistry.hasConfiguredGeneralChat(context)) tools += Tool.DIRECT_MODEL
        if (hasAttachments) tools += Tool.SYSTEM_SHARE

        if (tools.isEmpty()) tools += Tool.DIRECT_MODEL

        return Plan(
            orderedTools = tools.distinct(),
            silentFirst = true,
            terminalHandoffAllowed = false,
            acceptance = if (artifact) {
                "ملف نهائي محفوظ وقابل للفتح؛ فتح صفحة أو نموذج ليس نجاحًا."
            } else {
                "نتيجة نهائية داخل حكيم؛ فتح المتصفح أو التطبيق وحده ليس نجاحًا."
            }
        )
    }

    fun needsFreshWeb(text: String): Boolean {
        val q = text.lowercase()
        val markers = listOf(
            "اليوم", "الآن", "حالي", "أحدث", "آخر خبر", "ابحث", "تحقق من",
            "سعر", "طقس", "موعد", "متوفر", "فتح الآن", "latest", "today", "current"
        )
        return markers.any { q.contains(it) }
    }

    fun isDirectUrl(text: String): Boolean {
        val q = text.trim()
        return q.startsWith("https://") || q.startsWith("http://")
    }
}
