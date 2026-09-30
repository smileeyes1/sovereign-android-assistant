import crypto from "node:crypto";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { Client,handle_file } from "@gradio/client";

export const PUBLIC_ZEROGPU_VERSION="HAKIM_PUBLIC_ZEROGPU_WAN22_V1_2026-09-30";
const JOB=/^zgpu-[0-9a-f]{24}$/;
const TOKEN=/^[A-Za-z0-9_-]{32,120}$/;
const RETENTION_MS=48*60*60*1000;
const ZIMAGE="mrfakename/Z-Image-Turbo";
const WAN22="zerogpu-aoti/wan2-2-fp8da-aoti-faster";
const running=new Set<string>();

type JobState={
  job_id:string;
  token:string;
  created_at_ms:number;
  updated_at_ms:number;
  expires_at_ms:number;
  status:"queued"|"rendering"|"waiting_free_capacity"|"completed"|"failed";
  attempt:number;
  retry_at_ms:number|null;
  prompt:string;
  negative_prompt:string;
  duration_sec:number;
  seed:number;
  public_safe_synthetic:true;
  reference_file?:string;
  artifact?:{
    file:string;
    sha256:string;
    mime:"video/mp4";
    duration_sec:number;
    size_bytes:number;
    width:number;
    height:number;
    fps:number;
    playback_passed:boolean;
    technical_gates_passed:boolean;
    quality_gates_passed:boolean;
    cinematic_approval:boolean;
    renderer:string;
  };
  error?:string;
};

type CircuitState={
  failures:number;
  blocked_until_ms:number;
  last_reason:string|null;
  updated_at_ms:number;
};

function clean(v:unknown,max:number){
  return typeof v==="string"?v.trim().slice(0,max):"";
}

function root(env:NodeJS.ProcessEnv){
  const base=clean(env.HAKIM_DATA_DIR,600)||path.join(os.tmpdir(),"hakim-oauth-dev");
  return path.join(base,"public-zerogpu-renders");
}
function jobFile(dir:string,id:string){return path.join(dir,id+".json");}
function circuitFile(dir:string){return path.join(dir,"resource-state.json");}

async function ensure(env:NodeJS.ProcessEnv){
  const dir=root(env);
  await fs.mkdir(dir,{recursive:true});
  return dir;
}
async function atomic(file:string,value:unknown){
  const tmp=file+"."+process.pid+".tmp";
  await fs.writeFile(tmp,JSON.stringify(value),"utf8");
  await fs.rename(tmp,file);
}
async function saveJob(dir:string,j:JobState){await atomic(jobFile(dir,j.job_id),j);}
async function readJob(dir:string,id:string):Promise<JobState|null>{
  if(!JOB.test(id)) return null;
  try{
    const j=JSON.parse(await fs.readFile(jobFile(dir,id),"utf8")) as JobState;
    return j?.job_id===id?j:null;
  }catch{return null;}
}
async function readCircuit(dir:string):Promise<CircuitState>{
  try{
    const c=JSON.parse(await fs.readFile(circuitFile(dir),"utf8")) as CircuitState;
    if(Number.isInteger(c.failures)&&Number.isFinite(c.blocked_until_ms)) return c;
  }catch{}
  return {failures:0,blocked_until_ms:0,last_reason:null,updated_at_ms:Date.now()};
}
async function writeCircuit(dir:string,c:CircuitState){await atomic(circuitFile(dir),c);}

function isQuota(message:string){
  return /quota|zero.?gpu|subscribe to pro|exceeded.*daily|gpu.*duration|429/i.test(message);
}
function isTransient(message:string){
  return isQuota(message)||/timeout|fetch failed|5\d\d|ECONN|ENOTFOUND|temporar|unavailable/i.test(message);
}
function retryDelay(attempt:number,quota:boolean){
  if(quota) return Math.min(24*60*60*1000,60*60*1000*Math.max(1,Math.min(24,attempt)));
  return Math.min(60*60*1000,5*60*1000*Math.max(1,2**Math.min(4,attempt-1)));
}

async function noteFailure(dir:string,message:string){
  const current=await readCircuit(dir);
  const failures=Math.min(24,current.failures+1);
  const delay=retryDelay(failures,isQuota(message));
  await writeCircuit(dir,{
    failures,
    blocked_until_ms:Date.now()+delay,
    last_reason:isQuota(message)?"free_quota_exhausted":"transient_provider_failure",
    updated_at_ms:Date.now()
  });
}
async function noteSuccess(dir:string){
  await writeCircuit(dir,{failures:0,blocked_until_ms:0,last_reason:null,updated_at_ms:Date.now()});
}

