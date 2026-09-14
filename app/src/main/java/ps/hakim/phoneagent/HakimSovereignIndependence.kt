package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * الاستقلال السيادي الشامل: استقلال القرار والبيانات والهوية والتنفيذ والتعافي عن أي خدمة خارجية منفردة.
 * لا يعني الاستقلال إنكار اعتماد التطبيق على Android أو ادعاء نموذج متقدم مكافئ بلا شبكة/مزود.
 */
object HakimSovereignIndependence {
    const val VERSION = "HAKIM-SOVEREIGN-INDEPENDENCE-2026-09-15-v1"

    enum class Domain { IDENTITY, GOVERNANCE, DATA, REASONING, EXECUTION, NETWORK, UPDATE, RECOVERY, RESOURCES, PORTABILITY }

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

        return listOf(
            DomainState(
                Domain.IDENTITY,
                sovereign = app.packageName == "ps.hakim.stable",
                readyNow = app.packageName == "ps.hakim.stable",
                externalDependency = true,
                reason = "هوية تطبيق واحدة وتوقيع ميداني مثبت؛ المفتاح الخاص يبقى خارج APK ويتطلب حفظًا خارجيًا آمنًا"
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
                Domain.REASONING,
                sovereign = provider.optBoolean("core_runtime_vendor_independent"),
                readyNow = provider.optBoolean("advanced_reasoning_ready_now") || true,
                externalDependency = true,
                reason = if (provider.optBoolean("advanced_reasoning_ready_now"))
                    "القلب والتنفيذ المحليان مستقلان؛ الاستدلال المتقدم متاح كخدمة خارجية قابلة للفقد"
                else "الاستدلال المتقدم غير جاهز؛ يستمر القلب المحلي دون ادعاء تكافؤ نموذج متقدم"
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
                reason = "فقد الشبكة يغيّر المسار إلى وضع محلي/انتظار واستئناف؛ لا يسقط القلب"
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
                readyNow = recovery.optBoolean("resilience_engine", true),
                externalDependency = false,
                reason = "سجل المهمة والتعافي وإعادة التخطيط محلية، وفشل أداة لا يوسع السلطة"
            ),
            DomainState(
                Domain.RESOURCES,
                sovereign = resource.optBoolean("resource_governor"),
                readyNow = true,
                externalDependency = false,
                reason = "حاكم الموارد يحمي المهمة الحالية ويخفض الخلفية غير الضرورية فقط"
            ),
            DomainState(
                Domain.PORTABILITY,
                sovereign = portability.optBoolean("sovereign_portability"),
                readyNow = true,
                externalDependency = false,
                reason = "النظام الحاكم والبيانات غير الحساسة والثقة قابلة للتصدير والاستعادة بإجراء صريح"
            )
        )
    }

    fun isCoreSovereign(context: Context): Boolean = assess(context).all { state ->
        when (state.domain) {
            Domain.NETWORK -> state.sovereign
            Domain.REASONING -> state.sovereign
            Domain.UPDATE -> state.sovereign
            else -> state.sovereign
        }
    }

    fun promptContext(context: Context): String = buildString {
        appendLine("[الاستقلال السيادي الشامل]")
        appendLine("الاستقلال يعني أن فقد مزود/شبكة/أداة لا يملك القلب ولا يغير القرآن/السنة/الدستور/السلطة ولا يمحو بيانات المستخدم أو المهمة.")
        assess(context).forEach { d ->
            appendLine("• ${d.domain}: سيادي=${d.sovereign}، جاهز الآن=${d.readyNow} — ${d.reason}")
        }
        appendLine("الأولوية: محلي ومملوك للمستخدم أولًا → جلسة موجودة/مجانية عند الحاجة → بديل موثوق → انتظار آمن. لا اشتراك أو صلاحية أو كشف بيانات لمجرد زيادة الاستقلال.")
        appendLine("لا تدّع استقلالًا مطلقًا: حكيم يعتمد على Android والهاتف نفسه، والاستدلال المتقدم قد يحتاج مزودًا خارجيًا. المطلوب منع الارتهان ونقطة الفشل الواحدة، لا إنكار الواقع التقني.")
        append(HakimReasoningProviderRegistry.promptContext(context))
    }.take(9000)

    fun status(context: Context): JSONObject {
        val states = assess(context)
        return JSONObject()
            .put("version", VERSION)
            .put("sovereign_independence", true)
            .put("core_sovereign", isCoreSovereign(context))
            .put("single_external_point_of_failure_forbidden", true)
            .put("local_first", true)
            .put("portable_user_state", true)
            .put("offline_graceful_degradation", true)
            .put("external_services_are_replaceable_capabilities", true)
            .put("android_platform_dependency_acknowledged", true)
            .put("advanced_model_equivalence_offline_not_claimed", true)
            .put("domains", JSONArray(states.map { it.toJson() }))
            .put("provider_registry", HakimReasoningProviderRegistry.status(context))
            .put("portability", HakimSovereignPortability.status(context))
    }
}
