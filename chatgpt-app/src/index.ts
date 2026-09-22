import express from "express";
import cors from "cors";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { decodeBearer, createDeviceCredential, encodeBearer, pairingUrl } from "./protocol.js";
import { createHakimServer } from "./server.js";
import { publishResult } from "./relay.js";

const app=express();
app.set("trust proxy",true);
app.use(cors({origin:true,exposedHeaders:["Mcp-Session-Id"]}));
app.use(express.json({limit:"256kb"}));
app.use(express.text({type:"text/*",limit:"256kb"}));

app.get("/health",(_req,res)=>res.json({ok:true,service:"hakim-chatgpt-bridge",model_provider:"chatgpt-host",openai_api_key_required:false}));

app.get("/pair",(req,res)=>{
  const c=createDeviceCredential();
  const origin=req.protocol+"://"+req.get("host");
  const link=pairingUrl(origin,c);
  const bearer=encodeBearer(c);
  res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ربط حكيم</title><style>body{font-family:system-ui;max-width:680px;margin:auto;padding:32px;line-height:1.8}a,button{font-size:18px}.token{word-break:break-all;background:#f2f2f2;padding:12px;border-radius:12px}</style><h1>ربط حكيم مع ChatGPT</h1><p>١) اضغط «ربط الهاتف» على هاتفك. ٢) استخدم رمز الوصول كـ Bearer credential عند ربط خادم MCP. لا تشاركه مع أحد.</p><p><a href="${link.replaceAll("&","&amp;")}">ربط الهاتف</a></p><p class="token">${bearer}</p><p>لا تحتاج هذه الخدمة إلى مفتاح OpenAI؛ ChatGPT نفسه يبقى طبقة الذكاء.</p></html>`);
});

app.post("/relay/result/:topic/:secret",async(req,res)=>{
  const {topic,secret}=req.params;
  if(!/^[A-Za-z0-9_-]{20,120}$/.test(topic)||!/^[A-Za-z0-9_-]{32,100}$/.test(secret)) return res.status(404).end();
  const body=typeof req.body==="string"?JSON.parse(req.body):req.body;
  await publishResult(topic,body);
  res.json({ok:true});
});

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
