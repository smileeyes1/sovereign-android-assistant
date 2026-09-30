import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { buildFilmBlueprint,FilmProjectInput,FILM_OS_VERSION } from "./film-os.js";
import { buildNumberFiveGoldenProduction } from "./film-golden-number5.js";

const PROJECT_ID=/^film-[0-9a-f]{24}$/;

function root(env:NodeJS.ProcessEnv){
  const base=(env.HAKIM_DATA_DIR||path.join(os.tmpdir(),"hakim-film-os")).trim();
  return path.join(base,"film-os","projects");
}

async function ensure(env:NodeJS.ProcessEnv){
  const dir=root(env);
  await fs.mkdir(dir,{recursive:true});
  return dir;
}

async function atomicWrite(file:string,value:unknown){
  const tmp=file+"."+process.pid+".tmp";
  await fs.writeFile(tmp,JSON.stringify(value,null,2),"utf8");
  await fs.rename(tmp,file);
}

export async function createFilmProject(
  input:FilmProjectInput,
  env:NodeJS.ProcessEnv=process.env
){
  const dir=await ensure(env);
  const project_id="film-"+crypto.randomBytes(12).toString("hex");
  const now=Date.now();
  const project={
    project_id,
    film_os_version:FILM_OS_VERSION,
    revision:1,
    created_at_ms:now,
    updated_at_ms:now,
    status:"VISION_LOCKED",
    blueprint:buildFilmBlueprint(input),
    assets:{characters:[],locations:[],voices:[],shots:[],masters:[]},
    evidence:{shot_qa:[],film_qa:[],hashes:[]}
  };
  await atomicWrite(path.join(dir,project_id+".json"),project);
  return project;
}

export async function createGoldenNumberFiveProject(env:NodeJS.ProcessEnv=process.env){
  const golden=buildNumberFiveGoldenProduction();
  const project=await createFilmProject({
    title:golden.blueprint.title,
    goal:golden.blueprint.goal,
    format:"educational",
    audience:golden.blueprint.audience,
    duration_sec:golden.blueprint.duration_sec,
    aspect:golden.blueprint.aspect,
    language:golden.blueprint.language,
    locale:golden.blueprint.locale,
    style:golden.blueprint.style,
    educational:true
  },env);
  const dir=await ensure(env);
  const enriched={
    ...project,
    revision:2,
    updated_at_ms:Date.now(),
    status:"SHOT_CONTRACTS_READY",
    golden
  };
  await atomicWrite(path.join(dir,project.project_id+".json"),enriched);
  return enriched;
}

export async function getFilmProject(projectId:string,env:NodeJS.ProcessEnv=process.env){
  if(!PROJECT_ID.test(projectId)) throw new Error("invalid_film_project_id");
  const dir=await ensure(env);
  try{
    const raw=JSON.parse(await fs.readFile(path.join(dir,projectId+".json"),"utf8"));
    if(raw?.project_id!==projectId) throw new Error("film_project_corrupt");
    return raw;
  }catch(error){
    if((error as NodeJS.ErrnoException)?.code==="ENOENT") throw new Error("film_project_not_found");
    throw error;
  }
}


function projectShots(project:any){
  if(Array.isArray(project?.golden?.shots)) return project.golden.shots as any[];
  if(Array.isArray(project?.shots)) return project.shots as any[];
  return [];
}

export async function getNextFilmShot(projectId:string,env:NodeJS.ProcessEnv=process.env){
  const project=await getFilmProject(projectId,env) as any;
  const shots=projectShots(project);
  const states=project?.shot_runtime&&typeof project.shot_runtime==="object"?project.shot_runtime:{};
  const next=shots.find((shot:any)=>{
    const id=typeof shot?.shot_id==="string"?shot.shot_id:"";
    const state=states[id];
    return id&&state?.status!=="completed"&&state?.status!=="rendering";
  })??null;
  return {project,shot:next,state:next?states[next.shot_id]??null:null};
}

export async function markFilmShotJob(
  projectId:string,
  shotId:string,
  jobId:string,
  provider:string,
  env:NodeJS.ProcessEnv=process.env
){
  if(!PROJECT_ID.test(projectId)) throw new Error("invalid_film_project_id");
  if(!/^[A-Za-z0-9._:-]{2,80}$/.test(shotId)) throw new Error("invalid_film_shot_id");
  const dir=await ensure(env);
  const project=await getFilmProject(projectId,env) as any;
  const shots=projectShots(project);
  if(!shots.some((s:any)=>s?.shot_id===shotId)) throw new Error("film_shot_not_found");
  const now=Date.now();
  const shot_runtime={...(project.shot_runtime??{})};
  const previous=shot_runtime[shotId]??{};
  shot_runtime[shotId]={
    ...previous,
    attempts:Number(previous.attempts||0)+1,
    status:"rendering",
    job_id:jobId,
    provider,
    updated_at_ms:now
  };
  const updated={...project,revision:Number(project.revision||0)+1,updated_at_ms:now,shot_runtime};
  await atomicWrite(path.join(dir,projectId+".json"),updated);
  return updated;
}

export async function markFilmShotResult(
  projectId:string,
  shotId:string,
  result:Record<string,unknown>,
  env:NodeJS.ProcessEnv=process.env
){
  if(!PROJECT_ID.test(projectId)) throw new Error("invalid_film_project_id");
  if(!/^[A-Za-z0-9._:-]{2,80}$/.test(shotId)) throw new Error("invalid_film_shot_id");
  const dir=await ensure(env);
  const project=await getFilmProject(projectId,env) as any;
  const shots=projectShots(project);
  if(!shots.some((s:any)=>s?.shot_id===shotId)) throw new Error("film_shot_not_found");
  const now=Date.now();
  const status=result.status==="completed"?"completed":
    result.status==="waiting_free_capacity"?"waiting_free_capacity":
    result.status==="failed"?"failed":"rendering";
  const shot_runtime={...(project.shot_runtime??{})};
  shot_runtime[shotId]={
    ...(shot_runtime[shotId]??{}),
    status,
    job_id:typeof result.job_id==="string"?result.job_id:shot_runtime[shotId]?.job_id??null,
    provider:typeof result.adapter==="string"?result.adapter:shot_runtime[shotId]?.provider??null,
    artifact:result.artifact??null,
    artifact_verified:result.artifact_verified===true,
    render_success:result.render_success===true,
    cinematic_approval:result.cinematic_approval===true,
    retry_at_ms:typeof result.retry_at_ms==="number"?result.retry_at_ms:null,
    updated_at_ms:now
  };
  const completed=shots.filter((s:any)=>shot_runtime[s.shot_id]?.status==="completed").length;
  const projectStatus=completed===shots.length&&shots.length>0?"SHOT_QA":project.status;
  const updated={
    ...project,
    revision:Number(project.revision||0)+1,
    updated_at_ms:now,
    status:projectStatus,
    shot_runtime,
    render_progress:{completed_shots:completed,total_shots:shots.length}
  };
  await atomicWrite(path.join(dir,projectId+".json"),updated);
  return updated;
}

export async function listFilmProjects(env:NodeJS.ProcessEnv=process.env){
  const dir=await ensure(env);
  const names=await fs.readdir(dir).catch(()=>[]);
  const out:any[]=[];
  for(const name of names){
    if(!name.startsWith("film-")||!name.endsWith(".json")) continue;
    try{
      const p=JSON.parse(await fs.readFile(path.join(dir,name),"utf8"));
      if(PROJECT_ID.test(String(p?.project_id||""))) out.push(p);
    }catch{}
  }
  return out;
}
