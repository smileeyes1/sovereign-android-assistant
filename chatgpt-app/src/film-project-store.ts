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
    educational:true,
    paid_approved:false
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
