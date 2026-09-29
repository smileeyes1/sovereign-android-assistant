import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { buildCinematicPlan,cinematicCapabilities } from "./cinematic-director.js";
import { cinematicExecutorEnabled,cinematicExecutorSummary,getCinematicRenderStatus,submitCinematicRender } from "./cinematic-executor.js";
import type { DeviceCredential,HakimOp } from "./protocol.js";
import { pollResult,publishCommand } from "./relay.js";
import { ContinuityRevisionConflict,continuityStore } from "./continuity-store.js";
import { fetchLivePreflight,livePreflightSummary } from "./live-preflight.js";
import {
  SOVEREIGN_GOVERNANCE_VERSION,actionRequested,governedReadDescription,
  tagDeviceEvidence,tagExternalData
} from "./governance.js";

function text(value:unknown){
  return {content:[{type:"text" as const,text:JSON.stringify(value)}],structuredContent:value as Record<string,unknown>};
}

function authError(scope:string,metadataUrl:string){
  const challenge=`Bearer resource_metadata="${metadataUrl}", error="insufficient_scope", error_description="Authorization with ${scope} is required"`;
  return {
    content:[{type:"text" as const,text:"يلزم ربط جهاز حكيم بالحساب قبل المتابعة."}],
    isError:true,
    _meta:{"mcp/www_authenticate":[challenge]}
  };
}

const READ_ANNOTATIONS={readOnlyHint:true,destructiveHint:false,idempotentHint:true,openWorldHint:false};
const OPEN_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:true};
const NAV_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:false};
const PRIVATE_ACTION_ANNOTATIONS={readOnlyHint:false,destructiveHint:true,idempotentHint:false,openWorldHint:true};
const CHECKPOINT_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:true,openWorldHint:false};
const VIDEO_RENDER_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:true};
const VIDEO_STATUS_ANNOTATIONS={readOnlyHint:true,destructiveHint:false,idempotentHint:true,openWorldHint:true};
const readSecurity=[{type:"oauth2",scopes:["hakim.read"]}];
const writeSecurity=[{type:"oauth2",scopes:["hakim.write"]}];


const PUBLIC_READ_CATALOG=[
  ["get_device_status","status","حالة حكيم الحية — ابدأ هنا","هذه هي بوابة البداية لأي مهمة تخص حكيم: افحص الآن الحالة الفعلية الحية قبل أي تحليل أو إجراء. يعيد الإصدار وحالة القناة ومحرك التنفيذ ونقطة الاستمرارية دون محتوى شاشة أو أسرار. لا تعتمد على وصف سابق عندما تتوفر هذه الأداة."]
] as const;

const LAN_READ_CATALOG=[
  ["list_network_devices","network_devices","الأجهزة المحلية المأذونة","اكتشف أجهزة الشبكة المحلية بقراءة محدودة. يعيد حكيم معرفات مستعارة وبيانات تصنيف فقط؛ عناوين IP الخام تبقى على الهاتف."]
] as const;

const PRIVATE_READ_CATALOG=[
  ...PUBLIC_READ_CATALOG,
  ["read_browser_page","browser_read","صفحة متصفح حكيم","اقرأ لقطة محدودة من جلسة المتصفح النشطة على هاتف حكيم، مع حجب صفحات تسجيل الدخول والاقتران، في الوضع الخاص فقط."],
  ["get_current_ui","ui","الواجهة الحالية","اقرأ شجرة الواجهة الحالية من جهاز حكيم المرتبط في الوضع الخاص فقط."],
  ["list_notifications","notifications","الإشعارات المأذونة","اقرأ الإشعارات المأذونة في الوضع الخاص فقط."],
  ["capture_screenshot","screenshot","التقاط الشاشة","اطلب لقطة شاشة في الوضع الخاص فقط."]
] as const;

const PUBLIC_NAV_KINDS=["home","back","recents"] as const;
const PRIVATE_ACTION_KINDS=[
  "home","back","recents","notifications","quick_settings",
  "click_text","set_text","tap","swipe"
] as const;
type PrivateActionKind=typeof PRIVATE_ACTION_KINDS[number];

const NETWORK_ACTIONS=[
  "home","back","up","down","left","right","enter",
  "play_pause","volume_up","volume_down","mute",
  "open_url","launch_package"
] as const;
type NetworkAction=typeof NETWORK_ACTIONS[number];
const NETWORK_DEVICE_ID=/^lan-[0-9a-f]{16}$/;

const VIDEO_ASPECTS=["16:9","9:16","1:1","4:5"] as const;
const VIDEO_REALISM=["standard","high","maximum","cinematic"] as const;

const LAN_AUTHORIZE_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:true,openWorldHint:false};
const LAN_CONTROL_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:true};

