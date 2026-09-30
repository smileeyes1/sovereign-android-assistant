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
const BINDING_ID=/^hb1_[A-Za-z0-9_-]{32,80}$/;
const CONTINUITY_ID=/^hc3-[0-9a-f]{24}$/;

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

function topicContinuityId(topic:string){
  return "hc3-"+crypto.createHash("sha256").update("HAKIM-CONTINUITY-ID-v1\0"+topic).digest("hex").slice(0,24);
}
function bindingContinuityId(bindingId:string){
  return "hc3-"+crypto.createHash("sha256").update("HAKIM-CONTINUITY-BINDING-v1\0"+bindingId).digest("hex").slice(0,24);
}

export class ContinuityStore{
  private readonly root:string;
  private readonly locks=new Map<string,Promise<void>>();

  constructor(root=path.join(process.env.HAKIM_DATA_DIR??os.tmpdir(),"continuity")){
    this.root=root;
  }

  async init(){
    await Promise.all([
      fs.mkdir(this.root,{recursive:true}),
      fs.mkdir(path.join(this.root,"aliases"),{recursive:true,mode:0o700})
    ]);
  }

  private aliasFile(topic:string){
    return path.join(this.root,"aliases",crypto.createHash("sha256").update(topic).digest("hex")+".txt");
  }

  async bindAlias(topic:string,bindingId:string){
    if(!BINDING_ID.test(bindingId)) throw new Error("invalid_continuity_binding_id");
    const id=bindingContinuityId(bindingId);
    const file=this.aliasFile(topic);
    try{
      const current=(await fs.readFile(file,"utf8")).trim();
      if(current!==id) throw new Error("continuity_alias_conflict");
      return id;
    }catch(e){
      if((e as NodeJS.ErrnoException).code!=="ENOENT") throw e;
    }
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,id,{encoding:"utf8",mode:0o600,flag:"wx"});
    await fs.rename(tmp,file);
    return id;
  }

  private async continuityIdForTopic(topic:string){
    try{
      const id=(await fs.readFile(this.aliasFile(topic),"utf8")).trim();
      if(CONTINUITY_ID.test(id)) return id;
    }catch{}
    return topicContinuityId(topic);
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
      const raw=JSON.parse(await fs.readFile(file,"utf8")) as Record<string,unknown>;
      return this.normalize(raw);
    }catch{
      return this.empty();
    }
  }

  private async read(c:DeviceCredential):Promise<DeviceJournal>{
    return this.readFile(this.file(c));
  }

  private async write(c:DeviceCredential,journal:DeviceJournal){
    await fs.mkdir(this.root,{recursive:true});
    const file=this.file(c);
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,JSON.stringify(journal),{encoding:"utf8",mode:0o600});
    await fs.rename(tmp,file);
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

  async migrateCheckpoint(from:DeviceCredential,to:DeviceCredential,bindingId:string){
    const stableId=await this.bindAlias(from.topic,bindingId);
    await this.bindAlias(to.topic,bindingId);
    if(from.topic===to.topic) return {migrated:false,continuity_id:stableId};

    const source=await this.read(from);
    const target=await this.read(to);
    if(!source.work) return {migrated:false,continuity_id:stableId};

    const sourceWins=!target.work||source.work.updated_at_ms>=target.work.updated_at_ms;
    if(!sourceWins) return {migrated:false,continuity_id:stableId};

    target.work={...source.work};
    target.checkpoint_revision=Math.max(source.checkpoint_revision,target.checkpoint_revision)+1;
    target.updated_at_ms=Date.now();
    // Deliberately do not copy operation tokens across a credential rotation.
    // They are tied to the previous channel/key and must never be replayed implicitly.
    await this.write(to,target);
    return {migrated:true,continuity_id:stableId};
  }

  async stateForTopic(topic:string){
    return this.publicState(
      await this.readFile(this.fileForTopic(topic)),
      await this.continuityIdForTopic(topic)
    );
  }

  async state(c:DeviceCredential){
    return this.publicState(await this.read(c),await this.continuityIdForTopic(c.topic));
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