export function publicZeroGpuEnabled(env:NodeJS.ProcessEnv=process.env){
  return env.HAKIM_PUBLIC_ZERO_GPU_ENABLED==="1";
}

export async function publicZeroGpuResourceStatus(env:NodeJS.ProcessEnv=process.env){
  const dir=await ensure(env);
  const c=await readCircuit(dir);
  const now=Date.now();
  return {
    id:"public-hf-zimage-wan22",
    kind:"public_free" as const,
    capability:"generative_video",
    available:publicZeroGpuEnabled(env),
    verified:true,
    privacy:"public" as const,
    cost_usd:0,
    quota_remaining:c.blocked_until_ms<=now?"unknown" as const:false as const,
    resumable:true,
    priority:90,
    blocked_until_ms:c.blocked_until_ms,
    circuit_failures:c.failures,
    last_reason:c.last_reason
  };
}

type GradioPrediction={data?:unknown};

async function withTimeout<T>(promise:Promise<T>,timeoutMs:number,label:string){
  let timer:NodeJS.Timeout|undefined;
  try{
    return await Promise.race([
      promise,
      new Promise<T>((_,reject)=>{
        timer=setTimeout(()=>reject(new Error(label+"_timeout")),timeoutMs);
        timer.unref?.();
      })
    ]);
  }finally{
    if(timer) clearTimeout(timer);
  }
}

async function gradioPredict(
  space:string,
  endpoint:string,
  payload:Record<string,unknown>,
  timeoutMs:number
){
  try{
    const client=await withTimeout(Client.connect(space),60_000,"gradio_connect");
    const result=await withTimeout(
      client.predict(endpoint,payload) as Promise<GradioPrediction>,
      timeoutMs,
      "gradio_predict"
    );
    return Array.isArray(result?.data)?result.data:[result?.data];
  }catch(error){
    const message=error instanceof Error?error.message:String(error);
    throw new Error("gradio_client_failed:"+message.slice(0,1000));
  }
}

