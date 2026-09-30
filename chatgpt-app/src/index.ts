import express from "express";
import cors from "cors";
import os from "node:os";
import path from "node:path";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import {
  createDeviceCredential,decodeBearer,encodeBearer,pairingUrl,randomSecret,
  makeEnvelope,encryptCarrier,decryptResult
} from "./protocol.js";
import {
  FileCodeStore,isChatGPTClientId,isChatGPTRedirectUri,issueAccessToken,issueRefreshToken,
  makeAuthorizeContext,normalizeScopes,openAccessToken,openAuthorizeContext,openRefreshToken,
  pkceS256,requireProductionOAuthConfig,reviewCredentialsMatch
} from "./oauth.js";
import { normalizeDeviceWaitMs,pollPairAck,pollResult,publishCommand } from "./relay.js";
import { directRelayStore } from "./direct-relay.js";
import { ContinuityRevisionConflict,continuityStore } from "./continuity-store.js";
import { developmentRequestStore } from "./development-request-store.js";
import { chatgptToolList,createHakimServer } from "./server.js";
import { LIVE_PREFLIGHT_VERSION,MIN_FIELD_VERSION,fetchLivePreflight } from "./live-preflight.js";
import { GOVERNANCE_SUMMARY,SOVEREIGN_GOVERNANCE_VERSION } from "./governance.js";
import {
  getLocalRenderStatus,resolveLocalArtifact,submitLocalRender
} from "./local-cinematic-renderer.js";

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
await codeStore.cleanupExpired();
await directRelayStore.init();
await continuityStore.init();
await developmentRequestStore.init();
const oauthCleanupTimer=setInterval(()=>{void codeStore.cleanupExpired();},60_000);
oauthCleanupTimer.unref?.();
const relayCleanupTimer=setInterval(()=>{void directRelayStore.cleanup();},60_000);
relayCleanupTimer.unref?.();

const reviewAttempts=new Map<string,{count:number;windowStart:number}>();
const statusProbeCooldownByTopic=new Map<string,number>();
const statusProbeRequests=new Map<string,{sentAt:number}>();
const STATUS_PROBE_COOLDOWN_MS=5*60_000;
const UPDATE_20315_SIGNAL_AT_MS=1790705524*1000;

function maybeMakeStatusProbe(topic:string,key:string){
  const now=Date.now();
  const previous=statusProbeCooldownByTopic.get(topic)??0;
  if(now-previous<STATUS_PROBE_COOLDOWN_MS) return null;
  const envelope=makeEnvelope(key,"status",{},60_000);
  statusProbeCooldownByTopic.set(topic,now);
  statusProbeRequests.set(envelope.request_id,{sentAt:now});
  return {
    request_id:envelope.request_id,
    carrier:encryptCarrier(key,envelope),
    expires_at_ms:envelope.expires_at_ms
  };
}

