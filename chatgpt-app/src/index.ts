import express from "express";
import cors from "cors";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createDeviceCredential,decodeBearer,encodeBearer,pairingUrl } from "./protocol.js";
import { createHakimServer } from "./server.js";

const app=express();
app.set("trust proxy",true);
app.use(cors({origin:true,exposedHeaders:["Mcp-Session-Id"]}));
app.use(express.json({limit:"256kb"}));

app.get("/health",(_req,res)=>res.json({
  ok:true,
  service:"hakim-chatgpt-bridge",
  model_provider:"chatgpt-host",
  openai_api_key_required:false,
  result_transport:"end-to-end-encrypted-outbound-only"
}));

app.get("/pair",(_req,res)=>{
  const c=createDeviceCredential();
  const link=pairingUrl(c);
  const bearer=encodeBearer(c);
  res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ربط حكيم</title><style>body{font-family:system-ui;max-width:680px;margin:auto;padding:32px;line-height:1.8}a{font-size:18px}.token{word-break:break-all;background:#f2f2f2;padding:12px;border-radius:12px}</style><h1>ربط حكيم مع ChatGPT</h1><p>١) اضغط «ربط الهاتف». ٢) استخدم رمز الوصول كاعتماد Bearer عند ربط خادم MCP. لا تشاركه مع أحد.</p><p><a href="${link.replaceAll("&","&amp;")}">ربط الهاتف</a></p><p class="token">${bearer}</p><p>ChatGPT يبقى طبقة الذكاء؛ الجسر لا يحتاج مفتاح OpenAI. الأوامر والنتائج مشفرة طرفًا لطرف فوق الناقل العام.</p></html>`);
});

app.get("/privacy",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>خصوصية حكيم</title><body><h1>خصوصية حكيم</h1><p>الجسر لا يحتاج مفتاح OpenAI ولا يحتفظ بمحتوى الجهاز افتراضيًا. أوامر الهاتف ونتائجه تنتقل مشفرة طرفًا لطرف عبر ناقل عام، ومفاتيح الربط تبقى اعتمادًا سريًا للمستخدم. لا يتيح الجسر shell أو root. الأفعال التي تغيّر حالة الهاتف تبقى خلف موافقة أندرويد.</p><p>ChatGPT نفسه يعالج المحادثة وفق إعدادات حساب المستخدم وسياسات OpenAI. توفر الأدوات والنماذج يعتمد على الخطة والمنطقة والواجهة.</p></body></html>`));

app.get("/terms",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>شروط حكيم</title><body><h1>شروط حكيم</h1><p>حكيم ذراع تنفيذ اختياري لجهاز يملكه المستخدم أو يملك صلاحية إدارته. لا يمنح التطبيق صلاحيات خارج ما وافق عليه المستخدم والنظام. الأفعال الحساسة أو غير القابلة للعكس لا تُنفذ بلا الموافقات المطلوبة.</p></body></html>`));

app.all("/mcp",async(req,res)=>{
  try{
    const auth=String(req.headers.authorization??"");
    if(!auth.startsWith("Bearer ")) return res.status(401).json({error:"bearer_required"});
    const credential=decodeBearer(auth.slice(7));
    const server=createHakimServer(credential);
    const transport=new StreamableHTTPServerTransport({sessionIdGenerator:undefined});
    res.on("close",()=>{void transport.close();void server.close();});
    await server.connect(transport);
    await transport.handleRequest(req,res,req.body);
  }catch(e){
    if(!res.headersSent) res.status(400).json({error:e instanceof Error?e.message:"bridge_error"});
  }
});

const port=Number(process.env.PORT??3000);
app.listen(port,"0.0.0.0",()=>console.log(`Hakim ChatGPT bridge listening on :${port}`));
