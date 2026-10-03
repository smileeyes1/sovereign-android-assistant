package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * سياسة الإدراك الاحترافية.
 *
 * لا تدّعي أن مزودًا "أذكى" من غيره بدرجة ثابتة. الاختيار يعتمد على:
 * - ملاءمة المهمة والوسائط؛
 * - الموثوقية والزمن المرصودين محليًا؛
 * - صحة المسار الحالية؛
 * - سياسة المجانية والخصوصية؛
 * - قوة الدليل المتاحة قبل إغلاق الهدف.
 *
 * النتيجة حتمية عند ثبات نفس الحالة المحلية والمدخلات.
 */
object HakimCognitivePolicy {
    const val VERSION = "PROFESSIONAL-COGNITIVE-POLICY-2026-10-03-v1"

    enum class Depth { QUICK, STANDARD, DEEP, CRITICAL }

    data class TaskProfile(
        val depth: Depth,
        val complexity: Int,
        val privacySensitive: Boolean,
        val executionIntent: Boolean,
        val artifactIntent: Boolean,
        val freshnessDependent: Boolean,
        val verificationRequired: Boolean,
        val attachmentCount: Int
    )

    data class EngineScore(
        val engine: HakimInferenceEngine,
        val score: Int,
        val reliability: Int,
        val reliabilityConfidence: Int,
        val latency: Int,
        val stability: Int,
        val capabilityBreadth: Int,
        val reason: String
    )

    fun analyze(prompt: String, attachmentCount: Int = 0): TaskProfile {
        val q = prompt.trim().lowercase()
        val complexMarkers = listOf(
            "حلل", "حلّل", "قارن", "استنتج", "برمج", "كود", "هندس", "معمار",
            "خطة", "بحث", "تحقق", "دليل", "اكتمال", "احترافي", "متقدم", "استقلال",
            "قيادة ذاتية", "سيادي", "تطوير", "اختبار", "انحدار"
        )
        val executionMarkers = listOf(
            "نفذ", "نفّذ", "قم", "تول", "ثبّت", "ثبت", "أصلح", "اصلح", "ارسل", "أرسل",
            "احذف", "أنشئ", "انشئ", "عدّل", "عدل", "انشر", "ارفع"
        )
        val artifactMarkers = listOf(
            "pdf", "ملف", "وثيقة", "صورة", "فيديو", "ورقة عمل", "عرض", "apk"
        )
        val freshnessMarkers = listOf(
            "الآن", "اليوم", "أحدث", "حالي", "مباشر", "تحقق من", "ابحث", "latest", "current"
        )
        val privacyMarkers = listOf(
            "كلمة المرور", "رمز التحقق", "otp", "pin", "بطاقة", "حساب بنكي",
            "مفتاح خاص", "private key", "سر", "سري", "خصوصي"
        )
        val criticalMarkers = listOf(
            "ثبّت", "ثبت", "احذف", "انشر", "دفع", "حوّل", "تحويل", "شبكة", "راوتر",
            "صلاحية", "مفتاح خاص", "توقيع", "install", "delete", "publish"
        )

        var complexity = 20
        complexity += (q.length / 120).coerceAtMost(25)
        complexity += complexMarkers.count { q.contains(it) } * 8
        if (attachmentCount > 0) complexity += 12
        if (executionMarkers.any { q.contains(it) }) complexity += 10
        complexity = complexity.coerceIn(0, 100)

        val privacy = privacyMarkers.any { q.contains(it) }
        val execution = executionMarkers.any { q.contains(it) }
        val artifact = artifactMarkers.any { q.contains(it) }
        val freshness = freshnessMarkers.any { q.contains(it) }
        val critical = criticalMarkers.any { q.contains(it) }

        val depth = when {
            critical || (execution && complexity >= 70) -> Depth.CRITICAL
            complexity >= 68 || attachmentCount >= 2 -> Depth.DEEP
            complexity >= 38 || execution || attachmentCount > 0 -> Depth.STANDARD
            else -> Depth.QUICK
        }

        return TaskProfile(
            depth = depth,
            complexity = complexity,
            privacySensitive = privacy,
            executionIntent = execution,
            artifactIntent = artifact,
            freshnessDependent = freshness,
            verificationRequired = depth == Depth.CRITICAL || execution || artifact,
            attachmentCount = attachmentCount
        )
    }

