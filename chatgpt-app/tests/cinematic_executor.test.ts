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

const gradioEnv={
  HAKIM_VIDEO_RENDER_ENABLED:"1",
  HAKIM_VIDEO_EXECUTOR_NAME:"hakim-zerogpu",
  HAKIM_VIDEO_EXECUTOR_URL:"https://hakim-zerogpu.hf.space",
  HAKIM_VIDEO_EXECUTOR_ADAPTER:"gradio",
  HAKIM_VIDEO_ZEROGPU_STEPS:"12",
  HAKIM_VIDEO_ZEROGPU_SEED:"42",
  HAKIM_VIDEO_ZEROGPU_RESOLUTION:"480p"
} as NodeJS.ProcessEnv;

test("video executor fails closed unless all trusted configuration exists",()=>{
  assert.equal(cinematicExecutorEnabled({} as NodeJS.ProcessEnv),false);
  assert.equal(cinematicExecutorEnabled({...enabledEnv,HAKIM_VIDEO_EXECUTOR_SECRET:"short"}),false);
  assert.equal(cinematicExecutorEnabled({...enabledEnv,HAKIM_VIDEO_EXECUTOR_URL:"http://unsafe.example"}),false);
  assert.equal(cinematicExecutorEnabled(enabledEnv),true);
  assert.equal(cinematicExecutorEnabled(gradioEnv),true);
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


test("Gradio ZeroGPU submission uses named v2 endpoint and remains non-successful",async()=>{
  const original=globalThis.fetch;
  let captured:any=null;
  globalThis.fetch=async(input:any,init:any)=>{
    captured={input:String(input),init};
    return new Response(JSON.stringify({event_id:"zerogpu-job-12345678"}),{
      status:200,headers:{"Content-Type":"application/json"}
    });
  };
  try{
    const result=await submitCinematicRender({goal:"golden",duration_sec:2},gradioEnv) as any;
    assert.equal(result.adapter,"gradio");
    assert.equal(result.render_success,false);
    assert.equal(captured.input,"https://hakim-zerogpu.hf.space/gradio_api/call/v2/render_video");
    const body=JSON.parse(String(captured.init.body));
    assert.equal(body.duration_seconds,2);
    assert.equal(body.steps,12);
    assert.equal(body.seed,42);
    assert.equal(body.resolution,"480p");
    assert.equal(JSON.parse(body.plan_json).goal,"golden");
  }finally{
    globalThis.fetch=original;
  }
});

test("Gradio completed render returns artifact evidence but waits for cinematic QA",async()=>{
  const original=globalThis.fetch;
  const evidence={
    sha256:"b".repeat(64),
    mime:"video/mp4",
    duration_sec:2.042,
    technical_gates_passed:true,
    quality_gates_passed:false,
    playback_probe:"pending_hakim_verification"
  };
  globalThis.fetch=async()=>new Response(
    'event: complete\ndata: '+JSON.stringify([
      {url:"https://hakim-zerogpu.hf.space/gradio_api/file=/tmp/out.mp4"},
      JSON.stringify(evidence)
    ])+'\n\n',
    {status:200,headers:{"Content-Type":"text/event-stream"}}
  );
  try{
    const result=await getCinematicRenderStatus("zerogpu-job-12345678",gradioEnv) as any;
    assert.equal(result.status,"completed");
    assert.equal(result.render_success,false);
    assert.equal(result.artifact_verified,false);
    assert.equal(result.rule,"rendered_pending_cinematic_qa");
    assert.equal(result.artifact.sha256,"b".repeat(64));
    assert.equal(result.artifact.technical_gates_passed,true);
    assert.equal(result.artifact.quality_gates_passed,false);
  }finally{
    globalThis.fetch=original;
  }
});

test("Gradio render only becomes verified when playback and cinematic gates pass",async()=>{
  const original=globalThis.fetch;
  const evidence={
    sha256:"c".repeat(64),
    mime:"video/mp4",
    duration_sec:2,
    technical_gates_passed:true,
    quality_gates_passed:true,
    playback_probe:"passed"
  };
  globalThis.fetch=async()=>new Response(
    'event: complete\ndata: '+JSON.stringify([{url:"https://example.test/video.mp4"},JSON.stringify(evidence)])+'\n\n',
    {status:200,headers:{"Content-Type":"text/event-stream"}}
  );
  try{
    const result=await getCinematicRenderStatus("zerogpu-job-87654321",gradioEnv) as any;
    assert.equal(result.render_success,true);
    assert.equal(result.artifact_verified,true);
    assert.equal(result.rule,"verified_artifact");
  }finally{
    globalThis.fetch=original;
  }
});
