import test from "node:test";
import assert from "node:assert/strict";
import {
  cinematicExecutorEnabled,cinematicExecutorSummary,getCinematicRenderStatus,submitCinematicRender
} from "../src/cinematic-executor.js";

const enabledEnv={
  HAKIM_VIDEO_RENDER_ENABLED:"1",
  HAKIM_VIDEO_EXECUTOR_NAME:"free-gpu-worker",
  HAKIM_VIDEO_EXECUTOR_URL:"https://video-worker.example",
  HAKIM_VIDEO_EXECUTOR_SECRET:"S".repeat(48)
} as NodeJS.ProcessEnv;

test("video executor fails closed unless all trusted configuration exists",()=>{
  assert.equal(cinematicExecutorEnabled({} as NodeJS.ProcessEnv),false);
  assert.equal(cinematicExecutorEnabled({...enabledEnv,HAKIM_VIDEO_EXECUTOR_SECRET:"short"}),false);
  assert.equal(cinematicExecutorEnabled({...enabledEnv,HAKIM_VIDEO_EXECUTOR_URL:"http://unsafe.example"}),false);
  assert.equal(cinematicExecutorEnabled(enabledEnv),true);
  const summary=cinematicExecutorSummary(enabledEnv) as any;
  assert.equal(summary.free_only,true);
  assert.equal(summary.paid_render_allowed,false);
  assert.equal(summary.render_verified,false);
});

test("render submission is signed, free-only and never reported as success on acceptance",async()=>{
  const original=globalThis.fetch;
  let captured:any=null;
  globalThis.fetch=async(input:any,init:any)=>{
    captured={input:String(input),init};
    return new Response(JSON.stringify({ok:true,job_id:"video-job-12345678"}),{
      status:202,headers:{"Content-Type":"application/json"}
    });
  };
  try{
    const result=await submitCinematicRender({goal:"test"},enabledEnv) as any;
    assert.equal(result.status,"accepted");
    assert.equal(result.render_success,false);
    assert.equal(result.artifact_verified,false);
    assert.equal(captured.input,"https://video-worker.example/v1/render");
    const body=JSON.parse(String(captured.init.body));
    assert.deepEqual(body.budget_policy,{free_only:true,paid_approved:false});
    assert.equal(body.artifact_policy.required,true);
    assert.match(captured.init.headers["X-Hakim-Signature"],/^[0-9a-f]{64}$/);
  }finally{
    globalThis.fetch=original;
  }
});

test("render status is successful only with hash, playback and quality gates",async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async()=>new Response(JSON.stringify({
    status:"completed",
    artifact:{
      sha256:"a".repeat(64),
      mime:"video/mp4",
      duration_sec:42,
      playback_passed:true,
      quality_gates_passed:true
    }
  }),{status:200,headers:{"Content-Type":"application/json"}});
  try{
    const result=await getCinematicRenderStatus("video-job-12345678",enabledEnv) as any;
    assert.equal(result.render_success,true);
    assert.equal(result.artifact_verified,true);
    assert.equal(result.artifact.sha256,"a".repeat(64));
  }finally{
    globalThis.fetch=original;
  }
});

test("completed without verified artifact remains not successful",async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async()=>new Response(JSON.stringify({
    status:"completed",
    artifact:{sha256:"a".repeat(64),playback_passed:true,quality_gates_passed:false}
  }),{status:200,headers:{"Content-Type":"application/json"}});
  try{
    const result=await getCinematicRenderStatus("video-job-12345678",enabledEnv) as any;
    assert.equal(result.render_success,false);
    assert.equal(result.artifact_verified,false);
    assert.equal(result.artifact,null);
  }finally{
    globalThis.fetch=original;
  }
});
