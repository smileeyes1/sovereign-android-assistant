package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/**
 * نواة مصنع الفيديو في حكيم.
 *
 * هذه الطبقة تخطط وتفاضل وتفحص قبل أي توليد فعلي. لا تدّعي وجود GPU أو مزود
 * فيديو ما لم يُمرَّر كقدرة متاحة ومسموح بها. التنفيذ الثقيل يبقى مفصولًا عن
 * التخطيط حتى لا يتحول غياب المزود إلى نجاح وهمي أو تكلفة غير مأذونة.
 */
object HakimVideoFactory {
    const val CONTRACT_VERSION = "VIDEO-FACTORY-2026-09-30-v1"

    private val supportedAspects = setOf("16:9", "9:16", "1:1", "4:5")

    fun capabilities(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("hakim_video_factory", Context.MODE_PRIVATE)
        return JSONObject()
            .put("ok", true)
            .put("contract", CONTRACT_VERSION)
            .put("planner", true)
            .put("provider_router", true)
            .put("quality_gates", true)
            .put("segment_retry", true)
            .put("rights_gate", true)
            .put("cost_gate", true)
            .put("local_heavy_generation", false)
            .put("render_executor_connected", prefs.getBoolean("render_executor_connected", false))
            .put("render_executor_name", prefs.getString("render_executor_name", "").orEmpty())
            .put("policy", JSONObject()
                .put("paid_without_explicit_approval", false)
                .put("claim_render_success_without_artifact", false)
                .put("reuse_verified_assets_first", true)
                .put("regenerate_failed_segment_only", true)
                .put("fail_closed_on_rights_or_provider_uncertainty", true)
            )
    }

    fun plan(payload: JSONObject): JSONObject {
        val goal = payload.optString("goal").trim().take(1200)
        if (goal.isBlank()) return error("video_goal_required")

        val audience = payload.optString("audience", "عام").trim().take(240).ifBlank { "عام" }
        val style = payload.optString("style", "احترافي واقعي").trim().take(240).ifBlank { "احترافي واقعي" }
        val realism = payload.optString("realism", "high").lowercase()
        val durationSec = payload.optInt("duration_sec", 60).coerceIn(8, 900)
        val aspect = payload.optString("aspect", "16:9").let { if (it in supportedAspects) it else "16:9" }
        val educational = payload.optBoolean("educational", false)
        val childAudience = payload.optInt("audience_age", 0) in 5..10

        val targetShotLength = when {
            childAudience -> 5
            realism == "maximum" || realism == "cinematic" -> 4
            else -> 6
        }
        val shotCount = ceil(durationSec.toDouble() / targetShotLength).toInt().coerceIn(1, 120)

        val selectedProvider = selectProvider(payload.optJSONArray("providers"), durationSec)

        return JSONObject()
            .put("ok", true)
            .put("contract", CONTRACT_VERSION)
            .put("goal", goal)
            .put("audience", audience)
            .put("style", style)
            .put("duration_sec", durationSec)
            .put("aspect", aspect)
            .put("educational", educational)
            .put("shot_count_target", shotCount)
            .put("provider_decision", selectedProvider)
            .put("production_strategy", JSONObject()
                .put("generate_everything", false)
                .put("prefer_composition_when_generation_adds_no_value", true)
                .put("reuse_verified_assets", true)
                .put("lock_character_identity", true)
                .put("lock_location_and_wardrobe", true)
                .put("retry_failed_segment_only", true)
                .put("heavy_compute_only_when_materially_useful", true)
            )
            .put("phases", JSONArray()
                .put("فهم المقصد والجمهور")
                .put("كتابة السيناريو")
                .put("تقسيم المشاهد واللقطات")
                .put("تثبيت الشخصيات والهوية البصرية")
                .put("اختيار طريقة صنع كل لقطة")
                .put("اختيار المزود الأنسب المتاح")
                .put("توليد أو تركيب اللقطات")
                .put("فحص بصري وحركي وصوتي")
                .put("إصلاح المقاطع الفاشلة فقط")
                .put("المونتاج والصوت والعناوين")
                .put("بوابة الجودة النهائية")
                .put("تسليم الملف المثبت فقط")
            )
            .put("quality_gates", qualityGates(educational, childAudience))
            .put("resource_policy", JSONObject()
                .put("priority", JSONArray()
                    .put("مورد محلي متاح")
                    .put("مجاني أو مشمول")
                    .put("مفتوح المصدر أو ذاتي الاستضافة")
                    .put("مدفوع بعد موافقة صريحة فقط")
                )
                .put("no_provider_lock_in", true)
                .put("cache_successful_segments", true)
                .put("avoid_full_regeneration", true)
            )
            .put("acceptance", JSONObject()
                .put("artifact_required_for_success", true)
                .put("full_video_review_required", true)
                .put("rights_review_required", true)
                .put("audio_visual_sync_required", true)
                .put("continuity_required", true)
            )
    }

