import crypto from "node:crypto";
import { CINEMATIC_DIRECTOR_VERSION } from "./cinematic-director.js";

const EXECUTOR_VERSION="HAKIM_VIDEO_EXECUTOR_V1";

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

function secret(env:NodeJS.ProcessEnv){
  const value=clean(env.HAKIM_VIDEO_EXECUTOR_SECRET,300);
  return value.length>=32?value:"";
}

export function cinematicExecutorEnabled(env:NodeJS.ProcessEnv=process.env){
  return env.HAKIM_VIDEO_RENDER_ENABLED==="1" &&
    clean(env.HAKIM_VIDEO_EXECUTOR_NAME,80)!=="" &&
    endpoint(env)!==null &&
    secret(env)!=="";
}

export function cinematicExecutorSummary(env:NodeJS.ProcessEnv=process.env){
  return {
    executor_version:EXECUTOR_VERSION,
    enabled:cinematicExecutorEnabled(env),
    name:clean(env.HAKIM_VIDEO_EXECUTOR_NAME,80)||null,
    endpoint_configured:endpoint(env)!==null,
    secret_configured:secret(env)!=="",
    free_only:true,
    paid_render_allowed:false,
    render_verified:false,
    success_requires_artifact:true
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
  if(typeof value!=="string"||!/^[A-Za-z0-9._:-]{8,160}$/.test(value)) throw new Error("invalid_video_job_id");
  return value;
}

export async function submitCinematicRender(plan:unknown,env:NodeJS.ProcessEnv=process.env){
  if(!cinematicExecutorEnabled(env)) throw new Error("video_executor_unavailable");
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
  const jobId=validateJobId(data.job_id);
  return {
    ok:true,
    status:"accepted",
    job_id:jobId,
    render_success:false,
    artifact_verified:false,
    rule:"accepted_is_not_render_success"
  };
}

export async function getCinematicRenderStatus(jobId:string,env:NodeJS.ProcessEnv=process.env){
  if(!cinematicExecutorEnabled(env)) throw new Error("video_executor_unavailable");
  const id=validateJobId(jobId);
  const base=endpoint(env)!;
  const body="";
  const response=await fetch(base+"/v1/jobs/"+encodeURIComponent(id),{
    method:"GET",
    headers:signedHeaders(body,env),
    signal:AbortSignal.timeout(15_000)
  });
  if(!response.ok) throw new Error("video_executor_http_"+response.status);
  const data=await response.json() as Record<string,unknown>;
  const state=typeof data.status==="string"?data.status:"unknown";
  const artifact=(data.artifact&&typeof data.artifact==="object")?data.artifact as Record<string,unknown>:null;
  const sha=typeof artifact?.sha256==="string"&&/^[0-9a-f]{64}$/i.test(artifact.sha256)?artifact.sha256.toLowerCase():null;
  const playbackPassed=artifact?.playback_passed===true;
  const gatesPassed=artifact?.quality_gates_passed===true;
  const verified=state==="completed"&&sha!==null&&playbackPassed&&gatesPassed;
  return {
    ok:true,
    job_id:id,
    status:state,
    artifact_verified:verified,
    render_success:verified,
    artifact:verified?{
      sha256:sha,
      mime:artifact?.mime==="video/mp4"?"video/mp4":null,
      duration_sec:typeof artifact?.duration_sec==="number"?artifact.duration_sec:null
    }:null,
    rule:verified?"verified_artifact":"not_yet_verified"
  };
}
