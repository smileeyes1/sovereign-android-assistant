package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسيج التكامل والوصل لحكيم.
 * يوحد القلب الحاكم والقرآن كله ومنهج القرآن والهدي النبوي والإنسان أولًا ونظام الأنظمة وحاكم الموارد
 * والقدرات والاتصال والتنفيذ والتحقق والتعلم والمبادرة والتعافي دون خلط سلامة البنية بجاهزية الشبكة/الهاتف اللحظية.
 */
object HakimIntegrationFabric {
    const val VERSION = "SOVEREIGN-INTEGRATION-FABRIC-2026-09-14-v8"

    private val structuralNodes = listOf(
        "quranic_kernel",
        "quranic_corpus_114",
        "quran_sunnah_method",
        "human_first_policy",
        "resource_governor",
        "system_of_systems",
        "constitution",
        "intent_context",
        "decision_matrix",
        "sovereign_engine",
        "self_leadership",
        "proactive_engine",
        "agent_system",
        "mission_ledger",
        "authority_envelope",
        "capability_registry",
        "autonomous_executor",
        "reasoning_executor",
        "verification",
        "learning",
        "adaptive_learning",
        "connection_resilience",
        "unified_relay",
        "self_check"
    )

    private val structuralEdges = listOf(
        "quranic_kernel→quranic_corpus_114",
        "quranic_kernel→quran_sunnah_method",
        "quranic_corpus_114→quran_sunnah_method",
        "quran_sunnah_method→constitution",
        "quran_sunnah_method→system_of_systems",
        "quran_sunnah_method→sovereign_engine",
        "quranic_corpus_114→constitution",
        "quranic_kernel→constitution",
        "human_first_policy→intent_context",
        "human_first_policy→authority_envelope",
        "human_first_policy→system_of_systems",
        "human_first_policy→sovereign_engine",
        "resource_governor→system_of_systems",
        "resource_governor→proactive_engine",
        "resource_governor→self_check",
        "resource_governor→connection_resilience",
        "resource_governor→runtime_scheduling",
        "constitution→intent_context",
        "intent_context→decision_matrix",
        "intent_context→system_of_systems",
        "decision_matrix→sovereign_engine",
        "system_of_systems→sovereign_engine",
        "system_of_systems→agent_system",
        "sovereign_engine→self_leadership",
        "self_leadership→proactive_engine",
        "proactive_engine→authority_envelope",
        "proactive_engine→mission_ledger",
        "proactive_engine→connection_resilience",
        "proactive_engine→adaptive_learning",
        "sovereign_engine→agent_system",
        "agent_system→autonomous_executor",
        "agent_system→reasoning_executor",
        "mission_ledger↔sovereign_engine",
        "authority_envelope→system_of_systems",
        "authority_envelope→autonomous_executor",
        "authority_envelope→reasoning_executor",
        "capability_registry→self_leadership",
        "execution→verification→learning",
        "learning→adaptive_learning→safe_execution_order",
        "adaptive_learning→baseline_fallback",
        "connection_resilience↔unified_relay",
        "self_check→integration_fabric",
        "self_check→adaptive_learning",
        "recovery→mission_ledger→replan"
    )