    private fun qualityGates(educational: Boolean, childAudience: Boolean): JSONArray {
        val gates = JSONArray()
            .put("FACE_IDENTITY")
            .put("HANDS_AND_ANATOMY")
            .put("MOTION_CONTINUITY")
            .put("PHYSICS_AND_GEOMETRY")
            .put("BACKGROUND_STABILITY")
            .put("LIGHTING_AND_SHADOWS")
            .put("LIP_AUDIO_SYNC")
            .put("ARABIC_TEXT_INTEGRITY")
            .put("SCENE_CONTINUITY")
            .put("RIGHTS_AND_LICENSE")
            .put("NO_CLIP_OR_OVERLAP")
            .put("FINAL_ARTIFACT_PLAYBACK")
        if (educational) gates.put("LEARNING_OBJECTIVE_ALIGNMENT")
        if (childAudience) {
            gates.put("COGNITIVE_LOAD_FOR_CHILD")
            gates.put("DISTRACTION_CONTROL")
            gates.put("AGE_APPROPRIATE_LANGUAGE")
        }
        return gates
    }

    /**
     * المزود لا يُختار بالاسم التجاري؛ بل بقدرات معلنة لحظة التنفيذ.
     * النتيجة ليست ادعاءً بأن المزود متاح، بل قرارًا من قائمة القدرات المقدمة.
     */
    private fun selectProvider(providers: JSONArray?, durationSec: Int): JSONObject {
        if (providers == null || providers.length() == 0) {
            return JSONObject()
                .put("state", "deferred")
                .put("reason", "no_verified_provider_capabilities")
        }

        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until providers.length()) {
            val p = providers.optJSONObject(i) ?: continue
            if (!p.optBoolean("available", false)) continue
            if (!p.optBoolean("rights_ok", false)) continue
            if (!p.optBoolean("privacy_ok", false)) continue
            if (p.optInt("max_duration_sec", 0) in 1 until durationSec) continue

            var score = 0
            score += p.optInt("quality_score", 0).coerceIn(0, 100) * 5
            score += p.optInt("realism_score", 0).coerceIn(0, 100) * 4
            score += p.optInt("continuity_score", 0).coerceIn(0, 100) * 3
            score += p.optInt("control_score", 0).coerceIn(0, 100) * 2
            score += when (p.optString("cost_class").lowercase()) {
                "free", "local", "included" -> 220
                "metered_free" -> 140
                "paid" -> if (p.optBoolean("paid_approved", false)) 10 else -10_000
                else -> 0
            }
            if (p.optBoolean("supports_reference", false)) score += 60
            if (p.optBoolean("supports_audio", false)) score += 25
            score -= p.optInt("latency_score", 0).coerceIn(0, 100)

            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }

        if (best == null) {
            return JSONObject()
                .put("state", "blocked")
                .put("reason", "no_provider_passed_rights_privacy_cost_and_capacity_gates")
        }

        return JSONObject()
            .put("state", "selected")
            .put("id", best.optString("id", "unnamed"))
            .put("score", bestScore)
            .put("selection_basis", "capability_cost_rights_privacy_capacity")
    }

    private fun error(code: String): JSONObject =
        JSONObject()
            .put("ok", false)
            .put("contract", CONTRACT_VERSION)
            .put("error", code)
}
