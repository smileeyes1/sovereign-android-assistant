import { getCinematicRenderStatus,submitCinematicRender } from "./cinematic-executor.js";
import {
  getFilmProject,getNextFilmShot,listFilmProjects,markFilmShotJob,markFilmShotResult
} from "./film-project-store.js";

export const AUTONOMOUS_FILM_RUNNER_VERSION="HAKIM_AUTONOMOUS_FILM_RUNNER_V1_2026-09-30";
const active=new Set<string>();

function safeString(v:unknown,max:number){
  return typeof v==="string"?v.trim().slice(0,max):"";
}

function buildShotPlan(project:any,shot:any){
  const characters=Array.isArray(project?.golden?.characters)?project.golden.characters:[];
  const charLock=characters.map((c:any)=>({
    id:safeString(c?.id,80),
    name:safeString(c?.name,80),
    design_lock:safeString(c?.design_lock,600)
  }));
  const goal=[
    safeString(shot?.purpose,300),
    safeString(shot?.action,1000),
    safeString(shot?.dialogue,500)
  ].filter(Boolean).join(". ");
  return {
    goal,
    duration_sec:Math.max(0.5,Math.min(3,Number(shot?.duration_sec)||1.5)),
    style:safeString(project?.blueprint?.style,600)||"original cinematic animated-series shot",
    continuity_bible:{
      characters:charLock,
      location:safeString(shot?.location,240),
      locks:Array.isArray(shot?.continuity_locks)?shot.continuity_locks.slice(0,16):[],
      framing:safeString(shot?.framing,120),
      lens_mm:Number(shot?.lens_mm)||50,
      camera_move:safeString(shot?.camera_move,160)
    },
    execution_policy:{
      synthetic_public_safe:true,
      contains_personal_data:false,
      free_only:true
    }
  };
}

function activeShot(project:any){
  const states=project?.shot_runtime&&typeof project.shot_runtime==="object"?project.shot_runtime:{};
  for(const [shotId,state] of Object.entries(states)){
    const s=state as any;
    if(s?.status==="rendering"||s?.status==="waiting_free_capacity"){
      return {shot_id:shotId,state:s};
    }
  }
  return null;
}

export async function advanceFilmProject(projectId:string,env:NodeJS.ProcessEnv=process.env){
  if(active.has(projectId)){
    return {ok:true,project_id:projectId,state:"busy",rule:"single_runner_per_project"};
  }
  active.add(projectId);
  try{
    let project=await getFilmProject(projectId,env) as any;
    const running=activeShot(project);
    if(running?.state?.job_id){
      const status=await getCinematicRenderStatus(running.state.job_id,env);
      project=await markFilmShotResult(projectId,running.shot_id,status as any,env) as any;
      if(status.status==="waiting_free_capacity"){
        return {
          ok:true,project_id:projectId,state:"waiting_free_capacity",
          shot_id:running.shot_id,job_id:running.state.job_id,
          retry_at_ms:(status as any).retry_at_ms??null,
          progress:project.render_progress??null,
          rule:"free_quota_wait_preserves_completed_shots"
        };
      }
      if(status.status==="rendering"||status.status==="queued"||status.status==="pending"){
        return {
          ok:true,project_id:projectId,state:"rendering",
          shot_id:running.shot_id,job_id:running.state.job_id,
          progress:project.render_progress??null
        };
      }
      if(status.status==="failed"){
        const attempts=Number(project?.shot_runtime?.[running.shot_id]?.attempts||0);
        if(attempts>=3){
          return {
            ok:false,project_id:projectId,state:"blocked",
            shot_id:running.shot_id,attempts,
            blocker:"shot_failed_after_bounded_retries"
          };
        }
      }
      if(status.status==="completed"&&status.render_success===true){
        const next=await getNextFilmShot(projectId,env);
        if(!next.shot){
          return {
            ok:true,project_id:projectId,state:"all_shots_rendered",
            progress:(next.project as any).render_progress??null,
            next_gate:"SHOT_QA"
          };
        }
        project=next.project;
      }
    }

    const next=await getNextFilmShot(projectId,env);
    if(!next.shot){
      return {
        ok:true,project_id:projectId,state:"all_shots_rendered",
        progress:(next.project as any).render_progress??null,
        next_gate:"SHOT_QA"
      };
    }
    const shotId=safeString(next.shot.shot_id,80);
    const previous=(next.project as any)?.shot_runtime?.[shotId];
    if(Number(previous?.attempts||0)>=3){
      return {
        ok:false,project_id:projectId,state:"blocked",shot_id:shotId,
        attempts:Number(previous.attempts),blocker:"shot_failed_after_bounded_retries"
      };
    }
    const submitted=await submitCinematicRender(buildShotPlan(next.project,next.shot),env);
    await markFilmShotJob(
      projectId,shotId,String((submitted as any).job_id),
      String((submitted as any).adapter||"autonomy"),env
    );
    return {
      ok:true,project_id:projectId,state:(submitted as any).status==="waiting_free_capacity"?
        "waiting_free_capacity":"rendering",
      shot_id:shotId,job_id:(submitted as any).job_id,
      provider:(submitted as any).adapter??null,
      cost_usd:0,
      rule:"autonomous_next_shot_started"
    };
  }finally{
    active.delete(projectId);
  }
}

export async function resumeAutonomousFilmProjects(env:NodeJS.ProcessEnv=process.env){
  const projects=await listFilmProjects(env);
  let considered=0,resumed=0;
  for(const p of projects as any[]){
    if(p?.status==="DELIVERED"||p?.status==="BLOCKED") continue;
    if(!Array.isArray(p?.golden?.shots)&&!Array.isArray(p?.shots)) continue;
    considered+=1;
    const running=activeShot(p);
    const hasPending=Array.isArray(p?.golden?.shots)?
      p.golden.shots.some((s:any)=>p?.shot_runtime?.[s.shot_id]?.status!=="completed"):
      Array.isArray(p?.shots)&&p.shots.some((s:any)=>p?.shot_runtime?.[s.shot_id]?.status!=="completed");
    if(!hasPending) continue;
    if(running?.state?.status==="waiting_free_capacity"&&
      typeof running.state.retry_at_ms==="number"&&running.state.retry_at_ms>Date.now()) continue;
    resumed+=1;
    void advanceFilmProject(String(p.project_id),env);
  }
  return {considered,resumed,runner_version:AUTONOMOUS_FILM_RUNNER_VERSION};
}
