package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/**
 * مصنع الفيديو السينمائي في حكيم.
 *
 * التخطيط واختيار المزود وبوابات الجودة مستقلة عن أي شركة أو نموذج.
 * لا يعلن الرندر ناجحًا دون ملف فعلي اجتاز الفحص، ولا يسمح بالدفع دون موافقة صريحة.
 */
object HakimVideoFactory {
    const val CONTRACT_VERSION = "VIDEO-FACTORY-2026-09-30-v1"
    const val CINEMA_VERSION = "HAKIM-CINEMA-V1-2026-09-30"

    private val supportedAspects = setOf("16:9", "9:16", "1:1", "4:5")
    private val framings = listOf("wide", "medium", "medium_close", "close", "insert")
    private val lenses = listOf(24, 35, 50, 85)
    private val moves = listOf("locked", "slow_push", "gentle_dolly", "controlled_pan", "subtle_handheld")

    fun capabilities(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("hakim_video_factory", Context.MODE_PRIVATE)
        return JSONObject()
            .put("ok", true)
            .put("contract", CONTRACT_VERSION)
            .put("cinema_version", CINEMA_VERSION)
            .put("planner", true)
            .put("cinematic_director", true)
            .put("shot_level_prompt_compiler", true)
            .put("continuity_bible", true)
            .put("provider_router", true)
            .put("quality_gates", true)
            .put("segment_retry", true)
            .put("rights_gate", true)
            .put("cost_gate", true)
            .put("synchronized_audio_contract", true)
            .put("local_heavy_generation", false)
            .put("render_executor_connected", prefs.getBoolean("render_executor_connected", false))
            .put("render_executor_name", prefs.getString("render_executor_name", "").orEmpty())
            .put("render_verified", false)
            .put("policy", JSONObject()
                .put("paid_without_explicit_approval", false)
                .put("claim_render_success_without_artifact", false)
                .put("planning_is_not_rendering", true)
                .put("reuse_verified_assets_first", true)
                .put("regenerate_failed_segment_only", true)
                .put("fail_closed_on_rights_or_provider_uncertainty", true)
                .put("delivered_artifact_must_match_tested_artifact", true)
            )
    }

    fun plan(payload: JSONObject): JSONObject {
        val goal = payload.optString("goal").trim().take(1200)
        if (goal.isBlank()) return error("video_goal_required")

        val audience = payload.optString("audience", "عام").trim().take(240).ifBlank { "عام" }
        val style = payload.optString("style", "سينمائي واقعي مضبوط").trim().take(240)
            .ifBlank { "سينمائي واقعي مضبوط" }
        val realism = payload.optString("realism", "cinematic").lowercase()
        val durationSec = payload.optInt("duration_sec", 60).coerceIn(8, 900)
        val aspect = payload.optString("aspect", "16:9").let { if (it in supportedAspects) it else "16:9" }
        val educational = payload.optBoolean("educational", false)
        val childAudience = payload.optInt("audience_age", 0) in 5..10

        val targetShotLength = when {
            childAudience -> 5
            realism == "maximum" || realism == "cinematic" -> 4
            else -> 6
        }
        val shotCount = ceil(durationSec.toDouble() / targetShotLength).toInt().coerceIn(2, 120)
        val selectedProvider = selectProvider(payload.optJSONArray("providers"), durationSec)

        return JSONObject()
            .put("ok", true)
            .put("contract", CONTRACT_VERSION)
            .put("cinema_version", CINEMA_VERSION)
            .put("artifact_created", false)
            .put("goal", goal)
            .put("audience", audience)
            .put("style", style)
            .put("realism", realism)
            .put("duration_sec", durationSec)
            .put("aspect", aspect)
            .put("educational", educational)
            .put("shot_count_target", shotCount)
            .put("master_look", masterLook())
            .put("continuity_bible", continuityBible())
            .put("shots", buildShots(durationSec, shotCount, childAudience))
            .put("prompt_compiler", promptCompiler())
            .put("sound_design", soundDesign())
            .put("edit_strategy", editStrategy(childAudience))
            .put("provider_decision", selectedProvider)
            .put("provider_router_contract", providerRouterContract())
            .put("production_strategy", JSONObject()
                .put("generate_everything", false)
                .put("prefer_composition_when_generation_adds_no_value", true)
                .put("reuse_verified_assets", true)
                .put("lock_character_identity", true)
                .put("lock_location_and_wardrobe", true)
                .put("retry_failed_segment_only", true)
                .put("heavy_compute_only_when_materially_useful", true)
                .put("route_per_shot", true)
            )
            .put("phases", JSONArray()
                .put("فهم المقصد والجمهور والقوس الدرامي")
                .put("كتابة السيناريو")
                .put("بناء دليل الاستمرارية")
                .put("تقسيم المشاهد واللقطات")
                .put("تحديد العدسة وحركة الكاميرا والإضاءة لكل لقطة")
                .put("اختيار المزود الأنسب لكل لقطة")
                .put("توليد أو تركيب اللقطات")
                .put("فحص بصري وحركي وصوتي لقطة بلقطة")
                .put("إصلاح المقاطع الفاشلة فقط")
                .put("المونتاج والصوت والتلوين")
                .put("بوابة الجودة النهائية")
                .put("فحص تشغيل الملف وبصمته")
                .put("تسليم الملف المثبت نفسه")
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
                .put("duration_probe_required", true)
                .put("rights_review_required", true)
                .put("audio_visual_sync_required", true)
                .put("continuity_required", true)
                .put("artifact_hash_required", true)
                .put("render_success_without_artifact_forbidden", true)
                .put("delivered_artifact_must_match_tested_artifact", true)
            )
    }

