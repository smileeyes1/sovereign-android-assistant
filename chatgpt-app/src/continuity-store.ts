import crypto from "node:crypto";
import os from "node:os";
import path from "node:path";
import type { DeviceCredential,HakimOp } from "./protocol.js";
import { hakimStateBackend as stateBackend } from "./state-backend.js";

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
  version:"HAKIM_CONTINUITY_V3";
  updated_at_ms:number;
  checkpoint_revision:number;
  work:WorkCheckpoint|null;
  operations:OperationRecord[];
};

const MAX_OPERATIONS=64;
const MAX_AGE_MS=30*24*60*60_000;
const FINAL_STATUSES=new Set<ContinuityStatus>(["ok","error","failed","rejected","expired","duplicate","complete"]);
const GOAL_ID=/^[A-Za-z0-9._:-]{8,96}$/;
const CONTROL=/[\u0000-\u001F\u007F]/g;
const SECRET_HINT=/(?:bearer\s+[a-z0-9._~-]+|(?:password|passwd|secret|api[_ -]?key|access[_ -]?token|refresh[_ -]?token)\s*[:=])/i;

export class ContinuityRevisionConflict extends Error{
  readonly current_revision:number;
  constructor(currentRevision:number){
    super("continuity_revision_conflict");
    this.name="ContinuityRevisionConflict";
    this.current_revision=currentRevision;
  }
}

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

function continuityId(topic:string){
  return "hc3-"+crypto.createHash("sha256").update("HAKIM-CONTINUITY-ID-v1\0"+topic).digest("hex").slice(0,24);
}

export class ContinuityStore{
  private readonly root:string;
  private readonly locks=new Map<string,Promise<void>>();

  constructor(root=path.join(process.env.HAKIM_DATA_DIR??os.tmpdir(),"continuity")){
    this.root=root;
  }

  async init(){
    await stateBackend.initDir(this.root);
  }

  private deviceKey(c:DeviceCredential){
    return crypto.createHash("sha256").update(c.topic).digest("hex");
  }

  private file(c:DeviceCredential){
    return path.join(this.root,this.deviceKey(c)+".json");
  }

  private fileForTopic(topic:string){
    return path.join(this.root,crypto.createHash("sha256").update(topic).digest("hex")+".json");
  }

  private empty():DeviceJournal{
    return {
      version:"HAKIM_CONTINUITY_V3",
      updated_at_ms:Date.now(),
      checkpoint_revision:0,
      work:null,
      operations:[]
    };
  }

  private normalize(raw:Record<string,unknown>):DeviceJournal{
    const cutoff=Date.now()-MAX_AGE_MS;
    const operations=Array.isArray(raw.operations)?(raw.operations as OperationRecord[]).filter(x=>
      x&&typeof x.request_id==="string"&&typeof x.op==="string"&&
      typeof x.updated_at_ms==="number"&&x.updated_at_ms>=cutoff
    ).slice(-MAX_OPERATIONS):[];

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

    const checkpointRevision=
      typeof raw.checkpoint_revision==="number"&&Number.isInteger(raw.checkpoint_revision)&&raw.checkpoint_revision>=0
        ?raw.checkpoint_revision
        :(work?1:0);

    return {
      version:"HAKIM_CONTINUITY_V3",
      updated_at_ms:typeof raw.updated_at_ms==="number"?raw.updated_at_ms:Date.now(),
      checkpoint_revision:checkpointRevision,
      work,
      operations
    };
  }

  private async readFile(file:string):Promise<DeviceJournal>{
    try{
      const raw=JSON.parse(await stateBackend.readText(file)) as Record<string,unknown>;
      return this.normalize(raw);
    }catch{
      return this.empty();
    }
  }

  private async read(c:DeviceCredential):Promise<DeviceJournal>{
    return this.readFile(this.file(c));
  }

  private async write(c:DeviceCredential,journal:DeviceJournal){
    await stateBackend.initDir(this.root);
    await stateBackend.writeTextAtomic(this.file(c),JSON.stringify(journal));
  }

  private async mutate(c:DeviceCredential,fn:(journal:DeviceJournal)=>void):Promise<DeviceJournal>{
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
      journal.version="HAKIM_CONTINUITY_V3";
      journal.updated_at_ms=Date.now();
      journal.operations=journal.operations
        .filter(x=>Date.now()-x.updated_at_ms<=MAX_AGE_MS)
        .slice(-MAX_OPERATIONS);
      await this.write(c,journal);
      return journal;
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
      expected_revision?:number;
    }
  ){
    const goalId=(input.goal_id??("goal-"+crypto.randomUUID().replace(/-/g,"").slice(0,20))).trim();
    if(!GOAL_ID.test(goalId)) throw new Error("invalid_goal_id");
    if(input.expected_revision!==undefined&&
      (!Number.isInteger(input.expected_revision)||input.expected_revision<0)){
      throw new Error("invalid_expected_revision");
    }
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

    const journal=await this.mutate(c,current=>{
      if(input.expected_revision!==undefined&&current.checkpoint_revision!==input.expected_revision){
        throw new ContinuityRevisionConflict(current.checkpoint_revision);
      }
      current.checkpoint_revision+=1;
      current.work=checkpoint;
    });
    return {...checkpoint,checkpoint_revision:journal.checkpoint_revision};
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

  async stateForTopic(topic:string){
    return this.publicState(await this.readFile(this.fileForTopic(topic)),continuityId(topic));
  }

  async state(c:DeviceCredential){
    return this.publicState(await this.read(c),continuityId(c.topic));
  }

  private publicState(journal:DeviceJournal,id:string){
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
      continuity_id:id,
      durable:true,
      checkpoint_revision:journal.checkpoint_revision,
      updated_at_ms:journal.updated_at_ms,
      work:journal.work,
      pending_count:operations.filter(x=>x.resumable).length,
      operations,
      protocols:{
        mcp:true,
        https_json:"HAKIM_CONTINUITY_HTTP_V1",
        device_mirror:"HAKIM_DEVICE_CONTINUITY_V1"
      },
      resume_rule:"Read the canonical checkpoint before continuing. Reuse pending operation_token values instead of replaying actions. When updating a checkpoint, send the checkpoint_revision you read; on conflict, re-read and reconcile instead of overwriting another conversation or agent.",
      privacy:"The journal stores only bounded work checkpoint text plus operation type/token/status. It does not store screen/page/notification contents, typed values, relay keys, credentials, model prompts, provider cookies, or conversation transcripts."
    };
  }
}

export const continuityStore=new ContinuityStore();