function logSanitizedStatusProbe(_resultTopic:string,key:string,carrier:string){
  try{
    const decoded=decryptResult(key,carrier) as {
      request_id?:unknown;
      result?:{
        ok?:unknown;
        version_code?:unknown;
        version_name?:unknown;
        secure_relay_state?:unknown;
        secure_relay_running?:unknown;
        secure_relay_connected?:unknown;
        browser_service_running?:unknown;
        execution_fabric?:Record<string,unknown>;
        fault_containment?:Record<string,unknown>;
        self_check?:unknown;
        network_diagnostics?:Record<string,unknown>;
        auto_update?:Record<string,unknown>;
      };
    };
    const requestId=typeof decoded?.request_id==="string"?decoded.request_id:"";
    if(!statusProbeRequests.has(requestId)) return;
    // Log only fixed state enums and booleans. Raw pages, UI nodes, URLs,
    // network identifiers, result carriers and credentials stay out of logs.
    const fixed=(raw:unknown,allowed:readonly string[])=>
      typeof raw==="string"&&allowed.includes(raw)?raw:null;
    const flag=(raw:unknown)=>typeof raw==="boolean"?raw:null;
    const boundedCount=(raw:unknown)=>
      typeof raw==="number"&&Number.isInteger(raw)&&raw>=0&&raw<=1024?raw:null;
    const result=decoded.result;
    const fabric=result?.execution_fabric;
    const containment=result?.fault_containment;
    const diagnostics=result?.network_diagnostics;
    const autoUpdate=result?.auto_update;
    const safe={
      event:"hakim_status_probe",
      ok:flag(result?.ok),
      version_code:typeof result?.version_code==="number"?result.version_code:null,
      version_name_has_extender_survey:typeof result?.version_name==="string"?
        result.version_name.includes("extender-survey"):null,
      secure_relay_state:fixed(result?.secure_relay_state,
        ["direct_connected","direct_recovering","legacy_fallback","unconfigured","unknown"]),
      secure_relay_running:flag(result?.secure_relay_running),
      secure_relay_connected:flag(result?.secure_relay_connected),
      browser_service_running:flag(result?.browser_service_running),
      execution_fabric:{
        state:fixed(fabric?.state,["ONLINE","RECOVERING","OFFLINE","DEGRADED","UNKNOWN"]),
        online:flag(fabric?.online),
        secure_relay_connected:flag(fabric?.secure_relay_connected),
        legacy_connected:flag(fabric?.legacy_connected)
      },
      fault_containment:{
        active:flag(containment?.fault_containment),
        critical_path_silent_failures_forbidden:flag(containment?.critical_path_silent_failures_forbidden),
        bounded_retry:flag(containment?.bounded_retry),
        circuit_breaker:flag(containment?.circuit_breaker),
        high_impact_fail_closed:flag(containment?.high_impact_fail_closed),
        raw_exception_message_persisted:flag(containment?.raw_exception_message_persisted),
        open_circuits:boundedCount(containment?.open_circuits),
        critical_blocks:boundedCount(containment?.critical_blocks),
        high_impact_blocked:flag(containment?.high_impact_blocked),
        recovery_required:flag(containment?.recovery_required)
      },
      self_check:fixed(result?.self_check,["PASS","PASS_WITH_WARNINGS","FAIL_CLOSED","NOT_TESTED"]),
      network_diagnostics_available:!!diagnostics&&typeof diagnostics==="object"&&
        Object.keys(diagnostics).length>0,
      auto_update:{
        state:fixed(autoUpdate?.state,[
          "unknown","scheduled","checking","up_to_date","update_found","downloading",
          "verified","ready_in_downloads","push_received","realtime_connected",
          "realtime_disconnected","realtime_closed","check_network_failed",
          "check_http_failed","download_failed","download_http_failed",
          "download_size_mismatch","verify_size_failed","verify_sha_failed",
          "verify_identity_failed","export_failed"
        ]),
        push_seen:typeof autoUpdate?.last_push_at==="number"?autoUpdate.last_push_at>0:null,
        last_check_after_20315_signal:typeof autoUpdate?.last_check_at==="number"?
          autoUpdate.last_check_at>=UPDATE_20315_SIGNAL_AT_MS:null,
        last_push_after_20315_signal:typeof autoUpdate?.last_push_at==="number"?
          autoUpdate.last_push_at>=UPDATE_20315_SIGNAL_AT_MS:null,
        last_discovered_version:typeof autoUpdate?.last_discovered_version==="number"?
          autoUpdate.last_discovered_version:null,
        last_exported_version:typeof autoUpdate?.last_exported_version==="number"?
          autoUpdate.last_exported_version:null
      }
    };
    console.log("HAKIM_STATUS_PROBE "+JSON.stringify(safe));
    statusProbeRequests.delete(requestId);
  }catch{
    // Never log carrier/key/raw device data on probe decode failures.
  }
}
function logSanitizedHealthBeacon(key:string,carrier:string){
  try{
    const decoded=decryptResult(key,carrier) as {
      status?:unknown;
      result?:{
        version_code?:unknown;
        apk_sha256?:unknown;
        service_running?:unknown;
        service_connected?:unknown;
        constitution?:unknown;
        sovereign_acceptance_gate?:Record<string,unknown>;
        governance_catalog?:Record<string,unknown>;
      };
    };
    if(decoded?.status!=="health") return;
    const result=decoded.result;
    if(!result||typeof result!=="object") return;
    const apkSha=typeof result.apk_sha256==="string"&&/^[0-9a-f]{64}$/i.test(result.apk_sha256)
      ?result.apk_sha256.toLowerCase():null;
    const constitution=typeof result.constitution==="string"&&
      /^[A-Z0-9._-]{8,120}$/.test(result.constitution)?result.constitution:null;
    const gate=result.sovereign_acceptance_gate;
    const catalog=result.governance_catalog;
    const fixed=(raw:unknown,allowed:readonly string[])=>
      typeof raw==="string"&&allowed.includes(raw)?raw:null;
    const bool=(raw:unknown)=>typeof raw==="boolean"?raw:null;
    const safe={
      event:"hakim_health_evidence",
      version_code:typeof result.version_code==="number"?result.version_code:null,
      apk_sha256:apkSha,
      service_running:bool(result.service_running),
      service_connected:bool(result.service_connected),
      constitution,
      sovereign_acceptance_gate:{
        active:bool(gate?.acceptance_gate),
        version:typeof gate?.version==="string"&&/^[A-Z0-9._-]{8,120}$/.test(gate.version)?gate.version:null,
        state:fixed(gate?.state,["IDLE","CONTRACTED","VERIFYING","READY_TO_DELIVER","DELIVERED","BLOCKED"]),
        ready:bool(gate?.ready),
        evidence_required:bool(gate?.evidence_required),
        material_gap_blocks_close:bool(gate?.material_gap_blocks_close),
        same_tested_delivered_artifact_required:bool(gate?.same_tested_delivered_artifact_required),
        regression_gate_supported:bool(gate?.regression_gate_supported)
      },
      governance_catalog:{
        active:bool(catalog?.governance_catalog),
        version:typeof catalog?.version==="string"&&/^[A-Z0-9._-]{8,120}$/.test(catalog.version)?catalog.version:null,
        not_bound_to_custom_8000_limit:bool(catalog?.not_bound_to_custom_8000_limit),
        adaptive_rule_selection:bool(catalog?.adaptive_rule_selection),
        full_constitution_retained:bool(catalog?.full_constitution_retained),
        full_rule_count:typeof catalog?.full_rule_count==="number"?catalog.full_rule_count:null
      }
    };
    console.log("HAKIM_HEALTH_EVIDENCE "+JSON.stringify(safe));
  }catch{
    // Never log raw carriers, relay keys, UI/page content, selected domains or goal data.
  }
}

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
  return !!process.env.HAKIM_REVIEW_USER&&!!process.env.HAKIM_REVIEW_PASSWORD;
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

function accessSession(req:express.Request){
  const base=origin(req);
  const auth=String(req.headers.authorization??"");
  if(!auth.startsWith("Bearer ")) throw new Error("bearer_required");
  const raw=auth.slice(7).trim();
  if(!raw) throw new Error("bearer_required");
  if(raw.startsWith("HAKIM-B2.")&&process.env.HAKIM_ALLOW_DEV_BEARER==="1"){
    return {base,credential:decodeBearer(raw),scopes:["hakim.read","hakim.write"]};
  }
  const token=openAccessToken(oauthSecret,raw,base);
  return {base,credential:token.credential,scopes:token.scopes};
}

function requireAccessScope(req:express.Request,scope:"hakim.read"|"hakim.write"){
  const session=accessSession(req);
  if(!session.scopes.includes(scope)) throw new Error("insufficient_scope");
  return session;
}