    fun install(context: Context) {
        val report = requireCore(context, "integration_install")
        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE).edit()
            .putString("integration_fabric_version", VERSION)
            .putString("integration_fabric_last", report.toString().take(22000))
            .putLong("integration_fabric_checked_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * Fail-closed على القلب البنيوي فقط. ضغط الموارد أو فقد وصلة خارجية يغيّر المسار ولا يخفض الحاكمية أو جودة القرار.
     */
    fun requireCore(context: Context, scope: String): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("integration:$scope")
        check(context.packageName == "ps.hakim.stable") { "نسيج التكامل يعمل فقط داخل هوية حكيم الأصلية" }
        val governance = HakimConstitution.status(context)
        val corpus = HakimQuranicCorpusPolicy.status()
        val method = HakimQuranSunnahMethod.status()
        val human = HakimHumanFirstPolicy.status()
        val resources = HakimResourceGovernor.status(context)
        val systems = HakimSystemOfSystems.status(context)
        check(governance.optBoolean("quranic_normative_default")) { "الدستور القرآني الحاكم غير مثبت" }
        check(corpus.optBoolean("all_114_surahs_covered")) { "تغطية القرآن كله/السور الـ١١٤ غير مثبتة" }
        check(corpus.optBoolean("revelation_distinct_from_tafsir_and_inference")) { "الفصل بين الوحي والتفسير/الاستنباط غير مثبت" }
        check(method.optBoolean("quran_is_highest_normative_source")) { "القرآن ليس مثبتًا كمصدر معياري أعلى" }
        check(method.optBoolean("authentic_sunnah_is_authoritative_explanation_and_guidance")) { "الهدي النبوي الصحيح غير مثبت في القلب" }
        check(method.optBoolean("worldly_facts_and_means_require_domain_evidence")) { "الفصل بين الوحي والدليل الدنيوي غير مثبت" }
        check(human.optBoolean("human_first")) { "سياسة الإنسان أولًا غير مثبتة" }
        check(human.optBoolean("dignity_is_hard_constraint")) { "كرامة المستخدم ليست قيدًا حاكمًا" }
        check(human.optBoolean("silence_is_not_consent")) { "السكوت قد يفسر كموافقة" }
        check(resources.optBoolean("resource_governor")) { "حاكم موارد الهاتف غير مثبت" }
        check(resources.optBoolean("quality_and_governance_never_downgraded")) { "توفير الموارد قد يخفض جودة القرار" }
        check(resources.optBoolean("only_nonessential_background_is_throttled")) { "حاكم الموارد قد يخفض العمل الجوهري" }
        check(systems.optBoolean("system_of_systems")) { "نظام الأنظمة غير مثبت" }
        check(systems.optBoolean("derived_systems_inherit_quran_sunnah")) { "الأنظمة المنبثقة لا ترث القرآن والهدي النبوي" }
        check(systems.optBoolean("derived_systems_cannot_expand_authority")) { "نظام منبثق قد يوسع السلطة" }
        check(systems.optBoolean("derived_systems_cannot_mutate_code")) { "نظام منبثق قد يغير الكود ذاتيًا" }
        check(governance.optBoolean("fail_closed_core_changes")) { "حماية تغييرات القلب غير مفعلة" }
        return structuralStatus(context, scope)
    }

