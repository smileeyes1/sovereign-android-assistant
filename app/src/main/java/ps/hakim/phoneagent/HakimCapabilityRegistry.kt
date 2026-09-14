package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject

/**
 * معرفة ذاتية بالقدرات: لا يفترض حكيم أداة أو اتصالًا لم يثبت توفره الآن.
 * وجود القدرة في التطبيق يختلف عن جاهزيتها اللحظية للتنفيذ.
 */
object HakimCapabilityRegistry {
    data class Capability(
        val id: String,
        val available: Boolean,
        val readyNow: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("available", available)
            .put("ready_now", readyNow)
            .put("reason", reason)
    }

    fun discover(context: Context): List<Capability> {
        HakimQuranicInvariantKernel.requireInherited("capability_registry")
        val accessibilityReady = HakimAccessibilityService.instance != null
        val web = HakimRuntime.visibleWebView()
        val internetReady = hasInternetCapability(context)
        val chatGptInstalled = runCatching {
            context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt") != null
        }.getOrDefault(false)
        val integrationReady = runCatching {
            HakimIntegrationFabric.structuralStatus(context, "capability_registry")
                .optBoolean("structural_integrity")
        }.getOrDefault(false)
        val relayConfigured = HakimUnifiedRelay.isConfigured(context)
        val recovery = HakimConnectionResilience.status(context)
        val connectionReady = recovery.optBoolean("service_connected") || relayConfigured
        val resource = HakimResourceGovernor.status(context)
        val mesh = HakimCapabilityMesh.status(context)
        val providers = HakimReasoningProviderRegistry.status(context)
        val portability = HakimSovereignPortability.status(context)

        return listOf(
            Capability("quranic_kernel", true, true, "جذر الثقة القرآني من النواة إلى الحافة مدمج ومحروس"),
            Capability("quran_sunnah_method", true, true, "منهج القرآن والهدي النبوي الصحيح مدمج في القرار والتنفيذ"),
            Capability("system_of_systems", true, true, "توليد أنظمة منبثقة مؤقتة من الأنظمة الموثوقة مدمج دون تعديل كود أو توسيع سلطة"),
            Capability("capability_mesh", true, mesh.optBoolean("capability_mesh"), "شبكة ديناميكية ترتب الأدوات والخدمات حسب الجاهزية والدليل والخصوصية والسرعة والموارد والكلفة"),
            Capability("sovereign_independence", true, HakimSovereignIndependence.isCoreSovereign(context), "منع مزود/شبكة/أداة خارجية منفردة من امتلاك القلب أو إسقاطه"),
            Capability("sovereign_portability", true, portability.optBoolean("sovereign_portability"), "تصدير واستعادة النظام والبيانات غير الحساسة والثقة بإجراء صريح من المستخدم"),
            Capability("reasoning_provider_registry", true, providers.optBoolean("provider_registry"), "يفصل القلب المحلي عن مزود الاستدلال المتقدم ويصرح بحدود الجاهزية الفعلية"),
            Capability("resource_governor", true, true, "حاكم الموارد مدمج؛ الوضع الحالي=${resource.optString("mode", "UNKNOWN")}"),
            Capability("integration_fabric", true, integrationReady, if (integrationReady) "نسيج التكامل البنيوي سليم" else "فشل تكامل بنيوي؛ لا يجوز ادعاء الجاهزية"),
            Capability("secure_store", true, true, "AndroidKeyStore/AES-GCM مدمج"),
            Capability("mission_ledger", true, true, "WIP=1 وحالة مشفرة مدمجان"),
            Capability("quranic_governance", true, true, "الإطار القرآني وسياسة القرآن كله/السور ١١٤ مدمجان"),
            Capability("excellence_optimizer", true, true, "محسن التفوق الشامل مدمج"),
            Capability("in_app_reasoning", true, internetReady, if (internetReady) "الاستدلال عبر WebView حكيم الداخلي متاح؛ قد يلزم تسجيل دخول مرة واحدة داخل متصفح حكيم" else "الاستدلال الداخلي مدمج لكنه ينتظر اتصال إنترنت فعلي؛ القلب المحلي يبقى عاملًا"),
            Capability("browser", true, web != null, if (web != null) "WebView حكيم حاضر" else "المتصفح مدمج لكنه ليس حاضرًا الآن ويمكن فتحه عند الحاجة"),
            Capability("accessibility_actions", true, accessibilityReady, if (accessibilityReady) "خدمة الوصول متاحة الآن" else "خدمة الوصول غير مفعلة/غير متصلة الآن وليست شرطًا للاستدلال الداخلي"),
            Capability("chatgpt_official", true, chatGptInstalled, if (chatGptInstalled) "تطبيق ChatGPT الرسمي مثبت كمسار احتياطي اختياري" else "التطبيق الرسمي غير مثبت؛ حكيم لا يسقط بغيابه"),
            Capability("secure_relay", true, relayConfigured, if (relayConfigured) "قناة حكيم الآمنة مهيأة" else "القناة الآمنة غير مهيأة الآن"),
            Capability("connection_resilience", true, connectionReady, if (connectionReady) "وصلة خارجية متاحة أو مهيأة" else "نسيج التعافي موجود لكن الوصلة الخارجية غير جاهزة الآن"),
            Capability("profile_vault", true, true, "خزنة البيانات غير الحساسة مدمجة مع ثقة موقع دقيقة"),
            Capability("field_update", true, false, "جاهزية التحديث الميداني تُثبت من APK موقّع مطابق واختبار الهاتف، لا من وجود الكود وحده")
        )
    }