function accessError(req:express.Request,res:express.Response,e:unknown,scope:"hakim.read"|"hakim.write"){
  noStore(res);
  const base=origin(req);
  const message=e instanceof Error?e.message:"authorization_required";
  if(message==="insufficient_scope"){
    res.setHeader("WWW-Authenticate",`Bearer resource_metadata="${base}/.well-known/oauth-protected-resource", scope="${scope}", error="insufficient_scope"`);
    return res.status(403).json({error:"insufficient_scope"});
  }
  res.setHeader("WWW-Authenticate",`Bearer resource_metadata="${base}/.well-known/oauth-protected-resource", scope="${scope}"`);
  return res.status(401).json({error:"invalid_token"});
}

function parseCheckpointBody(raw:unknown){
  if(!raw||typeof raw!=="object"||Array.isArray(raw)) throw new Error("invalid_checkpoint_body");
  const body=raw as Record<string,unknown>;
  const allowed=new Set(["goal_id","goal_label","stage","last_verified","next_step","blocker","status","expected_revision"]);
  if(Object.keys(body).some(k=>!allowed.has(k))) throw new Error("unknown_checkpoint_field");
  const requiredString=(key:string,max:number)=>{
    const value=body[key];
    if(typeof value!=="string") throw new Error("invalid_"+key);
    const trimmed=value.trim();
    if(!trimmed||trimmed.length>max) throw new Error("invalid_"+key);
    return trimmed;
  };
  const optionalString=(key:string,max:number)=>{
    const value=body[key];
    if(value===undefined) return undefined;
    if(typeof value!=="string"||value.length>max) throw new Error("invalid_"+key);
    return value;
  };
  const status=body.status;
  if(typeof status!=="string"||!["active","waiting","blocked","complete","cancelled"].includes(status)){
    throw new Error("invalid_status");
  }
  const expected=body.expected_revision;
  if(expected!==undefined&&(!Number.isInteger(expected)||Number(expected)<0)){
    throw new Error("invalid_expected_revision");
  }
  const goalId=optionalString("goal_id",96);
  if(goalId!==undefined&&!/^[A-Za-z0-9._:-]{8,96}$/.test(goalId)) throw new Error("invalid_goal_id");
  return {
    goal_id:goalId,
    goal_label:requiredString("goal_label",240),
    stage:requiredString("stage",120),
    last_verified:optionalString("last_verified",280),
    next_step:optionalString("next_step",280),
    blocker:optionalString("blocker",220),
    status:status as "active"|"waiting"|"blocked"|"complete"|"cancelled",
    expected_revision:expected===undefined?undefined:Number(expected)
  };
}

function ifMatchRevision(req:express.Request){
  const raw=String(req.headers["if-match"]??"").trim();
  if(!raw) return undefined;
  const match=/^"hc3-r([0-9]+)"$/.exec(raw);
  if(!match) throw new Error("invalid_if_match");
  const value=Number(match[1]);
  if(!Number.isSafeInteger(value)||value<0) throw new Error("invalid_if_match");
  return value;
}

function setContinuityEtag(res:express.Response,revision:number){
  res.setHeader("ETag",`"hc3-r${revision}"`);
}

function directRelayKey(req:express.Request){
  const auth=String(req.headers.authorization??"");
  if(!auth.startsWith("Bearer ")) throw new Error("relay_auth_failed");
  const key=auth.slice(7).trim();
  if(!/^[A-Za-z0-9_-]{40,100}$/.test(key)) throw new Error("relay_auth_failed");
  return key;
}

