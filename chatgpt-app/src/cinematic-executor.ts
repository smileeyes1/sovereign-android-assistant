import crypto from "node:crypto";
import { CINEMATIC_DIRECTOR_VERSION } from "./cinematic-director.js";

const EXECUTOR_VERSION="HAKIM_VIDEO_EXECUTOR_V1";
type ExecutorAdapter="rest"|"gradio";

function clean(value:unknown,max:number){
  return typeof value==="string"?value.trim().slice(0,max):"";
}

function endpoint(env:NodeJS.ProcessEnv){
  const raw=clean(env.HAKIM_VIDEO_EXECUTOR_URL,600);
  if(!raw) return null;
  try{
    const u=new URL(raw);
    if(u.protocol!=="https:"||u.username||u.password||u.search||u.hash) return null;
    return u.origin+u.pathname.replace(/\/+$/,"");
  }catch{return null;}
}

function adapter(env:NodeJS.ProcessEnv):ExecutorAdapter{
  return env.HAKIM_VIDEO_EXECUTOR_ADAPTER==="gradio"?"gradio":"rest";
}

function secret(env:NodeJS.ProcessEnv){
  const value=clean(env.HAKIM_VIDEO_EXECUTOR_SECRET,300);
  return value.length>=32?value:"";
}

function boundedNumber(value:unknown,min:number,max:number,fallback:number){
  const n=typeof value==="number"?value:Number(value);
  return Number.isFinite(n)?Math.max(min,Math.min(max,n)):fallback;
}

function planRecord(plan:unknown):Record<string,unknown>{
  return typeof plan==="object"&&plan!==null?plan as Record<string,unknown>:{};
}

export function cinematicExecutorEnabled(env:NodeJS.ProcessEnv=process.env){
  const common=env.HAKIM_VIDEO_RENDER_ENABLED==="1" &&
    clean(env.HAKIM_VIDEO_EXECUTOR_NAME,80)!=="" &&
    endpoint(env)!==null;
  if(!common) return false;
  return adapter(env)==="gradio"||secret(env)!=="";
}

export function cinematicExecutorSummary(env:NodeJS.ProcessEnv=process.env){
  const kind=adapter(env);
  return {
    executor_version:EXECUTOR_VERSION,
    enabled:cinematicExecutorEnabled(env),
    adapter:kind,
    name:clean(env.HAKIM_VIDEO_EXECUTOR_NAME,80)||null,
    endpoint_configured:endpoint(env)!==null,
    secret_configured:secret(env)!=="",
    auth_mode:kind==="gradio"?"zerogpu_public_or_platform_session":"hmac",
    free_only:true,
    paid_render_allowed:false,
    render_verified:false,
    success_requires_artifact:true,
    gradio_quota_note:kind==="gradio"?"quota_is_enforced_by_hugging_face":null
  };
}

function signedHeaders(body:string,env:NodeJS.ProcessEnv){
  const key=secret(env);
  if(!key) throw new Error("video_executor_secret_missing");
  const ts=Date.now().toString();
  const nonce=crypto.randomBytes(16).toString("base64url");
  const canonical=[EXECUTOR_VERSION,ts,nonce,body].join("\n");
  const sig=crypto.createHmac("sha256",key).update(canonical,"utf8").digest("hex");
  return {
    "Content-Type":"application/json",
    "X-Hakim-Executor-Version":EXECUTOR_VERSION,
    "X-Hakim-Timestamp":ts,
    "X-Hakim-Nonce":nonce,
    "X-Hakim-Signature":sig
  };
}

function validateJobId(value:unknown){
  if(typeof value!=="string"||!/^[A-Za-z0-9._:-]{8,200}$/.test(value)) throw new Error("invalid_video_job_id");
  return value;
}

function accepted(jobId:string,kind:ExecutorAdapter){
  return {
    ok:true,
    status:"accepted",
    job_id:jobId,
    adapter:kind,
    render_success:false,
    artifact_verified:false,
    rule:"accepted_is_not_render_success"
  };
}

