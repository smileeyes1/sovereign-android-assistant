package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/**
 * مصنع الفيديو السيادي R30.
 *
 * النواة تملك التخطيط والإخراج والتوجيه وبوابات القبول داخل حكيم.
 * التنفيذ الثقيل يبقى قابلًا للنقل بين تشغيل مفتوح ذاتي الاستضافة وموصلات سحابية اختيارية.
 * لا مزود = لا ارتهان، ولا دفع أو رفع وسائط خاصة بلا تفويض صريح.
 */
object HakimVideoFactory {
    const val CONTRACT_VERSION = "VIDEO-FACTORY-2026-10-08-v2"
    const val STACK_VERSION = "HAKIM-SOVEREIGN-VIDEO-STACK-2026-10-08-r30"

    private val aspects = setOf("16:9", "9:16", "1:1", "4:5")
    private val framings = listOf("wide", "medium", "medium_close", "close", "insert")
    private val lenses = listOf(24, 35, 50, 85)
    private val moves = listOf("locked", "slow_push", "gentle_dolly", "controlled_pan", "subtle_handheld")

    fun capabilities(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("hakim_video_factory", Context.MODE_PRIVATE)
        return JSONObject()
            .put("ok", true)
            .put("contract", CONTRACT_VERSION)
            .put("stack", STACK_VERSION)
            .put("planner_owned", true)
            .put("cinematic_director_owned", true)
            .put("storyboard_contract_owned", true)
            .put("continuity_bible_owned", true)
            .put("shot_prompt_compiler_owned", true)
            .put("provider_router_owned", true)
            .put("quality_gates_owned", true)
            .put("segment_retry_owned", true)
            .put("rights_gate_owned", true)
            .put("cost_gate_owned", true)
            .put("artifact_hash_gate_owned", true)
            .put("local_post_pipeline_contract", true)
            .put("self_hosted_open_runtime_supported", true)
            .put("optional_cloud_connectors_supported", true)
            .put("provider_lock_in", false)
            .put("phone_heavy_generation_currently_available", false)
            .put("compute_independence_currently_proven", false)
            .put("render_executor_connected", prefs.getBoolean("render_executor_connected", false))
            .put("render_executor_name", prefs.getString("render_executor_name", "").orEmpty())
            .put("render_verified", false)
            .put("provider_blueprints", providerBlueprints())
            .put("policy", JSONObject()
                .put("local_or_self_hosted_first", true)
                .put("free_or_included_before_paid", true)
                .put("paid_without_explicit_approval", false)
                .put("private_media_upload_without_scope", false)
                .put("voice_clone_without_rights_confirmation", false)
                .put("claim_render_success_without_artifact", false)
                .put("planning_is_not_rendering", true)
                .put("reuse_verified_assets_first", true)
                .put("regenerate_failed_segment_only", true)
                .put("delivered_artifact_must_match_tested_artifact", true)
            )
    }