    fun directive(profile: TaskProfile): String = when (profile.depth) {
        Depth.QUICK ->
            "عمق الإدراك: سريع؛ أجب مباشرة، ولا تضف دورات تحليل لا تغيّر النتيجة."
        Depth.STANDARD ->
            "عمق الإدراك: معياري؛ افهم المقصد، نفّذ، ثم افحص معيار القبول قبل التسليم."
        Depth.DEEP ->
            "عمق الإدراك: عميق؛ فكك المهمة داخليًا، قارن البدائل المعتبرة، اختبر الافتراضات المؤثرة، ثم تحقق من الناتج والانحدار حيث يلزم."
        Depth.CRITICAL ->
            "عمق الإدراك: حرج؛ لا تعتمد افتراضًا مؤثرًا بلا دليل، افصل التنفيذ عن إثبات الأثر، استخدم أقل صلاحية، واطلب/استخدم تحققًا مستقلًا إضافيًا للأثر العالي متى كان عمليًا."
    }

    fun rank(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        excluded: Set<String> = emptySet()
    ): List<EngineScore> {
        val profile = analyze(prompt, attachments.size)
        return HakimEngineRegistry.directEngines(context)
            .asSequence()
            .filter { it.id !in excluded }
            .filter { HakimFreePolicy.allows(it.id, context) }
            .filter { HakimResiliencePolicy.isAvailable(context, it.id) }
            .filter { HakimInferenceEngine.Capability.GENERAL_CHAT in it.capabilities }
            .filter { HakimEngineRegistry.attachmentsSupported(it, attachments) }
            .map { engine -> score(context, engine, profile) }
            .sortedWith(
                compareByDescending<EngineScore> { it.score }
                    .thenByDescending { it.reliabilityConfidence }
                    .thenBy { it.engine.id }
            )
            .toList()
    }

    private fun score(
        context: Context,
        engine: HakimInferenceEngine,
        profile: TaskProfile
    ): EngineScore {
        val t = HakimEngineTelemetry.snapshot(context, engine.id)
        val latency = latencyScore(t.latencyMs)
        val stability = (100 - (t.consecutiveFailures * 22)).coerceIn(0, 100)
        val breadth = (engine.capabilities.size * 16).coerceIn(20, 100)

        val score = when (profile.depth) {
            Depth.QUICK ->
                t.reliability * 30 / 100 +
                    latency * 35 / 100 +
                    stability * 20 / 100 +
                    breadth * 5 / 100 +
                    t.confidence * 10 / 100
            Depth.STANDARD ->
                t.reliability * 38 / 100 +
                    latency * 24 / 100 +
                    stability * 20 / 100 +
                    breadth * 8 / 100 +
                    t.confidence * 10 / 100
            Depth.DEEP ->
                t.reliability * 46 / 100 +
                    latency * 12 / 100 +
                    stability * 20 / 100 +
                    breadth * 12 / 100 +
                    t.confidence * 10 / 100
            Depth.CRITICAL ->
                t.reliability * 50 / 100 +
                    latency * 8 / 100 +
                    stability * 24 / 100 +
                    breadth * 8 / 100 +
                    t.confidence * 10 / 100
        }

        val safeScore = score.coerceIn(0, 100)
        val reason = buildString {
            append("قرار ديناميكي قائم على دليل محلي: عمق=")
            append(profile.depth.name)
            append(" موثوقية=").append(t.reliability)
            append(" ثقة_العينة=").append(t.confidence)
            append(" استقرار=").append(stability)
            append(" سرعة=").append(latency)
            append(" سعة_قدرات=").append(breadth)
            append(" صحة=").append(HakimResiliencePolicy.describe(context, engine.id))
        }

        return EngineScore(
            engine = engine,
            score = safeScore,
            reliability = t.reliability,
            reliabilityConfidence = t.confidence,
            latency = latency,
            stability = stability,
            capabilityBreadth = breadth,
            reason = reason
        )
    }

    private fun latencyScore(latencyMs: Long): Int = when {
        latencyMs <= 0L -> 65
        latencyMs < 1_500L -> 100
        latencyMs < 3_500L -> 92
        latencyMs < 7_000L -> 82
        latencyMs < 15_000L -> 68
        latencyMs < 30_000L -> 52
        else -> 32
    }

    fun status(context: Context): JSONObject {
        val engines = JSONArray()
        HakimEngineRegistry.directEngines(context).forEach { engine ->
            val t = HakimEngineTelemetry.snapshot(context, engine.id)
            engines.put(
                JSONObject()
                    .put("engine", engine.id)
                    .put("reliability", t.reliability)
                    .put("confidence", t.confidence)
                    .put("latency_ms", t.latencyMs)
                    .put("consecutive_failures", t.consecutiveFailures)
                    .put("available", HakimResiliencePolicy.isAvailable(context, engine.id))
            )
        }
        return JSONObject()
            .put("professional_cognitive_policy", true)
            .put("version", VERSION)
            .put("provider_quality_static_ranking", false)
            .put("evidence_driven_routing", true)
            .put("adaptive_depth", true)
            .put("deterministic_given_same_state", true)
            .put("free_policy_hard_gate", true)
            .put("health_hard_gate", true)
            .put("engines", engines)
    }
}