async function submitRest(plan:unknown,env:NodeJS.ProcessEnv){
  const base=endpoint(env)!;
  const body=JSON.stringify({
    executor_contract:EXECUTOR_VERSION,
    director_version:CINEMATIC_DIRECTOR_VERSION,
    budget_policy:{free_only:true,paid_approved:false},
    artifact_policy:{
      required:true,
      full_playback_required:true,
      hash_required:true,
      delivered_must_match_tested:true
    },
    plan
  });
  const response=await fetch(base+"/v1/render",{
    method:"POST",
    headers:signedHeaders(body,env),
    body,
    signal:AbortSignal.timeout(20_000)
  });
  if(!response.ok) throw new Error("video_executor_http_"+response.status);
  const data=await response.json() as Record<string,unknown>;
  return accepted(validateJobId(data.job_id),"rest");
}

async function submitGradio(plan:unknown,env:NodeJS.ProcessEnv){
  const base=endpoint(env)!;
  const p=planRecord(plan);
  const duration=boundedNumber(p.duration_sec,0.75,5,2);
  const steps=Math.trunc(boundedNumber(env.HAKIM_VIDEO_ZEROGPU_STEPS,4,30,12));
  const seed=Math.trunc(boundedNumber(env.HAKIM_VIDEO_ZEROGPU_SEED,0,2_147_483_647,42));
  const resolution=env.HAKIM_VIDEO_ZEROGPU_RESOLUTION==="720p"?"720p":"480p";

  const body=JSON.stringify({
    plan_json:JSON.stringify(plan),
    seed,
    steps,
    duration_seconds:duration,
    resolution
  });
  const response=await fetch(base+"/gradio_api/call/v2/render_video",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body,
    signal:AbortSignal.timeout(20_000)
  });
  if(!response.ok) throw new Error("video_gradio_submit_http_"+response.status);
  const data=await response.json() as Record<string,unknown>;
  return accepted(validateJobId(data.event_id),"gradio");
}

export async function submitCinematicRender(plan:unknown,env:NodeJS.ProcessEnv=process.env){
  if(!cinematicExecutorEnabled(env)) throw new Error("video_executor_unavailable");
  return adapter(env)==="gradio"?submitGradio(plan,env):submitRest(plan,env);
}

