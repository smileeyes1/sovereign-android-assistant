import express from "express";
import cors from "cors";
import os from "node:os";
import path from "node:path";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import {
  createDeviceCredential,decodeBearer,encodeBearer,pairingUrl,randomSecret
} from "./protocol.js";
import {
  FileCodeStore,isChatGPTClientId,isChatGPTRedirectUri,issueAccessToken,issueRefreshToken,
  makeAuthorizeContext,normalizeScopes,openAccessToken,openAuthorizeContext,openRefreshToken,
  pkceS256,requireProductionOAuthConfig,reviewCredentialsMatch
} from "./oauth.js";
import { pollPairAck } from "./relay.js";
import { chatgptToolList,createHakimServer } from "./server.js";

requireProductionOAuthConfig(process.env);

const app=express();
app.set("trust proxy",true);
app.use(cors({
  origin:true,
  exposedHeaders:["Mcp-Session-Id","WWW-Authenticate"]
}));
app.use(express.json({limit:"256kb"}));
app.use(express.urlencoded({extended:false,limit:"64kb"}));

const oauthSecret=process.env.HAKIM_OAUTH_SECRET ?? randomSecret(48);
const dataDir=process.env.HAKIM_DATA_DIR ?? path.join(os.tmpdir(),"hakim-oauth-dev");
const codeStore=new FileCodeStore(dataDir);
await codeStore.init();

const reviewAttempts=new Map<string,{count:number;windowStart:number}>();
function reviewAttemptAllowed(ip:string){
  const now=Date.now();
  const current=reviewAttempts.get(ip);
  if(!current||now-current.windowStart>60_000){
    reviewAttempts.set(ip,{count:1,windowStart:now});
    return true;
  }
  current.count+=1;
  if(current.count>20) return false;
  return true;
}
function reviewModeEnabled(){
  return process.env.HAKIM_PUBLIC_REVIEW_DEMO==="1" ||
    (!!process.env.HAKIM_REVIEW_USER&&!!process.env.HAKIM_REVIEW_PASSWORD);
}

function origin(req:express.Request){
  const host=req.get("host");
  if(!host) throw new Error("host_required");
  return req.protocol+"://"+host;
}

function one(value:unknown):string{
  if(typeof value!=="string") return "";
  return value;
}

function html(value:string){
  return value.replaceAll("&","&amp;").replaceAll("<","&lt;")
    .replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;");
}

function noStore(res:express.Response){
  res.setHeader("Cache-Control","no-store");
  res.setHeader("Pragma","no-cache");
}

function oauthError(res:express.Response,status:number,error:string,description:string){
  noStore(res);
  return res.status(status).json({error,error_description:description});
}

function resourceMetadata(req:express.Request){
  const base=origin(req);
  return {
    resource:base,
    authorization_servers:[base],
    scopes_supported:["hakim.read","hakim.write","offline_access"],
    resource_documentation:base+"/privacy",
    resource_policy_uri:base+"/privacy",
    resource_tos_uri:base+"/terms"
  };
}

app.get("/.well-known/openai-apps-challenge",(_req,res)=>{
  const token=process.env.OPENAI_APPS_CHALLENGE;
  if(!token||!/^[A-Za-z0-9._~-]{8,512}$/.test(token)) return res.status(404).end();
  noStore(res);
  res.type("text/plain").send(token);
});

