import crypto from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { decryptResult } from "./protocol.js";

const REQUEST_ID=/^dev-[a-z0-9]+-[0-9a-f]{12}$/;
const FINGERPRINT=/^[0-9a-f]{64}$/;
const CONTROL_VERSION=/^HAKIM-DEVELOPMENT-CONTROL-[0-9-]+-v[0-9]+$/;
const TRIGGERS=new Set(["field_candidate_regression","self_check_failed","repeated_runtime_failure"]);
const SEVERITIES=new Set(["critical","high"]);
const MAX_PENDING=64;
const DEFAULT_LEASE_MS=20*60_000;

export type DevelopmentOutcome="success"|"no_change"|"failed";
export type DevelopmentEvidence={
  code:"rollback_forward_requested"|"self_check_fail_closed"|"consecutive_runtime_failures"|"legacy_unstructured";
  failure_count:number;
  self_check_status:""|"PASS"|"PASS_WITH_WARNINGS"|"FAIL_CLOSED"|"NOT_TESTED";
  candidate_state:string;
  action_code:string;
};
export type DevelopmentState="pending"|"leased"|"completed"|"failed";

export type DevelopmentRecord={
  intake_version:"HAKIM-DEVELOPMENT-INTAKE-2026-09-30-v2";
  request_id:string;
  device_id:string;
  received_at_ms:number;
  requested_at_ms:number;
  control_version:string;
  package:"ps.hakim.stable";
  current_version_code:number;
  trigger:string;
  severity:string;
  fingerprint:string;
  evidence:DevelopmentEvidence;
  state:DevelopmentState;
  lease_owner?:string;
  lease_expires_at_ms?:number;
  completed_at_ms?:number;
  outcome?:DevelopmentOutcome;
  result_sha?:string;
  pr_number?:number;
  attempt_count?:number;
  next_attempt_at_ms?:number;
  last_wait_reason?:string;
  constraints:{
    source_mutation_on_device:false;
    github_secret_on_device:false;
    isolated_branch_required:true;
    ci_required:true;
    regression_test_required:true;
    verified_baseline_required:true;
    field_install_requires_separate_authorization:true;
    same_artifact_field_evidence_required:true;
    no_permission_expansion:true;
  };
};

function sha(value:string){
  return crypto.createHash("sha256").update(value,"utf8").digest("hex");
}

function bool(obj:Record<string,unknown>,key:string,expected:boolean){
  return obj[key]===expected;
}

function boundedEvidence(raw:unknown,controlVersion:string):DevelopmentEvidence{
  if(raw===undefined&&controlVersion.endsWith("-v1")){
    return {
      code:"legacy_unstructured",
      failure_count:0,
      self_check_status:"",
      candidate_state:"",
      action_code:""
    };
  }
  if(!raw||typeof raw!=="object"||Array.isArray(raw)) throw new Error("development_evidence_invalid");
  const e=raw as Record<string,unknown>;
  const allowedCodes=new Set(["rollback_forward_requested","self_check_fail_closed","consecutive_runtime_failures"]);
  if(typeof e.code!=="string"||!allowedCodes.has(e.code)) throw new Error("development_evidence_code_invalid");
  const failureCount=e.failure_count;
  if(typeof failureCount!=="number"||!Number.isSafeInteger(failureCount)||failureCount<0||failureCount>1000){
    throw new Error("development_evidence_failure_count_invalid");
  }
  const selfCheck=typeof e.self_check_status==="string"?e.self_check_status:"";
  if(!["","PASS","PASS_WITH_WARNINGS","FAIL_CLOSED","NOT_TESTED"].includes(selfCheck)){
    throw new Error("development_evidence_self_check_invalid");
  }
  const candidateState=typeof e.candidate_state==="string"?e.candidate_state:"";
  if(candidateState.length>48||!/^[A-Za-z0-9_\-]*$/.test(candidateState)){
    throw new Error("development_evidence_candidate_state_invalid");
  }
  const actionCode=typeof e.action_code==="string"?e.action_code:"";
  if(actionCode.length>48||!/^[a-z0-9_\-]*$/.test(actionCode)){
    throw new Error("development_evidence_action_code_invalid");
  }
  return {
    code:e.code as DevelopmentEvidence["code"],
    failure_count:failureCount,
    self_check_status:selfCheck as DevelopmentEvidence["self_check_status"],
    candidate_state:candidateState,
    action_code:actionCode
  };
}

function safeWorkerId(raw:string){
  const value=raw.trim();
  if(!/^[A-Za-z0-9._:-]{8,160}$/.test(value)) throw new Error("development_worker_id_invalid");
  return value;
}

export class DevelopmentRequestStore{
  readonly root:string;

