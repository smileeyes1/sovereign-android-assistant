package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * الاستقلال السيادي الشامل: استقلال القرار والبيانات والهوية والتنفيذ والتعافي عن أي خدمة خارجية منفردة.
 * لا يعني الاستقلال إنكار اعتماد التطبيق على Android أو ادعاء نموذج متقدم مكافئ بلا شبكة/مزود.
 */
object HakimSovereignIndependence {
    const val VERSION = "HAKIM-SOVEREIGN-INDEPENDENCE-2026-09-15-v3"

    enum class Domain { IDENTITY, GOVERNANCE, DATA, QURAN_SOURCE, REASONING, EXECUTION, NETWORK, UPDATE, RECOVERY, RESOURCES, PORTABILITY }

    data class DomainState(
        val domain: Domain,
        val sovereign: Boolean,
        val readyNow: Boolean,
        val externalDependency: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("domain", domain.name)
            .put("sovereign", sovereign)
            .put("ready_now", readyNow)
            .put("external_dependency", externalDependency)
            .put("reason", reason)
    }

    fun assess(context: Context): List<DomainState> {
        HakimQuranicInvariantKernel.requireInherited("sovereign_independence")
        val app = context.applicationContext
        val governance = HakimConstitution.status(app)
        val provider = HakimReasoningProviderRegistry.status(app)
        val resource = HakimResourceGovernor.status(app)
        val recovery = HakimConnectionResilience.status(app)
        val mesh = HakimCapabilityMesh.discover(app).associateBy { it.id }
        val portability = HakimSovereignPortability.status(app)
        val quran = HakimVerifiedQuranCorpus.status(app)
        val localReasoning = HakimLocalReasoningBridge.status(app)

        return listOf(
            DomainState(
                Domain.IDENTITY,
                sovereign = app.packageName == "ps.hakim.stable",
                readyNow = app.packageName == "ps.hakim.stable",
                externalDependency = true,
                reason = "هوية تطبيق واحدة وسلسلة توقيع الإصدار محكومة؛ إثبات التثبيت الميداني حالة مستقلة ولا يُفترض من الكود"
            ),
            DomainState(
                Domain.GOVERNANCE,
                sovereign = governance.optBoolean("quranic_normative_default") && governance.optBoolean("fail_closed_core_changes"),
                readyNow = true,
                externalDependency = false,
                reason = "القلب الحاكم محلي ولا يأتي من مزود ذكاء أو صفحة ويب"
            ),
            DomainState(
                Domain.DATA,
                sovereign = true,
                readyNow = true,
                externalDependency = false,
                reason = "المخزن المشفر وخزنة البيانات محليان؛ الأسرار الخام ممنوعة من الخزنة"
            ),
            DomainState(
                Domain.QURAN_SOURCE,
                sovereign = quran.optBoolean("verified_local_quran_corpus") &&
                    quran.optBoolean("exact_text_fails_closed_without_verified_source") &&
                    quran.optBoolean("restore_reuses_full_official_verification"),
                readyNow = quran.optBoolean("ready") && quran.optBoolean("offline_reimport_source_available"),
                externalDependency = !quran.optBoolean("offline_reimport_source_available"),
                reason = when {
                    quran.optBoolean("ready") && quran.optBoolean("offline_reimport_source_available") ->
                        "نص السور الـ١١٤ متحقق محليًا والمصدر الرسمي الأصلي محفوظ وقابل للتصدير والاستعادة دون شبكة مع إعادة فحص البصمة"
                    quran.optBoolean("ready") ->
                        "النص القرآني متحقق محليًا، لكن نسخة المصدر الرسمية القابلة للاستعادة دون شبكة غير متاحة بعد"
                    else ->
                        "الحاكمية القرآنية فعالة، لكن النص الدقيق المحلي لم يُعتمد بعد من مصدر رسمي مطابق للبصمة"
                }
            ),
            DomainState(
                Domain.REASONING,
                sovereign = provider.optBoolean("core_runtime_vendor_independent") &&
                    localReasoning.optBoolean("loopback_only") &&
                    localReasoning.optBoolean("external_network_forbidden"),
                readyNow = provider.optBoolean("advanced_reasoning_ready_now"),
                externalDependency = !localReasoning.optBoolean("ready_now"),
                reason = when {
                    localReasoning.optBoolean("ready_now") ->
                        "الاستدلال المتقدم يعمل محليًا داخل الهاتف فقط؛ المزودات الخارجية أصبحت بدائل اختيارية"
                    provider.optBoolean("advanced_reasoning_ready_now") ->
                        "القلب مستقل والاستدلال المتقدم متاح خارجيًا كبديل مؤقت؛ النموذج المحلي غير جاهز بعد"
                    else ->
                        "القلب الحتمي المحلي مستمر، لكن نموذجًا لغويًا متقدمًا محليًا أو خارجيًا غير جاهز الآن"
                }
            ),
            DomainState(
                Domain.EXECUTION,
                sovereign = true,
                readyNow = true,
                externalDependency = false,
                reason = "التنفيذ المحلي والقواعد والسلطة والتحقق داخل حكيم؛ الأدوات الخارجية بدائل اختيارية"
            ),
            DomainState(
                Domain.NETWORK,
                sovereign = true,
                readyNow = mesh["validated_network"]?.readyNow == true,
                externalDependency = true,
                reason = "فقد الشبكة يغيّر المسار إلى محلي/cache/queue ثم استئناف؛ لا يسقط القلب، ولا تُستخدم إلا قنوات مأذونة"
            ),
            DomainState(
                Domain.UPDATE,
                sovereign = true,
                readyNow = mesh["trusted_updater"]?.available == true,
                externalDependency = true,
                reason = "التحديث لا يُقبل بلا تحقق هوية/توقيع؛ ويمكن تسليم APK موقع محليًا دون متجر إلزامي"
            ),
            DomainState(
                Domain.RECOVERY,
                sovereign = true,
                readyNow = recovery.optBoolean("integration_aware", false),
                externalDependency = false,
                reason = "سجل المهمة والتعافي وإعادة التخطيط محلية، وفشل أداة لا يوسع السلطة"
            ),
            DomainState(
                Domain.RESOURCES,
                sovereign = resource.optBoolean("resource_governor"),
                readyNow = resource.optBoolean("resource_governor"),
                externalDependency = false,
                reason = "حاكم الموارد يحمي البطارية والحرارة والذاكرة والبيانات؛ وسياسة السيادة تفضّل المجاني/المملوك المشروع دون سرقة خدمة أو ادعاء طاقة من العدم"
            ),
            DomainState(
                Domain.PORTABILITY,
                sovereign = portability.optBoolean("sovereign_portability"),
                readyNow = portability.optBoolean("sovereign_portability"),
                externalDependency = false,
                reason = "النظام الحاكم والقواعد والتعلم والبيانات والثقة قابلة للتصدير والاستعادة، ومصدر القرآن الموثق له مسار مستقل حتى لا يُضغط داخل نسخة الإعدادات"
            )
        )
    }

