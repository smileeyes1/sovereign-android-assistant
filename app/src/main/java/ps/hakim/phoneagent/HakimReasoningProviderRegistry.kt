package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل مزودات الاستدلال. القلب الحاكم لا يتبع أي مزود خارجي.
 * التنفيذ المحلي الحتمي متاح دائمًا ضمن قدراته، أما جودة الاستدلال المتقدم الخارجي فتتبع المزود المتاح فعليًا.
 */
object HakimReasoningProviderRegistry {
    enum class Class { LOCAL_DETERMINISTIC, EXTERNAL_ADVANCED, MANUAL_EXTERNAL }

    data class Provider(
        val id: String,
        val title: String,
        val klass: Class,
        val vendor: String,
        val available: Boolean,
        val readyNow: Boolean,
        val automatic: Boolean,
        val requiresNetwork: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("title", title)
            .put("class", klass.name)
            .put("vendor", vendor)
            .put("available", available)
            .put("ready_now", readyNow)
            .put("automatic", automatic)
            .put("requires_network", requiresNetwork)
            .put("reason", reason)
    }

    private const val PREFS = "hakim_reasoning_provider_registry"
    private const val CUSTOM_URL = "custom_provider_url"

    fun providers(context: Context): List<Provider> {
        val mesh = HakimCapabilityMesh.discover(context).associateBy { it.id }
        val custom = customProviderUrl(context)
        return listOf(
            Provider(
                "local_deterministic",
                "محرك حكيم المحلي الحتمي",
                Class.LOCAL_DETERMINISTIC,
                "Hakim",
                true,
                true,
                true,
                false,
                "ينفذ ويفهم الإجراءات المحلية الآمنة والقواعد والسياق دون مزود خارجي؛ لا يُدّعى أنه يعادل نموذجًا لغويًا متقدمًا"
            ),
            Provider(
                "hakim_web_reasoning",
                "الاستدلال المتقدم داخل حكيم",
                Class.EXTERNAL_ADVANCED,
                "OpenAI/web-session",
                true,
                mesh["in_app_reasoning"]?.readyNow == true,
                true,
                true,
                "جلسة ويب داخل حكيم؛ أداة قابلة للفقد والاستبدال وليست حاكمًا"
            ),
            Provider(
                "chatgpt_official",
                "تطبيق ChatGPT الرسمي الاحتياطي",
                Class.EXTERNAL_ADVANCED,
                "OpenAI/app-session",
                true,
                mesh["chatgpt_official"]?.readyNow == true,
                false,
                true,
                "مسار احتياطي عند توفر التطبيق/البيئة المناسبة"
            ),
            Provider(
                "user_selected_web",
                "مزود ويب يختاره المستخدم",
                Class.MANUAL_EXTERNAL,
                "User-selected",
                custom != null,
                custom != null,
                false,
                true,
                if (custom != null) "بوابة يدوية قابلة للاستبدال؛ لا تُعامل كمزود تلقائي موثوق بلا محول خاص" else "غير مهيأ"
            )
        )
    }

    fun setCustomProviderUrl(context: Context, raw: String): Boolean {
        val url = raw.trim()
        if (url.isBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(CUSTOM_URL).apply()
            return true
        }
        if (!url.startsWith("https://") || url.length > 1000) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CUSTOM_URL, url).apply()
        return true
    }

    fun customProviderUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CUSTOM_URL, null)?.trim()?.takeIf { it.startsWith("https://") }

    fun advancedReady(context: Context): Boolean =
        providers(context).any { it.klass == Class.EXTERNAL_ADVANCED && it.readyNow }

    fun promptContext(context: Context): String = buildString {
        appendLine("[استقلال مزود الاستدلال]")
        appendLine("القلب الحاكم والتنفيذ المحلي لا يعتمدان على مزود ذكاء خارجي واحد. الاستدلال المتقدم خدمة قابلة للفقد والاستبدال، لا مصدر سلطة.")
        providers(context).forEach { p ->
            appendLine("• ${p.title}: ${if (p.readyNow) "جاهز" else if (p.available) "موجود/غير جاهز" else "غير مهيأ"} — ${p.reason}")
        }
        appendLine("عند غياب الاستدلال المتقدم: استمر محليًا فيما يمكن إثباته، احفظ المهمة، ولا تختلق ذكاءً مكافئًا غير موجود. استأنف عند عودة مزود مناسب.")
    }.take(5000)

    fun status(context: Context): JSONObject = JSONObject()
        .put("provider_registry", true)
        .put("core_runtime_vendor_independent", true)
        .put("advanced_reasoning_optional_for_core", true)
        .put("advanced_reasoning_ready_now", advancedReady(context))
        .put("advanced_model_equivalence_offline_not_claimed", true)
        .put("single_external_provider_is_not_governor", true)
        .put("providers", JSONArray(providers(context).map { it.toJson() }))
}
