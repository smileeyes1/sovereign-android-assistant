import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { DeviceCredential,HakimOp } from "./protocol.js";
import { pollResult,publishCommand } from "./relay.js";

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
const ACTION_ANNOTATIONS={readOnlyHint:false,destructiveHint:true,idempotentHint:false,openWorldHint:true};
const readSecurity=[{type:"oauth2",scopes:["hakim.read"]}];
const writeSecurity=[{type:"oauth2",scopes:["hakim.write"]}];

const readCatalog=[
  ["get_device_status","status","حالة جهاز حكيم","اقرأ حالة جهاز حكيم المرتبط فقط عندما يحتاج المستخدم إلى معرفة الاتصال أو جاهزية القدرات."],
  ["get_current_ui","ui","الواجهة الحالية","اقرأ شجرة الواجهة الحالية من جهاز حكيم المرتبط عندما يحتاج المستخدم إلى فهم ما يظهر على الشاشة."],
  ["list_notifications","notifications","الإشعارات المأذونة","اقرأ الإشعارات التي منح المستخدم حكيم صلاحية الوصول إليها فقط عندما تكون لازمة للمقصد."],
  ["capture_screenshot","screenshot","التقاط الشاشة","اطلب لقطة من شاشة جهاز حكيم المرتبط عند الحاجة إلى دليل بصري وكانت صلاحية أندرويد متاحة."]
] as const;

const ACTION_KINDS=[
  "home","back","recents","notifications","quick_settings",
  "click_text","set_text","tap","swipe"
] as const;
type ActionKind=typeof ACTION_KINDS[number];

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

function buildActionPayload(kind:ActionKind,args:Record<string,unknown>|undefined){
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

export function chatgptToolList(){
  const readTools=readCatalog.map(([name,_op,title,description])=>({
    name,title,description,
    inputSchema:{type:"object",properties:{},additionalProperties:false},
    annotations:READ_ANNOTATIONS,
    securitySchemes:readSecurity,
    _meta:{securitySchemes:readSecurity}
  }));
  return [
    ...readTools,
    {
      name:"open_target",
      title:"فتح تطبيق أو رابط",
      description:"افتح تطبيقًا محددًا أو رابط HTTP/HTTPS على جهاز حكيم المرتبط بعد موافقة أندرويد. استخدم هدفًا واحدًا فقط: package أو url.",
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
    },
    {
      name:"perform_ui_action",
      title:"تنفيذ فعل واجهة مأذون",
      description:"اطلب فعل واجهة محددًا من القائمة المدعومة على جهاز حكيم بعد موافقة أندرويد. قد يغيّر هذا الفعل حالة تطبيق خارجي؛ لا تستخدمه دون مقصد صريح من المستخدم.",
      inputSchema:{
        type:"object",
        properties:{
          kind:{type:"string",enum:ACTION_KINDS},
          args:actionArgsJsonSchema
        },
        required:["kind"],
        additionalProperties:false
      },
      annotations:ACTION_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    },
    {
      name:"get_request_result",
      title:"قراءة نتيجة طلب سابق",
      description:"اقرأ نتيجة request_id سابق من حكيم دون إعادة تنفيذ الطلب الأصلي.",
      inputSchema:{
        type:"object",
        properties:{request_id:{type:"string",minLength:8,maxLength:128}},
        required:["request_id"],
        additionalProperties:false
      },
      annotations:READ_ANNOTATIONS,
      securitySchemes:readSecurity,
      _meta:{securitySchemes:readSecurity}
    }
  ];
}

export function createHakimServer(
  credential:DeviceCredential,
  scopes:string[],
  resourceMetadataUrl:string
){
  const server=new McpServer({name:"Hakim Executive Bridge",version:"0.4.0"});
  const has=(scope:string)=>scopes.includes(scope);
  const reviewMode=credential.topic.startsWith("hakim_review_");
  const reviewId=()=>("review-"+Date.now().toString(36));

  for(const [name,internalOp,title,description] of readCatalog){
    server.registerTool(name,{
      title,description,inputSchema:{},
      annotations:READ_ANNOTATIONS
    },async()=>{
      if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
      if(reviewMode) return text({
        ok:true,demo:true,tool:name,
        device:{name:"Hakim Review Device",connected:true},
        message:"Safe reviewer fixture; no real device was accessed."
      });
      const requestId=await publishCommand(credential,internalOp as HakimOp,{});
      const result=await pollResult(credential,requestId,8_000);
      return text(result??{ok:false,status:"pending",request_id:requestId});
    });
  }

  server.registerTool("open_target",{
    title:"فتح تطبيق أو رابط",
    description:"افتح تطبيقًا محددًا أو رابط HTTP/HTTPS على جهاز حكيم المرتبط بعد موافقة أندرويد. استخدم هدفًا واحدًا فقط.",
    inputSchema:{package:z.string().max(255).optional(),url:z.string().url().optional()},
    annotations:OPEN_ANNOTATIONS
  },async({package:pkg,url})=>{
    if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
    const hasPkg=typeof pkg==="string"&&pkg.trim().length>0;
    const hasUrl=typeof url==="string"&&url.trim().length>0;
    if(hasPkg===hasUrl) throw new Error("exactly_one_target_required");
    if(hasUrl){
      const u=new URL(url!);
      if(u.protocol!=="http:"&&u.protocol!=="https:") throw new Error("unsupported_url_scheme");
    }
    if(reviewMode) return text({ok:true,demo:true,status:"approval_requested",request_id:reviewId(),note:"No real device action occurs in reviewer mode."});
    const requestId=await publishCommand(credential,"launch",{package:hasPkg?pkg!.trim():"",url:hasUrl?url!.trim():""});
    return text({ok:true,status:"approval_requested",request_id:requestId});
  });

  server.registerTool("perform_ui_action",{
    title:"تنفيذ فعل واجهة مأذون",
    description:"اطلب فعل واجهة محددًا على جهاز حكيم بعد موافقة أندرويد. الأفعال المدعومة فقط: home, back, recents, notifications, quick_settings, click_text, set_text, tap, swipe.",
    inputSchema:{kind:z.enum(ACTION_KINDS),args:actionArgsZ.optional()},
    annotations:ACTION_ANNOTATIONS
  },async({kind,args})=>{
    if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
    const payload=buildActionPayload(kind,args);
    if(reviewMode) return text({ok:true,demo:true,status:"approval_requested",request_id:reviewId(),validated_action:payload.action,note:"No real device action occurs in reviewer mode."});
    const requestId=await publishCommand(credential,"action",payload);
    return text({ok:true,status:"approval_requested",request_id:requestId});
  });

  server.registerTool("get_request_result",{
    title:"قراءة نتيجة طلب سابق",
    description:"اقرأ نتيجة request_id سابق من حكيم دون إعادة تنفيذ الطلب الأصلي.",
    inputSchema:{request_id:z.string().min(8).max(128)},
    annotations:READ_ANNOTATIONS
  },async({request_id})=>{
    if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
    if(reviewMode) return text({ok:true,demo:true,status:"complete",request_id,result:{message:"Safe reviewer fixture."}});
    const result=await pollResult(credential,request_id,8_000);
    return text(result??{ok:false,status:"pending",request_id});
  });

  return server;
}
