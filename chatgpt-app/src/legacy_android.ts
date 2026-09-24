import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import https from "node:https";
import dns from "node:dns/promises";
import {randomSecret} from "./protocol.js";

async function httpsOnce(
  url:string,
  opts:{method?:"GET"|"POST";headers?:Record<string,string>;body?:string;timeoutMs?:number}={},
  forcedAddress?:string
){
  const u=new URL(url);
  const requestOptions:https.RequestOptions={
    protocol:"https:",
    hostname:forcedAddress??u.hostname,
    port:u.port?Number(u.port):443,
    path:u.pathname+u.search,
    method:opts.method??"GET",
    headers:{...(opts.headers??{}),...(forcedAddress?{Host:u.hostname}:{})},
    servername:u.hostname,
    family:forcedAddress?4:undefined,
    agent:false,
    timeout:opts.timeoutMs??3500
  };
  return await new Promise<{status:number;body:string}>((resolve,reject)=>{
    const req=https.request(requestOptions,res=>{
      const chunks:Buffer[]=[];
      res.on("data",chunk=>chunks.push(Buffer.isBuffer(chunk)?chunk:Buffer.from(chunk)));
      res.on("end",()=>resolve({status:res.statusCode??0,body:Buffer.concat(chunks).toString("utf8")}));
    });
    req.on("timeout",()=>req.destroy(new Error("ntfy_timeout")));
    req.on("error",reject);
    if(opts.body) req.write(opts.body);
    req.end();
  });
}

async function httpsText(url:string,opts:{method?:"GET"|"POST";headers?:Record<string,string>;body?:string;timeoutMs?:number}={}){
  const u=new URL(url);
  const errors:string[]=[];
  let addresses:string[]=[];
  try{ addresses=await dns.resolve4(u.hostname); }catch(e){ errors.push("dns4:"+(e instanceof Error?e.message:String(e))); }
  const candidates=[...new Set(addresses)].slice(0,4);
  for(const address of candidates){
    try{
      const r=await httpsOnce(url,{...opts,timeoutMs:Math.min(opts.timeoutMs??3500,3500)},address);
      if(r.status>0) return r;
    }catch(e){ errors.push(address+":"+(e instanceof Error?e.message:String(e))); }
  }
  try{
    return await httpsOnce(url,{...opts,timeoutMs:opts.timeoutMs??5000});
  }catch(e){
    errors.push("host:"+(e instanceof Error?e.message:String(e)));
    throw new Error("ntfy_all_routes_failed:"+errors.slice(-5).join("|"));
  }
}

export type LegacyAndroidSession={
  id:string;
  commandTopic:string;
  resultTopic:string;
  authKey:string;
  createdAt:number;
  expiresAt:number;
};

function keyBytes(keyText:string){
  const hex=keyText.trim();
  return hex.length%2===0&&/^[0-9a-fA-F]+$/.test(hex)?Buffer.from(hex,"hex"):Buffer.from(hex,"utf8");
}

function hmacHex(keyText:string,data:string){
  return crypto.createHmac("sha256",keyBytes(keyText)).update(data,"utf8").digest("hex");
}

export class LegacyAndroidStore{
  private readonly dir:string;
  constructor(private readonly dataDir:string){this.dir=path.join(dataDir,"legacy-android-pair");}
  async init(){await fs.mkdir(this.dir,{recursive:true});await this.cleanupExpired();}
  private file(id:string){
    if(!/^[A-Za-z0-9_-]{20,80}$/.test(id)) throw new Error("invalid_legacy_session");
    return path.join(this.dir,id+".json");
  }
  async create(ttlMs=24*60*60*1000):Promise<LegacyAndroidSession>{
    const now=Date.now();
    const s:LegacyAndroidSession={
      id:randomSecret(24),
      commandTopic:"hakim_cmd_"+randomSecret(18),
      resultTopic:"hakim_result_"+randomSecret(18),
      authKey:crypto.randomBytes(32).toString("hex"),
      createdAt:now,
      expiresAt:now+ttlMs
    };
    await fs.writeFile(this.file(s.id),JSON.stringify(s),{encoding:"utf8",mode:0o600,flag:"wx"});
    return s;
  }
  async get(id:string):Promise<LegacyAndroidSession>{
    const raw=await fs.readFile(this.file(id),"utf8");
    const s=JSON.parse(raw) as LegacyAndroidSession;
    if(s.id!==id||Date.now()>=s.expiresAt) throw new Error("legacy_session_expired");
    return s;
  }
  async cleanupExpired(){
    let names:string[]=[];
    try{names=await fs.readdir(this.dir);}catch{return;}
    const now=Date.now();
    await Promise.all(names.filter(n=>n.endsWith(".json")).map(async n=>{
      const p=path.join(this.dir,n);
      try{
        const s=JSON.parse(await fs.readFile(p,"utf8")) as LegacyAndroidSession;
        if(!s.expiresAt||now>=s.expiresAt) await fs.rm(p,{force:true});
      }catch{await fs.rm(p,{force:true});}
    }));
  }
}

export function legacyPairCode(s:LegacyAndroidSession){
  return [s.commandTopic,s.resultTopic,s.authKey].join("|");
}

