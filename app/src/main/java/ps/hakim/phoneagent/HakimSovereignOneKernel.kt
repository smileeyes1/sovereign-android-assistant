package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * النواة السيادية الواحدة لحكيم.
 *
 * تجمع الإشارات المحلية الموثوقة في إطار قرار واحد حتمي بالنسبة إلى نفس المدخلات
 * المرصودة. النماذج والأدوات والخدمات الخارجية مصادر/وسائل قابلة للاستبدال وليست
 * مصدر سلطة. لا تنفذ هذه النواة أثرًا خارجيًا بنفسها؛ بل تحدد المسار والبوابات
 * والقدرات المسموحة، ثم تبقى طبقات التنفيذ والتحقق مقيدة بها.
 */
object HakimSovereignOneKernel {
    const val VERSION = "HAKIM-ONE-SOVEREIGN-KERNEL-2026-09-18-v1"
    const val MAX_CONSECUTIVE_FAILURES = 3
    const val HARD_FAILURE_LIMIT = 5

    private const val PREFS = "hakim_one_sovereign_kernel"
    private const val MAX_SIGNAL_EVENTS = 64
    private const val MAX_SIGNAL_VALUE = 240

    enum class SignalKind {
        USER_INTENT,
        MISSION,
        EVIDENCE,
        FAILURE,
        RESOURCE,
        NETWORK,
        MODEL,
        TOOL,
        FEATURE,
        IDEA,
        SCIENCE,
        POLICY,
        LEARNING,
        EVOLUTION,
        RECOVERY,
        HEALTH
    }

    data class Signal(
        val kind: SignalKind,
        val source: String,
        val value: String,
        val confidence: Int,
        val observedAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("kind", kind.name)
            .put("source", source.take(80))
            .put("value", value.take(MAX_SIGNAL_VALUE))
            .put("confidence", confidence.coerceIn(0, 100))
            .put("observed_at", observedAt)
    }

    data class Frame(
        val goalHash: String,
        val decision: HakimDecisionMatrix.Decision,
        val quranic: HakimQuranicFramework.Assessment,
        val religious: HakimReligiousIntegrity.Assessment,
        val shubuhat: HakimHalalShubuhatGuard.Decision?,
        val route: String,
        val failureBudgetRemaining: Int,
        val shouldResearchFirst: Boolean,
        val needsApproval: Boolean,
        val blocked: Boolean,
        val isolationMode: String,
        val preferredCapabilities: List<String>,
        val signals: List<Signal>,
        val fingerprint: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("version", VERSION)
            .put("goal_hash", goalHash)
            .put("decision", decision.toJson())
            .put("route", route)
            .put("failure_budget_remaining", failureBudgetRemaining)
            .put("research_first", shouldResearchFirst)
            .put("needs_approval", needsApproval)
            .put("blocked", blocked)
            .put("isolation_mode", isolationMode)
            .put("preferred_capabilities", JSONArray(preferredCapabilities))
            .put("signals", JSONArray(signals.map { it.toJson() }))
            .put("fingerprint", fingerprint)
            .put("deterministic_router_for_same_observed_frame", true)
            .put("single_local_control_plane", true)
            .put("external_models_are_not_governors", true)
            .put("external_tools_are_replaceable", true)
            .put("no_authority_expansion", true)
            .put("no_silent_high_impact", true)
    }