function parseSseComplete(raw:string){
  let complete:unknown=null;
  let failure:string|null=null;
  for(const block of raw.split(/\n\n+/)){
    const lines=block.split(/\n/);
    let event="";
    const dataLines:string[]=[];
    for(const line of lines){
      if(line.startsWith("event:")) event=line.slice(6).trim();
      else if(line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
    }
    if(event==="complete"&&dataLines.length){
      try{ complete=JSON.parse(dataLines.join("\n")); }catch{}
    }
    if((event==="error"||event==="failed")&&dataLines.length){
      failure=dataLines.join("\n").slice(0,500);
    }
  }
  return {complete,failure};
}

function evidenceObject(value:unknown):Record<string,unknown>|null{
  if(typeof value==="string"){
    try{
      const parsed=JSON.parse(value);
      return typeof parsed==="object"&&parsed!==null?parsed as Record<string,unknown>:null;
    }catch{return null;}
  }
  return typeof value==="object"&&value!==null?value as Record<string,unknown>:null;
}

function fileUrl(value:unknown):string|null{
  if(typeof value==="string"&&/^https:\/\//.test(value)) return value;
  if(typeof value!=="object"||value===null) return null;
  const record=value as Record<string,unknown>;
  for(const key of ["url","path"]){
    const candidate=record[key];
    if(typeof candidate==="string"&&/^https:\/\//.test(candidate)) return candidate.slice(0,1600);
  }
  return null;
}

function normalizeArtifact(
  state:string,
  artifact:Record<string,unknown>|null,
  url:string|null
){
  const sha=typeof artifact?.sha256==="string"&&/^[0-9a-f]{64}$/i.test(artifact.sha256)?
    artifact.sha256.toLowerCase():null;
  const playbackPassed=artifact?.playback_passed===true||artifact?.playback_probe==="passed";
  const gatesPassed=artifact?.quality_gates_passed===true;
  const verified=state==="completed"&&sha!==null&&playbackPassed&&gatesPassed;
  return {
    sha,
    playbackPassed,
    gatesPassed,
    verified,
    artifact:sha?{
      sha256:sha,
      mime:artifact?.mime==="video/mp4"?"video/mp4":null,
      duration_sec:typeof artifact?.duration_sec==="number"?artifact.duration_sec:null,
      url,
      technical_gates_passed:artifact?.technical_gates_passed===true,
      quality_gates_passed:gatesPassed
    }:null
  };
}

async function statusRest(id:string,env:NodeJS.ProcessEnv){
  const base=endpoint(env)!;
  const response=await fetch(base+"/v1/jobs/"+encodeURIComponent(id),{
    method:"GET",
    headers:signedHeaders("",env),
    signal:AbortSignal.timeout(15_000)
  });
  if(!response.ok) throw new Error("video_executor_http_"+response.status);
  const data=await response.json() as Record<string,unknown>;
  const state=typeof data.status==="string"?data.status:"unknown";
  const rawArtifact=(data.artifact&&typeof data.artifact==="object")?data.artifact as Record<string,unknown>:null;
  const normalized=normalizeArtifact(state,rawArtifact,fileUrl(rawArtifact));
  return {
    ok:true,
    job_id:id,
    adapter:"rest",
    status:state,
    artifact_verified:normalized.verified,
    render_success:normalized.verified,
    artifact:normalized.artifact,
    rule:normalized.verified?"verified_artifact":"not_yet_verified"
  };
}

async function statusGradio(id:string,env:NodeJS.ProcessEnv){
  const base=endpoint(env)!;
  let response:Response;
  try{
    response=await fetch(base+"/gradio_api/call/render_video/"+encodeURIComponent(id),{
      method:"GET",
      headers:{"Accept":"text/event-stream"},
      signal:AbortSignal.timeout(8_000)
    });
  }catch(error){
    if(error instanceof Error&&(error.name==="AbortError"||error.name==="TimeoutError")){
      return {
        ok:true,job_id:id,adapter:"gradio",status:"pending",
        artifact_verified:false,render_success:false,artifact:null,
        rule:"not_yet_verified"
      };
    }
    throw error;
  }
  if(!response.ok) throw new Error("video_gradio_status_http_"+response.status);
  const parsed=parseSseComplete(await response.text());
  if(parsed.failure){
    return {
      ok:false,job_id:id,adapter:"gradio",status:"failed",
      artifact_verified:false,render_success:false,artifact:null,
      error:"video_gradio_failed",detail:parsed.failure,
      rule:"not_yet_verified"
    };
  }
  if(!Array.isArray(parsed.complete)){
    return {
      ok:true,job_id:id,adapter:"gradio",status:"pending",
      artifact_verified:false,render_success:false,artifact:null,
      rule:"not_yet_verified"
    };
  }
  const outputs=parsed.complete as unknown[];
  const rawEvidence=evidenceObject(outputs[1]);
  const url=fileUrl(outputs[0]);
  const normalized=normalizeArtifact("completed",rawEvidence,url);
  return {
    ok:true,
    job_id:id,
    adapter:"gradio",
    status:"completed",
    artifact_verified:normalized.verified,
    render_success:normalized.verified,
    artifact:normalized.artifact,
    rule:normalized.verified?"verified_artifact":"rendered_pending_cinematic_qa"
  };
}

export async function getCinematicRenderStatus(jobId:string,env:NodeJS.ProcessEnv=process.env){
  if(!cinematicExecutorEnabled(env)) throw new Error("video_executor_unavailable");
  const id=validateJobId(jobId);
  return adapter(env)==="gradio"?statusGradio(id,env):statusRest(id,env);
}
