export const CINEMATIC_DIRECTOR_VERSION="HAKIM_CINEMA_V1_2026-09-30";

export type CinematicInput={
  goal:string;
  audience?:string;
  audience_age?:number;
  duration_sec?:number;
  aspect?:"16:9"|"9:16"|"1:1"|"4:5";
  style?:string;
  realism?:"standard"|"high"|"maximum"|"cinematic";
  educational?:boolean;
};

type ShotRole="establish"|"develop"|"detail"|"reaction"|"transition"|"resolve";

const SHOT_ROLES:ShotRole[]=["establish","develop","detail","reaction","develop","transition","resolve"];
const FRAMINGS=["wide","medium","medium_close","close","insert"] as const;
const LENSES=[24,35,50,85] as const;
const MOVES=["locked","slow_push","gentle_dolly","controlled_pan","subtle_handheld"] as const;

function boundedText(value:unknown,max:number,fallback=""){
  if(typeof value!=="string") return fallback;
  const v=value.trim();
  return (v||fallback).slice(0,max);
}

function clampInt(value:unknown,min:number,max:number,fallback:number){
  const n=typeof value==="number"&&Number.isFinite(value)?Math.trunc(value):fallback;
  return Math.max(min,Math.min(max,n));
}

function shotDurations(total:number,count:number){
  const base=Math.floor(total/count);
  let remaining=total-base*count;
  return Array.from({length:count},()=>{
    const v=base+(remaining>0?1:0);
    if(remaining>0) remaining-=1;
    return v;
  });
}

function buildShots(total:number,child:boolean){
  const ideal=child?5:4;
  const count=Math.max(2,Math.min(120,Math.ceil(total/ideal)));
  const durations=shotDurations(total,count);
  return durations.map((duration,index)=>{
    const role=SHOT_ROLES[Math.min(SHOT_ROLES.length-1,Math.floor(index*SHOT_ROLES.length/count))]!;
    const framing=FRAMINGS[index%FRAMINGS.length]!;
    const lens=child?([35,50,50,85] as const)[index%4]!:LENSES[index%LENSES.length]!;
    const move=child
      ?(["locked","slow_push","locked","gentle_dolly"] as const)[index%4]!
      :MOVES[index%MOVES.length]!;
    return {
      shot_id:`S${String(index+1).padStart(2,"0")}`,
      role,
      duration_sec:duration,
      framing,
      lens_mm:lens,
      camera_move:move,
      blocking_rule:"حركة الكاميرا والشخصيات مدفوعة بالمعنى وليست للزينة",
      focus_rule:"ثبّت موضوع الانتباه وتجنب ضخّ التركيز غير المقصود",
      continuity:["هوية الشخصيات","الملابس","الإكسسوارات","اتجاه الحركة","مصدر الضوء","وقت المشهد"],
      transition:index===0?"opening":index===count-1?"resolution":"motivated_cut",
      generation_strategy:index===0||index===count-1?"hero_quality":"continuity_first"
    };
  });
}

export function cinematicCapabilities(env:NodeJS.ProcessEnv=process.env){
  const executorName=boundedText(env.HAKIM_VIDEO_EXECUTOR_NAME,80,"");
  const executorUrl=boundedText(env.HAKIM_VIDEO_EXECUTOR_URL,600,"");
  return {
    ok:true,
    director_version:CINEMATIC_DIRECTOR_VERSION,
    provider_neutral:true,
    planning_available:true,
    cinematic_language:true,
    continuity_bible:true,
    shot_level_prompt_compiler:true,
    synchronized_audio_contract:true,
    selective_regeneration:true,
    quality_gates:true,
    executor:{
      connected:Boolean(executorName&&executorUrl),
      name:executorName||null,
      endpoint_configured:Boolean(executorUrl),
      render_verified:false
    },
    provider_policy:{
      hardcoded_provider:false,
      choose_by_scene_capabilities:true,
      paid_without_explicit_approval:false,
      rights_and_privacy_required:true,
      unverified_provider_cannot_win:true
    },
    success_policy:{
      planning_is_not_rendering:true,
      approval_is_not_render_success:true,
      artifact_required:true,
      playback_probe_required:true,
      delivered_artifact_must_match_tested_artifact:true
    }
  };
}