function directRelayError(res:express.Response,e:unknown){
  noStore(res);
  const message=e instanceof Error?e.message:"direct_relay_failed";
  const status=message==="relay_auth_failed"?401:400;
  return res.status(status).json({error:status===401?"unauthorized":"invalid_request"});
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

const UNIVERSAL_GATEWAY_VERSION="HAKIM_UNIVERSAL_GATEWAY_V1";
const GATEWAY_OPERATION_ID=/^chatgpt-[A-Za-z0-9_-]{12,80}$/;
const GATEWAY_NAV_KINDS=new Set(["home","back","recents"]);

function gatewayCapabilities(base:string){
  return {
    gateway_version:UNIVERSAL_GATEWAY_VERSION,
    provider_neutral:true,
    live_preflight:LIVE_PREFLIGHT_VERSION,
    minimum_field_version:MIN_FIELD_VERSION,
    protocols:{
      https_json:base+"/gateway/v1/bootstrap",
      mcp:base+"/mcp",
      continuity:base+"/continuity/v1/state",
      openapi:base+"/gateway/v1/openapi.json"
    },
    actions:{
      bootstrap:{method:"GET",path:"/gateway/v1/bootstrap",scope:"hakim.read"},
      open:{method:"POST",path:"/gateway/v1/actions/open",scope:"hakim.write",approval_required:true},
      navigate:{method:"POST",path:"/gateway/v1/actions/navigate",scope:"hakim.write",approval_required:true},
      operation:{method:"GET",path:"/gateway/v1/operations/{operation_token}",scope:"hakim.read"},
      checkpoint:{method:"PUT",path:"/continuity/v1/checkpoint",scope:"hakim.write"}
    },
    invariants:[
      "Every device mutation performs a fresh live preflight inside Hakim.",
      "A stale conversation or model state never authorizes a device mutation.",
      "approval_requested is not success; verify the resulting operation state.",
      "Adapters must not receive device relay keys, cookies, provider session tokens, or conversation transcripts."
    ],
    authentication:{
      resource_metadata:base+"/.well-known/oauth-protected-resource",
      current_first_party_profile:"ChatGPT OAuth 2.1 PKCE/CIMD",
      external_provider_policy:"Use a Hakim-authorized adapter. Do not reuse device relay credentials as provider credentials."
    }
  };
}

function gatewayRuntimeError(res:express.Response,e:unknown){
  noStore(res);
  return res.status(503).json({
    ok:false,
    error:"hakim_runtime_unavailable",
    detail:e instanceof Error?e.message:"runtime_unavailable",
    retryable:true
  });
}

function gatewayOpenPayload(raw:unknown){
  if(!raw||typeof raw!=="object"||Array.isArray(raw)) throw new Error("invalid_open_body");
  const body=raw as Record<string,unknown>;
  if(Object.keys(body).some(k=>!["package","url"].includes(k))) throw new Error("unknown_open_field");
  const pkg=typeof body.package==="string"?body.package.trim():"";
  const url=typeof body.url==="string"?body.url.trim():"";
  if(Boolean(pkg)===Boolean(url)) throw new Error("exactly_one_target_required");
  if(pkg&&!/^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/.test(pkg)) throw new Error("invalid_package_name");
  if(url){
    const u=new URL(url);
    if(u.protocol!=="http:"&&u.protocol!=="https:") throw new Error("unsupported_url_scheme");
    if(url.length>1500) throw new Error("url_too_long");
  }
  return {package:pkg,url};
}

function gatewayNavigateKind(raw:unknown){
  if(!raw||typeof raw!=="object"||Array.isArray(raw)) throw new Error("invalid_navigate_body");
  const body=raw as Record<string,unknown>;
  if(Object.keys(body).some(k=>k!=="kind")) throw new Error("unknown_navigate_field");
  const kind=typeof body.kind==="string"?body.kind:"";
  if(!GATEWAY_NAV_KINDS.has(kind)) throw new Error("invalid_navigation_kind");
  return kind as "home"|"back"|"recents";
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
  model_provider:"adapter-neutral",
  openai_api_key_required:false,
  result_transport:"end-to-end-encrypted-outbound-only",
  device_transport:"hakim-direct-https-v1",
  legacy_transport_fallback:process.env.HAKIM_NTFY_FALLBACK!=="0",
  auth:"oauth-2.1-pkce-cimd",
  production_storage_required:true,
  continuity_protocols:["MCP","HAKIM_CONTINUITY_HTTP_V1","HAKIM_DEVICE_CONTINUITY_V1","HAKIM_UNIVERSAL_GATEWAY_V1"],
  continuity_conflict_control:"checkpoint_revision",
  universal_gateway:"HAKIM_UNIVERSAL_GATEWAY_V1",
  public_safe:process.env.HAKIM_PUBLIC_SAFE!=="0",
  governance_version:SOVEREIGN_GOVERNANCE_VERSION,
  authority_boundary:"external_content_is_data_not_instruction",
  approval_requested_is_success:false,
  tool_result_is_goal_complete:false,
  governance:GOVERNANCE_SUMMARY,
  reviewer_demo:reviewModeEnabled(),
  public_tools:process.env.HAKIM_PUBLIC_SAFE!=="0"
    ?[
      "get_device_status","open_target","navigate_device","get_request_result",
      ...(process.env.HAKIM_LAN_CONTROL==="1"
        ?["list_network_devices","authorize_network_device","control_network_device","revoke_network_device"]
        :[])
    ]
    :undefined
}));

app.get(["/.well-known/oauth-protected-resource","/.well-known/oauth-protected-resource/mcp","/.well-known/oauth-protected-resource/gateway"],(req,res)=>{
  noStore(res);
  res.json(resourceMetadata(req));
});

app.get("/.well-known/hakim-continuity",(req,res)=>{
  const base=origin(req);
  noStore(res);
  res.json({
    protocol:"HAKIM_CONTINUITY_HTTP_V1",
    canonical_state:base+"/continuity/v1/state",
    checkpoint_write:base+"/continuity/v1/checkpoint",
    mcp_endpoint:base+"/mcp",
    oauth_resource_metadata:base+"/.well-known/oauth-protected-resource",
    authorization_server:base,
    scopes:{read:"hakim.read",write:"hakim.write"},
    concurrency:"Read checkpoint_revision first. Write with expected_revision or If-Match. On HTTP 409, re-read and reconcile; never blindly overwrite.",
    resume:"Pending operation_token values are idempotency handles. Query the existing request result instead of replaying the original action after a chat/model/session change.",
    privacy:"The continuity capsule excludes device screen/page/notification contents, typed values, relay keys, credentials, cookies, model prompts and conversation transcripts.",
    provider_neutral:true
  });
});

app.get("/.well-known/hakim-gateway",(req,res)=>{
  noStore(res);
  res.json(gatewayCapabilities(origin(req)));
});

