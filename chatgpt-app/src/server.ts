import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { DeviceCredential, HakimOp } from "./protocol.js";
import { pollResult, publishCommand } from "./relay.js";

function text(value: unknown) {
  return {content:[{type:"text" as const,text:JSON.stringify(value)}], structuredContent:value as Record<string,unknown>};
}

export function createHakimServer(credential: DeviceCredential) {
  const server = new McpServer({name:"Hakim Executive Bridge",version:"0.1.0"});

  const readTool = (name: HakimOp, title: string, description: string) => {
    server.registerTool(name, {
      title, description,
      inputSchema:{},
      annotations:{readOnlyHint:true, destructiveHint:false, idempotentHint:true, openWorldHint:true}
    }, async () => {
      const requestId = await publishCommand(credential, name, {});
      const result = await pollResult(credential, requestId, 8_000);
      return text(result ?? {ok:false,status:"pending",request_id:requestId});
    });
  };

  readTool("status","حالة حكيم","اقرأ حالة جهاز حكيم المرتبط فقط.");
  readTool("ui","واجهة حكيم","اقرأ شجرة الواجهة الحالية من جهاز حكيم المرتبط.");
  readTool("notifications","إشعارات حكيم","اقرأ الإشعارات التي منح المستخدم حكيم صلاحية الوصول إليها.");
  readTool("screenshot","لقطة شاشة حكيم","التقط لقطة شاشة من جهاز حكيم المرتبط إذا كانت خدمة الوصول تسمح بذلك.");

  server.registerTool("launch", {
    title:"فتح تطبيق أو رابط على جهاز حكيم",
    description:"اطلب فتح تطبيق أو رابط على جهاز المستخدم. هذا تغيير مرئي للحالة ويتطلب موافقة أندرويد حسب سياسة حكيم.",
    inputSchema:{package:z.string().optional(),url:z.string().url().optional()},
    annotations:{readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:true}
  }, async ({package: pkg,url}) => {
    const requestId = await publishCommand(credential,"launch",{package:pkg ?? "",url:url ?? ""});
    return text({ok:true,status:"approval_requested",request_id:requestId});
  });

  server.registerTool("action", {
    title:"تنفيذ فعل واجهة مأذون على جهاز حكيم",
    description:"اطلب فعل واجهة محدودًا على جهاز المستخدم. لا يوجد shell أو root. يتطلب موافقة أندرويد قبل التنفيذ.",
    inputSchema:{kind:z.string().min(1).max(64),args:z.record(z.string(),z.unknown()).optional()},
    annotations:{readOnlyHint:false,destructiveHint:false,idempotentHint:false,openWorldHint:true}
  }, async ({kind,args}) => {
    const requestId = await publishCommand(credential,"action",{kind,args:args ?? {}});
    return text({ok:true,status:"approval_requested",request_id:requestId});
  });

  server.registerTool("check_request", {
    title:"تحقق من نتيجة طلب حكيم",
    description:"اقرأ نتيجة طلب سابق باستخدام request_id دون إعادة تنفيذه.",
    inputSchema:{request_id:z.string().min(8).max(128)},
    annotations:{readOnlyHint:true,destructiveHint:false,idempotentHint:true,openWorldHint:true}
  }, async ({request_id}) => {
    const result = await pollResult(credential,request_id,8_000);
    return text(result ?? {ok:false,status:"pending",request_id});
  });

  return server;
}
