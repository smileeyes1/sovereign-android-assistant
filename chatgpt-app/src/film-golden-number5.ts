import { buildFilmBlueprint,FilmShotContract } from "./film-os.js";

export const NUMBER_FIVE_GOLDEN_VERSION="HAKIM_GOLDEN_NUMBER_5_V1_2026-09-30";

export function buildNumberFiveGoldenProduction(){
  const blueprint=buildFilmBlueprint({
    title:"مغامرة العدد ٥",
    goal:"أن يتعرف طالب الصف الأول إلى العدد ٥، يعد خمس وحدات بدقة، ويربط الكمية بالرمز ٥ ضمن قصة كرتونية سينمائية قصيرة.",
    format:"educational",
    audience:{label:"طلبة الصف الأول الأساسي",min_age:6,max_age:7},
    duration_sec:90,
    aspect:"16:9",
    language:"العربية",
    locale:"ar-PS",
    style:"فيلم كرتوني سينمائي أصلي دافئ؛ شخصيات ثابتة الهوية؛ إضاءة وقصة وحركة كاميرا بمستوى مسلسل أطفال احترافي",
    educational:true,
    paid_approved:false
  });

  const characters=[
    {
      id:"layan",
      name:"ليان",
      role:"طفلة فضولية تقود الاكتشاف",
      age_presentation:7,
      design_lock:"وجه دائري لطيف، شعر بني داكن مربوط، ملابس مدرسية أصلية غير مقتبسة، حقيبة صغيرة بلون ثابت",
      voice_lock:"طفولي واضح ودافئ، عربية فصحى مبسطة"
    },
    {
      id:"noor",
      name:"نور",
      role:"رفيق كرتوني مضيء صغير يساعد في العد",
      design_lock:"مخلوق ضوئي أصلي بسيط، هيئة ثابتة، لا يشبه شخصية محمية معروفة",
      voice_lock:"مرح قصير الجمل، لا يطغى على ليان"
    }
  ];

  const exactFive=(kind:"counted_objects"|"digit",value:string,count?:number)=>({
    kind,
    value,
    ...(count===undefined?{}:{count}),
    rule:kind==="digit"
      ?"يُركب حتميًا كرقم عربي شرقي ٥؛ يمنع توليده داخل صورة النموذج ويمنع الرقم الغربي 5 في عين الطالب"
      :"يُركب أو يتحقق حتميًا؛ يجب أن يظهر خمسة بالضبط، والزخارف لا تُحسب"
  } as const);

  const shots:FilmShotContract[]=[
    {
      shot_id:"S01",
      purpose:"افتتاح قصصي وإثارة الفضول",
      duration_sec:14,
      characters:["layan","noor"],
      location:"حديقة مدرسية خيالية صباحية",
      framing:"wide_to_medium",
      lens_mm:35,
      camera_move:"slow_push",
      action:"تدخل ليان الحديقة وتلاحظ صندوقًا يلمع؛ يظهر نور ويدعوها لاكتشاف سر العدد.",
      dialogue:"ليان: ما هذا الضوء الجميل؟ نور: لنكتشف السر معًا!",
      continuity_locks:["وجها الشخصيتين","الملابس","الحقيبة","اتجاه ضوء الصباح","موقع الصندوق"],
      acceptance_gates:["FACE_IDENTITY","CHARACTER_CONTINUITY","LIGHTING_CONTINUITY","AGE_APPROPRIATENESS"]
    },
    {
      shot_id:"S02",
      purpose:"محسوس: تكوين كمية خمسة",
      duration_sec:20,
      characters:["layan","noor"],
      location:"الحديقة نفسها قرب الصندوق",
      framing:"medium_with_inserts",
      lens_mm:50,
      camera_move:"controlled_dolly",
      action:"تظهر الكرات واحدة بعد أخرى مع نبضة ضوئية خفيفة؛ تعد ليان ببطء حتى خمسة.",
      dialogue:"ليان: واحد، اثنان، ثلاثة، أربعة، خمسة.",
      continuity_locks:["نفس الصندوق","نفس اتجاه الشاشة","نفس الكرات عبر القطع"],
      deterministic_overlays:[exactFive("counted_objects","كرات",5)],
      acceptance_gates:["COUNT_ACCURACY","OBJECT_PERMANENCE","MOTION_COHERENCE","COGNITIVE_LOAD"]
    },
    {
      shot_id:"S03",
      purpose:"تثبيت مفهوم العدد عبر مثال محسوس ثانٍ",
      duration_sec:18,
      characters:["layan","noor"],
      location:"طاولة خشبية في الحديقة",
      framing:"top_down_then_reaction",
      lens_mm:50,
      camera_move:"locked",
      action:"توضع خمس تفاحات بترتيب واضح غير متداخل؛ تشير ليان لكل تفاحة أثناء العد.",
      dialogue:"نور: هل تستطيعين عدّها؟ ليان: نعم، إنها خمسة!",
      continuity_locks:["الطاولة","مصدر الضوء","ترتيب العناصر لا يتغير أثناء العد"],
      deterministic_overlays:[exactFive("counted_objects","تفاحات",5)],
      acceptance_gates:["COUNT_ACCURACY","OBJECT_PERMANENCE","SHOT_INTENT","DISTRACTION_CONTROL"]
    },
    {
      shot_id:"S04",
      purpose:"شبه محسوس ثم مجرد",
      duration_sec:20,
      characters:["layan","noor"],
      location:"فضاء بصري نظيف مستمد من الحديقة",
      framing:"graphic_medium",
      lens_mm:50,
      camera_move:"gentle_dolly",
      action:"تتحول العناصر الخمسة إلى خمس نقاط مرتبة، ثم تتجمع الحركة حول رمز العدد.",
      dialogue:"نور: خمس نقاط تعني العدد خمسة.",
      continuity_locks:["ألوان العالم","اتجاه الحركة من العناصر إلى الرمز"],
      deterministic_overlays:[
        exactFive("counted_objects","نقاط",5),
        exactFive("digit","٥")
      ],
      acceptance_gates:["COUNT_ACCURACY","EASTERN_ARABIC_DIGIT_INTEGRITY","TEXT_INTEGRITY","ARABIC_DIRECTIONAL_INTEGRITY"]
    },
    {
      shot_id:"S05",
      purpose:"تقويم وختام قصصي",
      duration_sec:18,
      characters:["layan","noor"],
      location:"الحديقة عند الغروب الذهبي الخفيف",
      framing:"medium_close_to_hero",
      lens_mm:50,
      camera_move:"slow_push",
      action:"تظهر خمس نجوم حول ليان؛ يسأل نور: كم نجمة؟ وقفة قصيرة، ثم يظهر ٥ وتحتفل الشخصيتان.",
      dialogue:"نور: كم نجمة؟ ... ليان: خمس نجوم! ممتاز، هذا هو العدد خمسة.",
      continuity_locks:["هوية الشخصيتين","نفس العالم","تحول زمني مبرر إلى ضوء ختامي دافئ"],
      deterministic_overlays:[
        exactFive("counted_objects","نجوم",5),
        exactFive("digit","٥")
      ],
      acceptance_gates:["COUNT_ACCURACY","LEARNING_OBJECTIVE_ALIGNMENT","EASTERN_ARABIC_DIGIT_INTEGRITY","FINAL_PLAYBACK"]
    }
  ];

  return {
    golden_version:NUMBER_FIVE_GOLDEN_VERSION,
    blueprint,
    characters,
    screenplay:{
      premise:"تكتشف ليان مع نور أن سر الصندوق لا يفتح إلا عندما تتعرف إلى كمية خمسة ورمزها.",
      arc:["فضول","اكتشاف","تجربة","تجريد","نجاح"],
      scenes:5,
      dialogue_policy:"جمل قصيرة واضحة؛ لا مصطلحات تقنية؛ لا نص إنجليزي"
    },
    shots,
    continuity:{
      character_reference_pack_required:true,
      location_reference_pack_required:true,
      voice_reference_required:true,
      approved_shots_frozen:true
    },
    educational_truth:{
      target_digit:"٥",
      target_quantity:5,
      student_facing_western_digit_forbidden:true,
      counted_examples:["كرات","تفاحات","نقاط","نجوم"],
      decoration_never_counts:true,
      hybrid_compositor_required:true
    },
    golden_acceptance:[
      "شخصيتان ثابتتا الهوية عبر جميع اللقطات",
      "كل مثال كمي يحتوي خمسة عناصر بالضبط",
      "لا يظهر الرقم الغربي 5 في عين الطالب",
      "الرمز ٥ يُركب حتميًا ولا يعتمد على نص مولد",
      "الصوت العربي واضح ومتزامن",
      "الحركة والكاميرا والإضاءة متصلة دراميًا",
      "كل اللقطات تجتاز QA قبل المونتاج",
      "الفيلم كاملًا يجتاز تشغيلًا من البداية للنهاية",
      "بصمة الملف المسلّم تطابق الملف المختبَر"
    ],
    status:"SPEC_READY_NOT_RENDERED"
  };
}
