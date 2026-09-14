package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل وموجّه مزودات الاستدلال.
 * قلب حكيم لا يتبع مزودًا خارجيًا واحدًا؛ المحلي أولًا، والاستدلال المتقدم أداة قابلة للاستبدال.
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

    data class WebProviderSpec(
        val id: String,
        val title: String,
        val vendor: String,
        val startUrl: String,
        val trustedHosts: Set<String>
    )

    private const val PREFS = "hakim_reasoning_provider_registry"
    private const val CUSTOM_URL = "custom_provider_url"
    private const val PREFERRED = "preferred_provider"
    const val AUTO = "auto"
    const val CHATGPT_WEB = "hakim_web_chatgpt"
    const val GEMINI_WEB = "hakim_web_gemini"
    const val COPILOT_WEB = "hakim_web_copilot"
    const val LOCAL_ONLY = "local_only"

    private val knownWebProviders = listOf(
        WebProviderSpec(
            CHATGPT_WEB,
            "ChatGPT داخل حكيم",
            "OpenAI",
            "https://chatgpt.com/",
            setOf("chatgpt.com")
        ),
        WebProviderSpec(
            GEMINI_WEB,
            "Gemini داخل حكيم",
            "Google",
            "https://gemini.google.com/app",
            setOf("gemini.google.com")
        ),
        WebProviderSpec(
            COPILOT_WEB,
            "Copilot داخل حكيم",
            "Microsoft",
            "https://copilot.microsoft.com/",
            setOf("copilot.microsoft.com")
        )
    )

    fun providers(context: Context): List<Provider> {
        val custom = customProviderUrl(context)
        val online = hasNetwork(context)
        val selected = preferredProviderId(context)
        val external = knownWebProviders.map { spec ->
            val health = health(context, spec.id)
            Provider(
                spec.id,
                spec.title,
                Class.EXTERNAL_ADVANCED,
                spec.vendor,
                true,
                online && !health.optBoolean("cooldown", false),
                selected == AUTO || selected == spec.id,
                true,
                if (!online) "لا توجد شبكة الآن" else if (health.optBoolean("cooldown", false)) "في تهدئة مؤقتة بعد فشل متكرر؛ سيجرب حكيم بديلًا" else "جلسة ويب مستقلة داخل حكيم؛ قد تحتاج تسجيل دخول مرة واحدة"
            )
        }
        val chatGptInstalled = runCatching {
            context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt") != null
        }.getOrDefault(false)
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
                "يفهم وينفذ الإجراءات المحلية الآمنة والقواعد والسياق دون مزود خارجي؛ لا يُدّعى أنه يعادل نموذجًا لغويًا متقدمًا"
            )
        ) + external + listOf(
            Provider(
                "chatgpt_official",
                "تطبيق ChatGPT الرسمي الاحتياطي",
                Class.EXTERNAL_ADVANCED,
                "OpenAI/app-session",
                chatGptInstalled,
                chatGptInstalled && HakimAccessibilityService.instance != null,
                false,
                true,
                "احتياط تطويري فقط؛ النسخة الميدانية لا تعتمد عليه ولا تعلن Accessibility"
            ),
            Provider(
                "user_selected_web",
                "مزود ويب يختاره المستخدم",
                Class.MANUAL_EXTERNAL,
                "User-selected",
                custom != null,
                custom != null && online,
                false,
                true,
                if (custom != null) "بوابة يدوية قابلة للاستبدال؛ لا تُستخدم تلقائيًا بلا محول موثوق" else "غير مهيأ"
            )
        )
    }

    fun webProvider(id: String): WebProviderSpec? = knownWebProviders.firstOrNull { it.id == id }

    fun preferredProviderId(context: Context): String {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREFERRED, AUTO).orEmpty()
        return if (raw == AUTO || raw == LOCAL_ONLY || knownWebProviders.any { it.id == raw }) raw else AUTO
    }

    fun setPreferredProvider(context: Context, id: String): Boolean {
        if (id != AUTO && id != LOCAL_ONLY && knownWebProviders.none { it.id == id }) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFERRED, id).apply()
        return true
    }

    fun preferredTitle(context: Context): String = when (val id = preferredProviderId(context)) {
        AUTO -> "تلقائي — أفضل مزود متاح"
        LOCAL_ONLY -> "محلي فقط"
        else -> webProvider(id)?.title ?: "تلقائي — أفضل مزود متاح"
    }

    /**
     * في AUTO يقدم آخر مزود نجح فعليًا، ثم الأقل فشلًا. لا يُفترض نجاح أي مزود لمجرد وجوده.
     */
    fun orderedWebProviders(context: Context): List<WebProviderSpec> {
        val preferred = preferredProviderId(context)
        if (preferred == LOCAL_ONLY) return emptyList()
        if (preferred != AUTO) return listOfNotNull(webProvider(preferred))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return knownWebProviders.sortedWith(
            compareByDescending<WebProviderSpec> { prefs.getLong("${it.id}_last_success", 0L) }
                .thenBy { prefs.getInt("${it.id}_failures", 0) }
                .thenBy { knownWebProviders.indexOf(it) }
        )
    }

    fun recordWebResult(context: Context, providerId: String, success: Boolean, latencyMs: Long) {
        if (knownWebProviders.none { it.id == providerId }) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e = prefs.edit().putLong("${providerId}_last_latency", latencyMs.coerceAtLeast(0L))
        if (success) {
            e.putLong("${providerId}_last_success", System.currentTimeMillis())
                .putInt("${providerId}_failures", 0)
                .remove("${providerId}_cooldown_until")
        } else {
            val failures = (prefs.getInt("${providerId}_failures", 0) + 1).coerceAtMost(20)
            e.putInt("${providerId}_failures", failures)
            if (failures >= 2) {
                val backoff = (failures.coerceAtMost(6) * 2L) * 60L * 1000L
                e.putLong("${providerId}_cooldown_until", System.currentTimeMillis() + backoff)
            }
        }
        e.apply()
    }

    fun health(context: Context, providerId: String): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cooldownUntil = p.getLong("${providerId}_cooldown_until", 0L)
        return JSONObject()
            .put("failures", p.getInt("${providerId}_failures", 0))
            .put("last_success", p.getLong("${providerId}_last_success", 0L))
            .put("last_latency_ms", p.getLong("${providerId}_last_latency", 0L))
            .put("cooldown_until", cooldownUntil)
            .put("cooldown", cooldownUntil > System.currentTimeMillis())
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
        preferredProviderId(context) != LOCAL_ONLY && hasNetwork(context)

    fun promptContext(context: Context): String = buildString {
        appendLine("[استقلال مزود الاستدلال]")
        appendLine("القلب الحاكم والتنفيذ المحلي لا يعتمدان على مزود ذكاء خارجي واحد. المحلي أولًا، والاستدلال المتقدم أداة قابلة للفقد والاستبدال، لا مصدر سلطة.")
        appendLine("الاختيار الحالي=${preferredTitle(context)}. في الوضع التلقائي يبدل حكيم بين ChatGPT وGemini وCopilot بحسب الجاهزية والنجاح الفعلي، ويضع المزود المتكرر فشله في تهدئة مؤقتة.")
        providers(context).forEach { p ->
            appendLine("• ${p.title}: ${if (p.readyNow) "جاهز/قابل للمحاولة" else if (p.available) "موجود/غير جاهز" else "غير مهيأ"} — ${p.reason}")
        }
        appendLine("عند غياب الاستدلال المتقدم: استمر محليًا فيما يمكن إثباته، احفظ المهمة، ولا تختلق ذكاءً مكافئًا غير موجود. لا تعرض نافذة مزود فوق حكيم إلا عند تسجيل دخول لازم أو اختيار المستخدم له صراحة.")
    }.take(7000)

    fun status(context: Context): JSONObject = JSONObject()
        .put("provider_registry", true)
        .put("core_runtime_vendor_independent", true)
        .put("multi_provider_router", true)
        .put("automatic_failover", true)
        .put("provider_health_learning", true)
        .put("preferred_provider", preferredProviderId(context))
        .put("advanced_reasoning_optional_for_core", true)
        .put("advanced_reasoning_ready_now", advancedReady(context))
        .put("advanced_model_equivalence_offline_not_claimed", true)
        .put("single_external_provider_is_not_governor", true)
        .put("providers", JSONArray(providers(context).map { it.toJson() }))

    private fun hasNetwork(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.activeNetwork != null
    }.getOrDefault(false)
}