const actionArgsZ=z.object({
  text:z.string().max(500).optional(),
  id:z.string().max(500).optional(),
  value:z.string().max(2000).optional(),
  x:z.number().finite().min(0).max(10000).optional(),
  y:z.number().finite().min(0).max(10000).optional(),
  x1:z.number().finite().min(0).max(10000).optional(),
  y1:z.number().finite().min(0).max(10000).optional(),
  x2:z.number().finite().min(0).max(10000).optional(),
  y2:z.number().finite().min(0).max(10000).optional(),
  duration:z.number().int().min(100).max(3000).optional()
}).strict();

const actionArgsJsonSchema={
  type:"object",
  properties:{
    text:{type:"string",maxLength:500},
    id:{type:"string",maxLength:500},
    value:{type:"string",maxLength:2000},
    x:{type:"number",minimum:0,maximum:10000},
    y:{type:"number",minimum:0,maximum:10000},
    x1:{type:"number",minimum:0,maximum:10000},
    y1:{type:"number",minimum:0,maximum:10000},
    x2:{type:"number",minimum:0,maximum:10000},
    y2:{type:"number",minimum:0,maximum:10000},
    duration:{type:"integer",minimum:100,maximum:3000}
  },
  additionalProperties:false
};

function isPublicSafeDefault(){
  return process.env.HAKIM_PUBLIC_SAFE!=="0";
}

function lanControlEnabled(){
  return process.env.HAKIM_LAN_CONTROL==="1";
}

function activeReadCatalog(publicSafe:boolean){
  const base=publicSafe?PUBLIC_READ_CATALOG:PRIVATE_READ_CATALOG;
  return lanControlEnabled()?[...base,...LAN_READ_CATALOG]:[...base];
}

function buildActionPayload(kind:PrivateActionKind,args:Record<string,unknown>|undefined){
  const a=(args??{}) as Record<string,unknown>;
  const payload:Record<string,unknown>={action:kind};
  const needNumber=(key:string)=>{
    const v=a[key];
    if(typeof v!=="number"||!Number.isFinite(v)||v<0||v>10000) throw new Error(`invalid_${key}`);
    return v;
  };
  switch(kind){
    case "home":
    case "back":
    case "recents":
    case "notifications":
    case "quick_settings":
      return payload;
    case "click_text":{
      const value=typeof a.text==="string"?a.text.trim():"";
      if(!value) throw new Error("text_required");
      payload.text=value;
      return payload;
    }
    case "set_text":{
      const id=typeof a.id==="string"?a.id.trim():"";
      const hint=typeof a.text==="string"?a.text.trim():"";
      const value=typeof a.value==="string"?a.value:"";
      if(!id&&!hint) throw new Error("id_or_text_required");
      if(!value) throw new Error("value_required");
      if(id) payload.id=id;
      if(hint) payload.text=hint;
      payload.value=value;
      return payload;
    }
    case "tap":
      payload.x=needNumber("x");
      payload.y=needNumber("y");
      return payload;
    case "swipe":
      payload.x1=needNumber("x1");
      payload.y1=needNumber("y1");
      payload.x2=needNumber("x2");
      payload.y2=needNumber("y2");
      payload.duration=typeof a.duration==="number"?Math.trunc(a.duration):400;
      if((payload.duration as number)<100||(payload.duration as number)>3000) throw new Error("invalid_duration");
      return payload;
  }
}

function buildNetworkPayload(
  deviceId:string,
  action:NetworkAction,
  url:string|undefined,
  pkg:string|undefined
){
  const id=deviceId.trim();
  if(!NETWORK_DEVICE_ID.test(id)) throw new Error("invalid_network_device_id");
  const payload:Record<string,unknown>={device_id:id,action};
  const hasUrl=typeof url==="string"&&url.trim().length>0;
  const hasPkg=typeof pkg==="string"&&pkg.trim().length>0;
  if(action==="open_url"){
    if(!hasUrl||hasPkg) throw new Error("url_required");
    const u=new URL(url!);
    if(u.protocol!=="http:"&&u.protocol!=="https:") throw new Error("unsupported_url_scheme");
    if(url!.length>1500) throw new Error("url_too_long");
    payload.url=url!.trim();
  }else if(action==="launch_package"){
    if(!hasPkg||hasUrl) throw new Error("package_required");
    if(!/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(pkg!.trim())) throw new Error("invalid_package_name");
    payload.package=pkg!.trim();
  }else if(hasUrl||hasPkg){
    throw new Error("unexpected_network_target");
  }
  return payload;
}

function limitedStatus(result:unknown,requestId:string){
  if(result===null||result===undefined) return {ok:false,status:"pending",request_id:requestId};
  const root=(typeof result==="object"&&result!==null)?result as Record<string,unknown>:{};
  const inner=(typeof root.result==="object"&&root.result!==null)?root.result as Record<string,unknown>:{};
  const status=typeof root.status==="string"?root.status:"complete";
  const ok=typeof root.ok==="boolean"?root.ok:(typeof inner.ok==="boolean"?inner.ok:status!=="error"&&status!=="failed");
  return {
    ok,
    status,
    device:{connected:true},
    privacy:"content_redacted"
  };
}

