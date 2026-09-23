package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Free-first routing matrix.
 * Hard gates come before scores: monetary policy + modality + GENERAL_CHAT.
 */
object HakimWisdomMatrix {
    data class Profile(
        val engineId: String,
        val quality: Int,
        val privacy: Int,
        val speed: Int,
        val multimodal: Int,
        val zeroCostCertainty: Int
    )

    data class Ranked(
        val engine: HakimInferenceEngine,
        val score: Int,
        val reason: String
    )

    private val profiles = mapOf(
        LiteRtLocalEngine.ID to Profile(
            engineId = LiteRtLocalEngine.ID,
            quality = 74,
            privacy = 100,
            speed = 56,
            multimodal = 0,
            zeroCostCertainty = 100
        ),
        OpenRouterFreeEngine.ID to Profile(
            engineId = OpenRouterFreeEngine.ID,
            quality = 82,
            privacy = 58,
            speed = 70,
            multimodal = 72,
            zeroCostCertainty = 100
        ),
        "gemini-direct" to Profile(
            engineId = "gemini-direct",
            quality = 94,
            privacy = 62,
            speed = 88,
            multimodal = 96,
            zeroCostCertainty = 84
        )
    )

    fun rank(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        excluded: Set<String> = emptySet()
    ): List<Ranked> {
        val complex = isComplex(prompt)
        val privacySensitive = isPrivacySensitive(prompt)
        val offline = isOffline(context)
        val simple = !complex && prompt.length < 320 && attachments.isEmpty()
        return HakimEngineRegistry.directEngines(context)
            .asSequence()
            .filter { it.id !in excluded }
            .filter { HakimFreePolicy.allows(it.id, context) }
            .filter { HakimInferenceEngine.Capability.GENERAL_CHAT in it.capabilities }
            .filter { HakimEngineRegistry.attachmentsSupported(it, attachments) }
            .map { engine ->
                val p = profiles[engine.id] ?: Profile(engine.id, 70, 60, 65, 60, 50)
                val t = HakimEngineTelemetry.snapshot(context, engine.id)
                val learnedSpeed = when {
                    t.latencyMs <= 0L -> p.speed
                    t.latencyMs < 2_000L -> 100
                    t.latencyMs < 5_000L -> 88
                    t.latencyMs < 12_000L -> 72
                    t.latencyMs < 30_000L -> 55
                    else -> 35
                }
                val fit = when {
                    attachments.isNotEmpty() -> p.multimodal
                    complex -> p.quality
                    else -> ((p.quality + learnedSpeed) / 2)
                }
                val privacy = if (privacySensitive) p.privacy else (p.privacy + 15).coerceAtMost(100)
                val localBonus = when {
                    engine.id != LiteRtLocalEngine.ID -> 0
                    privacySensitive -> 35
                    offline -> 30
                    simple -> 14
                    else -> 4
                }
                val score =
                    p.zeroCostCertainty * 30 / 100 +
                    p.quality * 25 / 100 +
                    t.reliability * 20 / 100 +
                    privacy * 10 / 100 +
                    learnedSpeed * 10 / 100 +
                    fit * 5 / 100 +
                    localBonus
                Ranked(
                    engine = engine,
                    score = score,
                    reason = "مجاني أولًا؛ جودة=" + p.quality +
                        " موثوقية=" + t.reliability +
                        " خصوصية=" + privacy +
                        " سرعة=" + learnedSpeed +
                        " ملاءمة=" + fit +
                        " محلي=" + localBonus
                )
            }
            .sortedByDescending { it.score }
            .toList()
    }

    fun choose(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        excluded: Set<String> = emptySet()
    ): Ranked? = rank(context, prompt, attachments, excluded).firstOrNull()

    private fun isOffline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isComplex(text: String): Boolean {
        val q = text.lowercase()
        val markers = listOf(
            "حلل", "حلّل", "قارن", "برمج", "كود", "خطط", "خطة",
            "بحث", "استنتج", "اثبت", "أثبت", "معقد", "معقّد", "اكتمال"
        )
        return q.length > 500 || markers.any { q.contains(it) }
    }

    private fun isPrivacySensitive(text: String): Boolean {
        val q = text.lowercase()
        val markers = listOf(
            "كلمة المرور", "رمز التحقق", "بطاقة", "حساب بنكي",
            "هوية", "جواز", "سر", "سري", "خاص جدًا"
        )
        return markers.any { q.contains(it) }
    }
}