app.get("/",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>حكيم — ذراع ChatGPT التنفيذي</title><style>body{font-family:system-ui;max-width:760px;margin:auto;padding:40px;line-height:1.8}a{color:inherit}.box{padding:18px;border:1px solid #ddd;border-radius:16px;margin:18px 0}</style><h1>حكيم</h1><p>جسر آمن يجعل ChatGPT طبقة المحادثة والاستدلال، ويجعل تطبيق حكيم على جهاز المستخدم ذراع تنفيذ مأذونًا.</p><div class="box"><strong>لا يحتاج مفتاح OpenAI API.</strong><br>الأوامر والنتائج مشفرة، ولا توجد قناة shell أو root. الأفعال التي تغيّر حالة الهاتف تبقى خلف موافقة Android.</div><p><a href="/privacy">الخصوصية</a> · <a href="/terms">الشروط</a> · <a href="/support">الدعم</a> · <a href="/health">الحالة</a></p></html>`));


app.get("/health",(_req,res)=>res.json({
  ok:true,
  service:"hakim-chatgpt-bridge",
  model_provider:"chatgpt-host",
  openai_api_key_required:false,
  result_transport:"end-to-end-encrypted-outbound-only",
  auth:"oauth-2.1-pkce-cimd",
  production_storage_required:true
}));

app.get(["/.well-known/oauth-protected-resource","/.well-known/oauth-protected-resource/mcp"],(req,res)=>{
  noStore(res);
  res.json(resourceMetadata(req));
});

app.get("/.well-known/oauth-authorization-server",(req,res)=>{
  const base=origin(req);
  noStore(res);
  res.json({
    issuer:base,
    authorization_endpoint:base+"/oauth/authorize",
    token_endpoint:base+"/oauth/token",
    response_types_supported:["code"],
    grant_types_supported:["authorization_code","refresh_token"],
    code_challenge_methods_supported:["S256"],
    token_endpoint_auth_methods_supported:["none"],
    scopes_supported:["hakim.read","hakim.write","offline_access"],
    client_id_metadata_document_supported:true,
    authorization_response_iss_parameter_supported:true
  });
});

app.get("/oauth/authorize",(req,res)=>{
  try{
    if(one(req.query.response_type)!=="code") return oauthError(res,400,"unsupported_response_type","Only authorization code is supported.");
    const clientId=one(req.query.client_id);
    const redirectUri=one(req.query.redirect_uri);
    const state=one(req.query.state);
    const codeChallenge=one(req.query.code_challenge);
    const method=one(req.query.code_challenge_method);
    const resource=one(req.query.resource);
    const base=origin(req);
    if(!isChatGPTClientId(clientId)) return oauthError(res,400,"invalid_client","Only ChatGPT CIMD clients are accepted.");
    if(!isChatGPTRedirectUri(redirectUri)) return oauthError(res,400,"invalid_request","Invalid ChatGPT redirect URI.");
    if(method!=="S256"||!/^[A-Za-z0-9_-]{43,128}$/.test(codeChallenge)) return oauthError(res,400,"invalid_request","PKCE S256 is required.");
    if(resource!==base) return oauthError(res,400,"invalid_target","The OAuth resource must match this Hakim bridge.");
    const scopes=normalizeScopes(one(req.query.scope)||undefined);
    const credential=createDeviceCredential();
    const context=makeAuthorizeContext(oauthSecret,{
      credential,clientId,redirectUri,state,codeChallenge,resource,scopes
    });
    const link=pairingUrl(credential);
    noStore(res);
    res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ربط حكيم</title>
<style>body{font-family:system-ui;max-width:680px;margin:auto;padding:32px;line-height:1.8}a,button{font-size:18px}button{padding:12px 18px}.box{background:#f5f5f5;padding:14px;border-radius:14px}</style>
<h1>ربط جهاز حكيم</h1>
<p>ChatGPT سيستخدم قدرات حسابك نفسه. هذه الخطوة تربط فقط جهاز حكيم بهذا الاتصال؛ لا يوجد مفتاح OpenAI API.</p>
<p><a href="${html(link)}">١) ربط الهاتف</a></p>
<form method="post" action="/oauth/authorize"><input type="hidden" name="context" value="${html(context)}"><button type="submit">٢) تحقق من الهاتف وأكمل</button></form>
${reviewModeEnabled()?`<details class="box"><summary>وصول المراجع</summary><form method="post" action="/oauth/authorize"><input type="hidden" name="context" value="${html(context)}"><label>اسم المراجع <input name="review_user" autocomplete="username"></label><br><label>كلمة المرور <input name="review_password" type="password" autocomplete="current-password"></label><br><button type="submit">دخول مراجعة آمن</button></form></details>`:""}
<p class="box">لن يصدر رمز الوصول حتى يؤكد تطبيق حكيم الاقتران برسالة مشفرة.</p>
</html>`);
  }catch(e){
    oauthError(res,400,"invalid_request",e instanceof Error?e.message:"authorization_failed");
  }
});

app.post("/oauth/authorize",async(req,res)=>{
  try{
    const context=openAuthorizeContext(oauthSecret,one(req.body.context));
    const reviewUser=one(req.body.review_user);
    const reviewPassword=one(req.body.review_password);
    const reviewRequested=reviewUser.length>0||reviewPassword.length>0;
    if(reviewRequested){
      if(!reviewAttemptAllowed(req.ip??"unknown")){
        return oauthError(res,429,"temporarily_unavailable","Too many reviewer login attempts.");
      }
      if(!reviewCredentialsMatch(process.env,reviewUser,reviewPassword)){
        return oauthError(res,403,"access_denied","Invalid reviewer credential.");
      }
      context.credential.topic="hakim_review_"+randomSecret(18);
      context.credential.resultTopic="hakim_review_result_"+randomSecret(18);
    }
    const paired=reviewRequested ? true : await pollPairAck(context.credential,10_000);
    if(!paired){
      noStore(res);
      const link=pairingUrl(context.credential);
      return res.status(409).type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>حكيم — لم يثبت الربط</title>
<body><h1>لم يصل تأكيد الهاتف بعد</h1><p><a href="${html(link)}">افتح رابط ربط الهاتف</a> ثم أعد التحقق.</p>
<form method="post" action="/oauth/authorize"><input type="hidden" name="context" value="${html(one(req.body.context))}"><button type="submit">تحقق مجددًا</button></form></body></html>`);
    }
    const code=await codeStore.issue({
      credential:context.credential,
      clientId:context.clientId,
      redirectUri:context.redirectUri,
      codeChallenge:context.codeChallenge,
      resource:context.resource,
      scopes:context.scopes,
      expiresAt:Date.now()+2*60_000
    });
    const redirect=new URL(context.redirectUri);
    redirect.searchParams.set("code",code);
    if(context.state) redirect.searchParams.set("state",context.state);
    redirect.searchParams.set("iss",origin(req));
    noStore(res);
    return res.redirect(302,redirect.toString());
  }catch(e){
    return oauthError(res,400,"access_denied",e instanceof Error?e.message:"pairing_failed");
  }
});