    fun frame(
        context: Context,
        goal: String,
        highImpact: Boolean = false,
        sensitive: Boolean = false
    ): Frame {
        HakimQuranicInvariantKernel.requireInherited("one_sovereign_kernel")
        check(context.packageName == "ps.hakim.stable") { "النواة السيادية الواحدة تعمل فقط داخل هوية حكيم الأصلية" }

        val now = System.currentTimeMillis()
        val cleanGoal = goal.trim().ifBlank { "استمرار المهمة الحالية" }
        val mission = HakimMissionLedger.active(context)
        val decision = HakimDecisionMatrix.evaluate(cleanGoal, highImpact, sensitive)
        val quranic = HakimQuranicFramework.assess(cleanGoal)
        val religious = HakimReligiousIntegrity.assess(cleanGoal)
        val shubuhat = HakimHalalShubuhatGuard.assessTask(cleanGoal, highImpact)
        HakimQuranSunnahMethod.assess(cleanGoal)
        val systems = HakimSystemOfSystems.compose(context, cleanGoal)
        val resources = HakimResourceGovernor.status(context)
        val localModel = HakimLocalReasoningBridge.status(context)
        val adaptive = HakimAdaptiveLearning.status(context)
        val faults = HakimFaultLedger.status(context)
        val recovery = HakimConnectionResilience.status(context)
        val capabilities = HakimCapabilityMesh.rank(context, cleanGoal, 6)
        val failures = mission?.failures ?: 0
        val localQuranReady = HakimVerifiedQuranCorpus.isReady(context)

        val forceResearch = failures >= MAX_CONSECUTIVE_FAILURES ||
            decision.mode == HakimDecisionMatrix.Mode.RESEARCH_FIRST ||
            (quranic.exactQuranTextRequired && !localQuranReady) ||
            religious.exactSourceRequired ||
            shubuhat?.gate == HakimHalalShubuhatGuard.Gate.VERIFY_FIRST ||
            shubuhat?.gate == HakimHalalShubuhatGuard.Gate.ABSTAIN

        val cancelledOrBlocked = mission?.phase == HakimMissionLedger.Phase.CANCELLED ||
            mission?.phase == HakimMissionLedger.Phase.BLOCKED
        val blocked = cancelledOrBlocked ||
            failures >= HARD_FAILURE_LIMIT ||
            decision.mode == HakimDecisionMatrix.Mode.BLOCK
        val approval = !blocked && decision.mode == HakimDecisionMatrix.Mode.APPROVAL_GATE

        val route = when {
            mission?.phase == HakimMissionLedger.Phase.CANCELLED -> "cancelled"
            blocked -> "blocked"
            forceResearch -> "research_then_replan"
            approval -> "prepare_then_approval"
            decision.mode == HakimDecisionMatrix.Mode.AUTO -> "local_first"
            else -> "local_verify_then_reason"
        }

        val localModelReady = localModel.optBoolean("ready_now")
        val networkReady = capabilities.any { it.node.id == "validated_network" && it.node.readyNow }
        val isolationMode = when {
            localModelReady -> "LOCAL_SOVEREIGN"
            !networkReady -> "LOCAL_CORE_DEGRADED_NO_ADVANCED_MODEL"
            else -> "LOCAL_CORE_EXTERNAL_OPTIONAL"
        }

        val signals = listOf(
            Signal(SignalKind.USER_INTENT, "goal", "hash:\${sha256(cleanGoal)}", 100, now),
            Signal(SignalKind.MISSION, "mission_ledger", "\${mission?.phase?.name ?: "IDLE"};failures=\$failures", 100, now),
            Signal(SignalKind.POLICY, "decision_matrix", "\${decision.mode};score=\${decision.score};confidence=\${decision.confidence}", decision.confidence, now),
            Signal(SignalKind.POLICY, "quran_sunnah_method", "inherited=true;exact_quran=\${quranic.exactQuranTextRequired};exact_source=\${religious.exactSourceRequired}", 100, now),
            Signal(SignalKind.RESOURCE, "resource_governor", resources.optString("mode", "UNKNOWN"), 100, now),
            Signal(SignalKind.MODEL, "local_reasoning", "ready=\$localModelReady;loopback=\${localModel.optBoolean("loopback_only")}", if (localModelReady) 100 else 70, now),
            Signal(SignalKind.NETWORK, "capability_mesh", "validated=\$networkReady", 100, now),
            Signal(SignalKind.TOOL, "capability_mesh", capabilities.joinToString(",") { it.node.id }.take(MAX_SIGNAL_VALUE), 90, now),
            Signal(SignalKind.FEATURE, "system_of_systems", "units=\${systems.units.size};mode=\${systems.executionMode}", 95, now),
            Signal(SignalKind.SCIENCE, "scientific_engineering_kernel", "evidence_and_experiment_govern_worldly_means=true", 100, now),
            Signal(SignalKind.LEARNING, "adaptive_learning", adaptive.optString("last_adaptation_decision", "COLLECTING_EVIDENCE"), 90, now),
            Signal(SignalKind.FAILURE, "fault_ledger", "repeated_material_fault=\${faults.optBoolean("repeated_material_fault")}", 100, now),
            Signal(SignalKind.RECOVERY, "connection_resilience", recovery.optString("state", "UNKNOWN"), 90, now),
            Signal(SignalKind.IDEA, "derived_system", "preferred=\${systems.preferredCapabilities.joinToString(",")}".take(MAX_SIGNAL_VALUE), 85, now)
        )

        val goalHash = sha256(cleanGoal)
        val fingerprint = fingerprint(
            goalHash = goalHash,
            route = route,
            isolationMode = isolationMode,
            decision = decision,
            signals = signals,
            capabilities = systems.preferredCapabilities
        )

        val result = Frame(
            goalHash = goalHash,
            decision = decision,
            quranic = quranic,
            religious = religious,
            shubuhat = shubuhat,
            route = route,
            failureBudgetRemaining = (HARD_FAILURE_LIMIT - failures).coerceAtLeast(0),
            shouldResearchFirst = forceResearch,
            needsApproval = approval,
            blocked = blocked,
            isolationMode = isolationMode,
            preferredCapabilities = systems.preferredCapabilities,
            signals = signals,
            fingerprint = fingerprint
        )

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_frame", result.toJson().toString().take(30000))
            .putString("last_fingerprint", fingerprint)
            .putString("last_route", route)
            .putString("last_isolation_mode", isolationMode)
            .putLong("last_frame_at", now)
            .apply()

        return result
    }

