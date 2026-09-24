import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import {randomSecret} from "./protocol.js";

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
  const payloadObj={...command,issued_at:Date.now()};
  const requestId=String(payloadObj.request_id??("legacy-"+randomSecret(10)));
  payloadObj.request_id=requestId;
  const payload=Buffer.from(JSON.stringify(payloadObj),"utf8").toString("base64url");
  const sig=hmacHex(s.authKey,payload);
  const body=JSON.stringify({payload,sig});
  const response=await fetch("https://ntfy.sh/"+encodeURIComponent(s.commandTopic),{
    method:"POST",
    headers:{"Content-Type":"text/plain; charset=utf-8"},
    body,
    signal:AbortSignal.timeout(10_000)
  });
  if(!response.ok) throw new Error("legacy_publish_failed");
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
    const response=await fetch(u,{signal:AbortSignal.timeout(8_000)});
    if(response.ok){
      const groups=new Map<string,{total:number;parts:Map<number,string>}>();
      const body=await response.text();
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
