import crypto from "node:crypto";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";

const LOCAL_RENDERER_VERSION="HAKIM_LOCAL_CINEMATIC_RENDERER_V1_2026-09-30";
const JOB_ID=/^local-[0-9a-f]{24}$/;
const TOKEN=/^[A-Za-z0-9_-]{32,120}$/;
const RETENTION_MS=24*60*60*1000;

type JobState={
  job_id:string;
  status:"queued"|"rendering"|"completed"|"failed";
  created_at_ms:number;
  updated_at_ms:number;
  expires_at_ms:number;
  token:string;
  duration_sec:number;
  artifact?:{
    file:string;
    sha256:string;
    mime:"video/mp4";
    duration_sec:number;
    size_bytes:number;
    playback_passed:boolean;
    technical_gates_passed:boolean;
    quality_gates_passed:boolean;
    cinematic_approval:boolean;
    renderer:string;
    fps:number;
    width:number;
    height:number;
    has_video:boolean;
    has_audio:boolean;
  };
  error?:string;
};

function clean(value:unknown,max:number){
  return typeof value==="string"?value.trim().slice(0,max):"";
}

function dataDir(env:NodeJS.ProcessEnv){
  const root=clean(env.HAKIM_DATA_DIR,600)||path.join(os.tmpdir(),"hakim-oauth-dev");
  return path.join(root,"video-renders");
}

function publicBase(env:NodeJS.ProcessEnv){
  const raw=clean(env.HAKIM_VIDEO_PUBLIC_BASE_URL,600);
  if(!raw) return null;
  try{
    const u=new URL(raw);
    if(u.protocol!=="https:"||u.username||u.password||u.search||u.hash) return null;
    return u.origin+u.pathname.replace(/\/+$/,"");
  }catch{return null;}
}

function boundedDuration(plan:unknown){
  const record=typeof plan==="object"&&plan!==null?plan as Record<string,unknown>:{};
  const raw=Number(record.duration_sec);
  if(!Number.isFinite(raw)) return 8;
  return Math.max(8,Math.min(30,Math.round(raw)));
}

async function ensureDir(env:NodeJS.ProcessEnv){
  const dir=dataDir(env);
  await fs.mkdir(dir,{recursive:true});
  return dir;
}

function jobPath(dir:string,id:string){
  return path.join(dir,id+".json");
}

async function saveJob(dir:string,job:JobState){
  const target=jobPath(dir,job.job_id);
  const tmp=target+"."+process.pid+".tmp";
  await fs.writeFile(tmp,JSON.stringify(job),"utf8");
  await fs.rename(tmp,target);
}

async function loadJob(dir:string,id:string):Promise<JobState|null>{
  if(!JOB_ID.test(id)) return null;
  try{
    const parsed=JSON.parse(await fs.readFile(jobPath(dir,id),"utf8")) as JobState;
    return parsed&&parsed.job_id===id?parsed:null;
  }catch{return null;}
}

function run(command:string,args:string[],timeoutMs:number){
  return new Promise<{stdout:string;stderr:string}>((resolve,reject)=>{
    const child=spawn(command,args,{stdio:["ignore","pipe","pipe"]});
    let stdout="";
    let stderr="";
    child.stdout.on("data",d=>{stdout+=String(d).slice(0,20000);});
    child.stderr.on("data",d=>{stderr+=String(d).slice(-30000);});
    const timer=setTimeout(()=>{
      child.kill("SIGKILL");
      reject(new Error(command+"_timeout"));
    },timeoutMs);
    timer.unref?.();
    child.on("error",error=>{
      clearTimeout(timer);
      reject(error);
    });
    child.on("close",code=>{
      clearTimeout(timer);
      if(code===0) resolve({stdout,stderr});
      else reject(new Error(command+"_exit_"+String(code)+":"+stderr.slice(-1200)));
    });
  });
}

async function sha256File(file:string){
  const hash=crypto.createHash("sha256");
  const handle=await fs.open(file,"r");
  try{
    const stream=handle.createReadStream();
    for await(const chunk of stream) hash.update(chunk);
  }finally{
    await handle.close();
  }
  return hash.digest("hex");
}