export async function publishLegacyCommand(s:LegacyAndroidSession,command:Record<string,unknown>){
  const payloadObj:Record<string,unknown>={...command,issued_at:Date.now()};
  const requestId=String(payloadObj.request_id??("legacy-"+randomSecret(10)));
  payloadObj.request_id=requestId;
  const payload=Buffer.from(JSON.stringify(payloadObj),"utf8").toString("base64url");
  const sig=hmacHex(s.authKey,payload);
  const body=JSON.stringify({payload,sig});
  const response=await httpsText("https://ntfy.sh/"+encodeURIComponent(s.commandTopic),{
    method:"POST",
    headers:{"Content-Type":"text/plain; charset=utf-8"},
    body,
    timeoutMs:10_000
  });
  if(response.status<200||response.status>=300) throw new Error("legacy_publish_failed:"+response.status);
  return requestId;
}

function verifyChunk(s:LegacyAndroidSession,value:any){
  if(!value||typeof value!=="object") return false;
  const requestId=String(value.request_id??"");
  const chunk=Number(value.chunk??0);
  const total=Number(value.total??0);
  const data=String(value.data??"");
  const sig=String(value.sig??"").toLowerCase();
  if(!requestId||chunk<1||total<1||chunk>total||!/^[0-9a-f]{64}$/.test(sig)) return false;
  const expected=hmacHex(s.authKey,[requestId,String(chunk),String(total),data].join("\n"));
  return crypto.timingSafeEqual(Buffer.from(expected,"ascii"),Buffer.from(sig,"ascii"));
}

export async function pollLegacyResult(s:LegacyAndroidSession,requestId:string|undefined,timeoutMs=8_000){
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    const u=new URL("https://ntfy.sh/"+encodeURIComponent(s.resultTopic)+"/json");
    u.searchParams.set("poll","1");
    u.searchParams.set("since","10m");
    const response=await httpsText(u.toString(),{timeoutMs:8_000});
    if(response.status>=200&&response.status<300){
      const groups=new Map<string,{total:number;parts:Map<number,string>}>();
      const body=response.body;
      for(const line of body.split("\n")){
        if(!line.trim()) continue;
        try{
          const evt=JSON.parse(line);
          const value=JSON.parse(String(evt.message??""));
          if(!verifyChunk(s,value)) continue;
          const rid=String(value.request_id);
          if(requestId&&rid!==requestId) continue;
          const g=groups.get(rid)??{total:Number(value.total),parts:new Map<number,string>()};
          g.parts.set(Number(value.chunk),String(value.data??""));
          groups.set(rid,g);
          if(g.parts.size===g.total){
            let raw="";
            for(let i=1;i<=g.total;i++) raw+=g.parts.get(i)??"";
            const parsed=JSON.parse(raw);
            if(requestId||parsed?.status==="online") return parsed;
          }
        }catch{}
      }
    }
    await new Promise(r=>setTimeout(r,700));
  }
  return null;
}


export async function probeNtfyIpv4(){
  const topic="hakim_diag_"+randomSecret(8);
  const started=Date.now();
  try{
    const response=await httpsText("https://ntfy.sh/"+encodeURIComponent(topic),{
      method:"POST",
      headers:{"Content-Type":"text/plain; charset=utf-8"},
      body:"hakim-ipv4-probe",
      timeoutMs:5_000
    });
    return {ok:response.status>=200&&response.status<300,status:response.status,latency_ms:Date.now()-started,body:response.body.slice(0,300)};
  }catch(e){
    return {ok:false,status:0,latency_ms:Date.now()-started,error:e instanceof Error?e.message:String(e)};
  }
}


function digestB64(label:string,id:string,bytes:number){
  return crypto.createHash("sha256").update(label+"\n"+id,"utf8").digest().subarray(0,bytes).toString("base64url");
}

export function statelessLegacySession(id:string):LegacyAndroidSession{
  if(!/^[A-Za-z0-9_-]{32,96}$/.test(id)) throw new Error("invalid_stateless_session");
  const now=Date.now();
  return {
    id,
    commandTopic:"hakim_cmd_"+digestB64("cmd",id,18),
    resultTopic:"hakim_result_"+digestB64("res",id,18),
    authKey:crypto.createHash("sha256").update("key\n"+id,"utf8").digest("hex"),
    createdAt:0,
    expiresAt:Number.MAX_SAFE_INTEGER
  };
}

export function newStatelessLegacyId(){
  return randomSecret(32);
}


export async function pollLegacyHealth(s:LegacyAndroidSession,maxAgeMs=120_000){
  const u=new URL("https://ntfy.sh/"+encodeURIComponent(s.resultTopic)+"/json");
  u.searchParams.set("poll","1");
  u.searchParams.set("since","10m");
  const response=await httpsText(u.toString(),{timeoutMs:8_000});
  if(response.status<200||response.status>=300) return null;
  const now=Date.now();
  let latest:any=null;
  let latestTime=0;
  for(const line of response.body.split("\n")){
    if(!line.trim()) continue;
    try{
      const evt=JSON.parse(line);
      const value=JSON.parse(String(evt.message??""));
      if(!verifyChunk(s,value)) continue;
      const raw=String(value.data??"");
      const parsed=JSON.parse(raw);
      if(parsed?.status!=="health") continue;
      const time=Number(parsed?.time??0);
      if(!Number.isFinite(time)||time<=0||Math.abs(now-time)>maxAgeMs) continue;
      if(parsed?.service_connected!==true) continue;
      if(time>latestTime){ latest=parsed; latestTime=time; }
    }catch{}
  }
  return latest;
}
