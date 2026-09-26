import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import type { DeviceCredential } from "./protocol.js";

type BindingKind="command"|"result";
type BindingRecord={kind:BindingKind;key_hash:string;created_at_ms:number};
type CommandRecord={
  request_id:string;
  carrier:string;
  created_at_ms:number;
  expires_at_ms:number;
  leased_until_ms:number;
};
type ResultRecord={id:string;carrier:string;created_at_ms:number};

const REQUEST_ID=/^[A-Za-z0-9._:-]{8,128}$/;
const TOPIC=/^[A-Za-z0-9_-]{20,120}$/;
const KEY=/^[A-Za-z0-9_-]{40,100}$/;
const RESULT_CARRIER=/^HR1\.[A-Za-z0-9_-]{24,131072}$/;

function sha(value:string){
  return crypto.createHash("sha256").update(value,"utf8").digest("hex");
}
function safeEqualHex(a:string,b:string){
  if(a.length!==b.length) return false;
  return crypto.timingSafeEqual(Buffer.from(a,"hex"),Buffer.from(b,"hex"));
}
function sleep(ms:number){return new Promise(resolve=>setTimeout(resolve,ms));}

export class DirectRelayStore{
  readonly root:string;
  constructor(dataDir=process.env.HAKIM_DATA_DIR ?? path.join(os.tmpdir(),"hakim-oauth-dev")){
    this.root=path.join(dataDir,"direct-relay-v1");
  }

  async init(){
    await Promise.all([
      fs.mkdir(path.join(this.root,"bindings"),{recursive:true}),
      fs.mkdir(path.join(this.root,"commands"),{recursive:true}),
      fs.mkdir(path.join(this.root,"results"),{recursive:true})
    ]);
  }