app.post("/oauth/token",async(req,res)=>{
  try{
    const base=origin(req);
    const grant=one(req.body.grant_type);
    noStore(res);

    if(grant==="authorization_code"){
      const record=await codeStore.consume(one(req.body.code));
      if(one(req.body.client_id)!==record.clientId) return oauthError(res,400,"invalid_grant","Client mismatch.");
      if(one(req.body.redirect_uri)!==record.redirectUri) return oauthError(res,400,"invalid_grant","Redirect mismatch.");
      if(one(req.body.resource)!==record.resource||record.resource!==base) return oauthError(res,400,"invalid_target","Resource mismatch.");
      const verifier=one(req.body.code_verifier);
      if(!/^[A-Za-z0-9._~-]{43,128}$/.test(verifier)||pkceS256(verifier)!==record.codeChallenge)
        return oauthError(res,400,"invalid_grant","PKCE verification failed.");
      const access=issueAccessToken(oauthSecret,{
        credential:record.credential,clientId:record.clientId,aud:record.resource,scopes:record.scopes
      });
      const refresh=record.scopes.includes("offline_access")
        ? issueRefreshToken(oauthSecret,{
            credential:record.credential,clientId:record.clientId,aud:record.resource,scopes:record.scopes
          })
        : undefined;
      return res.json({
        access_token:access,token_type:"Bearer",expires_in:3600,
        ...(refresh?{refresh_token:refresh}:{}),scope:record.scopes.join(" ")
      });
    }

    if(grant==="refresh_token"){
      const current=openRefreshToken(oauthSecret,one(req.body.refresh_token),base);
      if(one(req.body.client_id)!==current.clientId) return oauthError(res,400,"invalid_grant","Client mismatch.");
      const requested=req.body.scope?normalizeScopes(one(req.body.scope)):current.scopes;
      if(requested.some(s=>!current.scopes.includes(s))) return oauthError(res,400,"invalid_scope","Scope escalation is not allowed.");
      const access=issueAccessToken(oauthSecret,{
        credential:current.credential,clientId:current.clientId,aud:current.aud,scopes:requested
      });
      const refresh=requested.includes("offline_access")
        ? issueRefreshToken(oauthSecret,{
            credential:current.credential,clientId:current.clientId,aud:current.aud,scopes:requested
          })
        : undefined;
      return res.json({
        access_token:access,token_type:"Bearer",expires_in:3600,
        ...(refresh?{refresh_token:refresh}:{}),scope:requested.join(" ")
      });
    }

    return oauthError(res,400,"unsupported_grant_type","Supported grants: authorization_code, refresh_token.");
  }catch(e){
    return oauthError(res,400,"invalid_grant",e instanceof Error?e.message:"token_exchange_failed");
  }
});