async function probe(file:string){
  const result=await run("ffprobe",[
    "-v","error",
    "-show_entries","format=duration,size,format_name",
    "-show_entries","stream=codec_type,codec_name,width,height,r_frame_rate",
    "-of","json",
    file
  ],20_000);
  const parsed=JSON.parse(result.stdout) as {
    streams?:Array<Record<string,unknown>>;
    format?:Record<string,unknown>;
  };
  const streams=Array.isArray(parsed.streams)?parsed.streams:[];
  const video=streams.find(x=>x.codec_type==="video");
  const audio=streams.find(x=>x.codec_type==="audio");
  const duration=Number(parsed.format?.duration);
  const size=Number(parsed.format?.size);
  const width=Number(video?.width);
  const height=Number(video?.height);
  const rate=typeof video?.r_frame_rate==="string"?video.r_frame_rate:"";
  const fps=rate.includes("/")?Number(rate.split("/")[0])/Number(rate.split("/")[1]):Number(rate);
  const playbackPassed=Number.isFinite(duration)&&duration>0&&
    Number.isFinite(size)&&size>4096&&!!video&&
    String(video.codec_name||"").length>0&&
    width>=640&&height>=360&&Number.isFinite(fps)&&fps>=23&&fps<=25;
  return {
    duration_sec:Number.isFinite(duration)?Number(duration.toFixed(3)):0,
    size_bytes:Number.isFinite(size)?size:0,
    width:Number.isFinite(width)?width:0,
    height:Number.isFinite(height)?height:0,
    fps:Number.isFinite(fps)?Number(fps.toFixed(3)):0,
    has_video:!!video,
    has_audio:!!audio,
    playback_passed:playbackPassed
  };
}

async function cleanup(dir:string){
  let names:string[]=[];
  try{names=await fs.readdir(dir);}catch{return;}
  const now=Date.now();
  for(const name of names){
    if(!name.endsWith(".json")) continue;
    const p=path.join(dir,name);
    try{
      const job=JSON.parse(await fs.readFile(p,"utf8")) as JobState;
      if(!job?.expires_at_ms||job.expires_at_ms>now) continue;
      if(job.artifact?.file) await fs.rm(job.artifact.file,{force:true});
      await fs.rm(p,{force:true});
    }catch{
      await fs.rm(p,{force:true});
    }
  }
}

async function render(job:JobState,dir:string){
  job.status="rendering";
  job.updated_at_ms=Date.now();
  await saveJob(dir,job);
  const output=path.join(dir,job.job_id+".mp4");
  const d=job.duration_sec;
  const fadeOut=Math.max(0,d-0.6).toFixed(2);
  const moveDur=Math.max(1,d).toFixed(2);

  const filter=[
    "[0:v][1:v]overlay=x='80+(W-w-160)*t/"+moveDur+"':y='H/2-h/2':shortest=1[tmp1]",
    "[tmp1][2:v]overlay=x='W-250+18*sin(t*1.4)':y='H/2+105+16*sin(t*2.2)':shortest=1[tmp2]",
    "[tmp2]eq=contrast=1.06:saturation=0.88:brightness=-0.015",
    "vignette=PI/5",
    "fade=t=in:st=0:d=0.55",
    "fade=t=out:st="+fadeOut+":d=0.55",
    "format=yuv420p[v]",
    "[3:a]volume=0.018,afade=t=in:st=0:d=0.8,afade=t=out:st="+Math.max(0,d-0.9).toFixed(2)+":d=0.8[a]"
  ].join(";");

  try{
    await run("ffmpeg",[
      "-hide_banner","-loglevel","error","-y",
      "-f","lavfi","-i","color=c=0x11161f:s=1280x720:r=24:d="+d,
      "-f","lavfi","-i","color=c=0xb43b42:s=240x120:r=24:d="+d,
      "-f","lavfi","-i","color=c=0x3477b8:s=88x88:r=24:d="+d,
      "-f","lavfi","-i","sine=frequency=196:sample_rate=48000:duration="+d,
      "-filter_complex",filter,
      "-map","[v]","-map","[a]",
      "-c:v","libx264","-preset","veryfast","-crf","20",
      "-c:a","aac","-b:a","96k",
      "-movflags","+faststart",
      "-shortest",output
    ],90_000);

    const p=await probe(output);
    const digest=await sha256File(output);
    const technical=p.playback_passed&&/^[0-9a-f]{64}$/.test(digest);
    const quality=technical&&p.has_video&&p.has_audio&&p.width===1280&&p.height===720;
    job.artifact={
      file:output,
      sha256:digest,
      mime:"video/mp4",
      duration_sec:p.duration_sec,
      size_bytes:p.size_bytes,
      playback_passed:p.playback_passed,
      technical_gates_passed:technical,
      quality_gates_passed:quality,
      cinematic_approval:false,
      renderer:LOCAL_RENDERER_VERSION,
      fps:p.fps,
      width:p.width,
      height:p.height,
      has_video:p.has_video,
      has_audio:p.has_audio
    };
    job.status=quality?"completed":"failed";
    if(!quality) job.error="local_render_quality_gate_failed";
  }catch(error){
    job.status="failed";
    job.error=error instanceof Error?error.message.slice(0,800):"local_render_failed";
  }
  job.updated_at_ms=Date.now();
  await saveJob(dir,job);
}