  constructor(dataDir=process.env.HAKIM_DATA_DIR ?? path.join(os.tmpdir(),"hakim-oauth-dev")){
    this.root=path.join(dataDir,"development-intake-v2");
  }

  async init(){
    await fs.mkdir(this.root,{recursive:true});
  }

  async capture(resultTopic:string,relayKey:string,carrier:string):Promise<DevelopmentRecord|null>{
    const decoded=decryptResult(relayKey,carrier) as {
      status?:unknown;
      result?:{development_request?:unknown};
    };
    if(decoded?.status!=="health") return null;
    const raw=decoded.result?.development_request;
    if(!raw||typeof raw!=="object"||Array.isArray(raw)) return null;
    const r=raw as Record<string,unknown>;

    if(r.schema_version!==1) throw new Error("development_schema_invalid");
    if(typeof r.control_version!=="string"||!CONTROL_VERSION.test(r.control_version)) throw new Error("development_control_version_invalid");
    if(typeof r.request_id!=="string"||!REQUEST_ID.test(r.request_id)) throw new Error("development_request_id_invalid");
    if(r.package!=="ps.hakim.stable") throw new Error("development_package_invalid");
    if(typeof r.current_version_code!=="number"||!Number.isSafeInteger(r.current_version_code)||r.current_version_code<1) throw new Error("development_version_invalid");
    if(typeof r.requested_at_ms!=="number"||!Number.isSafeInteger(r.requested_at_ms)||r.requested_at_ms<1) throw new Error("development_time_invalid");
    if(typeof r.trigger!=="string"||!TRIGGERS.has(r.trigger)) throw new Error("development_trigger_invalid");
    if(typeof r.severity!=="string"||!SEVERITIES.has(r.severity)) throw new Error("development_severity_invalid");
    if(typeof r.fingerprint!=="string"||!FINGERPRINT.test(r.fingerprint)) throw new Error("development_fingerprint_invalid");
    const evidence=boundedEvidence(r.evidence,r.control_version);

    const c=r.constraints;
    if(!c||typeof c!=="object"||Array.isArray(c)) throw new Error("development_constraints_invalid");
    const constraints=c as Record<string,unknown>;
    const valid=
      bool(constraints,"source_mutation_on_device",false)&&
      bool(constraints,"github_secret_on_device",false)&&
      bool(constraints,"isolated_branch_required",true)&&
      bool(constraints,"ci_required",true)&&
      bool(constraints,"regression_test_required",true)&&
      bool(constraints,"verified_baseline_required",true)&&
      bool(constraints,"field_install_requires_separate_authorization",true)&&
      bool(constraints,"same_artifact_field_evidence_required",true)&&
      bool(constraints,"no_permission_expansion",true);
    if(!valid) throw new Error("development_constraints_weakened");

    const record:DevelopmentRecord={
      intake_version:"HAKIM-DEVELOPMENT-INTAKE-2026-09-30-v2",
      request_id:r.request_id,
      device_id:sha(resultTopic).slice(0,24),
      received_at_ms:Date.now(),
      requested_at_ms:r.requested_at_ms,
      control_version:r.control_version,
      package:"ps.hakim.stable",
      current_version_code:r.current_version_code,
      trigger:r.trigger,
      severity:r.severity,
      fingerprint:r.fingerprint,
      evidence,
      state:"pending",
      attempt_count:0,
      constraints:{
        source_mutation_on_device:false,
        github_secret_on_device:false,
        isolated_branch_required:true,
        ci_required:true,
        regression_test_required:true,
        verified_baseline_required:true,
        field_install_requires_separate_authorization:true,
        same_artifact_field_evidence_required:true,
        no_permission_expansion:true
      }
    };

    await this.init();
    const file=this.fileFor(record.request_id);
    try{
      const existing=JSON.parse(await fs.readFile(file,"utf8")) as DevelopmentRecord;
      return existing;
    }catch(e){
      if((e as NodeJS.ErrnoException).code!=="ENOENT") throw e;
    }
    await this.writeAtomic(file,record);
    await this.trim();
    return record;
  }

  async list():Promise<DevelopmentRecord[]>{
    await this.init();
    const names=(await fs.readdir(this.root)).filter(x=>x.endsWith(".json")).sort().slice(-MAX_PENDING);
    const out:DevelopmentRecord[]=[];
    for(const name of names){
      try{
        out.push(await this.readRecord(path.join(this.root,name)));
      }catch{
        await fs.unlink(path.join(this.root,name)).catch(()=>{});
      }
    }
    return out.sort((a,b)=>a.received_at_ms-b.received_at_ms);
  }