    fun plan(payload: JSONObject): JSONObject {
        val goal = payload.optString("goal").trim().take(1600)
        if (goal.isBlank()) return error("video_goal_required")

        val audience = payload.optString("audience", "عام").trim().take(240).ifBlank { "عام" }
        val style = payload.optString("style", "سينمائي واقعي احترافي").trim().take(240)
            .ifBlank { "سينمائي واقعي احترافي" }
        val realism = payload.optString("realism", "cinematic").lowercase()
        val durationSec = payload.optInt("duration_sec", 60).coerceIn(4, 1800)
        val aspect = payload.optString("aspect", "16:9").let { if (it in aspects) it else "16:9" }
        val educational = payload.optBoolean("educational", false)
        val audienceAge = payload.optInt("audience_age", 0)
        val childAudience = audienceAge in 5..10

        val shotLength = when {
            childAudience -> 5
            realism == "maximum" || realism == "cinematic" -> 4
            else -> 6
        }
        val shotCount = ceil(durationSec.toDouble() / shotLength).toInt().coerceIn(1, 180)
        val runtimeProviders = payload.optJSONArray("providers")
        val providerDecision = selectProvider(runtimeProviders, minOf(durationSec, 30))

        return JSONObject()
            .put("ok", true)
            .put("contract", CONTRACT_VERSION)
            .put("stack", STACK_VERSION)
            .put("artifact_created", false)
            .put("goal", goal)
            .put("audience", audience)
            .put("style", style)
            .put("realism", realism)
            .put("duration_sec", durationSec)
            .put("aspect", aspect)
            .put("shot_count_target", shotCount)
            .put("master_look", masterLook())
            .put("continuity_bible", continuityBible())
            .put("shots", buildShots(durationSec, shotCount, childAudience))
            .put("prompt_compiler", promptCompiler())
            .put("provider_router", providerRouterContract())
            .put("provider_decision", providerDecision)
            .put("sound_design", soundDesign())
            .put("edit_strategy", editStrategy(childAudience))
            .put("quality_gates", qualityGates(educational, childAudience))
            .put("production_pipeline", JSONArray()
                .put("قصة وهدف وقيود")
                .put("سيناريو وقوس درامي")
                .put("دليل هوية واستمرارية")
                .put("Storyboard وShot list")
                .put("مرجع أول/أخير أو عناصر مرجعية عند الحاجة")
                .put("توجيه كل لقطة إلى أفضل منفذ متاح")
                .put("توليد اللقطات أو تركيبها")
                .put("فحص الهوية والحركة والتشريح والفيزياء")
                .put("إصلاح اللقطة الفاشلة فقط")
                .put("مونتاج وصوت وتلوين وترجمة")
                .put("فحص تشغيل الملف كاملًا")
                .put("بصمة SHA-256 وتسليم نفس الملف المختبر")
            )
            .put("independence", JSONObject()
                .put("workflow_portable", true)
                .put("provider_neutral_plan", true)
                .put("self_hosted_open_path", true)
                .put("cloud_is_optional_accelerator", true)
                .put("phone_can_plan_without_heavy_gpu", true)
                .put("phone_frontier_video_generation_proven", false)
                .put("external_compute_needed_for_heavy_models", true)
                .put("single_vendor_dependency", false)
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

    private fun providerBlueprints(): JSONArray = JSONArray()
        .put(JSONObject()
            .put("id", "open_graph_runtime")
            .put("class", "self_hosted")
            .put("role", "orchestration")
            .put("examples", JSONArray().put("ComfyUI"))
            .put("portable", true)
            .put("heavy_compute_external", true)
            .put("runtime_verification_required", true))
        .put(JSONObject()
            .put("id", "open_video_foundation")
            .put("class", "self_hosted")
            .put("role", "generation")
            .put("examples", JSONArray().put("LTX-2").put("Wan open video").put("HunyuanVideo"))
            .put("portable", true)
            .put("license_review_required", true)
            .put("gpu_required", true)
            .put("runtime_verification_required", true))
        .put(JSONObject()
            .put("id", "local_post")
            .put("class", "local")
            .put("role", "edit_audio_encode")
            .put("examples", JSONArray().put("FFmpeg"))
            .put("portable", true)
            .put("runtime_verification_required", true))
        .put(JSONObject()
            .put("id", "optional_cloud_accelerator")
            .put("class", "connector")
            .put("role", "generation_or_avatar_or_voice")
            .put("portable", false)
            .put("paid_may_apply", true)
            .put("explicit_approval_required_when_paid", true)
            .put("runtime_verification_required", true))

    private fun providerRouterContract(): JSONObject = JSONObject()
        .put("route_per_shot", true)
        .put("one_provider_not_required", true)
        .put("prefer_open_self_hosted", true)
        .put("fallback_between_provider_classes", true)
        .put("required_fields", JSONArray()
            .put("id").put("available").put("rights_ok").put("privacy_ok")
            .put("cost_class").put("paid_approved").put("max_duration_sec")
            .put("resolution").put("fps").put("image_reference").put("multi_keyframe")
            .put("audio_generation").put("lip_sync").put("camera_control")
            .put("quality_score").put("realism_score").put("continuity_score")
            .put("motion_score").put("control_score").put("latency_score"))
        .put("selection_order", JSONArray()
            .put("الحقوق والخصوصية")
            .put("التوفر الحقيقي")
            .put("ملاءمة المشهد")
            .put("ثبات الهوية والاستمرارية")
            .put("جودة الحركة والواقعية")
            .put("التحكم بالكاميرا والإطارات المرجعية")
            .put("الصوت المتزامن عند الحاجة")
            .put("المحلي/المفتوح/المشمول")
            .put("الكلفة")
            .put("الزمن"))

    private fun selectProvider(providers: JSONArray?, shotDurationSec: Int): JSONObject {
        if (providers == null || providers.length() == 0) {
            return JSONObject()
                .put("state", "deferred")
                .put("reason", "runtime_provider_discovery_required")
                .put("no_provider_lock_in", true)
        }

        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until providers.length()) {
            val p = providers.optJSONObject(i) ?: continue
            if (!p.optBoolean("available", false)) continue
            if (!p.optBoolean("rights_ok", false)) continue
            if (!p.optBoolean("privacy_ok", false)) continue
            if (p.optInt("max_duration_sec", 0) in 1 until shotDurationSec) continue
            if (p.optString("cost_class").lowercase() == "paid" && !p.optBoolean("paid_approved", false)) continue

            var score = 0
            score += p.optInt("quality_score", 0).coerceIn(0, 100) * 5
            score += p.optInt("realism_score", 0).coerceIn(0, 100) * 5
            score += p.optInt("continuity_score", 0).coerceIn(0, 100) * 5
            score += p.optInt("motion_score", 0).coerceIn(0, 100) * 4
            score += p.optInt("control_score", 0).coerceIn(0, 100) * 4
            score += when (p.optString("cost_class").lowercase()) {
                "local", "self_hosted", "free", "included" -> 300
                "metered_free" -> 180
                "paid" -> 20
                else -> 0
            }
            if (p.optBoolean("image_reference", false)) score += 90
            if (p.optBoolean("multi_keyframe", false)) score += 80
            if (p.optBoolean("camera_control", false)) score += 70
            if (p.optBoolean("audio_generation", false)) score += 45
            if (p.optBoolean("lip_sync", false)) score += 45
            score -= p.optInt("latency_score", 0).coerceIn(0, 100)

            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }

        return if (best == null) {
            JSONObject()
                .put("state", "blocked")
                .put("reason", "no_provider_passed_availability_rights_privacy_cost_capacity_gates")
        } else {
            JSONObject()
                .put("state", "selected")
                .put("id", best.optString("id", "unnamed"))
                .put("score", bestScore)
                .put("selection_basis", "availability_rights_privacy_quality_realism_continuity_motion_control_cost")
        }
    }

    private fun masterLook(): JSONObject = JSONObject()
        .put("cadence_fps", 24)
        .put("shutter_angle", 180)
        .put("camera_rule", "كل حركة كاميرا تخدم معنى أو كشفًا أو توترًا")
        .put("lens_rule", "العدسة تتغير لسبب درامي مع ثبات منطق المنظور")
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
            .put("اتجاه الحركة وخط ١٨٠ درجة"))