app.get("/gateway/v1/openapi.json",(req,res)=>{
  const base=origin(req);
  noStore(res);
  res.json({
    openapi:"3.1.0",
    info:{
      title:"Hakim Universal Gateway",
      version:"1.0.0",
      description:"Provider-neutral, privacy-bounded Hakim gateway. Bootstrap current live state before device work; every mutation re-runs live preflight server-side."
    },
    servers:[{url:base}],
    security:[{hakimOAuth:["hakim.read"]}],
    paths:{
      "/gateway/v1/bootstrap":{
        get:{summary:"Read fresh Hakim runtime plus durable continuation",security:[{hakimOAuth:["hakim.read"]}],responses:{"200":{description:"Live bootstrap"}}}
      },
      "/gateway/v1/actions/open":{
        post:{summary:"Request opening one app or HTTP/HTTPS URL after mandatory live preflight",security:[{hakimOAuth:["hakim.write"]}],responses:{"202":{description:"Approval requested"},"409":{description:"Live preflight blocked"}}}
      },
      "/gateway/v1/actions/navigate":{
        post:{summary:"Request Home, Back or Recents after mandatory live preflight",security:[{hakimOAuth:["hakim.write"]}],responses:{"202":{description:"Approval requested"},"409":{description:"Live preflight blocked"}}}
      },
      "/gateway/v1/operations/{operation_token}":{
        get:{summary:"Check a previously requested operation without replaying it",security:[{hakimOAuth:["hakim.read"]}],parameters:[{name:"operation_token",in:"path",required:true,schema:{type:"string"}}],responses:{"200":{description:"Operation state"}}}
      }
    },
    components:{
      securitySchemes:{
        hakimOAuth:{
          type:"oauth2",
          flows:{
            authorizationCode:{
              authorizationUrl:base+"/oauth/authorize",
              tokenUrl:base+"/oauth/token",
              scopes:{"hakim.read":"Read bounded Hakim state","hakim.write":"Request bounded approved device changes"}
            }
          }
        }
      }
    },
    "x-hakim-provider-neutral":true,
    "x-hakim-auth-note":"The current production OAuth authorization profile is restricted to approved ChatGPT CIMD clients. Other AI providers must use an explicitly authorized Hakim adapter; the device relay secret is never a provider credential."
  });
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

app.get("/oauth/authorize",async(req,res)=>{
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
    await directRelayStore.registerCredential(credential,10*60_000);
    const context=makeAuthorizeContext(oauthSecret,{
      credential,clientId,redirectUri,state,codeChallenge,resource,scopes
    });
    const link=pairingUrl(credential,base.startsWith("https://")?base:undefined);
    noStore(res);
    res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ربط حكيم</title>
<style>body{font-family:system-ui;max-width:680px;margin:auto;padding:32px;line-height:1.8}a,button{font-size:18px}button{padding:12px 18px}.box{background:#f5f5f5;padding:14px;border-radius:14px}</style>
<h1>ربط جهاز حكيم</h1>
<p>ChatGPT سيستخدم قدرات حسابك نفسه. هذه الخطوة تربط فقط جهاز حكيم بهذا الاتصال؛ لا يوجد مفتاح OpenAI API.</p>
<p><a href="${html(link)}">١) ربط الهاتف</a></p>
<p class="box">افتح زر الربط على هاتفك مباشرة. لا تنسخ الرابط إلى محادثة، ولا تشارك صورة تظهره؛ فهو يحتوي بيانات اقتران سرية مؤقتة.</p>
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
    if(!reviewRequested) await directRelayStore.registerCredential(context.credential,10*60_000);
    const paired=reviewRequested ? true : await pollPairAck(context.credential,10_000);
    if(!paired){
      noStore(res);
      const retryBase=origin(req);
      const link=pairingUrl(context.credential,retryBase.startsWith("https://")?retryBase:undefined);
      return res.status(409).type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>حكيم — لم يثبت الربط</title>
<body><h1>لم يصل تأكيد الهاتف بعد</h1><p><a href="${html(link)}">افتح رابط ربط الهاتف</a> ثم أعد التحقق.</p>
<form method="post" action="/oauth/authorize"><input type="hidden" name="context" value="${html(one(req.body.context))}"><button type="submit">تحقق مجددًا</button></form></body></html>`);
    }
    if(!reviewRequested) await directRelayStore.markCredentialPaired(context.credential);
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

app.get("/pair",async(req,res)=>{
  if(process.env.HAKIM_ALLOW_DEV_BEARER!=="1") return res.status(404).end();
  const c=createDeviceCredential();
  await directRelayStore.registerCredential(c);
  const devBase=origin(req);
  const link=pairingUrl(c,devBase.startsWith("https://")?devBase:undefined);
  const bearer=encodeBearer(c);
  noStore(res);
  res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ربط حكيم — تطوير</title>
<h1>ربط تطويري فقط</h1><p><a href="${html(link)}">ربط الهاتف</a></p><p style="word-break:break-all">${html(bearer)}</p></html>`);
});


app.get("/video/v1/artifacts/:jobId/:token.mp4",async(req,res)=>{
  try{
    const artifact=await resolveLocalArtifact(String(req.params.jobId??""),String(req.params.token??""));
    if(!artifact){
      noStore(res);
      return res.status(404).json({error:"video_artifact_not_found"});
    }
    noStore(res);
    res.setHeader("Content-Type","video/mp4");
    res.setHeader("Content-Disposition",`attachment; filename="${String(req.params.jobId)}.mp4"`);
    res.setHeader("X-Content-Type-Options","nosniff");
    res.setHeader("X-Hakim-Artifact-Sha256",artifact.sha256);
    return res.sendFile(artifact.file);
  }catch{
    noStore(res);
    return res.status(404).json({error:"video_artifact_not_found"});
  }
});

app.get("/support",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>دعم حكيم</title><body><h1>دعم حكيم</h1><p>حكيم يربط ChatGPT بجهاز Android يملكه المستخدم أو يملك صلاحية إدارته. إذا تعذر الربط، تحقق من أن تطبيق حكيم مثبت ومفتوح وأن الجهاز متصل بالإنترنت، ثم أعد عملية الاقتران.</p><p>للأعطال أو بلاغات الأمان والخصوصية، استخدم <a href="https://github.com/smileeyes1/sovereign-android-assistant/issues">GitHub Issues</a>. لا ترسل رموز الربط أو مفاتيح الوصول أو لقطات أو محتوى حساسًا في بلاغ عام.</p><p>يمكن فصل التطبيق من إعدادات Plugins/Apps في ChatGPT، وإعادة الاقتران تتطلب تفويضًا جديدًا.</p></body></html>`));

app.get("/privacy",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>خصوصية حكيم</title><body><h1>سياسة خصوصية حكيم</h1>
<h2>ما الذي نعالجه</h2>
<p>تعالج الخدمة الحد الأدنى اللازم لربط ChatGPT بجهاز Android مأذون: بيانات اقتران عشوائية، رموز OAuth المشفرة، حالة اتصال مختصرة، وطلبات الأدوات التي يختارها المستخدم. وعند استخدام الاستمرارية قد تحفظ نقطة عمل مختصرة يكوّنها المساعد من: تسمية المقصد، المرحلة، آخر نجاح مثبت، الخطوة التالية، المانع، والحالة؛ ولا تُصمم هذه النقطة لحفظ محتوى الشاشة أو الصفحة أو الإشعارات أو القيم المكتوبة أو بيانات الاعتماد. النسخة العامة لا تعرض لـChatGPT لقطات شاشة خامًا أو إشعارات أو كتابة حرة أو نقرًا عامًا.</p>
<h2>لماذا نعالجه</h2>
<p>نستخدم هذه البيانات حصراً للمصادقة، توجيه أوامر حكيم المأذونة، إعادة نتيجة الطلب، منع إعادة التشغيل/التلاعب، وتشخيص الأعطال التشغيلية دون تسجيل محتوى الجهاز عمدًا.</p>
<h2>المستلمون والمعالِجون</h2>
<p>قد تمر البيانات عبر ChatGPT/OpenAI وفق إعدادات حساب المستخدم، وعبر Railway لاستضافة الجسر، وعبر جسر حكيم نفسه كناقل HTTPS أساسي للبيانات المشفرة طرفًا لطرف. وقد يُستخدم ntfy كمسار احتياطي مشفر أثناء الهجرة أو التعافي، ولا يحصل على مفاتيح فك محتوى أوامر حكيم ونتائجه. إذا فتح المستخدم بلاغ دعم عام على GitHub، فإن ما يكتبه هناك يخضع لإعدادات GitHub؛ لذلك نحذر من نشر الأسرار أو اللقطات الحساسة.</p>
<h2>الاحتفاظ</h2>
<p>لا يستخدم الجسر محتوى الجهاز الخام كقاعدة معرفة دائمة. تُحفظ أوامر القراءة الآمنة المشفرة في طابور مؤقت لمدة تصل إلى ٣٠ دقيقة، بينما تبقى الأفعال ذات الأثر قصيرة العمر؛ وقد تبقى النتائج المشفرة مؤقتًا حتى نحو ساعة لتسمح بالاستئناف. يحتفظ سجل الاستمرارية ببيانات checkpoint التشغيلية المحدودة وبيانات العملية لمدة تصل إلى ٣٠ يومًا، مع حد أقصى للسجل وتنظيف دوري. رموز تفويض OAuth أحادية الاستخدام تنتهي بعد دقيقتين، ورموز الوصول تنتهي بعد ساعة، ورموز التجديد تنتهي بعد ٣٠ يومًا ما لم يُفصل الربط قبل ذلك. قد تحتفظ منصة الاستضافة بسجلات تشغيلية/شبكية وفق سياساتها، لكن حكيم لا يتعمد كتابة مفاتيح الربط أو بيانات الاعتماد أو محتوى الجهاز الخام إلى السجلات.</p>
<h2>الحماية</h2>
<p>أوامر الهاتف تنتقل عبر HC1 ونتائج الهاتف عبر HR1 باستخدام AES-256-GCM؛ الأوامر موقعة ومقيدة بمعرّف ومدة صلاحية لمنع العبث وإعادة التشغيل. لا يتيح الجسر shell أو root، والأفعال التي تغيّر حالة الهاتف تبقى خلف موافقة Android/Hakim.</p>
<h2>تحكم المستخدم</h2>
<p>يمكن للمستخدم فصل التطبيق من إعدادات Plugins/Apps في ChatGPT، وإلغاء اقتران حكيم أو مسح بيانات تطبيق حكيم على جهازه لإبطال الربط المحلي. عدم منح صلاحية write يبقي الأدوات الكتابية غير متاحة. يمكن طلب دعم أو الإبلاغ عن مشكلة عبر صفحة الدعم.</p>
<h2>ChatGPT</h2>
<p>ChatGPT نفسه يعالج المحادثة وفق حساب المستخدم وإعداداته وسياسات OpenAI. حكيم لا يطلب مفتاح OpenAI API ولا ينسخ cookies أو session tokens الخاصة بـChatGPT.</p>
<p><a href="/support">الدعم</a> · <a href="/terms">الشروط</a></p>
</body></html>`));

app.get("/terms",(_req,res)=>res.type("html").send(`<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>شروط حكيم</title><body><h1>شروط حكيم</h1><p>حكيم ذراع تنفيذ اختياري لجهاز يملكه المستخدم أو يملك صلاحية إدارته. استخدامه يعني أنك مخول باستخدام الجهاز والخدمات التي تطلب من حكيم الوصول إليها.</p><p>لا يمنح الجسر نفسه صلاحيات Android ولا يتجاوز حماية النظام. فتح التطبيقات أو الروابط والتنقل على الجهاز تبقى خاضعة لموافقة Android وسياسات ChatGPT. لا يضمن حكيم توافر نموذج بعينه؛ ChatGPT يطبق ما تتيحه خطة المستخدم ومنطقته وحدودها.</p><p>يُحظر استخدام حكيم للوصول غير المصرح به أو تجاوز الحماية أو تنفيذ نشاط مخالف للقانون أو شروط الخدمات الخارجية. قد تُرفض الأفعال عالية المخاطر أو غير المدعومة بدل تنفيذها.</p></body></html>`));



app.get("/gateway/v1/bootstrap",async(req,res)=>{
  let credential;
  try{
    ({credential}=requireAccessScope(req,"hakim.read"));
  }catch(e){
    return accessError(req,res,e,"hakim.read");
  }
  try{
    const preflight=await fetchLivePreflight(credential,false);
    const continuity=await continuityStore.state(credential);
    const base=origin(req);
    noStore(res);
    setContinuityEtag(res,continuity.checkpoint_revision);
    return res.json({
      ok:preflight.runtime_ready===true,
      gateway_version:UNIVERSAL_GATEWAY_VERSION,
      provider_neutral:true,
      observed_at_ms:Date.now(),
      preflight,
      continuity,
      capabilities:gatewayCapabilities(base),
      rule:"This bootstrap is authoritative for this turn. Every later mutation re-runs a fresh preflight inside Hakim before dispatch."
    });
  }catch(e){
    return gatewayRuntimeError(res,e);
  }
});

app.post("/gateway/v1/actions/open",async(req,res)=>{
  let credential;
  try{
    ({credential}=requireAccessScope(req,"hakim.write"));
  }catch(e){
    return accessError(req,res,e,"hakim.write");
  }
  let payload:{package:string;url:string};
  try{
    payload=gatewayOpenPayload(req.body);
  }catch(e){
    noStore(res);
    return res.status(400).json({error:e instanceof Error?e.message:"invalid_open_body"});
  }
  try{
    const preflight=await fetchLivePreflight(credential,false);
    if(preflight.action_ready!==true){
      noStore(res);
      return res.status(409).json({ok:false,error:"hakim_live_preflight_failed",preflight});
    }
    const requestId=await publishCommand(credential,"launch",payload);
    await continuityStore.recordRequested(credential,requestId,"launch");
    noStore(res);
    return res.status(202).json({
      ok:true,
      status:"approval_requested",
      operation_token:requestId,
      preflight_version:preflight.preflight_version,
      effect:"not_yet_verified",
      next:"GET /gateway/v1/operations/"+encodeURIComponent(requestId)
    });
  }catch(e){
    return gatewayRuntimeError(res,e);
  }
});

app.post("/gateway/v1/actions/navigate",async(req,res)=>{
  let credential;
  try{
    ({credential}=requireAccessScope(req,"hakim.write"));
  }catch(e){
    return accessError(req,res,e,"hakim.write");
  }
  let kind:"home"|"back"|"recents";
  try{
    kind=gatewayNavigateKind(req.body);
  }catch(e){
    noStore(res);
    return res.status(400).json({error:e instanceof Error?e.message:"invalid_navigate_body"});
  }
  try{
    const preflight=await fetchLivePreflight(credential,false);
    if(preflight.action_ready!==true){
      noStore(res);
      return res.status(409).json({ok:false,error:"hakim_live_preflight_failed",preflight});
    }
    const requestId=await publishCommand(credential,"action",{action:kind});
    await continuityStore.recordRequested(credential,requestId,"action");
    noStore(res);
    return res.status(202).json({
      ok:true,
      status:"approval_requested",
      operation_token:requestId,
      validated_action:kind,
      preflight_version:preflight.preflight_version,
      effect:"not_yet_verified",
      next:"GET /gateway/v1/operations/"+encodeURIComponent(requestId)
    });
  }catch(e){
    return gatewayRuntimeError(res,e);
  }
});

app.get("/gateway/v1/operations/:operationToken",async(req,res)=>{
  let credential;
  try{
    ({credential}=requireAccessScope(req,"hakim.read"));
  }catch(e){
    return accessError(req,res,e,"hakim.read");
  }
  const requestId=String(req.params.operationToken??"");
  if(!GATEWAY_OPERATION_ID.test(requestId)){
    noStore(res);
    return res.status(400).json({error:"invalid_operation_token"});
  }
  try{
    const result=await pollResult(credential,requestId,normalizeDeviceWaitMs(req.query.wait_ms));
    if(result!==null) await continuityStore.recordObserved(credential,requestId,result);
    const continuity=await continuityStore.state(credential);
    const operation=continuity.operations.find(x=>x.operation_token===requestId)??null;
    noStore(res);
    return res.json({
      ok:true,
      operation_token:requestId,
      status:operation?.status??(result===null?"pending":"result_available"),
      resumable:operation?.resumable??(result===null),
      result_observed:result!==null,
      replay_original_action:false,
      rule:"Reuse this operation_token; never replay the original mutation merely because a chat/model/session changed."
    });
  }catch(e){
    return gatewayRuntimeError(res,e);
  }
});

app.get("/continuity/v1/state",async(req,res)=>{
  try{
    const {credential}=requireAccessScope(req,"hakim.read");
    const state=await continuityStore.state(credential);
    noStore(res);
    setContinuityEtag(res,state.checkpoint_revision);
    return res.json(state);
  }catch(e){
    return accessError(req,res,e,"hakim.read");
  }
});

app.put("/continuity/v1/checkpoint",async(req,res)=>{
  try{
    const {credential}=requireAccessScope(req,"hakim.write");
    const input=parseCheckpointBody(req.body);
    const headerRevision=ifMatchRevision(req);
    if(headerRevision!==undefined&&input.expected_revision!==undefined&&headerRevision!==input.expected_revision){
      noStore(res);
      return res.status(400).json({error:"revision_precondition_mismatch"});
    }
    const expectedRevision=headerRevision??input.expected_revision;
    const checkpoint=await continuityStore.saveCheckpoint(credential,{
      ...input,
      expected_revision:expectedRevision
    });
    noStore(res);
    setContinuityEtag(res,checkpoint.checkpoint_revision);
    return res.json({
      ok:true,
      persisted:true,
      checkpoint_revision:checkpoint.checkpoint_revision,
      work:{
        goal_id:checkpoint.goal_id,
        goal_label:checkpoint.goal_label,
        stage:checkpoint.stage,
        last_verified:checkpoint.last_verified,
        next_step:checkpoint.next_step,
        blocker:checkpoint.blocker,
        status:checkpoint.status,
        updated_at_ms:checkpoint.updated_at_ms
      }
    });
  }catch(e){
    if(e instanceof ContinuityRevisionConflict){
      noStore(res);
      setContinuityEtag(res,e.current_revision);
      return res.status(409).json({
        error:"continuity_revision_conflict",
        current_revision:e.current_revision,
        recovery:"GET /continuity/v1/state, reconcile the newer checkpoint, then retry with that checkpoint_revision."
      });
    }
    if(e instanceof Error&&[
      "invalid_checkpoint_body","unknown_checkpoint_field","invalid_goal_id","invalid_goal_label","invalid_stage",
      "invalid_last_verified","invalid_next_step","invalid_blocker","invalid_status","invalid_expected_revision","invalid_if_match"
    ].includes(e.message)){
      noStore(res);
      return res.status(400).json({error:e.message});
    }
    return accessError(req,res,e,"hakim.write");
  }
});

app.get("/device/v1/continuity",async(req,res)=>{
  try{
    const topic=one(req.query.topic);
    const key=directRelayKey(req);
    await directRelayStore.authorizeCommandTopic(topic,key);
    const state=await continuityStore.stateForTopic(topic);
    noStore(res);
    return res.json({
      continuity_version:state.continuity_version,
      durable:state.durable,
      updated_at_ms:state.updated_at_ms,
      work:state.work,
      pending_count:state.pending_count
    });
  }catch(e){
    return directRelayError(res,e);
  }
});

app.get("/device/v1/commands",async(req,res)=>{
  try{
    const topic=one(req.query.topic);
    const key=directRelayKey(req);
    const waitMs=normalizeDeviceWaitMs(one(req.query.wait_ms));
    const command=await directRelayStore.leaseCommand(topic,key,waitMs);
    noStore(res);
    if(command){
      return res.json({
        request_id:command.request_id,
        carrier:command.carrier,
        expires_at_ms:command.expires_at_ms
      });
    }
    const probe=maybeMakeStatusProbe(topic,key);
    if(probe) return res.json(probe);
    return res.status(204).end();
  }catch(e){
    return directRelayError(res,e);
  }
});

app.post("/device/v1/commands/:requestId/ack",async(req,res)=>{
  try{
    const topic=one(req.query.topic);
    const key=directRelayKey(req);
    await directRelayStore.ackCommand(topic,key,req.params.requestId);
    noStore(res);
    return res.status(204).end();
  }catch(e){
    return directRelayError(res,e);
  }
});

app.post(
  "/device/v1/results",
  express.text({type:"text/plain",limit:"128kb"}),
  async(req,res)=>{
    try{
      const topic=one(req.query.topic);
      const key=directRelayKey(req);
      if(typeof req.body!=="string") throw new Error("result_body_required");
      const carrier=req.body.trim();
      await directRelayStore.pushResult(topic,key,carrier);
      logSanitizedStatusProbe(topic,key,carrier);
      logSanitizedHealthBeacon(key,carrier);
      void developmentRequestStore.capture(topic,key,carrier).catch(error=>{
        const message=error instanceof Error?error.message:"development_intake_failed";
        console.log("HAKIM_DEVELOPMENT_INTAKE "+JSON.stringify({
          accepted:false,
          reason:[
            "development_schema_invalid","development_control_version_invalid","development_request_id_invalid",
            "development_package_invalid","development_version_invalid","development_time_invalid",
            "development_trigger_invalid","development_severity_invalid","development_fingerprint_invalid",
            "development_constraints_invalid","development_constraints_weakened"
          ].includes(message)?message:"rejected"
        }));
      });
      noStore(res);
      return res.status(202).json({accepted:true});
    }catch(e){
      return directRelayError(res,e);
    }
  }
);

app.all("/mcp",async(req,res)=>{
  const base=origin(req);
  const metadataUrl=base+"/.well-known/oauth-protected-resource";
  try{
    const {credential,scopes}=accessSession(req);
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

async function maybeRunVideoStartupSelftest(){
  if(process.env.HAKIM_VIDEO_SELFTEST_ON_START!=="1") return;
  try{
    const submitted=await submitLocalRender({
      goal:"Hakim production synthetic video self-test",
      duration_sec:8,
      aspect:"16:9",
      realism:"cinematic"
    });
    const deadline=Date.now()+75_000;
    let status:any=null;
    while(Date.now()<deadline){
      status=await getLocalRenderStatus(submitted.job_id);
      if(status.status==="completed"||status.status==="failed") break;
      await new Promise(resolve=>setTimeout(resolve,500));
    }
    const a=status?.artifact;
    console.log("HAKIM_VIDEO_SELFTEST "+JSON.stringify({
      ok:status?.render_success===true&&status?.artifact_verified===true,
      status:status?.status??"timeout",
      sha256:typeof a?.sha256==="string"&&/^[0-9a-f]{64}$/.test(a.sha256)?a.sha256:null,
      duration_sec:typeof a?.duration_sec==="number"?a.duration_sec:null,
      size_bytes:typeof a?.size_bytes==="number"?a.size_bytes:null,
      playback_passed:a?.playback_passed===true,
      quality_gates_passed:a?.quality_gates_passed===true,
      cinematic_approval:a?.cinematic_approval===true,
      fps:typeof a?.fps==="number"?a.fps:null,
      width:typeof a?.width==="number"?a.width:null,
      height:typeof a?.height==="number"?a.height:null,
      has_audio:a?.has_audio===true,
      renderer:typeof a?.renderer==="string"?a.renderer:null,
      rule:"startup_selftest_proves_local_mp4_pipeline_only"
    }));
  }catch(error){
    console.log("HAKIM_VIDEO_SELFTEST "+JSON.stringify({
      ok:false,status:"error",
      error:error instanceof Error?error.message.slice(0,300):"selftest_failed",
      rule:"startup_selftest_proves_local_mp4_pipeline_only"
    }));
  }
}

const port=Number(process.env.PORT??3000);
app.listen(port,"0.0.0.0",()=>{
  console.log(`Hakim ChatGPT bridge listening on :${port}`);
  void maybeRunVideoStartupSelftest();
});