    fun isCoreSovereign(context: Context): Boolean = assess(context).all { it.sovereign }

    fun promptContext(context: Context): String = buildString {
        appendLine("[الاستقلال السيادي الشامل]")
        appendLine("الاستقلال يعني أن فقد مزود/شبكة/أداة لا يملك القلب ولا يغير القرآن/السنة/الدستور/السلطة ولا يمحو بيانات المستخدم أو المهمة.")
        assess(context).forEach { d ->
            appendLine("• ${d.domain}: سيادي=${d.sovereign}، جاهز الآن=${d.readyNow} — ${d.reason}")
        }
        appendLine("الأولوية: محلي ومملوك للمستخدم أولًا → جلسة موجودة/مجانية مشروعة عند الحاجة → بديل موثوق → انتظار آمن واستئناف. لا اشتراك أو صلاحية أو كشف بيانات لمجرد زيادة الاستقلال.")
        appendLine("لا تدّع استقلالًا مطلقًا: حكيم يعتمد على Android والهاتف نفسه، والاستدلال المتقدم قد يحتاج مزودًا خارجيًا. المطلوب منع الارتهان ونقطة الفشل الواحدة، لا إنكار الواقع التقني.")
        append(HakimResourceSovereigntyPolicy.promptContext())
        append(HakimReasoningProviderRegistry.promptContext(context))
    }.take(18000)

    fun status(context: Context): JSONObject {
        val states = assess(context)
        return JSONObject()
            .put("version", VERSION)
            .put("sovereign_independence", true)
            .put("core_sovereign", states.all { it.sovereign })
            .put("single_external_point_of_failure_forbidden", true)
            .put("local_first", true)
            .put("portable_user_state", true)
            .put("offline_graceful_degradation", true)
            .put("external_services_are_replaceable_capabilities", true)
            .put("android_platform_dependency_acknowledged", true)
            .put("advanced_model_equivalence_offline_not_claimed", true)
            .put("resource_sovereignty", HakimResourceSovereigntyPolicy.status())
            .put("domains", JSONArray(states.map { it.toJson() }))
            .put("provider_registry", HakimReasoningProviderRegistry.status(context))
            .put("local_reasoning", HakimLocalReasoningBridge.status(context))
            .put("portability", HakimSovereignPortability.status(context))
            .put("verified_quran_corpus", HakimVerifiedQuranCorpus.status(context))
    }
}
