package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسيج التكامل والوصل لحكيم.
 * يوحد القلب الحاكم والقرآن كله والإنسان أولًا والقدرات والاتصال والتنفيذ والتحقق والتعلم والمبادرة والتعافي دون خلط
 * سلامة البنية بجاهزية الشبكة/الهاتف اللحظية.
 */
object HakimIntegrationFabric {
    const val VERSION = "SOVEREIGN-INTEGRATION-FABRIC-2026-09-14-v5"

    private val structuralNodes = listOf(
        "quranic_kernel",
        "quranic_corpus_114",
        "human_first_policy",
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
        "quranic_corpus_114→constitution",
        "quranic_kernel→constitution",
        "human_first_policy→intent_context",
        "human_first_policy→authority_envelope",
        "human_first_policy→sovereign_engine",
        "constitution→intent_context",
        "intent_context→decision_matrix",
        "decision_matrix→sovereign_engine",
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
            .putString("integration_fabric_last", report.toString().take(18000))
            .putLong("integration_fabric_checked_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * Fail-closed على القلب البنيوي فقط. الجاهزية الخارجية لا تمنع مهام لا تحتاجها.
     */
    fun requireCore(context: Context, scope: String): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("integration:$scope")
        check(context.packageName == "ps.hakim.stable") { "نسيج التكامل يعمل فقط داخل هوية حكيم الأصلية" }
        val governance = HakimConstitution.status(context)
        val corpus = HakimQuranicCorpusPolicy.status()
        val human = HakimHumanFirstPolicy.status()
        check(governance.optBoolean("quranic_normative_default")) { "الدستور القرآني الحاكم غير مثبت" }
        check(corpus.optBoolean("all_114_surahs_covered")) { "تغطية القرآن كله/السور الـ١١٤ غير مثبتة" }
        check(corpus.optBoolean("revelation_distinct_from_tafsir_and_inference")) { "الفصل بين الوحي والتفسير/الاستنباط غير مثبت" }
        check(human.optBoolean("human_first")) { "سياسة الإنسان أولًا غير مثبتة" }
        check(human.optBoolean("dignity_is_hard_constraint")) { "كرامة المستخدم ليست قيدًا حاكمًا" }
        check(human.optBoolean("silence_is_not_consent")) { "السكوت قد يفسر كموافقة" }
        check(governance.optBoolean("fail_closed_core_changes")) { "حماية تغييرات القلب غير مفعلة" }
        return structuralStatus(context, scope)
    }

    fun structuralStatus(context: Context, scope: String = "status"): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("integration_status:$scope")
        val governance = HakimConstitution.status(context)
        val corpus = HakimQuranicCorpusPolicy.status()
        val human = HakimHumanFirstPolicy.status()
        val adaptive = HakimAdaptiveLearning.status(context)
        val proactive = HakimProactiveEngine.status(context)
        val packageOk = context.packageName == "ps.hakim.stable"
        val quranicOk = governance.optBoolean("quranic_normative_default")
        val corpusOk = corpus.optBoolean("all_114_surahs_covered") &&
            corpus.optBoolean("revelation_distinct_from_tafsir_and_inference")
        val humanOk = human.optBoolean("human_first") &&
            human.optBoolean("dignity_is_hard_constraint") &&
            human.optBoolean("zero_technical_burden_default") &&
            human.optBoolean("kindness_must_not_be_exploited") &&
            human.optBoolean("silence_is_not_consent") &&
            human.optBoolean("preserve_user_agency")
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
        val ok = packageOk && quranicOk && corpusOk && humanOk && adaptiveOk && proactiveOk && failClosed
        return JSONObject()
            .put("version", VERSION)
            .put("scope", scope.take(120))
            .put("structural_integrity", ok)
            .put("single_app_identity", packageOk)
            .put("quranic_root_inherited", quranicOk)
            .put("quranic_corpus_114_integrated", corpusOk)
            .put("human_first_integrated", humanOk)
            .put("adaptive_learning_integrated", adaptiveOk)
            .put("proactive_engine_integrated", proactiveOk)
            .put("quranic_corpus", corpus)
            .put("human_first", human)
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

        return JSONObject()
            .put("structural", structuralStatus(context, "runtime"))
            .put("runtime_readiness_is_not_structural_integrity", true)
            .put("browser_ready_now", webReady)
            .put("accessibility_ready_now", accessibilityReady)
            .put("chatgpt_official_installed", chatGptInstalled)
            .put("secure_relay_configured", HakimUnifiedRelay.isConfigured(context))
            .put("connection_recovery", recovery)
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
        return buildString {
            appendLine("[نسيج التكامل والوصل السيادي]")
            appendLine("القلب البنيوي=${if (s.optJSONObject("structural")?.optBoolean("structural_integrity") == true) "سليم" else "غير سليم"}؛ اتصال الشبكة/المتصفح/الهاتف جاهزية لحظية وليست بديلًا عن سلامة القلب.")
            appendLine("القرآن كله/السور الـ١١٤ جزء من القلب الحاكم، مع فصل النص عن التفسير والقراءات وأسباب النزول والاستنباط، ومنع الانتقائية والتكلف.")
            appendLine("الإنسان أولًا جزء من القلب: كرامة المستخدم، أقل عبء تقني، عدم استغلال الطيبة/الرحمة، وعدم اعتبار السكوت موافقة، وحفظ سيادته وقراره الجوهري.")
            appendLine("التعلم التكيفي جزء من القلب: محلي، لا يوسع السلطة، لا يغير الكود تلقائيًا، ويعيد ترتيب البدائل الآمنة فقط مع رجوع إلى خط الأساس عند الانحدار.")
            appendLine("المبادرة الذاتية جزء من القلب: تنفذ تلقائيًا كل مكسب آمن منخفض الأثر داخل السلطة، ولا تعتبر الصمت تفويضًا للأثر العالي.")
            appendLine("حالة التكيف=${adaptive.optString("last_adaptation_decision", "COLLECTING_EVIDENCE")}؛ المبادرة=${proactive.optBoolean("enabled")}.")
            appendLine("لا توجد طبقة حرجة معزولة: المقصد والقرار والوكلاء والتنفيذ والتحقق والتعلم والمبادرة والتعافي والاتصال والتحديث تعود إلى القلب الحاكم وسجل المهمة.")
            appendLine("عند فقد وصلة خارجية: غيّر المسار أو استعد الاتصال إذا كان ذلك آمنًا ومسموحًا؛ لا توسع السلطة ولا تدّع أن الوصلة جاهزة.")
            appendLine("المتصفح=${s.optBoolean("browser_ready_now")}، الوصول=${s.optBoolean("accessibility_ready_now")}، ChatGPT=${s.optBoolean("chatgpt_official_installed")}، القناة الآمنة=${s.optBoolean("secure_relay_configured")}.")
        }.take(5600)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("integration_fabric", true)
        .put("version", VERSION)
        .put("structural", structuralStatus(context))
        .put("runtime", runtimeStatus(context))
}