    private fun buildShots(durationSec: Int, shotCount: Int, childAudience: Boolean): JSONArray {
        val out = JSONArray()
        val base = durationSec / shotCount
        var remainder = durationSec % shotCount
        for (i in 0 until shotCount) {
            val duration = base + if (remainder-- > 0) 1 else 0
            val lens = if (childAudience) listOf(35, 50, 50, 85)[i % 4] else lenses[i % lenses.size]
            val move = if (childAudience) listOf("locked", "slow_push", "locked", "gentle_dolly")[i % 4] else moves[i % moves.size]
            out.put(JSONObject()
                .put("shot_id", "S" + (i + 1).toString().padStart(3, '0'))
                .put("duration_sec", duration)
                .put("framing", framings[i % framings.size])
                .put("lens_mm", lens)
                .put("camera_move", move)
                .put("blocking_rule", "الحركة مدفوعة بالمعنى وليست للزينة")
                .put("focus_rule", "ثبات موضوع الانتباه ومنع ضخ التركيز غير المقصود")
                .put("generation_strategy", if (i == 0 || i == shotCount - 1) "hero_quality" else "continuity_first"))
        }
        return out
    }

    private fun promptCompiler(): JSONObject = JSONObject()
        .put("per_shot_order", JSONArray()
            .put("المقصد الدرامي").put("الهوية المرجعية").put("الفعل المحدد")
            .put("المكان والزمن").put("التكوين وحجم اللقطة").put("العدسة وموضع الكاميرا")
            .put("حركة الكاميرا").put("الإضاءة").put("اللون والملمس")
            .put("الحركة الفيزيائية").put("الصوت").put("قيود الاستمرارية").put("الممنوعات"))
        .put("negative_constraints", JSONArray()
            .put("لا تبدل هوية الشخصية أو الملابس بلا سبب")
            .put("لا أطراف زائدة أو تشوهات تشريحية")
            .put("لا وميض أو morphing غير مقصود")
            .put("لا تغير مفاجئ في الإضاءة أو الخلفية")
            .put("لا نص مولد داخل الصورة إلا بمسار نص مستقل")
            .put("لا حركة كاميرا عشوائية"))