app.get("/pair",(_req,res)=>{
  if(process.env.HAKIM_ALLOW_DEV_BEARER!=="1") return res.status(404).end();
  const c=createDeviceCredential();
  const link=pairingUrl(c);
  const bearer=encodeBearer(c);
  noStore(res);
  res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ربط حكيم — تطوير</title>
<h1>ربط تطويري فقط</h1><p><a href="${html(link)}">ربط الهاتف</a></p><p style="word-break:break-all">${html(bearer)}</p></html>`);
});


app.get("/support",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>دعم حكيم</title><body><h1>دعم حكيم</h1><p>حكيم يربط ChatGPT بجهاز Android مأذون. إذا تعذر الربط، تحقق من أن تطبيق حكيم مثبت ومفتوح وأن الجهاز متصل بالإنترنت، ثم أعد عملية الاقتران من ChatGPT.</p><p>لأعطال الأمان أو الخصوصية أو التنفيذ، افتح بلاغًا في مستودع المشروع: <a href="https://github.com/smileeyes1/sovereign-android-assistant/issues">GitHub Issues</a>. لا ترسل رموز الربط أو مفاتيح الوصول أو لقطات حساسة في بلاغ عام.</p></body></html>`));

app.get("/privacy",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>خصوصية حكيم</title><body><h1>خصوصية حكيم</h1><p>الجسر لا يحتاج مفتاح OpenAI ولا يحتفظ بمحتوى الجهاز افتراضيًا. أوامر الهاتف ونتائجه تنتقل مشفرة طرفًا لطرف، ولا يتيح الجسر shell أو root. الأفعال التي تغيّر حالة الهاتف تبقى خلف موافقة أندرويد.</p><p>ChatGPT نفسه يعالج المحادثة وفق إعدادات حساب المستخدم وسياسات OpenAI. توفر الأدوات والنماذج يعتمد على الخطة والمنطقة والواجهة.</p></body></html>`));

app.get("/terms",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>شروط حكيم</title><body><h1>شروط حكيم</h1><p>حكيم ذراع تنفيذ اختياري لجهاز يملكه المستخدم أو يملك صلاحية إدارته. لا يمنح التطبيق صلاحيات خارج ما وافق عليه المستخدم والنظام. الأفعال الحساسة أو غير القابلة للعكس لا تُنفذ بلا الموافقات المطلوبة.</p></body></html>`));

app.all("/mcp",async(req,res)=>{
  const base=origin(req);
  const metadataUrl=base+"/.well-known/oauth-protected-resource";
  try{
    const auth=String(req.headers.authorization??"");
    if(!auth.startsWith("Bearer ")) throw new Error("bearer_required");
    const raw=auth.slice(7);
    let credential;
    let scopes:string[];
    if(raw.startsWith("HAKIM-B2.")&&process.env.HAKIM_ALLOW_DEV_BEARER==="1"){
      credential=decodeBearer(raw);
      scopes=["hakim.read","hakim.write"];
    }else{
      const token=openAccessToken(oauthSecret,raw,base);
      credential=token.credential;
      scopes=token.scopes;
    }
    if(req.body?.method==="tools/list"){
      return res.json({jsonrpc:"2.0",id:req.body.id,result:{tools:chatgptToolList()}});
    }

    const server=createHakimServer(credential,scopes,metadataUrl);
    const transport=new StreamableHTTPServerTransport({sessionIdGenerator:undefined});
    res.on("close",()=>{void transport.close();void server.close();});
    await server.connect(transport);
    await transport.handleRequest(req,res,req.body);
  }catch(e){
    if(!res.headersSent){
      res.setHeader("WWW-Authenticate",`Bearer resource_metadata="${metadataUrl}", scope="hakim.read hakim.write"`);
      res.status(401).json({error:"invalid_token",error_description:e instanceof Error?e.message:"authorization_required"});
    }
  }
});

const port=Number(process.env.PORT??3000);
app.listen(port,"0.0.0.0",()=>console.log(`Hakim ChatGPT bridge listening on :${port}`));
