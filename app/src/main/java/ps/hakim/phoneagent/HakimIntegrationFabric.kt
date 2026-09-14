package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * نسيج التكامل والوصل لحكيم.
 * يوحد القلب الحاكم والقدرات والاتصال والتنفيذ والتحقق والتعلم والتعافي دون خلط
 * سلامة البنية بجاهزية الشبكة/الهاتف اللحظية.
 */
object HakimIntegrationFabric {
    const val VERSION = "SOVEREIGN-INTEGRATION-FABRIC-2026-09-14-v1"

    private val structuralNodes = listOf(
        "quranic_kernel",
        "constitution",
        "intent_context",
        "decision_matrix",
        "sovereign_engine",
        "self_leadership",
        "agent_system",
        "mission_ledger",
        "authority_envelope",
        "capability_registry",
        "autonomous_executor",
        "reasoning_executor",
        "verification",
        "learning",
        "connection_resilience",
        "unified_relay",
        "self_check"
    )

    private val structuralEdges = listOf(
        "quranic_kernel→constitution",
        "constitution→intent_context",
        "intent_context→decision_matrix",
        "decision_matrix→sovereign_engine",
        "sovereign_engine→self_leadership",
        "sovereign_engine→agent_system",
        "agent_system→autonomous_executor",
        "agent_system→reasoning_executor",
        "mission_ledger↔sovereign_engine",
        "authority_envelope→autonomous_executor",
        "authority_envelope→reasoning_executor",
        "capability_registry→self_leadership",
        "execution→verification→learning",
        "connection_resilience↔unified_relay",
        "self_check→integration_fabric",
        "recovery→mission_ledger→replan"
    )

    fun install(context: Context) {
        val report = requireCore(context, "integration_install")
        context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE).edit()
            .putString("integration_fabric_version", VERSION)
            .putString("integration_fabric_last", report.toString().take(16000))
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
        check(governance.optBoolean("quranic_normative_default")) { "الدستور القرآني الحاكم غير مثبت" }
        check(governance.optBoolean("fail_closed_core_changes")) { "حماية تغييرات القلب غير مفعلة" }
        return structuralStatus(context, scope)
    }

    fun structuralStatus(context: Context, scope: String = "status"): JSONObject {
        HakimQuranicInvariantKernel.requireInherited("integration_status:$scope")
        val governance = HakimConstitution.status(context)
        val packageOk = context.packageName == "ps.hakim.stable"
        val quranicOk = governance.optBoolean("quranic_normative_default")
        val failClosed = governance.optBoolean("fail_closed_core_changes")
        val ok = packageOk && quranicOk && failClosed
        return JSONObject()
            .put("version", VERSION)
            .put("scope", scope.take(120))
            .put("structural_integrity", ok)
            .put("single_app_identity", packageOk)
            .put("quranic_root_inherited", quranicOk)
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
            .put("mission_active", activeMission != null)
            .put("mission_phase", activeMission?.phase?.name ?: "IDLE")
            .put("missing_external_link_changes_route_not_governance", true)
    }

    fun promptContext(context: Context): String {
        val s = runtimeStatus(context)
        return buildString {
            appendLine("[نسيج التكامل والوصل السيادي]")
            appendLine("القلب البنيوي=${if (s.optJSONObject("structural")?.optBoolean("structural_integrity") == true) "سليم" else "غير سليم"}؛ اتصال الشبكة/المتصفح/الهاتف جاهزية لحظية وليست بديلًا عن سلامة القلب.")
            appendLine("لا توجد طبقة حرجة معزولة: المقصد والقرار والوكلاء والتنفيذ والتحقق والتعلم والتعافي والاتصال والتحديث تعود إلى القلب الحاكم وسجل المهمة.")
            appendLine("عند فقد وصلة خارجية: غيّر المسار أو استعد الاتصال إذا كان ذلك آمنًا ومسموحًا؛ لا توسع السلطة ولا تدّع أن الوصلة جاهزة.")
            appendLine("المتصفح=${s.optBoolean("browser_ready_now")}، الوصول=${s.optBoolean("accessibility_ready_now")}، ChatGPT=${s.optBoolean("chatgpt_official_installed")}، القناة الآمنة=${s.optBoolean("secure_relay_configured")}.")
        }.take(3000)
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("integration_fabric", true)
        .put("version", VERSION)
        .put("structural", structuralStatus(context))
        .put("runtime", runtimeStatus(context))
}