export function buildCinematicPlan(raw:CinematicInput){
  const goal=boundedText(raw.goal,1200);
  if(!goal) throw new Error("video_goal_required");
  const duration=clampInt(raw.duration_sec,8,900,60);
  const age=raw.audience_age===undefined?null:clampInt(raw.audience_age,3,100,18);
  const child=age!==null&&age<=10;
  const aspect=(["16:9","9:16","1:1","4:5"] as const).includes(raw.aspect as any)?raw.aspect!:"16:9";
  const style=boundedText(raw.style,240,"سينمائي واقعي مضبوط");
  const audience=boundedText(raw.audience,240,"عام");
  const realism=raw.realism??"cinematic";
  const educational=raw.educational===true;
  const shots=buildShots(duration,child);

  return {
    ok:true,
    director_version:CINEMATIC_DIRECTOR_VERSION,
    artifact_created:false,
    goal,
    audience,
    audience_age:age,
    duration_sec:duration,
    aspect,
    style,
    realism,
    educational,
    master_look:{
      cadence_fps:24,
      shutter_angle:180,
      capture_intent:"natural_motion_with_controlled_motion_blur",
      composition:"motivated_composition_with_clear_subject_hierarchy",
      camera_rule:"كل حركة كاميرا يجب أن تضيف معنى أو كشفًا أو توترًا",
      lens_rule:"غيّر البعد البؤري لسبب درامي وحافظ على منطق المنظور بين اللقطات",
      lighting:"motivated_key_fill_rim_or_practical_sources_with_consistent_direction",
      exposure:"protect_highlights_keep_skin_and_subject_detail_readable",
      color:"single_show_LUT_or_equivalent_grade_intent_across_all_shots",
      depth:"intentional_depth_of_field_no_random_focus_pumping",
      texture:"retain_natural_detail_avoid_plastic_overprocessing"
    },
    continuity_bible:{
      lock_before_render:[
        "الشخصية والوجه والعمر النسبي",
        "الشعر والملابس والإكسسوارات",
        "الموقع والهندسة المكانية",
        "الوقت والطقس ومصدر الضوء",
        "الألوان والعدسات ونسبة الأبعاد",
        "الدعائم ومواقعها",
        "اتجاه الحركة وخط ١٨٠ درجة"
      ],
      reference_first:true,
      identity_reference_required_for_recurring_characters:true,
      regenerate_failed_shot_only:true
    },
    shots,
    prompt_compiler:{
      per_shot_order:[
        "المقصد الدرامي",
        "الموضوع والهوية المرجعية",
        "الفعل المحدد",
        "المكان والزمن",
        "التكوين وحجم اللقطة",
        "العدسة وموضع الكاميرا",
        "حركة الكاميرا",
        "الإضاءة",
        "اللون والملمس",
        "الحركة الفيزيائية",
        "الصوت المطلوب إن كان المزود يدعمه",
        "قيود الاستمرارية",
        "الممنوعات"
      ],
      negative_constraints:[
        "لا تبدّل هوية الشخصية أو الملابس بلا سبب",
        "لا أطراف زائدة أو أصابع مشوهة أو وجوه مندمجة",
        "لا اهتزاز أو وميض أو morphing غير مقصود",
        "لا تغير مفاجئ في الإضاءة أو الخلفية",
        "لا نص مولد داخل الصورة إلا عند الحاجة ومع مسار نص منفصل",
        "لا حركة كاميرا عشوائية",
        "لا تغير في اتجاه الشاشة يكسر الاستمرارية"
      ]
    },
    sound_design:{
      hierarchy:["الحوار أو السرد","المؤثرات المرتبطة بالفعل","الجو المحيطي","الموسيقى"],
      dialogue_rule:"وضوح الكلام أولًا مع اتساق المسافة والمكان",
      ambience_rule:"سرير صوتي مستمر يمنع القطع السمعي",
      foley_rule:"أضف الأصوات التي تثبت الوزن والملمس والمكان",
      music_rule:"الموسيقى تخدم القوس الدرامي ولا تنافس الكلام",
      sync_rule:"لا يعتمد أي مشهد حواري قبل فحص مزامنة الشفاه والصوت"
    },
    edit_strategy:{
      cut_on_action:true,
      protect_screen_direction:true,
      avoid_unmotivated_jump_cuts:true,
      pace:child?"هادئ واضح مع زمن كافٍ للفهم":"متغير حسب الشحنة الدرامية",
      transitions:"القطع المباشر هو الأصل؛ الانتقالات المؤثرية تستخدم لسبب سردي فقط",
      b_roll:"يستخدم لتغطية القطع ودعم المعنى لا لملء الزمن"
    },
    provider_router_contract:{
      required_fields:[
        "available","rights_ok","privacy_ok","cost_class","paid_approved",
        "max_duration_sec","resolution","fps","image_reference","multi_keyframe",
        "audio_generation","lip_sync","camera_control","quality_score",
        "continuity_score","motion_score","latency_score"
      ],
      selection_order:[
        "الحقوق والخصوصية",
        "ملاءمة قدرات المشهد",
        "ثبات الهوية والاستمرارية",
        "جودة الحركة",
        "التحكم السينمائي",
        "الصوت المتزامن عند الحاجة",
        "الكلفة",
        "الزمن"
      ],
      route_per_shot:true,
      one_provider_not_required:true
    },
    quality_gates:[
      "STORY_INTENT",
      "SHOT_INTENT",
      "FACE_IDENTITY",
      "CHARACTER_CONTINUITY",
      "WARDROBE_PROP_CONTINUITY",
      "ANATOMY_HANDS",
      "OBJECT_PERMANENCE",
      "TEMPORAL_FLICKER",
      "MOTION_COHERENCE",
      "PHYSICS",
      "CAMERA_STABILITY",
      "LENS_PERSPECTIVE",
      "EYELINE_180_RULE",
      "EXPOSURE_HIGHLIGHTS_SHADOWS",
      "WHITE_BALANCE_COLOR_CONTINUITY",
      "BACKGROUND_STABILITY",
      "TEXT_INTEGRITY",
      "LIP_AUDIO_SYNC",
      "DIALOGUE_INTELLIGIBILITY",
      "EDIT_RHYTHM",
      "AUDIO_CONTINUITY",
      ...(educational?["LEARNING_OBJECTIVE_ALIGNMENT","FACTUAL_ACCURACY"]:[]),
      ...(child?["CHILD_COGNITIVE_LOAD","AGE_APPROPRIATE_LANGUAGE","DISTRACTION_CONTROL"]:[]),
      "RIGHTS_LICENSE_PRIVACY",
      "FINAL_PLAYBACK",
      "ARTIFACT_HASH_MATCH"
    ],
    acceptance:{
      all_required_gates_must_pass:true,
      full_duration_playback_required:true,
      duration_probe_required:true,
      audio_stream_probe_required_when_audio_expected:true,
      no_material_visual_defect:true,
      no_unapproved_paid_render:true,
      artifact_hash_required:true,
      render_success_without_artifact_forbidden:true
    }
  };
}