function limitedRequestResult(result:unknown,requestId:string){
  if(result===null||result===undefined) return {ok:false,status:"pending",request_id:requestId};
  const root=(typeof result==="object"&&result!==null)?result as Record<string,unknown>:{};
  const status=typeof root.status==="string"?root.status:"complete";
  return {
    ok:status!=="error"&&status!=="failed",
    status,
    operation_token:requestId,
    effect:"result_available",
    privacy:"content_redacted"
  };
}

export function chatgptToolList(publicSafe=isPublicSafeDefault()){
  const readCatalog=activeReadCatalog(publicSafe);

  const readTools=readCatalog.map(([name,_op,title,description])=>({
    name,title,description:governedReadDescription(description),
    inputSchema:{type:"object",properties:{},additionalProperties:false},
    annotations:READ_ANNOTATIONS,
    securitySchemes:readSecurity,
    _meta:{securitySchemes:readSecurity}
  }));

  const tools:any[]=[
    ...readTools,
    {
      name:"get_video_capabilities",
      title:"قدرات مصنع الفيديو السينمائي",
      description:governedReadDescription("يعرض قدرات مخرج حكيم السينمائي وحالة منفذ التوليد دون تنفيذ رندر أو الوصول إلى محتوى الجهاز."),
      inputSchema:{type:"object",properties:{},additionalProperties:false},
      annotations:READ_ANNOTATIONS,
      securitySchemes:readSecurity,
      _meta:{securitySchemes:readSecurity}
    },
    {
      name:"plan_video_project",
      title:"تخطيط مشروع فيديو",
      description:governedReadDescription("حوّل هدف الفيديو إلى خطة إنتاج محكومة من حكيم: مشاهد، استراتيجية موارد، بوابات جودة وسياسة عدم الادعاء. هذه الأداة لا تولّد الفيديو ولا تثبت وجود منفذ توليد فعلي."),
      inputSchema:{
        type:"object",
        properties:{
          goal:{type:"string",minLength:1,maxLength:1200},
          audience:{type:"string",maxLength:240},
          audience_age:{type:"integer",minimum:3,maximum:100},
          duration_sec:{type:"integer",minimum:8,maximum:900},
          aspect:{type:"string",enum:VIDEO_ASPECTS},
          style:{type:"string",maxLength:240},
          realism:{type:"string",enum:VIDEO_REALISM},
          educational:{type:"boolean"}
        },
        required:["goal"],
        additionalProperties:false
      },
      annotations:READ_ANNOTATIONS,
      securitySchemes:readSecurity,
      _meta:{securitySchemes:readSecurity}
    },
    {
      name:"open_target",
      title:"فتح تطبيق أو رابط",
      description:"افتح تطبيقًا محددًا أو رابط HTTP/HTTPS على جهاز حكيم المرتبط. النتيجة approval_requested تعني طلب موافقة فقط وليست نجاحًا أو اكتمالًا، ولا ينفذ أي معاملة أو إرسال بذاته.",
      inputSchema:{
        type:"object",
        properties:{
          package:{type:"string",minLength:3,maxLength:255,pattern:"^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$"},
          url:{type:"string",format:"uri",pattern:"^https?://"}
        },
        oneOf:[{required:["package"]},{required:["url"]}],
        additionalProperties:false
      },
      annotations:OPEN_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    }
  ];

  if(cinematicExecutorEnabled()){
    tools.push({
      name:"render_video_project",
      title:"بدء رندر فيديو سينمائي",
      description:"ابدأ رندر الخطة السينمائية عبر منفذ حكيم الموثق. المسار مجاني فقط؛ لا يسمح بمزود مدفوع، وaccepted ليست نجاح رندر. النجاح لا يثبت إلا بعد فحص ملف الفيديو.",
      inputSchema:{
        type:"object",
        properties:{
          goal:{type:"string",minLength:1,maxLength:1200},
          audience:{type:"string",maxLength:240},
          audience_age:{type:"integer",minimum:3,maximum:100},
          duration_sec:{type:"integer",minimum:8,maximum:900},
          aspect:{type:"string",enum:VIDEO_ASPECTS},
          style:{type:"string",maxLength:240},
          realism:{type:"string",enum:VIDEO_REALISM},
          educational:{type:"boolean"}
        },
        required:["goal"],
        additionalProperties:false
      },
      annotations:VIDEO_RENDER_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    });
    tools.push({
      name:"get_video_render_status",
      title:"حالة رندر الفيديو",
      description:governedReadDescription("تحقق من حالة رندر سينمائي سابق. لا تعتبر completed نجاحًا إلا إذا أعاد حكيم ملفًا ذا بصمة وفحص تشغيل وبوابات جودة ناجحة."),
      inputSchema:{
        type:"object",
        properties:{job_id:{type:"string",minLength:8,maxLength:160,pattern:"^[A-Za-z0-9._:-]+$"}},
        required:["job_id"],
        additionalProperties:false
      },
      annotations:VIDEO_STATUS_ANNOTATIONS,
      securitySchemes:readSecurity,
      _meta:{securitySchemes:readSecurity}
    });
  }

  if(publicSafe){
    tools.push({
      name:"navigate_device",
      title:"تنقل آمن على الجهاز",
      description:"اطلب فقط تنقل Android العام: الرئيسية أو رجوع أو التطبيقات الحديثة. approval_requested ليس نجاحًا؛ الأثر يحتاج نتيجة لاحقة من الجهاز. لا ينقر عناصر محتوى ولا يكتب نصًا.",
      inputSchema:{
        type:"object",
        properties:{kind:{type:"string",enum:PUBLIC_NAV_KINDS}},
        required:["kind"],
        additionalProperties:false
      },
      annotations:NAV_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    });
  }else{
    tools.push({
      name:"perform_ui_action",
      title:"تنفيذ فعل واجهة مأذون — وضع خاص",
      description:"وضع خاص فقط: اطلب فعل واجهة محددًا من القائمة المدعومة. الطلب لا يثبت الأثر ولا اكتمال مقصد المستخدم؛ النتيجة اللاحقة من الجهاز تبقى دليلًا ضمن نطاقها.",
      inputSchema:{
        type:"object",
        properties:{
          kind:{type:"string",enum:PRIVATE_ACTION_KINDS},
          args:actionArgsJsonSchema
        },
        required:["kind"],
        additionalProperties:false
      },
      annotations:PRIVATE_ACTION_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    });
  }

  if(lanControlEnabled()){
    tools.push({
      name:"authorize_network_device",
      title:"اعتماد جهاز محلي",
      description:"اطلب اعتماد جهاز محلي اكتشفه حكيم لاستخدام محول ADB المحدود. approval_requested تعني طلب موافقة فقط؛ لا يتم تجاوز حماية الجهاز أو تجربة بيانات اعتماد.",
      inputSchema:{
        type:"object",
        properties:{
          device_id:{type:"string",pattern:"^lan-[0-9a-f]{16}$"},
          adapter:{type:"string",enum:["adb"]}
        },
        required:["device_id"],
        additionalProperties:false
      },
      annotations:LAN_AUTHORIZE_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    });
    tools.push({
      name:"control_network_device",
      title:"التحكم بجهاز محلي معتمد",
      description:"أرسل أمر ريموت محدودًا إلى جهاز Android/TV محلي معتمد. لا توجد أوامر shell عامة أو تثبيت/حذف/إعادة تشغيل. approval_requested ليست نجاحًا نهائيًا.",
      inputSchema:{
        type:"object",
        properties:{
          device_id:{type:"string",pattern:"^lan-[0-9a-f]{16}$"},
          action:{type:"string",enum:NETWORK_ACTIONS},
          url:{type:"string",format:"uri",pattern:"^https?://",maxLength:1500},
          package:{type:"string",minLength:3,maxLength:255,pattern:"^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$"}
        },
        required:["device_id","action"],
        additionalProperties:false
      },
      annotations:LAN_CONTROL_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    });
    tools.push({
      name:"revoke_network_device",
      title:"إلغاء اعتماد جهاز محلي",
      description:"ألغِ اعتماد جهاز محلي من حكيم وامسح بصمة ADB المحلية المرتبطة به. العملية خلف موافقة أندرويد ولا تنفذ أي أمر على الجهاز المستهدف.",
      inputSchema:{
        type:"object",
        properties:{
          device_id:{type:"string",pattern:"^lan-[0-9a-f]{16}$"},
          adapter:{type:"string",enum:["adb"]}
        },
        required:["device_id"],
        additionalProperties:false
      },
      annotations:LAN_AUTHORIZE_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    });


  }

  tools.push({
    name:"get_continuation_state",
    title:"حالة الاستمرارية",
    description:governedReadDescription(
      "استرجع سجلًا دائمًا ومختصرًا لآخر عمليات حكيم المرتبطة بهذا الجهاز، لتستأنف محادثة جديدة الطلبات المعلقة دون إعادة تنفيذها. لا يعيد محتوى الشاشة أو الصفحة أو القيم المكتوبة أو الأسرار."
    ),
    inputSchema:{type:"object",properties:{},additionalProperties:false},
    annotations:READ_ANNOTATIONS,
    securitySchemes:readSecurity,
    _meta:{securitySchemes:readSecurity}
  });

  tools.push({
    name:"save_continuation_checkpoint",
    title:"حفظ نقطة استئناف",
    description:"احفظ نقطة استئناف مختصرة وآمنة للمقصد الحالي: المقصد، المرحلة، آخر نجاح مثبت، الخطوة التالية والمانع. لا تحفظ أسرارًا أو محتوى شاشة/صفحة أو قيم إدخال.",
    inputSchema:{
      type:"object",
      properties:{
        goal_id:{type:"string",minLength:8,maxLength:96,pattern:"^[A-Za-z0-9._:-]+$"},
        goal_label:{type:"string",minLength:1,maxLength:240},
        stage:{type:"string",minLength:1,maxLength:120},
        last_verified:{type:"string",maxLength:280},
        next_step:{type:"string",maxLength:280},
        blocker:{type:"string",maxLength:220},
        status:{type:"string",enum:["active","waiting","blocked","complete","cancelled"]},
        expected_revision:{type:"integer",minimum:0}
      },
      required:["goal_label","stage","status"],
      additionalProperties:false
    },
    annotations:CHECKPOINT_ANNOTATIONS,
    securitySchemes:writeSecurity,
    _meta:{securitySchemes:writeSecurity}
  });

  tools.push({
    name:"get_request_result",
    title:"قراءة حالة طلب سابق",
    description:governedReadDescription(
      publicSafe
        ?"تحقق من اكتمال طلب سابق دون إعادة تنفيذه. في الوضع العام تُحجب محتويات الجهاز ويعاد فقط نجاح/فشل/حالة الطلب."
        :"اقرأ نتيجة request_id سابق من حكيم دون إعادة تنفيذ الطلب الأصلي."
    ),
    inputSchema:{
      type:"object",
      properties:{operation_token:{type:"string",minLength:8,maxLength:128}},
      required:["operation_token"],
      additionalProperties:false
    },
    annotations:READ_ANNOTATIONS,
    securitySchemes:readSecurity,
    _meta:{securitySchemes:readSecurity}
  });
  return tools;
}