export function localRendererAvailable(env:NodeJS.ProcessEnv=process.env){
  return env.HAKIM_VIDEO_RENDER_ENABLED==="1"&&
    env.HAKIM_VIDEO_EXECUTOR_ADAPTER==="local";
}

export async function submitLocalRender(plan:unknown,env:NodeJS.ProcessEnv=process.env){
  if(!localRendererAvailable(env)) throw new Error("local_video_renderer_disabled");
  const dir=await ensureDir(env);
  await cleanup(dir);
  const job:JobState={
    job_id:"local-"+crypto.randomBytes(12).toString("hex"),
    status:"queued",
    created_at_ms:Date.now(),
    updated_at_ms:Date.now(),
    expires_at_ms:Date.now()+RETENTION_MS,
    token:crypto.randomBytes(32).toString("base64url"),
    duration_sec:boundedDuration(plan)
  };
  await saveJob(dir,job);
  void render(job,dir);
  return {
    ok:true,
    status:"accepted",
    job_id:job.job_id,
    adapter:"local",
    render_success:false,
    artifact_verified:false,
    rule:"accepted_is_not_render_success"
  };
}

export async function getLocalRenderStatus(jobId:string,env:NodeJS.ProcessEnv=process.env){
  const dir=await ensureDir(env);
  const job=await loadJob(dir,jobId);
  if(!job) throw new Error("local_video_job_not_found");
  const expired=job.expires_at_ms<=Date.now();
  if(expired) throw new Error("local_video_job_expired");
  const a=job.artifact;
  const base=publicBase(env);
  const url=a&&base?base+"/video/v1/artifacts/"+job.job_id+"/"+job.token+".mp4":null;
  const verified=job.status==="completed"&&
    a?.playback_passed===true&&a?.technical_gates_passed===true&&a?.quality_gates_passed===true;
  return {
    ok:job.status!=="failed",
    job_id:job.job_id,
    adapter:"local",
    status:job.status,
    artifact_verified:verified,
    render_success:verified,
    artifact:a?{
      sha256:a.sha256,
      mime:a.mime,
      duration_sec:a.duration_sec,
      url,
      size_bytes:a.size_bytes,
      playback_passed:a.playback_passed,
      technical_gates_passed:a.technical_gates_passed,
      quality_gates_passed:a.quality_gates_passed,
      cinematic_approval:a.cinematic_approval,
      renderer:a.renderer,
      fps:a.fps,
      width:a.width,
      height:a.height,
      has_audio:a.has_audio
    }:null,
    error:job.error??null,
    rule:verified?"verified_local_fallback_artifact_not_generative_cinema":"not_yet_verified"
  };
}

export async function resolveLocalArtifact(jobId:string,token:string,env:NodeJS.ProcessEnv=process.env){
  if(!JOB_ID.test(jobId)||!TOKEN.test(token)) return null;
  const dir=await ensureDir(env);
  const job=await loadJob(dir,jobId);
  if(!job||job.expires_at_ms<=Date.now()||job.status!=="completed"||!job.artifact) return null;
  const a=Buffer.from(job.token);
  const b=Buffer.from(token);
  if(a.length!==b.length||!crypto.timingSafeEqual(a,b)) return null;
  try{
    const stat=await fs.stat(job.artifact.file);
    if(!stat.isFile()||stat.size<4096) return null;
  }catch{return null;}
  return {
    file:job.artifact.file,
    sha256:job.artifact.sha256,
    size_bytes:job.artifact.size_bytes,
    expires_at_ms:job.expires_at_ms
  };
}