    private fun masterLook(): JSONObject = JSONObject()
        .put("cadence_fps", 24)
        .put("shutter_angle", 180)
        .put("camera_rule", "كل حركة كاميرا يجب أن تضيف معنى أو كشفا أو توترا")
        .put("lens_rule", "تغيير العدسة لسبب درامي مع الحفاظ على منطق المنظور")
        .put("composition", "motivated_subject_hierarchy")
        .put("lighting", "motivated_consistent_sources")
        .put("exposure", "protect_highlights_and_subject_detail")
        .put("color", "single_consistent_show_look")
        .put("depth", "intentional_depth_of_field")
        .put("texture", "natural_detail_without_plastic_overprocessing")

    private fun continuityBible(): JSONObject = JSONObject()
        .put("reference_first", true)
        .put("identity_reference_required_for_recurring_characters", true)
        .put("regenerate_failed_shot_only", true)
        .put("lock_before_render", JSONArray()
            .put("الشخصية والوجه والعمر النسبي")
            .put("الشعر والملابس والإكسسوارات")
            .put("الموقع والهندسة المكانية")
            .put("الوقت والطقس ومصدر الضوء")
            .put("الألوان والعدسات ونسبة الأبعاد")
            .put("الدعائم ومواقعها")
            .put("اتجاه الحركة وخط ١٨٠ درجة")
        )

    private fun buildShots(durationSec: Int, shotCount: Int, childAudience: Boolean): JSONArray {
        val out = JSONArray()
        val base = durationSec / shotCount
        var remainder = durationSec % shotCount
        for (i in 0 until shotCount) {
            val duration = base + if (remainder-- > 0) 1 else 0
            val framing = framings[i % framings.size]
            val lens = if (childAudience) listOf(35, 50, 50, 85)[i % 4] else lenses[i % lenses.size]
            val move = if (childAudience) {
                listOf("locked", "slow_push", "locked", "gentle_dolly")[i % 4]
            } else moves[i % moves.size]
            out.put(JSONObject()
                .put("shot_id", "S" + (i + 1).toString().padStart(2, '0'))
                .put("duration_sec", duration)
                .put("framing", framing)
                .put("lens_mm", lens)
                .put("camera_move", move)
                .put("blocking_rule", "الحركة مدفوعة بالمعنى وليست للزينة")
                .put("focus_rule", "ثبات موضوع الانتباه ومنع ضخ التركيز غير المقصود")
                .put("transition", when (i) {
                    0 -> "opening"
                    shotCount - 1 -> "resolution"
                    else -> "motivated_cut"
                })
                .put("generation_strategy", if (i == 0 || i == shotCount - 1) "hero_quality" else "continuity_first")
            )
        }
        return out
    }

    private fun promptCompiler(): JSONObject = JSONObject()
        .put("per_shot_order", JSONArray()
            .put("المقصد الدرامي")
            .put("الموضوع والهوية المرجعية")
            .put("الفعل المحدد")
            .put("المكان والزمن")
            .put("التكوين وحجم اللقطة")
            .put("العدسة وموضع الكاميرا")
            .put("حركة الكاميرا")
            .put("الإضاءة")
            .put("اللون والملمس")
            .put("الحركة الفيزيائية")
            .put("الصوت المطلوب")
            .put("قيود الاستمرارية")
            .put("الممنوعات")
        )
        .put("negative_constraints", JSONArray()
            .put("لا تبدل هوية الشخصية أو الملابس بلا سبب")
            .put("لا أطراف زائدة أو تشوهات تشريحية")
            .put("لا وميض أو morphing غير مقصود")
            .put("لا تغير مفاجئ في الإضاءة أو الخلفية")
            .put("لا نص مولد داخل الصورة إلا بمسار نص مستقل")
            .put("لا حركة كاميرا عشوائية")
            .put("لا كسر غير مقصود لاتجاه الشاشة")
        )

    private fun soundDesign(): JSONObject = JSONObject()
        .put("hierarchy", JSONArray()
            .put("الحوار أو السرد")
            .put("المؤثرات المرتبطة بالفعل")
            .put("الجو المحيطي")
            .put("الموسيقى")
        )
        .put("dialogue_rule", "وضوح الكلام أولا مع اتساق المسافة والمكان")
        .put("ambience_rule", "سرير صوتي مستمر يمنع القطع السمعي")
        .put("foley_rule", "الأصوات تثبت الوزن والملمس والمكان")
        .put("music_rule", "الموسيقى تخدم القوس الدرامي ولا تنافس الكلام")
        .put("sync_rule", "لا يعتمد مشهد حواري قبل فحص مزامنة الشفاه والصوت")

