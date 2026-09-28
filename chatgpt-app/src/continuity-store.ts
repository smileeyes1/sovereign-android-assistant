import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import type { DeviceCredential,HakimOp } from "./protocol.js";

export type ContinuityStatus="requested"|"pending"|"ok"|"error"|"failed"|"rejected"|"expired"|"duplicate"|"complete";
export type WorkStatus="active"|"waiting"|"blocked"|"complete"|"cancelled";

type OperationRecord={
  request_id:string;
  op:HakimOp;
  status:ContinuityStatus;
  created_at_ms:number;
  updated_at_ms:number;
};

export type WorkCheckpoint={
  goal_id:string;
  goal_label:string;
  stage:string;
  last_verified:string;
  next_step:string;
  blocker:string;
  status:WorkStatus;
  updated_at_ms:number;
};

type DeviceJournal={
  version:"HAKIM_CONTINUITY_V2";
  updated_at_ms:number;
  work:WorkCheckpoint|null;
  operations:OperationRecord[];
};

const MAX_OPERATIONS=64;
const MAX_AGE_MS=30*24*60*60_000;
const FINAL_STATUSES=new Set<ContinuityStatus>(["ok","error","failed","rejected","expired","duplicate","complete"]);
const GOAL_ID=/^[A-Za-z0-9._:-]{8,96}$/;
const CONTROL=/[\u0000-\u001F\u007F]/g;
const SECRET_HINT=/(?:bearer\s+[a-z0-9._~-]+|(?:password|passwd|secret|api[_ -]?key|access[_ -]?token|refresh[_ -]?token)\s*[:=])/i;

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

function safeField(raw:string,max:number){
  const value=raw.replace(CONTROL," ").replace(/\s+/g," ").trim().slice(0,max);
  if(SECRET_HINT.test(value)) throw new Error("checkpoint_sensitive_content_rejected");
  return value;
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
    return {version:"HAKIM_CONTINUITY_V2",updated_at_ms:Date.now(),work:null,operations:[]};
  }

  private async read(c:DeviceCredential):Promise<DeviceJournal>{
    try{
      const raw=JSON.parse(await fs.readFile(this.file(c),"utf8")) as Record<string,unknown>;
      if(!Array.isArray(raw.operations)) return this.empty();
      const cutoff=Date.now()-MAX_AGE_MS;
      const operations=(raw.operations as OperationRecord[]).filter(x=>
        x&&typeof x.request_id==="string"&&typeof x.op==="string"&&
        typeof x.updated_at_ms==="number"&&x.updated_at_ms>=cutoff
      ).slice(-MAX_OPERATIONS);
      const workRaw=raw.work;
      let work:WorkCheckpoint|null=null;
      if(workRaw&&typeof workRaw==="object"){
        const w=workRaw as Record<string,unknown>;
        const status=typeof w.status==="string"&&["active","waiting","blocked","complete","cancelled"].includes(w.status)
          ? w.status as WorkStatus : "active";
        if(typeof w.goal_id==="string"&&GOAL_ID.test(w.goal_id)){
          work={
            goal_id:w.goal_id,
            goal_label:safeField(typeof w.goal_label==="string"?w.goal_label:"",240),
            stage:safeField(typeof w.stage==="string"?w.stage:"",120),
            last_verified:safeField(typeof w.last_verified==="string"?w.last_verified:"",280),
            next_step:safeField(typeof w.next_step==="string"?w.next_step:"",280),
            blocker:safeField(typeof w.blocker==="string"?w.blocker:"",220),
            status,
            updated_at_ms:typeof w.updated_at_ms==="number"?w.updated_at_ms:0
          };
        }
      }
      return {
        version:"HAKIM_CONTINUITY_V2",
        updated_at_ms:typeof raw.updated_at_ms==="number"?raw.updated_at_ms:Date.now(),
        work,
        operations
      };
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
    const gate=previous.catch(()=>{}).then(()=>current);
    this.locks.set(key,gate);
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
      if(this.locks.get(key)===gate) this.locks.delete(key);
    }
  }

  async saveCheckpoint(
    c:DeviceCredential,
    input:{
      goal_id?:string;
      goal_label:string;
      stage:string;
      last_verified?:string;
      next_step?:string;
      blocker?:string;
      status:WorkStatus;
    }
  ){
    const goalId=(input.goal_id??("goal-"+crypto.randomUUID().replace(/-/g,"").slice(0,20))).trim();
    if(!GOAL_ID.test(goalId)) throw new Error("invalid_goal_id");
    const checkpoint:WorkCheckpoint={
      goal_id:goalId,
      goal_label:safeField(input.goal_label,240),
      stage:safeField(input.stage,120),
      last_verified:safeField(input.last_verified??"",280),
      next_step:safeField(input.next_step??"",280),
      blocker:safeField(input.blocker??"",220),
      status:input.status,
      updated_at_ms:Date.now()
    };
    if(!checkpoint.goal_label||!checkpoint.stage) throw new Error("checkpoint_goal_and_stage_required");
    await this.mutate(c,journal=>{journal.work=checkpoint;});
    return checkpoint;
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
      work:journal.work,
      pending_count:operations.filter(x=>x.resumable).length,
      operations,
      resume_rule:"Resume the work checkpoint first. For pending operations, call get_request_result with the same operation_token. Do not re-run an original action merely because the chat or session changed.",
      privacy:"The journal stores only bounded work checkpoint text plus operation type/token/status. It does not store screen/page/notification contents, typed values, relay keys, or credentials."
    };
  }
}

export const continuityStore=new ContinuityStore();