    fun isReady(context: Context, id: String): Boolean = discover(context).firstOrNull { it.id == id }?.readyNow == true

    fun promptContext(context: Context): String = buildString {
        appendLine("[معرفة حكيم الذاتية بالقدرات والتكامل]")
        discover(context).forEach { c ->
            appendLine("• ${c.id}: ${if (c.readyNow) "جاهزة الآن" else if (c.available) "موجودة لكن غير جاهزة الآن" else "غير متاحة"} — ${c.reason}")
        }
        appendLine("لا تدّع قدرة غير جاهزة، ولا تدّع وصلة غير جاهزة، ولا تحوّل وجود مكوّن برمجي إلى ادعاء نجاح ميداني. غيّر المسار تلقائيًا عند غياب قدرة خارجية، ما دام البديل مشروعًا وآمنًا ومتاحًا.")
        appendLine("الاستقلال يعني منع الارتهان، لا ادعاء اختفاء الاعتماد الواقعي على Android أو الشبكة أو مزود الاستدلال المتقدم عند الحاجة.")
        appendLine("الاستدلال داخل حكيم هو المسار المتقدم الافتراضي عندما تتوفر الشبكة؛ القلب والتنفيذ المحليان يبقيان عاملين بدونه، وAccessibility وتطبيق ChatGPT الرسمي مسارات اختيارية.")
        appendLine("شبكة التفوق ترتب الأدوات بدل تشغيلها كلها؛ المحلي/المجاني/المهيأ أولًا، والخدمات المدفوعة أو الصلاحيات الجديدة لا تُفعل تلقائيًا.")
        appendLine("نظام الأنظمة لا يعني تفعيل كل شيء دائمًا: فعّل أقل تركيب يحقق الغاية، وحاكم الموارد يحدد توقيت وكثافة الخلفية دون خفض جودة القرار.")
    }.take(8000)

    fun status(context: Context): JSONObject = JSONObject()
        .put("self_capability_awareness", true)
        .put("integration_aware", true)
        .put("system_of_systems_aware", true)
        .put("capability_mesh_aware", true)
        .put("sovereign_independence_aware", true)
        .put("portability_aware", true)
        .put("provider_registry_aware", true)
        .put("resource_aware", true)
        .put("in_app_reasoning_aware", true)
        .put("reasoning_provider_registry", HakimReasoningProviderRegistry.status(context))
        .put("sovereign_portability", HakimSovereignPortability.status(context))
        .put("capability_mesh", HakimCapabilityMesh.status(context))
        .put("capabilities", JSONArray(discover(context).map { it.toJson() }))

    private fun hasInternetCapability(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}
