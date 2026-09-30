export const FILM_OS_VERSION="HAKIM_FILM_OS_V1_2026-09-30";

export function filmOsCapabilities(){
  return {
    ok:true,
    film_os_version:FILM_OS_VERSION,
    project_memory:"durable_json_under_hakim_data_dir",
    closed_loop:true,
    world_bible:true,
    character_bible:true,
    shot_contracts:true,
    provider_router:true,
    hybrid_deterministic_compositor:true,
    selective_regeneration:true,
    shot_qa:true,
    film_qa:true,
    artifact_hash_gate:true,
    golden_number_five_spec:true,
    golden_number_five_rendered:false,
    paid_without_explicit_approval:false,
    rule:"Film OS readiness is not proof that a cinematic generative master has been rendered."
  };
}

export type FilmAudience={
  label:string;
  min_age?:number;
  max_age?:number;
};

export type FilmProjectInput={
  title:string;
  goal:string;
  format?:"short"|"episode"|"film"|"educational";
  audience?:FilmAudience;
  duration_sec?:number;
  aspect?:"16:9"|"9:16"|"1:1"|"4:5";
  language?:string;
  locale?:string;
  style?:string;
  educational?:boolean;
  paid_approved?:boolean;
};

export type FilmShotContract={
  shot_id:string;
  purpose:string;
  duration_sec:number;
  characters:string[];
  location:string;
  framing:string;
  lens_mm:number;
  camera_move:string;
  action:string;
  dialogue?:string;
  continuity_locks:string[];
  deterministic_overlays?:Array<{
    kind:"text"|"digit"|"counted_objects"|"diagram";
    value:string;
    count?:number;
    rule:string;
  }>;
  acceptance_gates:string[];
};

function text(value:unknown,max:number,fallback=""){
  if(typeof value!=="string") return fallback;
  const v=value.trim();
  return (v||fallback).slice(0,max);
}

function boundedInt(value:unknown,min:number,max:number,fallback:number){
  const n=typeof value==="number"&&Number.isFinite(value)?Math.trunc(value):fallback;
  return Math.max(min,Math.min(max,n));
}

export function buildFilmBlueprint(raw:FilmProjectInput){
  const title=text(raw.title,160);
  const goal=text(raw.goal,1600);
  if(!title) throw new Error("film_title_required");
  if(!goal) throw new Error("film_goal_required");
  const educational=raw.educational===true||raw.format==="educational";
  const duration=boundedInt(raw.duration_sec,8,7200,educational?90:300);
  const aspect=(["16:9","9:16","1:1","4:5"] as const).includes(raw.aspect as any)?raw.aspect!:"16:9";

  return {
    film_os_version:FILM_OS_VERSION,
    title,
    goal,
    format:raw.format??(educational?"educational":"short"),
    audience:raw.audience??{label:"عام"},
    duration_sec:duration,
    aspect,
    language:text(raw.language,80,"العربية"),
    locale:text(raw.locale,80,"ar-PS"),
    style:text(raw.style,320,"سينمائي أصلي متماسك"),
    educational,
    budget_policy:{
      free_first:true,
      paid_approved:raw.paid_approved===true,
      paid_without_explicit_approval:false,
      cheapest_provider_does_not_override_quality_gate:true
    },
    authority_order:[
      "حقوق وسلامة وخصوصية",
      "مقصد المشروع",
      "الحالة الفعلية والأدلة",
      "حماية الأصول واللقطات المعتمدة",
      "الاستمرارية",
      "الجودة",
      "الكلفة والزمن"
    ],
    lifecycle:[
      "VISION_LOCKED",
      "WORLD_BIBLE_LOCKED",
      "CHARACTER_BIBLE_LOCKED",
      "SCREENPLAY_LOCKED",
      "STORYBOARD_READY",
      "SHOT_CONTRACTS_READY",
      "ROUTED",
      "RENDERING",
      "SHOT_QA",
      "EDITING",
      "FILM_QA",
      "MASTERING",
      "DELIVERED"
    ],
    world_bible:{
      immutable_after_lock:[
        "قواعد العالم",
        "الزمن والمكان",
        "هندسة المواقع الرئيسية",
        "منطق الإضاءة",
        "لوحة الألوان",
        "الدعائم الأساسية",
        "قواعد اللغة واللهجة"
      ],
      change_requires_explicit_story_reason:true
    },
    character_bible:{
      required_reference_views:[
        "front","three_quarter","profile","full_body","expressions","wardrobe"
      ],
      identity_locks:[
        "face","apparent_age","body_proportions","hair","wardrobe","accessories","voice"
      ],
      recurring_character_requires_reference_pack:true,
      reference_pack_must_be_approved_before_series_render:true
    },
    shot_contract_required:true,
    hybrid_pipeline:{
      generative_layer:[
        "characters","performance","environment","camera_motion","lighting_variation","atmospherics"
      ],
      deterministic_layer:[
        "Arabic text","Arabic-Indic digits","mathematical notation","counted educational objects",
        "logos","captions","diagrams","legal slates"
      ],
      generated_text_is_never_authoritative:true
    },
    provider_router:{
      route_per_shot:true,
      provider_lock_not_required:true,
      hard_gates:[
        "available","rights_ok","privacy_ok","required_capabilities_supported","budget_allowed"
      ],
      scoring:[
        "identity_continuity","motion_quality","camera_control","audio_support",
        "reference_support","resolution","latency","cost"
      ],
      unverified_provider_cannot_win:true
    },
    qa:{
      shot_gates:[
        "SHOT_INTENT","FACE_IDENTITY","CHARACTER_CONTINUITY","WARDROBE_CONTINUITY",
        "ANATOMY_HANDS","OBJECT_PERMANENCE","TEMPORAL_FLICKER","MOTION_COHERENCE",
        "PHYSICS","CAMERA_STABILITY","EYELINE_180","LIGHTING_CONTINUITY",
        "BACKGROUND_STABILITY","DIALOGUE_INTELLIGIBILITY","LIP_SYNC","TEXT_INTEGRITY"
      ],
      film_gates:[
        "STORY_COHERENCE","CHARACTER_ARC","SCENE_CONTINUITY","EDIT_RHYTHM",
        "AUDIO_CONTINUITY","COLOR_CONTINUITY","FULL_PLAYBACK","RIGHTS_LICENSE_PRIVACY",
        "ARTIFACT_HASH_MATCH"
      ],
      educational_gates:educational?[
        "LEARNING_OBJECTIVE_ALIGNMENT","FACTUAL_ACCURACY","AGE_APPROPRIATENESS",
        "COGNITIVE_LOAD","COUNT_ACCURACY","ARABIC_DIRECTIONAL_INTEGRITY",
        "EASTERN_ARABIC_DIGIT_INTEGRITY"
      ]:[]
    },
    repair_policy:{
      selective_regeneration:true,
      approved_shot_is_frozen:true,
      regenerate_failed_shot_only:true,
      no_global_rebuild_without_dependency_reason:true
    },
    acceptance:{
      generated_is_not_approved:true,
      edited_is_not_delivered:true,
      all_required_gates_must_pass:true,
      full_playback_required:true,
      artifact_hash_required:true,
      delivered_must_match_tested:true,
      no_material_gap_allowed:true
    },
    learning_loop:{
      retain_successful_prompt_patterns:true,
      retain_provider_per_shot_evidence:true,
      retain_failure_causes:true,
      next_project_starts_from_proven_baseline:true
    }
  };
}