    fun structuralStatus(context: Context, scope: String = "status"): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("integration_status:$scope")
        val governance = HakimConstitution.status(context)
        val corpus = HakimQuranicCorpusPolicy.status()
        val method = HakimQuranSunnahMethod.status()
        val human = HakimHumanFirstPolicy.status()
        val resources = HakimResourceGovernor.status(context)
        val systems = HakimSystemOfSystems.status(context)
        val adaptive = HakimAdaptiveLearning.status(context)
        val proactive = HakimProactiveEngine.status(context)
        val packageOk = context.packageName == "ps.hakim.stable"
        val quranicOk = governance.optBoolean("quranic_normative_default")
        val corpusOk = corpus.optBoolean("all_114_surahs_covered") &&
            corpus.optBoolean("revelation_distinct_from_tafsir_and_inference")
        val methodOk = method.optBoolean("quran_is_highest_normative_source") &&
            method.optBoolean("authentic_sunnah_is_authoritative_explanation_and_guidance") &&
            method.optBoolean("prophetic_example_applies_to_method_and_conduct") &&
            method.optBoolean("exact_attribution_requires_verification") &&
            method.optBoolean("worldly_facts_and_means_require_domain_evidence") &&
            method.optBoolean("no_religious_technical_mystification")
        val humanOk = human.optBoolean("human_first") &&
            human.optBoolean("dignity_is_hard_constraint") &&
            human.optBoolean("zero_technical_burden_default") &&
            human.optBoolean("kindness_must_not_be_exploited") &&
            human.optBoolean("silence_is_not_consent") &&
            human.optBoolean("preserve_user_agency")
        val resourceOk = resources.optBoolean("resource_governor") &&
            resources.optBoolean("quality_and_governance_never_downgraded") &&
            resources.optBoolean("only_nonessential_background_is_throttled") &&
            resources.optBoolean("no_large_on_device_model_required")
        val systemsOk = systems.optBoolean("system_of_systems") &&
            systems.optBoolean("derived_systems") &&
            systems.optBoolean("derived_systems_are_ephemeral_orchestration") &&
            systems.optBoolean("derived_systems_inherit_quran_sunnah") &&
            systems.optBoolean("derived_systems_inherit_human_first") &&
            systems.optBoolean("derived_systems_inherit_authority_envelope") &&
            systems.optBoolean("derived_systems_inherit_resource_governor") &&
            systems.optBoolean("derived_systems_cannot_expand_authority") &&
            systems.optBoolean("derived_systems_cannot_mutate_code")
        val adaptiveOk = adaptive.optBoolean("adaptive_learning") &&
            adaptive.optBoolean("local_only") &&
            !adaptive.optBoolean("can_expand_authority") &&
            adaptive.optBoolean("safe_candidates_only") &&
            adaptive.optBoolean("baseline_fallback")
        val proactiveOk = proactive.optBoolean("proactive_engine") &&
            proactive.optBoolean("beneficial_safe_actions_auto") &&
            proactive.optBoolean("high_impact_never_silently_authorized") &&
            proactive.optBoolean("silence_not_consent") &&
            proactive.optBoolean("no_secret_or_permission_escalation")
        val failClosed = governance.optBoolean("fail_closed_core_changes")
        val ok = packageOk && quranicOk && corpusOk && methodOk && humanOk && resourceOk && systemsOk && adaptiveOk && proactiveOk && failClosed
        return JSONObject()
            .put("version", VERSION)
            .put("scope", scope.take(120))
            .put("structural_integrity", ok)
            .put("single_app_identity", packageOk)
            .put("quranic_root_inherited", quranicOk)
            .put("quranic_corpus_114_integrated", corpusOk)
            .put("quran_sunnah_method_integrated", methodOk)
            .put("human_first_integrated", humanOk)
            .put("resource_governor_integrated", resourceOk)
            .put("system_of_systems_integrated", systemsOk)
            .put("adaptive_learning_integrated", adaptiveOk)
            .put("proactive_engine_integrated", proactiveOk)
            .put("quranic_corpus", corpus)
            .put("quran_sunnah_method", method)
            .put("human_first", human)
            .put("resource_governor", resources)
            .put("system_of_systems", systems)
            .put("adaptive_learning", adaptive)
            .put("proactive", proactive)
            .put("fail_closed_core_changes", failClosed)
            .put("nodes", JSONArray(structuralNodes))
            .put("edges", JSONArray(structuralEdges))
            .put("no_isolated_critical_layer", true)
    }

    fun runtimeStatus(context: Context): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("integration_runtime")
        val webReady = HakimRuntime.visibleWebView() != null
        val accessibilityReady = HakimAccessibilityService.instance != null
        val chatGptInstalled = runCatching {
            context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt") != null
        }.getOrDefault(false)
        val recovery = HakimConnectionResilience.status(context)
        val activeMission = HakimMissionLedger.active(context)
        val resources = HakimResourceGovernor.status(context)

        return JSONObject()
            .put("structural", structuralStatus(context, "runtime"))
            .put("runtime_readiness_is_not_structural_integrity", true)
            .put("browser_ready_now", webReady)
            .put("accessibility_ready_now", accessibilityReady)
            .put("chatgpt_official_installed", chatGptInstalled)
            .put("secure_relay_configured", HakimUnifiedRelay.isConfigured(context))
            .put("connection_recovery", recovery)
            .put("resource_governor", resources)
            .put("adaptive_learning", HakimAdaptiveLearning.status(context))
            .put("proactive", HakimProactiveEngine.status(context))
            .put("mission_active", activeMission != null)
            .put("mission_phase", activeMission?.phase?.name ?: "IDLE")
            .put("missing_external_link_changes_route_not_governance", true)
    }

    fun promptContext(context: Context): String {
        val s = runtimeStatus(context)
        val adaptive = s.optJSONObject("adaptive_learning") ?: JSONObject()
        val proactive = s.optJSONObject("proactive") ?: JSONObject()
        val resources = s.optJSONObject("resource_governor") ?: JSONObject()
        return buildString {
            appendLine("[نسيج التكامل والوصل السيادي]")
            appendLine("القلب البنيوي=${if (s.optJSONObject("structural")?.optBoolean("structural_integrity") == true) "سليم" else "غير سليم"}؛ اتصال الشبكة/المتصفح/الهاتف جاهزية لحظية وليست بديلًا عن سلامة القلب.")
            appendLine("القرآن كله/السور الـ١١٤ جزء من القلب الحاكم، ومعه منهج القرآن والهدي النبوي الصحيح: القرآن مصدر الهداية والقيم والحدود الشرعية، والسنة الصحيحة بيان وهدي وقدوة؛ مع فصل الوحي عن التفسير والفقه والسيرة والاجتهاد والدليل التجريبي.")
            appendLine("الإنسان أولًا جزء من القلب: كرامة المستخدم، أقل عبء تقني، عدم استغلال الطيبة/الرحمة، وعدم اعتبار السكوت موافقة، وحفظ سيادته وقراره الجوهري.")
            appendLine("نظام الأنظمة جزء من القلب: لكل مهمة يُولد تركيب مؤقت من أقل الأنظمة اللازمة، وكل نظام منبثق يرث الحاكمية والسلطة والتحقق والموارد ولا يغير الكود ذاتيًا.")
            appendLine("حاكم الموارد جزء من القلب: الوضع=${resources.optString("mode", "UNKNOWN")}؛ يحمي سرعة المهمة الحالية ويؤجل الخلفية غير الضرورية دون خفض جودة القرار أو الحاكمية.")
            appendLine("التعلم التكيفي جزء من القلب: محلي، لا يوسع السلطة، لا يغير الكود تلقائيًا، ويعيد ترتيب البدائل الآمنة فقط مع رجوع إلى خط الأساس عند الانحدار.")
            appendLine("المبادرة الذاتية جزء من القلب: تنفذ تلقائيًا كل مكسب آمن منخفض الأثر داخل السلطة، ولا تعتبر الصمت تفويضًا للأثر العالي.")
            appendLine("حالة التكيف=${adaptive.optString("last_adaptation_decision", "COLLECTING_EVIDENCE")}؛ المبادرة=${proactive.optBoolean("enabled")}.")
            appendLine("لا توجد طبقة حرجة معزولة: المقصد ونظام الأنظمة والقرار والوكلاء والتنفيذ والتحقق والتعلم والمبادرة والتعافي والاتصال والتحديث تعود إلى القلب الحاكم وسجل المهمة.")
            appendLine("عند فقد وصلة خارجية: غيّر المسار أو استعد الاتصال إذا كان ذلك آمنًا ومسموحًا؛ لا توسع السلطة ولا تدّع أن الوصلة جاهزة.")
            appendLine("المتصفح=${s.optBoolean("browser_ready_now")}، الوصول=${s.optBoolean("accessibility_ready_now")}، ChatGPT=${s.optBoolean("chatgpt_official_installed")}، القناة الآمنة=${s.optBoolean("secure_relay_configured")}.")
        }.take(7600)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("integration_fabric", true)
        .put("version", VERSION)
        .put("structural", structuralStatus(context))
        .put("runtime", runtimeStatus(context))
}