export function createHakimServer(
  credential:DeviceCredential,
  scopes:string[],
  resourceMetadataUrl:string,
  publicSafe=isPublicSafeDefault()
){
  const server=new McpServer({name:"Hakim Executive Bridge",version:publicSafe?"0.7.0-sovereign-public-safe":"0.7.0-sovereign-private"});
  const has=(scope:string)=>scopes.includes(scope);
  const reviewMode=credential.topic.startsWith("hakim_review_");
  const reviewId=()=>("review-"+Date.now().toString(36));
  const remember=async(requestId:string,op:HakimOp)=>{
    if(!reviewMode) await continuityStore.recordRequested(credential,requestId,op);
    return requestId;
  };
  const observe=async(requestId:string,result:unknown)=>{
    if(!reviewMode) await continuityStore.recordObserved(credential,requestId,result);
    return result;
  };
  const readCatalog=activeReadCatalog(publicSafe);

  const blockOnPreflight=(preflight:ReturnType<typeof livePreflightSummary>|Record<string,unknown>,purpose:string)=>{
    const actionReady=(preflight as {action_ready?:unknown}).action_ready===true;
    if(actionReady) return null;
    return text({
      ok:false,
      status:"blocked",
      error:"hakim_live_preflight_failed",
      purpose,
      preflight,
      rule:"No device mutation may run from stale or unverified Hakim state. Re-check live state first."
    });
  };

  for(const [name,internalOp,title,description] of readCatalog){
    server.registerTool(name,{
      title,description:governedReadDescription(description),inputSchema:{},
      annotations:READ_ANNOTATIONS
    },async()=>{
      if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
      if(reviewMode) return text(tagExternalData({
        ok:true,demo:true,tool:name,
        device:{name:"Hakim Review Device",connected:true},
        ...(publicSafe?{privacy:"content_redacted"}:{message:"Safe reviewer fixture; no real device was accessed."})
      },name));
      if(internalOp!=="status"){
        const preflight=await fetchLivePreflight(credential,reviewMode);
        if((preflight as {runtime_ready?:unknown}).runtime_ready!==true){
          return text({
            ok:false,
            status:"blocked",
            error:"hakim_live_preflight_failed",
            purpose:name,
            preflight,
            rule:"Read current Hakim runtime state before any device read."
          });
        }
      }
      const requestId=await remember(await publishCommand(credential,internalOp as HakimOp,{}),internalOp as HakimOp);
      const result=await observe(requestId,await pollResult(credential,requestId,8_000));
      if(publicSafe&&name==="get_device_status"){
        const preflight=livePreflightSummary(result,requestId);
        const continuity=await continuityStore.state(credential);
        return text(tagExternalData({
          ...limitedStatus(result,requestId),
          preflight,
          continuity,
          rule:"Treat this live state as authoritative for the current turn. Before every later device mutation, Hakim re-runs this preflight internally."
        },name));
      }
      return text(tagDeviceEvidence(result??{ok:false,status:"pending",request_id:requestId},requestId));
    });
  }

  server.registerTool("get_video_capabilities",{
    title:"قدرات مصنع الفيديو السينمائي",
    description:governedReadDescription(
      "اقرأ حالة مخرج حكيم السينمائي محليًا على الجسر. لا يصل إلى الهاتف ولا يشغّل مزودًا ولا ينشئ ملفًا."
    ),
    inputSchema:{},
    annotations:READ_ANNOTATIONS
  },async()=>{
    if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
    return text({...cinematicCapabilities(),executor:cinematicExecutorSummary()});
  });

  server.registerTool("plan_video_project",{
    title:"تخطيط مشروع فيديو سينمائي",
    description:governedReadDescription(
      "حوّل الهدف إلى خطة إخراج سينمائي كاملة: لغة كاميرا، عدسات، إضاءة، استمرارية، صوت، مونتاج، توجيه مزودات وبوابات قبول. لا ينفذ رندرًا ولا يدعي إنتاج ملف."
    ),
    inputSchema:{
      goal:z.string().min(1).max(1200),
      audience:z.string().max(240).optional(),
      audience_age:z.number().int().min(3).max(100).optional(),
      duration_sec:z.number().int().min(8).max(900).optional(),
      aspect:z.enum(VIDEO_ASPECTS).optional(),
      style:z.string().max(240).optional(),
      realism:z.enum(VIDEO_REALISM).optional(),
      educational:z.boolean().optional()
    },
    annotations:READ_ANNOTATIONS
  },async(input)=>{
    if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
    return text(buildCinematicPlan(input));
  });

  if(cinematicExecutorEnabled()){
    server.registerTool("render_video_project",{
      title:"بدء رندر فيديو سينمائي",
      description:"ابدأ مهمة رندر عبر منفذ حكيم الموثق مع سياسة free_only. قبول المهمة ليس نجاحًا، ولا يسمح هذا المسار بدفع تلقائي.",
      inputSchema:{
        goal:z.string().min(1).max(1200),
        audience:z.string().max(240).optional(),
        audience_age:z.number().int().min(3).max(100).optional(),
        duration_sec:z.number().int().min(8).max(900).optional(),
        aspect:z.enum(VIDEO_ASPECTS).optional(),
        style:z.string().max(240).optional(),
        realism:z.enum(VIDEO_REALISM).optional(),
        educational:z.boolean().optional()
      },
      annotations:VIDEO_RENDER_ANNOTATIONS
    },async(input)=>{
      if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
      const plan=buildCinematicPlan(input);
      if(reviewMode){
        return text({
          ok:true,demo:true,status:"accepted",job_id:"review-video-job-0001",
          render_success:false,artifact_verified:false,
          rule:"review_fixture_no_real_render"
        });
      }
      return text(await submitCinematicRender(plan));
    });

    server.registerTool("get_video_render_status",{
      title:"حالة رندر الفيديو",
      description:governedReadDescription("تحقق من مهمة رندر سابقة دون إعادة تشغيلها."),
      inputSchema:{job_id:z.string().min(8).max(160).regex(/^[A-Za-z0-9._:-]+$/)},
      annotations:VIDEO_STATUS_ANNOTATIONS
    },async({job_id})=>{
      if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
      if(reviewMode){
        return text({
          ok:true,demo:true,job_id,status:"pending",
          artifact_verified:false,render_success:false,
          rule:"review_fixture_no_real_artifact"
        });
      }
      return text(await getCinematicRenderStatus(job_id));
    });
  }

  server.registerTool("open_target",{
    title:"فتح تطبيق أو رابط",
    description:"اطلب فتح تطبيق أو رابط على جهاز حكيم. الجسر يفرض فحصًا حيًا جديدًا للحالة قبل الإجراء؛ إذا كانت الحالة قديمة أو القناة/محرك التنفيذ غير جاهزين يُحظر الإجراء. approval_requested ليس نجاحًا أو اكتمالًا.",
    inputSchema:{package:z.string().max(255).optional(),url:z.string().url().optional()},
    annotations:OPEN_ANNOTATIONS
  },async({package:pkg,url})=>{
    if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
    const hasPkg=typeof pkg==="string"&&pkg.trim().length>0;
    const hasUrl=typeof url==="string"&&url.trim().length>0;
    if(hasPkg===hasUrl) throw new Error("exactly_one_target_required");
    if(hasPkg&&!/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(pkg!.trim())) throw new Error("invalid_package_name");
    if(hasUrl){
      const u=new URL(url!);
      if(u.protocol!=="http:"&&u.protocol!=="https:") throw new Error("unsupported_url_scheme");
    }
    if(reviewMode) return text(actionRequested(reviewId(),{demo:true,note:"No real device action occurs in reviewer mode."}));
    const preflight=await fetchLivePreflight(credential,reviewMode);
    const preflightBlock=blockOnPreflight(preflight,"open_target");
    if(preflightBlock) return preflightBlock;
    const requestId=await remember(
      await publishCommand(credential,"launch",{package:hasPkg?pkg!.trim():"",url:hasUrl?url!.trim():""}),
      "launch"
    );
    return text(actionRequested(requestId,publicSafe?{}:{request_id:requestId}));
  });

  if(publicSafe){
    server.registerTool("navigate_device",{
      title:"تنقل آمن على الجهاز",
      description:"نفّذ فقط home أو back أو recents بعد موافقة أندرويد. الجسر يفرض فحص الحالة الحية أولًا ولا يعتمد على حالة من محادثة سابقة.",
      inputSchema:{kind:z.enum(PUBLIC_NAV_KINDS)},
      annotations:NAV_ANNOTATIONS
    },async({kind})=>{
      if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
      if(reviewMode) return text(actionRequested(reviewId(),{demo:true,validated_action:kind,note:"No real device action occurs in reviewer mode."}));
      const preflight=await fetchLivePreflight(credential,reviewMode);
      const preflightBlock=blockOnPreflight(preflight,"navigate_device");
      if(preflightBlock) return preflightBlock;
      const requestId=await remember(await publishCommand(credential,"action",{action:kind}),"action");
      return text(actionRequested(requestId,{validated_action:kind}));
    });
  }else{
    server.registerTool("perform_ui_action",{
      title:"تنفيذ فعل واجهة مأذون — وضع خاص",
      description:"وضع خاص فقط: اطلب فعل واجهة محددًا على جهاز حكيم بعد موافقة أندرويد.",
      inputSchema:{kind:z.enum(PRIVATE_ACTION_KINDS),args:actionArgsZ.optional()},
      annotations:PRIVATE_ACTION_ANNOTATIONS
    },async({kind,args})=>{
      if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
      const payload=buildActionPayload(kind,args);
      if(reviewMode) {
        const id=reviewId();
        return text(actionRequested(id,{demo:true,request_id:id,validated_action:payload.action,note:"No real device action occurs in reviewer mode."}));
      }
      const preflight=await fetchLivePreflight(credential,reviewMode);
      const preflightBlock=blockOnPreflight(preflight,"perform_ui_action");
      if(preflightBlock) return preflightBlock;
      const requestId=await remember(await publishCommand(credential,"action",payload),"action");
      return text(actionRequested(requestId,{request_id:requestId,validated_action:payload.action}));
    });
  }

  if(lanControlEnabled()){
    server.registerTool("authorize_network_device",{
      title:"اعتماد جهاز محلي",
      description:"اعتمد جهازًا محليًا مكتشفًا لاستخدام ADB المحدود بعد موافقة أندرويد.",
      inputSchema:{
        device_id:z.string().regex(NETWORK_DEVICE_ID),
        adapter:z.literal("adb").optional()
      },
      annotations:LAN_AUTHORIZE_ANNOTATIONS
    },async({device_id,adapter})=>{
      if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
      if(reviewMode) return text(actionRequested(reviewId(),{demo:true,device_id,adapter:"adb"}));
      const preflight=await fetchLivePreflight(credential,reviewMode);
      const preflightBlock=blockOnPreflight(preflight,"authorize_network_device");
      if(preflightBlock) return preflightBlock;
      const requestId=await remember(
        await publishCommand(credential,"network_authorize",{device_id,adapter:adapter??"adb"}),
        "network_authorize"
      );
      return text(actionRequested(requestId,{device_id,adapter:"adb"}));
    });

    server.registerTool("control_network_device",{
      title:"التحكم بجهاز محلي معتمد",
      description:"نفّذ أمر ريموت محدودًا على جهاز Android/TV محلي معتمد بعد موافقة أندرويد.",
      inputSchema:{
        device_id:z.string().regex(NETWORK_DEVICE_ID),
        action:z.enum(NETWORK_ACTIONS),
        url:z.string().url().max(1500).optional(),
        package:z.string().max(255).optional()
      },
      annotations:LAN_CONTROL_ANNOTATIONS
    },async({device_id,action,url,package:pkg})=>{
      if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
      const payload=buildNetworkPayload(device_id,action,url,pkg);
      if(reviewMode) return text(actionRequested(reviewId(),{demo:true,device_id,validated_action:action}));
      const preflight=await fetchLivePreflight(credential,reviewMode);
      const preflightBlock=blockOnPreflight(preflight,"control_network_device");
      if(preflightBlock) return preflightBlock;
      const requestId=await remember(await publishCommand(credential,"network_control",payload),"network_control");
      return text(actionRequested(requestId,{device_id,validated_action:action}));
    });
    server.registerTool("revoke_network_device",{
      title:"إلغاء اعتماد جهاز محلي",
      description:"امسح اعتماد وبصمة جهاز ADB المحلي من حكيم بعد موافقة أندرويد.",
      inputSchema:{
        device_id:z.string().regex(NETWORK_DEVICE_ID),
        adapter:z.literal("adb").optional()
      },
      annotations:LAN_AUTHORIZE_ANNOTATIONS
    },async({device_id,adapter})=>{
      if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
      if(reviewMode) return text(actionRequested(reviewId(),{demo:true,device_id,adapter:"adb",validated_action:"revoke"}));
      const preflight=await fetchLivePreflight(credential,reviewMode);
      const preflightBlock=blockOnPreflight(preflight,"revoke_network_device");
      if(preflightBlock) return preflightBlock;
      const requestId=await remember(
        await publishCommand(credential,"network_revoke",{device_id,adapter:adapter??"adb"}),
        "network_revoke"
      );
      return text(actionRequested(requestId,{device_id,adapter:"adb",validated_action:"revoke"}));
    });


  }

  server.registerTool("get_continuation_state",{
    title:"حالة الاستمرارية",
    description:governedReadDescription(
      "استرجع آخر عمليات حكيم الآمنة للاستئناف عبر محادثة جديدة دون إعادة تنفيذ الطلبات المعلقة."
    ),
    inputSchema:{},
    annotations:READ_ANNOTATIONS
  },async()=>{
    if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
    if(reviewMode) return text({
      continuity_version:"HAKIM_CONTINUITY_V1",
      durable:true,
      pending_count:0,
      operations:[],
      privacy:"content_redacted",
      demo:true
    });
    return text(await continuityStore.state(credential));
  });

  server.registerTool("save_continuation_checkpoint",{
    title:"حفظ نقطة استئناف",
    description:"احفظ نقطة استئناف مختصرة وآمنة للمقصد الحالي دون تنفيذ أي فعل على الجهاز.",
    inputSchema:{
      goal_id:z.string().min(8).max(96).regex(/^[A-Za-z0-9._:-]+$/).optional(),
      goal_label:z.string().min(1).max(240),
      stage:z.string().min(1).max(120),
      last_verified:z.string().max(280).optional(),
      next_step:z.string().max(280).optional(),
      blocker:z.string().max(220).optional(),
      status:z.enum(["active","waiting","blocked","complete","cancelled"]),
      expected_revision:z.number().int().min(0).optional()
    },
    annotations:CHECKPOINT_ANNOTATIONS
  },async(input)=>{
    if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
    if(reviewMode) return text({
      ok:true,demo:true,
      goal_id:input.goal_id??"goal-review-demo",
      status:input.status,
      persisted:false,
      privacy:"content_redacted"
    });
    try{
      const checkpoint=await continuityStore.saveCheckpoint(credential,input);
      return text({
        ok:true,
        persisted:true,
        goal_id:checkpoint.goal_id,
        status:checkpoint.status,
        checkpoint_revision:checkpoint.checkpoint_revision,
        updated_at_ms:checkpoint.updated_at_ms,
        privacy:"checkpoint_metadata_only"
      });
    }catch(e){
      if(e instanceof ContinuityRevisionConflict){
        return text({
          ok:false,
          persisted:false,
          conflict:true,
          error:"continuity_revision_conflict",
          current_revision:e.current_revision,
          recovery:"Re-read get_continuation_state, reconcile the newer checkpoint, then retry with its checkpoint_revision."
        });
      }
      throw e;
    }
  });

  server.registerTool("get_request_result",{
    title:"قراءة حالة طلب سابق",
    description:governedReadDescription(
      publicSafe
        ?"تحقق من اكتمال طلب سابق دون إعادة تنفيذه، مع حجب محتوى الجهاز."
        :"اقرأ نتيجة request_id سابق من حكيم دون إعادة تنفيذ الطلب الأصلي."
    ),
    inputSchema:{operation_token:z.string().min(8).max(128)},
    annotations:READ_ANNOTATIONS
  },async({operation_token})=>{
    if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
    const request_id=operation_token;
    if(reviewMode) return text(tagDeviceEvidence(
      publicSafe
        ?{ok:true,demo:true,status:"complete",operation_token,effect:"result_available",privacy:"content_redacted"}
        :{ok:true,demo:true,status:"complete",request_id,result:{message:"Safe reviewer fixture."}},
      request_id
    ));
    const result=await observe(request_id,await pollResult(credential,request_id,8_000));
    if(publicSafe) return text(tagDeviceEvidence(limitedRequestResult(result,operation_token),request_id));
    return text(tagDeviceEvidence(result??{ok:false,status:"pending",request_id},request_id));
  });

  return server;
}

export const BRIDGE_GOVERNANCE_VERSION=SOVEREIGN_GOVERNANCE_VERSION;
