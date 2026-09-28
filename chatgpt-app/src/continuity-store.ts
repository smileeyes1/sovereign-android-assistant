import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import type { DeviceCredential,HakimOp } from "./protocol.js";

export type ContinuityStatus="requested"|"pending"|"ok"|"error"|"failed"|"rejected"|"expired"|"duplicate"|"complete";

type OperationRecord={
  request_id:string;
  op:HakimOp;
  status:ContinuityStatus;
  created_at_ms:number;
  updated_at_ms:number;
};

type DeviceJournal={
  version:"HAKIM_CONTINUITY_V1";
  updated_at_ms:number;
  operations:OperationRecord[];
};

const MAX_OPERATIONS=64;
const MAX_AGE_MS=30*24*60*60_000;
const FINAL_STATUSES=new Set<ContinuityStatus>(["ok","error","failed","rejected","expired","duplicate","complete"]);

function safeStatus(result:unknown):ContinuityStatus{
  if(result===null||result===undefined) return "pending";
  if(typeof result!=="object") return "complete";
  const root=result as Record<string,unknown>;
  const raw=typeof root.status==="string"?root.status.toLowerCase():"";
  if(raw==="ok") return "ok";
  if(raw==="error") return "error";
  if(raw==="failed") return "failed";
  if(raw==="rejected") return "rejected";
  if(raw==="expired") return "expired";
  if(raw==="duplicate") return "duplicate";
  if(raw==="pending"||raw==="approval_requested"||raw==="requested") return "pending";
  return "complete";
}

export class ContinuityStore{
  private readonly root:string;
  private readonly locks=new Map<string,Promise<void>>();

  constructor(root=path.join(process.env.HAKIM_DATA_DIR??os.tmpdir(),"continuity")){
    this.root=root;
  }

  async init(){
    await fs.mkdir(this.root,{recursive:true});
  }

  private deviceKey(c:DeviceCredential){
    return crypto.createHash("sha256").update(c.topic).digest("hex");
  }

  private file(c:DeviceCredential){
    return path.join(this.root,this.deviceKey(c)+".json");
  }

  private empty():DeviceJournal{
    return {version:"HAKIM_CONTINUITY_V1",updated_at_ms:Date.now(),operations:[]};
  }

  private async read(c:DeviceCredential):Promise<DeviceJournal>{
    try{
      const parsed=JSON.parse(await fs.readFile(this.file(c),"utf8")) as DeviceJournal;
      if(parsed?.version!=="HAKIM_CONTINUITY_V1"||!Array.isArray(parsed.operations)) return this.empty();
      const cutoff=Date.now()-MAX_AGE_MS;
      parsed.operations=parsed.operations.filter(x=>
        x&&typeof x.request_id==="string"&&typeof x.op==="string"&&
        typeof x.updated_at_ms==="number"&&x.updated_at_ms>=cutoff
      ).slice(-MAX_OPERATIONS);
      return parsed;
    }catch{
      return this.empty();
    }
  }

  private async write(c:DeviceCredential,journal:DeviceJournal){
    await fs.mkdir(this.root,{recursive:true});
    const file=this.file(c);
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,JSON.stringify(journal),{encoding:"utf8",mode:0o600});
    await fs.rename(tmp,file);
  }

  private async mutate(c:DeviceCredential,fn:(journal:DeviceJournal)=>void){
    const key=this.deviceKey(c);
    const previous=this.locks.get(key)??Promise.resolve();
    let release!:()=>void;
    const current=new Promise<void>(resolve=>{release=resolve;});
    this.locks.set(key,previous.catch(()=>{}).then(()=>current));
    await previous.catch(()=>{});
    try{
      const journal=await this.read(c);
      fn(journal);
      journal.updated_at_ms=Date.now();
      journal.operations=journal.operations
        .filter(x=>Date.now()-x.updated_at_ms<=MAX_AGE_MS)
        .slice(-MAX_OPERATIONS);
      await this.write(c,journal);
    }finally{
      release();
      if(this.locks.get(key)===current) this.locks.delete(key);
    }
  }

  async recordRequested(c:DeviceCredential,requestId:string,op:HakimOp){
    await this.mutate(c,journal=>{
      const now=Date.now();
      const existing=journal.operations.find(x=>x.request_id===requestId);
      if(existing){
        existing.op=op;
        existing.status=FINAL_STATUSES.has(existing.status)?existing.status:"requested";
        existing.updated_at_ms=now;
        return;
      }
      journal.operations.push({
        request_id:requestId,
        op,
        status:"requested",
        created_at_ms:now,
        updated_at_ms:now
      });
    });
  }

  async recordObserved(c:DeviceCredential,requestId:string,result:unknown){
    await this.mutate(c,journal=>{
      const now=Date.now();
      const existing=journal.operations.find(x=>x.request_id===requestId);
      if(!existing) return;
      existing.status=safeStatus(result);
      existing.updated_at_ms=now;
    });
  }

  async state(c:DeviceCredential){
    const journal=await this.read(c);
    const operations=[...journal.operations]
      .sort((a,b)=>b.updated_at_ms-a.updated_at_ms)
      .slice(0,12)
      .map(x=>({
        operation_token:x.request_id,
        operation:x.op,
        status:x.status,
        created_at_ms:x.created_at_ms,
        updated_at_ms:x.updated_at_ms,
        resumable:!FINAL_STATUSES.has(x.status)
      }));
    return {
      continuity_version:journal.version,
      durable:true,
      updated_at_ms:journal.updated_at_ms,
      pending_count:operations.filter(x=>x.resumable).length,
      operations,
      resume_rule:"For pending operations, call get_request_result with the same operation_token. Do not re-run the original action merely because the chat changed.",
      privacy:"No screen text, page contents, notification contents, URLs, typed values, relay keys, or credentials are stored in this journal."
    };
  }
}

export const continuityStore=new ContinuityStore();