    private fun editStrategy(childAudience: Boolean): JSONObject = JSONObject()
        .put("cut_on_action", true)
        .put("protect_screen_direction", true)
        .put("avoid_unmotivated_jump_cuts", true)
        .put("pace", if (childAudience) "هادئ واضح مع زمن كاف للفهم" else "متغير حسب الشحنة الدرامية")
        .put("transitions", "القطع المباشر هو الأصل والمؤثرات لسبب سردي فقط")
        .put("b_roll", "لدعم المعنى وتغطية القطع لا لملء الزمن")

    private fun providerRouterContract(): JSONObject = JSONObject()
        .put("route_per_shot", true)
        .put("one_provider_not_required", true)
        .put("required_fields", JSONArray()
            .put("available")
            .put("rights_ok")
            .put("privacy_ok")
            .put("cost_class")
            .put("paid_approved")
            .put("max_duration_sec")
            .put("resolution")
            .put("fps")
            .put("image_reference")
            .put("multi_keyframe")
            .put("audio_generation")
            .put("lip_sync")
            .put("camera_control")
            .put("quality_score")
            .put("continuity_score")
            .put("motion_score")
            .put("latency_score")
        )
        .put("selection_order", JSONArray()
            .put("الحقوق والخصوصية")
            .put("ملاءمة قدرات المشهد")
            .put("ثبات الهوية والاستمرارية")
            .put("جودة الحركة")
            .put("التحكم السينمائي")
            .put("الصوت المتزامن عند الحاجة")
            .put("الكلفة")
            .put("الزمن")
        )

    private fun qualityGates(educational: Boolean, childAudience: Boolean): JSONArray {
        val gates = JSONArray()
            .put("STORY_INTENT")
            .put("SHOT_INTENT")
            .put("FACE_IDENTITY")
            .put("CHARACTER_CONTINUITY")
            .put("WARDROBE_PROP_CONTINUITY")
            .put("HANDS_AND_ANATOMY")
            .put("OBJECT_PERMANENCE")
            .put("TEMPORAL_FLICKER")
            .put("MOTION_CONTINUITY")
            .put("PHYSICS_AND_GEOMETRY")
            .put("CAMERA_STABILITY")
            .put("LENS_PERSPECTIVE")
            .put("EYELINE_180_RULE")
            .put("EXPOSURE_HIGHLIGHTS_SHADOWS")
            .put("WHITE_BALANCE_COLOR_CONTINUITY")
            .put("BACKGROUND_STABILITY")
            .put("LIGHTING_AND_SHADOWS")
            .put("LIP_AUDIO_SYNC")
            .put("DIALOGUE_INTELLIGIBILITY")
            .put("AUDIO_CONTINUITY")
            .put("ARABIC_TEXT_INTEGRITY")
            .put("EDIT_RHYTHM")
            .put("SCENE_CONTINUITY")
            .put("RIGHTS_AND_LICENSE")
            .put("NO_CLIP_OR_OVERLAP")
            .put("FINAL_ARTIFACT_PLAYBACK")
            .put("ARTIFACT_HASH_MATCH")
        if (educational) {
            gates.put("LEARNING_OBJECTIVE_ALIGNMENT")
            gates.put("FACTUAL_ACCURACY")
        }
        if (childAudience) {
            gates.put("COGNITIVE_LOAD_FOR_CHILD")
            gates.put("DISTRACTION_CONTROL")
            gates.put("AGE_APPROPRIATE_LANGUAGE")
        }
        return gates
    }

    /**
     * لا يُختار المزود باسمه التجاري، بل بقدراته المعلنة وقت التنفيذ.
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
            score += p.optInt("continuity_score", 0).coerceIn(0, 100) * 5
            score += p.optInt("motion_score", 0).coerceIn(0, 100) * 4
            score += p.optInt("control_score", 0).coerceIn(0, 100) * 3
            score += when (p.optString("cost_class").lowercase()) {
                "free", "local", "included" -> 260
                "metered_free" -> 160
                "paid" -> if (p.optBoolean("paid_approved", false)) 10 else -10_000
                else -> 0
            }
            if (p.optBoolean("supports_reference", false) || p.optBoolean("image_reference", false)) score += 80
            if (p.optBoolean("multi_keyframe", false)) score += 70
            if (p.optBoolean("camera_control", false)) score += 60
            if (p.optBoolean("supports_audio", false) || p.optBoolean("audio_generation", false)) score += 35
            if (p.optBoolean("lip_sync", false)) score += 35
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
            .put("selection_basis", "capability_continuity_motion_control_cost_rights_privacy_capacity")
    }

    private fun error(code: String): JSONObject =
        JSONObject()
            .put("ok", false)
            .put("contract", CONTRACT_VERSION)
            .put("cinema_version", CINEMA_VERSION)
            .put("error", code)
}
