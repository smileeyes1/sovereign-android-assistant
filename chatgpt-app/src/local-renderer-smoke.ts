import { getLocalRenderStatus,submitLocalRender } from "./local-cinematic-renderer.js";

async function main(){
  const env={
    ...process.env,
    HAKIM_VIDEO_RENDER_ENABLED:"1",
    HAKIM_VIDEO_EXECUTOR_ADAPTER:"local",
    HAKIM_DATA_DIR:"/tmp/hakim-local-renderer-smoke"
  } as NodeJS.ProcessEnv;

  const submitted=await submitLocalRender({
    goal:"Synthetic deterministic golden render",
    duration_sec:8,
    aspect:"16:9",
    realism:"cinematic"
  },env);

  let status:any=null;
  const deadline=Date.now()+60_000;
  while(Date.now()<deadline){
    status=await getLocalRenderStatus(submitted.job_id,env);
    if(status.status==="completed"||status.status==="failed") break;
    await new Promise(resolve=>setTimeout(resolve,250));
  }

  if(!status||status.status!=="completed"||status.render_success!==true||
    status.artifact_verified!==true||status.artifact?.playback_passed!==true||
    status.artifact?.technical_gates_passed!==true||
    status.artifact?.quality_gates_passed!==true||
    status.artifact?.cinematic_approval!==false||
    !/^[0-9a-f]{64}$/.test(String(status.artifact?.sha256||""))){
    console.error(JSON.stringify(status));
    process.exit(1);
  }

  console.log(JSON.stringify({
    ok:true,
    job_id:status.job_id,
    status:status.status,
    sha256:status.artifact.sha256,
    duration_sec:status.artifact.duration_sec,
    size_bytes:status.artifact.size_bytes,
    fps:status.artifact.fps,
    width:status.artifact.width,
    height:status.artifact.height,
    has_audio:status.artifact.has_audio,
    playback_passed:status.artifact.playback_passed,
    cinematic_approval:status.artifact.cinematic_approval,
    rule:status.rule
  }));
}

void main().catch(error=>{
  console.error(error);
  process.exit(1);
});
