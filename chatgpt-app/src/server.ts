import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
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
const WRITE_ANNOTATIONS={readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:true};
const readSecurity=[{type:"oauth2",scopes:["hakim.read"]}];
const writeSecurity=[{type:"oauth2",scopes:["hakim.write"]}];

const readCatalog=[
  ["status","حالة حكيم","استخدم هذه الأداة عندما تحتاج إلى قراءة حالة جهاز حكيم المرتبط فقط."],
  ["ui","واجهة حكيم","استخدم هذه الأداة عندما تحتاج إلى قراءة شجرة الواجهة الحالية من جهاز حكيم المرتبط."],
  ["notifications","إشعارات حكيم","استخدم هذه الأداة عندما تحتاج إلى قراءة الإشعارات التي منح المستخدم حكيم صلاحية الوصول إليها."],
  ["screenshot","لقطة شاشة حكيم","استخدم هذه الأداة عندما تحتاج إلى لقطة شاشة من جهاز حكيم المرتبط، إذا كانت خدمة الوصول تسمح بذلك."]
] as const;

export function createHakimServer(
  credential:DeviceCredential,
  scopes:string[],
  resourceMetadataUrl:string
){
  const server=new McpServer({name:"Hakim Executive Bridge",version:"0.3.0"});
  const has=(scope:string)=>scopes.includes(scope);
  const reviewMode=credential.topic.startsWith("hakim_review_");
  const reviewId=()=>("review-"+Date.now().toString(36));


  for(const [name,title,description] of readCatalog){
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
      const requestId=await publishCommand(credential,name as HakimOp,{});
      const result=await pollResult(credential,requestId,8_000);
      return text(result??{ok:false,status:"pending",request_id:requestId});
    });
  }

  server.registerTool("launch",{
    title:"فتح تطبيق أو رابط على جهاز حكيم",
    description:"استخدم هذه الأداة عندما يطلب المستخدم فتح تطبيق أو رابط على جهازه. هذا تغيير مرئي للحالة ويتطلب موافقة أندرويد حسب سياسة حكيم.",
    inputSchema:{package:z.string().optional(),url:z.string().url().optional()},
    annotations:WRITE_ANNOTATIONS
  },async({package:pkg,url})=>{
    if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
    if(reviewMode) return text({ok:true,demo:true,status:"approval_requested",request_id:reviewId(),note:"No real device action occurs in reviewer mode."});
    const requestId=await publishCommand(credential,"launch",{package:pkg??"",url:url??""});
    return text({ok:true,status:"approval_requested",request_id:requestId});
  });

  server.registerTool("action",{
    title:"تنفيذ فعل واجهة مأذون على جهاز حكيم",
    description:"استخدم هذه الأداة عندما يطلب المستخدم فعل واجهة محدودًا على جهازه. لا يوجد shell أو root، ويتطلب التنفيذ موافقة أندرويد.",
    inputSchema:{kind:z.string().min(1).max(64),args:z.record(z.string(),z.unknown()).optional()},
    annotations:WRITE_ANNOTATIONS
  },async({kind,args})=>{
    if(!has("hakim.write")) return authError("hakim.write",resourceMetadataUrl);
    if(reviewMode) return text({ok:true,demo:true,status:"approval_requested",request_id:reviewId(),note:"No real device action occurs in reviewer mode."});
    const requestId=await publishCommand(credential,"action",{kind,args:args??{}});
    return text({ok:true,status:"approval_requested",request_id:requestId});
  });

  server.registerTool("check_request",{
    title:"تحقق من نتيجة طلب حكيم",
    description:"استخدم هذه الأداة عندما تحتاج إلى قراءة نتيجة طلب حكيم سابق باستخدام request_id دون إعادة تنفيذه.",
    inputSchema:{request_id:z.string().min(8).max(128)},
    annotations:READ_ANNOTATIONS
  },async({request_id})=>{
    if(!has("hakim.read")) return authError("hakim.read",resourceMetadataUrl);
    if(reviewMode) return text({ok:true,demo:true,status:"complete",request_id,result:{message:"Safe reviewer fixture."}});
    const result=await pollResult(credential,request_id,8_000);
    return text(result??{ok:false,status:"pending",request_id});
  });

  // @modelcontextprotocol/sdk v1.30 validates calls correctly but does not expose
  // OpenAI's root-level securitySchemes through McpServer.registerTool.
  // Override only tools/list; tools/call remains the SDK-validated handler.
  const readTools=readCatalog.map(([name,title,description])=>({
    name,title,description,
    inputSchema:{type:"object",properties:{},additionalProperties:false},
    annotations:READ_ANNOTATIONS,
    securitySchemes:readSecurity,
    _meta:{securitySchemes:readSecurity}
  }));
  const listedTools=[
    ...readTools,
    {
      name:"launch",
      title:"فتح تطبيق أو رابط على جهاز حكيم",
      description:"استخدم هذه الأداة عندما يطلب المستخدم فتح تطبيق أو رابط على جهازه. هذا تغيير مرئي للحالة ويتطلب موافقة أندرويد حسب سياسة حكيم.",
      inputSchema:{
        type:"object",
        properties:{
          package:{type:"string"},
          url:{type:"string",format:"uri"}
        },
        additionalProperties:false
      },
      annotations:WRITE_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    },
    {
      name:"action",
      title:"تنفيذ فعل واجهة مأذون على جهاز حكيم",
      description:"استخدم هذه الأداة عندما يطلب المستخدم فعل واجهة محدودًا على جهازه. لا يوجد shell أو root، ويتطلب التنفيذ موافقة أندرويد.",
      inputSchema:{
        type:"object",
        properties:{
          kind:{type:"string",minLength:1,maxLength:64},
          args:{type:"object",additionalProperties:true}
        },
        required:["kind"],
        additionalProperties:false
      },
      annotations:WRITE_ANNOTATIONS,
      securitySchemes:writeSecurity,
      _meta:{securitySchemes:writeSecurity}
    },
    {
      name:"check_request",
      title:"تحقق من نتيجة طلب حكيم",
      description:"استخدم هذه الأداة عندما تحتاج إلى قراءة نتيجة طلب حكيم سابق باستخدام request_id دون إعادة تنفيذه.",
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

  server.server.setRequestHandler(ListToolsRequestSchema,async()=>({tools:listedTools as any}));
  return server;
}