    private fun soundDesign(): JSONObject = JSONObject()
        .put("hierarchy", JSONArray().put("الحوار أو السرد").put("المؤثرات").put("الجو المحيطي").put("الموسيقى"))
        .put("dialogue_rule", "وضوح الكلام أولًا")
        .put("ambience_rule", "سرير صوتي مستمر")
        .put("music_rule", "الموسيقى تخدم القوس الدرامي ولا تنافس الكلام")
        .put("sync_rule", "لا يعتمد مشهد حواري قبل فحص مزامنة الشفاه والصوت")

    private fun editStrategy(childAudience: Boolean): JSONObject = JSONObject()
        .put("cut_on_action", true)
        .put("protect_screen_direction", true)
        .put("avoid_unmotivated_jump_cuts", true)
        .put("pace", if (childAudience) "هادئ واضح مع زمن كاف للفهم" else "متغير حسب الشحنة الدرامية")
        .put("b_roll", "لدعم المعنى وتغطية القطع لا لملء الزمن")

    private fun qualityGates(educational: Boolean, childAudience: Boolean): JSONArray {
        val gates = JSONArray()
            .put("STORY_INTENT").put("SHOT_INTENT").put("FACE_IDENTITY")
            .put("CHARACTER_CONTINUITY").put("WARDROBE_PROP_CONTINUITY")
            .put("HANDS_AND_ANATOMY").put("OBJECT_PERMANENCE").put("TEMPORAL_FLICKER")
            .put("MOTION_CONTINUITY").put("PHYSICS_AND_GEOMETRY").put("CAMERA_STABILITY")
            .put("LENS_PERSPECTIVE").put("EYELINE_180_RULE").put("EXPOSURE")
            .put("COLOR_CONTINUITY").put("BACKGROUND_STABILITY").put("LIGHTING_AND_SHADOWS")
            .put("LIP_AUDIO_SYNC").put("DIALOGUE_INTELLIGIBILITY").put("AUDIO_CONTINUITY")
            .put("ARABIC_TEXT_INTEGRITY").put("EDIT_RHYTHM").put("SCENE_CONTINUITY")
            .put("RIGHTS_AND_LICENSE").put("FINAL_ARTIFACT_PLAYBACK").put("ARTIFACT_HASH_MATCH")
        if (educational) gates.put("LEARNING_OBJECTIVE_ALIGNMENT").put("FACTUAL_ACCURACY")
        if (childAudience) gates.put("COGNITIVE_LOAD_FOR_CHILD").put("DISTRACTION_CONTROL").put("AGE_APPROPRIATE_LANGUAGE")
        return gates
    }

    private fun error(code: String): JSONObject = JSONObject()
        .put("ok", false)
        .put("contract", CONTRACT_VERSION)
        .put("stack", STACK_VERSION)
        .put("error", code)
}