function outputUrl(value:unknown){
  const queue:Array<unknown>=[value];
  while(queue.length){
    const x=queue.shift();
    if(typeof x==="string"&&/^https:\/\//.test(x)) return x;
    if(Array.isArray(x)){queue.push(...x);continue;}
    if(x&&typeof x==="object"){
      const r=x as Record<string,unknown>;
      if(typeof r.url==="string"&&/^https:\/\//.test(r.url)) return r.url;
      queue.push(...Object.values(r));
    }
  }
  return null;
}

async function download(url:string,file:string,timeoutMs:number){
  const response=await fetch(url,{signal:AbortSignal.timeout(timeoutMs)});
  if(!response.ok) throw new Error("artifact_download_http_"+response.status);
  const max=40*1024*1024;
  const len=Number(response.headers.get("content-length")||0);
  if(Number.isFinite(len)&&len>max) throw new Error("artifact_too_large");
  const buf=Buffer.from(await response.arrayBuffer());
  if(buf.length<1024||buf.length>max) throw new Error("artifact_invalid_size");
  await fs.writeFile(file,buf);
}

function run(command:string,args:string[],timeoutMs:number){
  return new Promise<string>((resolve,reject)=>{
    const child=spawn(command,args,{stdio:["ignore","pipe","pipe"]});
    let out="",err="";
    child.stdout.on("data",d=>{out+=String(d).slice(0,20000);});
    child.stderr.on("data",d=>{err+=String(d).slice(-12000);});
    const timer=setTimeout(()=>{child.kill("SIGKILL");reject(new Error(command+"_timeout"));},timeoutMs);
    timer.unref?.();
    child.on("error",e=>{clearTimeout(timer);reject(e);});
    child.on("close",code=>{
      clearTimeout(timer);
      if(code===0) resolve(out);
      else reject(new Error(command+"_exit_"+String(code)+":"+err.slice(-1000)));
    });
  });
}
async function probe(file:string){
  const raw=await run("ffprobe",[
    "-v","error","-show_entries","format=duration,size",
    "-show_entries","stream=codec_type,codec_name,width,height,r_frame_rate",
    "-of","json",file
  ],20_000);
  const p=JSON.parse(raw) as {streams?:Array<Record<string,unknown>>,format?:Record<string,unknown>};
  const video=(p.streams??[]).find(x=>x.codec_type==="video");
  const duration=Number(p.format?.duration);
  const size=Number(p.format?.size);
  const width=Number(video?.width),height=Number(video?.height);
  const rate=typeof video?.r_frame_rate==="string"?video.r_frame_rate:"";
  const parts=rate.split("/").map(Number);
  const n=Number(parts[0]??0);
  const d=Number(parts[1]??0);
  const fps=d? n/d:Number(rate);
  const playback=!!video&&duration>0&&size>4096&&width>=480&&height>=360&&fps>=12;
  return {
    duration_sec:Number.isFinite(duration)?Number(duration.toFixed(3)):0,
    size_bytes:Number.isFinite(size)?size:0,
    width:Number.isFinite(width)?width:0,
    height:Number.isFinite(height)?height:0,
    fps:Number.isFinite(fps)?Number(fps.toFixed(3)):0,
    playback_passed:playback
  };
}
async function sha256(file:string){
  return crypto.createHash("sha256").update(await fs.readFile(file)).digest("hex");
}

async function render(job:JobState,dir:string){
  if(running.has(job.job_id)) return;
  running.add(job.job_id);
  try{
    const circuit=await readCircuit(dir);
    if(circuit.blocked_until_ms>Date.now()){
      job.status="waiting_free_capacity";
      job.retry_at_ms=circuit.blocked_until_ms;
      job.updated_at_ms=Date.now();
      await saveJob(dir,job);
      return;
    }
    job.status="rendering";
    job.retry_at_ms=null;
    job.attempt+=1;
    job.updated_at_ms=Date.now();
    await saveJob(dir,job);

    const reference=job.reference_file||path.join(dir,job.job_id+"-reference.png");
    if(!job.reference_file){
      const image=await gradioPredict(ZIMAGE,"/generate_image",{
        prompt:job.prompt+" Single polished keyframe, character fully visible, no text, no numbers, no logo.",
        height:768,width:1344,num_inference_steps:6,seed:job.seed,randomize_seed:false
      },180_000);
      const url=outputUrl(image);
      if(!url) throw new Error("zimage_output_missing");
      await download(url,reference,45_000);
      job.reference_file=reference;
      job.updated_at_ms=Date.now();
      await saveJob(dir,job);
    }

    const video=await gradioPredict(WAN22,"/generate_video",{
      input_image:handle_file(reference),
      prompt:job.prompt,
      steps:4,
      negative_prompt:job.negative_prompt,
      duration_seconds:job.duration_sec,
      guidance_scale:1.0,
      guidance_scale_2:1.0,
      seed:job.seed,
      randomize_seed:false
    },300_000);
    const url=outputUrl(video);
    if(!url) throw new Error("wan22_output_missing");
    const output=path.join(dir,job.job_id+".mp4");
    await download(url,output,90_000);
    const p=await probe(output);
    const digest=await sha256(output);
    const technical=p.playback_passed&&/^[0-9a-f]{64}$/.test(digest);
    job.artifact={
      file:output,sha256:digest,mime:"video/mp4",
      duration_sec:p.duration_sec,size_bytes:p.size_bytes,width:p.width,height:p.height,fps:p.fps,
      playback_passed:p.playback_passed,technical_gates_passed:technical,
      quality_gates_passed:technical,cinematic_approval:false,renderer:PUBLIC_ZEROGPU_VERSION
    };
    job.status=technical?"completed":"failed";
    job.error=technical?undefined:"public_zerogpu_quality_gate_failed";
    job.updated_at_ms=Date.now();
    if(technical) await noteSuccess(dir);
    else await noteFailure(dir,job.error??"public_zerogpu_quality_gate_failed");
    await saveJob(dir,job);
  }catch(error){
    const message=error instanceof Error?error.message:"public_zerogpu_failed";
    job.updated_at_ms=Date.now();
    job.error=message.slice(0,800);
    if(isTransient(message)){
      await noteFailure(dir,message);
      const circuit=await readCircuit(dir);
      job.status="waiting_free_capacity";
      job.retry_at_ms=circuit.blocked_until_ms;
    }else{
      job.status="failed";
    }
    await saveJob(dir,job);
  }finally{
    running.delete(job.job_id);
  }
}

export async function submitPublicZeroGpuRender(input:{
  prompt:string;
  negative_prompt?:string;
  duration_sec?:number;
  seed?:number;
  public_safe_synthetic:true;
},env:NodeJS.ProcessEnv=process.env){
  if(!publicZeroGpuEnabled(env)) throw new Error("public_zerogpu_disabled");
  if(input.public_safe_synthetic!==true) throw new Error("public_zerogpu_requires_synthetic_public_safe");
  const prompt=clean(input.prompt,4000);
  if(!prompt) throw new Error("public_zerogpu_prompt_required");
  const dir=await ensure(env);
  const now=Date.now();
  const job:JobState={
    job_id:"zgpu-"+crypto.randomBytes(12).toString("hex"),
    token:crypto.randomBytes(32).toString("base64url"),
    created_at_ms:now,updated_at_ms:now,expires_at_ms:now+RETENTION_MS,
    status:"queued",attempt:0,retry_at_ms:null,
    prompt,
    negative_prompt:clean(input.negative_prompt,1600)||
      "text, letters, numbers, logo, watermark, duplicate character, extra limbs, malformed anatomy, flicker, morphing, unstable background, sudden camera jump",
    duration_sec:Math.max(0.5,Math.min(3,Number(input.duration_sec)||1.5)),
    seed:Math.max(0,Math.min(2_147_483_647,Math.trunc(Number(input.seed)||42))),
    public_safe_synthetic:true
  };
  await saveJob(dir,job);
  void render(job,dir);
  return {
    ok:true,status:"accepted",job_id:job.job_id,adapter:"public_zerogpu_wan22",
    render_success:false,artifact_verified:false,public_safe_synthetic:true,cost_usd:0,
    rule:"accepted_is_not_render_success"
  };
}

export async function getPublicZeroGpuStatus(id:string,env:NodeJS.ProcessEnv=process.env){
  const dir=await ensure(env);
  const job=await readJob(dir,id);
  if(!job) throw new Error("public_zerogpu_job_not_found");
  if(job.status==="waiting_free_capacity"&&(job.retry_at_ms??0)<=Date.now()) void render(job,dir);
  const a=job.artifact;
  const base=clean(env.HAKIM_VIDEO_PUBLIC_BASE_URL,600).replace(/\/+$/,"");
  const url=a&&base?base+"/video/v1/artifacts/"+job.job_id+"/"+job.token+".mp4":null;
  return {
    ok:job.status!=="failed",
    job_id:job.job_id,
    adapter:"public_zerogpu_wan22",
    status:job.status,
    retry_at_ms:job.retry_at_ms,
    attempt:job.attempt,
    cost_usd:0,
    artifact_verified:job.status==="completed"&&a?.technical_gates_passed===true,
    render_success:job.status==="completed"&&a?.technical_gates_passed===true,
    cinematic_approval:a?.cinematic_approval===true,
    artifact:a?{
      sha256:a.sha256,mime:a.mime,duration_sec:a.duration_sec,url,
      size_bytes:a.size_bytes,width:a.width,height:a.height,fps:a.fps,
      playback_passed:a.playback_passed,technical_gates_passed:a.technical_gates_passed,
      quality_gates_passed:a.quality_gates_passed
    }:null,
    error:job.error??null,
    rule:job.status==="waiting_free_capacity"?
      "free_capacity_wait_preserves_progress":"generated_shot_requires_cinematic_qa_before_film_delivery"
  };
}

export async function resolvePublicZeroGpuArtifact(id:string,token:string,env:NodeJS.ProcessEnv=process.env){
  if(!JOB.test(id)||!TOKEN.test(token)) return null;
  const dir=await ensure(env);
  const job=await readJob(dir,id);
  if(!job||job.status!=="completed"||!job.artifact||job.expires_at_ms<=Date.now()) return null;
  const a=Buffer.from(job.token),b=Buffer.from(token);
  if(a.length!==b.length||!crypto.timingSafeEqual(a,b)) return null;
  try{
    const st=await fs.stat(job.artifact.file);
    if(!st.isFile()||st.size<4096) return null;
  }catch{return null;}
  return {file:job.artifact.file,sha256:job.artifact.sha256,size_bytes:job.artifact.size_bytes};
}

export async function resumePublicZeroGpuJobs(env:NodeJS.ProcessEnv=process.env){
  if(!publicZeroGpuEnabled(env)) return {resumed:0};
  const dir=await ensure(env);
  const names=await fs.readdir(dir).catch(()=>[]);
  let resumed=0;
  for(const name of names){
    if(!name.startsWith("zgpu-")||!name.endsWith(".json")) continue;
    const id=name.slice(0,-5);
    const job=await readJob(dir,id);
    if(!job) continue;
    if((job.status==="queued"||job.status==="waiting_free_capacity")&&(job.retry_at_ms??0)<=Date.now()){
      resumed+=1; void render(job,dir);
    }
  }
  return {resumed};
}