  private bindingPath(topic:string){
    return path.join(this.root,"bindings",sha(topic)+".json");
  }
  private commandDir(topic:string){
    return path.join(this.root,"commands",sha(topic));
  }
  private resultDir(topic:string){
    return path.join(this.root,"results",sha(topic));
  }
  private async atomicJson(file:string,value:unknown){
    await fs.mkdir(path.dirname(file),{recursive:true});
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,JSON.stringify(value),{encoding:"utf8",mode:0o600});
    await fs.rename(tmp,file);
  }

  async registerCredential(c:DeviceCredential){
    await this.init();
    if(!TOPIC.test(c.topic)||!TOPIC.test(c.resultTopic)||!KEY.test(c.relayKey)) throw new Error("invalid_direct_relay_credential");
    const key_hash=sha(c.relayKey);
    const now=Date.now();
    const bindings:[string,BindingRecord][]=[
      [c.topic,{kind:"command",key_hash,created_at_ms:now}],
      [c.resultTopic,{kind:"result",key_hash,created_at_ms:now}]
    ];
    for(const [topic,record] of bindings){
      const file=this.bindingPath(topic);
      try{
        const current=JSON.parse(await fs.readFile(file,"utf8")) as BindingRecord;
        if(current.kind!==record.kind||!safeEqualHex(current.key_hash,record.key_hash)) throw new Error("relay_binding_conflict");
      }catch(e){
        if((e as NodeJS.ErrnoException).code!=="ENOENT") throw e;
        await this.atomicJson(file,record);
      }
    }
  }

  private async authorize(topic:string,relayKey:string,kind:BindingKind){
    if(!TOPIC.test(topic)||!KEY.test(relayKey)) throw new Error("relay_auth_failed");
    let current:BindingRecord;
    try{
      current=JSON.parse(await fs.readFile(this.bindingPath(topic),"utf8")) as BindingRecord;
    }catch{
      throw new Error("relay_auth_failed");
    }
    const candidate=sha(relayKey);
    if(current.kind!==kind||!safeEqualHex(current.key_hash,candidate)) throw new Error("relay_auth_failed");
  }

  async enqueueCommand(c:DeviceCredential,requestId:string,carrier:string,expiresAtMs:number){
    if(!REQUEST_ID.test(requestId)) throw new Error("invalid_request_id");
    if(!carrier.startsWith("HC1.")||carrier.length>131072) throw new Error("invalid_command_carrier");
    await this.registerCredential(c);
    const record:CommandRecord={
      request_id:requestId,
      carrier,
      created_at_ms:Date.now(),
      expires_at_ms:expiresAtMs,
      leased_until_ms:0
    };
    await this.atomicJson(path.join(this.commandDir(c.topic),requestId+".json"),record);
  }

  async leaseCommand(topic:string,relayKey:string,waitMs=25_000):Promise<CommandRecord|null>{
    await this.authorize(topic,relayKey,"command");
    const deadline=Date.now()+Math.max(0,Math.min(waitMs,25_000));
    do{
      const found=await this.leaseOnce(topic);
      if(found) return found;
      if(Date.now()>=deadline) break;
      await sleep(300);
    }while(true);
    return null;
  }

  private async leaseOnce(topic:string):Promise<CommandRecord|null>{
    const dir=this.commandDir(topic);
    let names:string[]=[];
    try{names=(await fs.readdir(dir)).filter(x=>x.endsWith(".json")).sort();}
    catch(e){if((e as NodeJS.ErrnoException).code==="ENOENT") return null; throw e;}
    const now=Date.now();
    for(const name of names.slice(0,128)){
      const file=path.join(dir,name);
      try{
        const record=JSON.parse(await fs.readFile(file,"utf8")) as CommandRecord;
        if(record.expires_at_ms<=now){
          await fs.unlink(file).catch(()=>{});
          continue;
        }
        if(record.leased_until_ms>now) continue;
        record.leased_until_ms=now+30_000;
        await this.atomicJson(file,record);
        return record;
      }catch{
        await fs.unlink(file).catch(()=>{});
      }
    }
    return null;
  }

  async ackCommand(topic:string,relayKey:string,requestId:string){
    await this.authorize(topic,relayKey,"command");
    if(!REQUEST_ID.test(requestId)) throw new Error("invalid_request_id");
    await fs.unlink(path.join(this.commandDir(topic),requestId+".json")).catch(e=>{
      if((e as NodeJS.ErrnoException).code!=="ENOENT") throw e;
    });
  }

  async pushResult(resultTopic:string,relayKey:string,carrier:string){
    await this.authorize(resultTopic,relayKey,"result");
    if(!RESULT_CARRIER.test(carrier)||carrier.length>131072) throw new Error("invalid_result_carrier");
    const id=Date.now().toString(36)+"-"+crypto.randomBytes(8).toString("hex");
    const record:ResultRecord={id,carrier,created_at_ms:Date.now()};
    await this.atomicJson(path.join(this.resultDir(resultTopic),id+".json"),record);
    return id;
  }

  async listResults(c:DeviceCredential):Promise<ResultRecord[]>{
    await this.authorize(c.resultTopic,c.relayKey,"result");
    const dir=this.resultDir(c.resultTopic);
    let names:string[]=[];
    try{names=(await fs.readdir(dir)).filter(x=>x.endsWith(".json")).sort();}
    catch(e){if((e as NodeJS.ErrnoException).code==="ENOENT") return []; throw e;}
    const cutoff=Date.now()-10*60_000;
    const out:ResultRecord[]=[];
    for(const name of names.slice(0,128)){
      const file=path.join(dir,name);
      try{
        const record=JSON.parse(await fs.readFile(file,"utf8")) as ResultRecord;
        if(record.created_at_ms<cutoff){
          await fs.unlink(file).catch(()=>{});
          continue;
        }
        out.push(record);
      }catch{
        await fs.unlink(file).catch(()=>{});
      }
    }
    return out;
  }

  async deleteResult(c:DeviceCredential,id:string){
    if(!/^[A-Za-z0-9-]{8,80}$/.test(id)) return;
    await this.authorize(c.resultTopic,c.relayKey,"result");
    await fs.unlink(path.join(this.resultDir(c.resultTopic),id+".json")).catch(()=>{});
  }

  async cleanup(){
    await this.init();
    const now=Date.now();
    for(const group of ["commands","results"] as const){
      const root=path.join(this.root,group);
      let dirs:string[]=[];
      try{dirs=await fs.readdir(root);}catch{return;}
      for(const dir of dirs.slice(0,512)){
        const full=path.join(root,dir);
        let files:string[]=[];
        try{files=await fs.readdir(full);}catch{continue;}
        for(const name of files.slice(0,512)){
          const file=path.join(full,name);
          try{
            const raw=JSON.parse(await fs.readFile(file,"utf8")) as CommandRecord|ResultRecord;
            const expiry=group==="commands"
              ? (raw as CommandRecord).expires_at_ms+60_000
              : (raw as ResultRecord).created_at_ms+10*60_000;
            if(expiry<now) await fs.unlink(file).catch(()=>{});
          }catch{await fs.unlink(file).catch(()=>{});}
        }
      }
    }
  }
}

export const directRelayStore=new DirectRelayStore();