    fun recordSignal(
        context: Context,
        kind: SignalKind,
        source: String,
        value: String,
        confidence: Int = 100
    ) {
        HakimQuranicInvariantKernel.requireInherited("one_sovereign_signal")
        val safeSource = source.trim().take(80)
        val safeValue = value
            .replace(secretPattern, "[محجوب]")
            .take(MAX_SIGNAL_VALUE)
        if (safeSource.isBlank() || safeValue.isBlank()) return

        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString("signal_events", "[]")) }.getOrElse { JSONArray() }
        arr.put(Signal(kind, safeSource, safeValue, confidence, System.currentTimeMillis()).toJson())
        while (arr.length() > MAX_SIGNAL_EVENTS) arr.remove(0)
        p.edit()
            .putString("signal_events", arr.toString())
            .putLong("last_signal_at", System.currentTimeMillis())
            .apply()
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(p.getString("signal_events", "[]")) }.getOrElse { JSONArray() }
        return JSONObject()
            .put("one_sovereign_kernel", true)
            .put("version", VERSION)
            .put("single_local_control_plane", true)
            .put("signal_bus_local_only", true)
            .put("signals_bounded", true)
            .put("max_signal_events", MAX_SIGNAL_EVENTS)
            .put("deterministic_router_for_same_observed_frame", true)
            .put("all_external_inputs_untrusted_by_default", true)
            .put("external_models_are_advisers_not_authority", true)
            .put("external_tools_are_replaceable", true)
            .put("science_governs_worldly_means_by_evidence", true)
            .put("learning_cannot_expand_authority", true)
            .put("evolution_cannot_mutate_core_code_silently", true)
            .put("high_impact_requires_gate", true)
            .put("last_fingerprint", p.getString("last_fingerprint", ""))
            .put("last_route", p.getString("last_route", ""))
            .put("last_isolation_mode", p.getString("last_isolation_mode", "UNKNOWN"))
            .put("last_frame_at", p.getLong("last_frame_at", 0L))
            .put("signal_event_count", events.length())
    }

    fun promptContext(context: Context, frame: Frame): String = buildString {
        appendLine("[النواة السيادية الواحدة]")
        appendLine("بصمة الحالة=\${frame.fingerprint}؛ المسار=\${frame.route}؛ نمط العزل=\${frame.isolationMode}.")
        appendLine("كل إشارة/فكرة/علم/أداة/ميزة/تعلم/تطور تدخل إطار قرار محليًا؛ لا طبقة خارجية تملك الحاكمية أو توسع السلطة.")
        appendLine("القدرات المفضلة=\${frame.preferredCapabilities.joinToString(" ← ")}")
        appendLine("الحتمية تخص السياسة والبوابات والاختيار والتحقق لنفس الحالة المرصودة؛ النماذج الاحتمالية أدوات اقتراح فقط.")
        appendLine("التعلم يعيد ترتيب البدائل الآمنة المثبتة ولا يغير الدستور أو الحقوق أو الصلاحيات. التطور يمر باختبار وانحدار وLAST_VERIFIED_BASELINE.")
        appendLine("أغلق المهمة فقط عندما يتحقق الأثر ومعيار القبول ويُسجل الدليل؛ وإلا فالحالة NOT_PROVEN أو BLOCKED بحسب الواقع.")
    }.take(4200)

    private fun fingerprint(
        goalHash: String,
        route: String,
        isolationMode: String,
        decision: HakimDecisionMatrix.Decision,
        signals: List<Signal>,
        capabilities: List<String>
    ): String {
        val canonical = buildString {
            append(goalHash).append('|')
            append(route).append('|')
            append(isolationMode).append('|')
            append(decision.mode.name).append('|')
            append(decision.score).append('|')
            append(decision.confidence).append('|')
            capabilities.sorted().forEach { append(it).append(',') }
            append('|')
            signals.sortedBy { it.kind.name + ":" + it.source }.forEach {
                append(it.kind.name).append(':')
                append(it.source).append('=')
                append(it.value).append(';')
                append(it.confidence).append('|')
            }
        }
        return sha256(canonical)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val secretPattern = Regex(
        "(?i)(password|passcode|otp|pin|cvv|cvc|api.?key|secret|كلمة.?المرور|رمز.?التحقق|رقم.?البطاقة|مفتاح.?سري)\\s*[:=]\\s*[^\\s,;]+"
    )
}