  async claimNext(workerIdRaw:string,leaseMs=DEFAULT_LEASE_MS):Promise<DevelopmentRecord|null>{
    const workerId=safeWorkerId(workerIdRaw);
    const boundedLease=Math.max(60_000,Math.min(leaseMs,60*60_000));
    const now=Date.now();
    for(const record of await this.list()){
      const expired=record.state==="leased"&&(record.lease_expires_at_ms??0)<=now;
      if(record.state!=="pending"&&!expired) continue;
      if(record.state==="pending"&&(record.next_attempt_at_ms??0)>now) continue;
      const claimed:DevelopmentRecord={
        ...record,
        state:"leased",
        attempt_count:(record.attempt_count??0)+1,
        lease_owner:workerId,
        lease_expires_at_ms:now+boundedLease
      };
      await this.writeAtomic(this.fileFor(record.request_id),claimed);
      return claimed;
    }
    return null;
  }

  async complete(
    requestId:string,
    workerIdRaw:string,
    outcome:DevelopmentOutcome,
    result:{result_sha?:string;pr_number?:number}={}
  ):Promise<DevelopmentRecord>{
    if(!REQUEST_ID.test(requestId)) throw new Error("development_request_id_invalid");
    const workerId=safeWorkerId(workerIdRaw);
    if(!["success","no_change","failed"].includes(outcome)) throw new Error("development_outcome_invalid");
    const file=this.fileFor(requestId);
    const current=await this.readRecord(file);
    if(current.state!=="leased") throw new Error("development_request_not_leased");
    if(current.lease_owner!==workerId) throw new Error("development_lease_owner_mismatch");
    if((current.lease_expires_at_ms??0)<Date.now()) throw new Error("development_lease_expired");

    const resultSha=result.result_sha?.trim();
    if(resultSha!==undefined&&!/^[0-9a-f]{40}$/i.test(resultSha)) throw new Error("development_result_sha_invalid");
    const prNumber=result.pr_number;
    if(prNumber!==undefined&&(!Number.isSafeInteger(prNumber)||prNumber<1)) throw new Error("development_pr_number_invalid");

    const completed:DevelopmentRecord={
      ...current,
      state:outcome==="failed"?"failed":"completed",
      completed_at_ms:Date.now(),
      outcome,
      ...(resultSha?{result_sha:resultSha.toLowerCase()}:{ }),
      ...(prNumber?{pr_number:prNumber}:{ })
    };
    delete completed.lease_owner;
    delete completed.lease_expires_at_ms;
    await this.writeAtomic(file,completed);
    return completed;
  }

  async defer(
    requestId:string,
    workerIdRaw:string,
    reason:"free_engine_unavailable"|"insufficient_evidence"|"safe_patch_not_found"|"transient_runner_failure",
    delayMs:number
  ):Promise<DevelopmentRecord>{
    if(!REQUEST_ID.test(requestId)) throw new Error("development_request_id_invalid");
    const workerId=safeWorkerId(workerIdRaw);
    if(!["free_engine_unavailable","insufficient_evidence","safe_patch_not_found","transient_runner_failure"].includes(reason)){
      throw new Error("development_defer_reason_invalid");
    }
    if(!Number.isSafeInteger(delayMs)||delayMs<5*60_000||delayMs>24*60*60_000){
      throw new Error("development_defer_delay_invalid");
    }
    const file=this.fileFor(requestId);
    const current=await this.readRecord(file);
    if(current.state!=="leased") throw new Error("development_request_not_leased");
    if(current.lease_owner!==workerId) throw new Error("development_lease_owner_mismatch");
    if((current.lease_expires_at_ms??0)<Date.now()) throw new Error("development_lease_expired");

    const deferred:DevelopmentRecord={
      ...current,
      state:"pending",
      next_attempt_at_ms:Date.now()+delayMs,
      last_wait_reason:reason
    };
    delete deferred.lease_owner;
    delete deferred.lease_expires_at_ms;
    await this.writeAtomic(file,deferred);
    return deferred;
  }

  private fileFor(requestId:string){
    return path.join(this.root,requestId+".json");
  }

  private async readRecord(file:string):Promise<DevelopmentRecord>{
    return JSON.parse(await fs.readFile(file,"utf8")) as DevelopmentRecord;
  }

  private async writeAtomic(file:string,value:unknown){
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,JSON.stringify(value),{encoding:"utf8",mode:0o600});
    await fs.rename(tmp,file);
  }

  private async trim(){
    const records=await this.list();
    const removable=records.filter(x=>x.state==="completed"||x.state==="failed");
    const over=Math.max(0,records.length-MAX_PENDING);
    for(const record of removable.slice(0,over)){
      await fs.unlink(this.fileFor(record.request_id)).catch(()=>{});
    }
  }
}

export const developmentRequestStore=new DevelopmentRequestStore();
