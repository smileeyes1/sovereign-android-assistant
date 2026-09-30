import test from "node:test";
import assert from "node:assert/strict";
import os from "node:os";
import path from "node:path";
import fs from "node:fs/promises";
import { buildFilmBlueprint,FILM_OS_VERSION } from "../src/film-os.js";
import { routeFilmShot } from "../src/film-provider-router.js";
import { buildNumberFiveGoldenProduction } from "../src/film-golden-number5.js";
import { createGoldenNumberFiveProject,getFilmProject } from "../src/film-project-store.js";

test("Film OS blueprint is closed-loop and fail-closed",()=>{
  const p=buildFilmBlueprint({
    title:"اختبار",
    goal:"فيلم أصلي",
    format:"film",
    duration_sec:120,
    paid_approved:false
  });
  assert.equal(p.film_os_version,FILM_OS_VERSION);
  assert.equal(p.budget_policy.free_only,true);
  assert.equal(p.budget_policy.paid_approved,false);
  assert.equal(p.budget_policy.paid_provider_route,false);
  assert.equal(p.budget_policy.zero_automatic_or_manual_spend,true);
  assert.equal(p.budget_policy.no_paid_fallback,true);
  assert.equal(p.acceptance.generated_is_not_approved,true);
  assert.equal(p.acceptance.delivered_must_match_tested,true);
  assert.equal(p.repair_policy.regenerate_failed_shot_only,true);
  assert.ok(p.lifecycle.includes("FILM_QA"));
});

test("provider router rejects every paid provider even when approval is passed",()=>{
  const result=routeFilmShot(
    {duration_sec:5,min_resolution_rank:2,camera_control:true},
    [
      {id:"paid",available:true,rights_ok:true,privacy_ok:true,paid:true,max_duration_sec:10,max_resolution_rank:3,image_reference:true,video_reference:false,audio_generation:true,lip_sync:true,camera_control:true,multi_shot:true,identity_continuity:10,motion_quality:10,camera_quality:10,audio_quality:10,latency_score:10,cost_score:1,verified:true},
      {id:"free-unverified",available:true,rights_ok:true,privacy_ok:true,paid:false,max_duration_sec:10,max_resolution_rank:3,image_reference:true,video_reference:false,audio_generation:true,lip_sync:true,camera_control:true,multi_shot:true,identity_continuity:10,motion_quality:10,camera_quality:10,audio_quality:10,latency_score:10,cost_score:10,verified:false},
      {id:"free-ok",available:true,rights_ok:true,privacy_ok:true,paid:false,max_duration_sec:10,max_resolution_rank:2,image_reference:true,video_reference:false,audio_generation:false,lip_sync:false,camera_control:true,multi_shot:false,identity_continuity:7,motion_quality:7,camera_quality:7,audio_quality:0,latency_score:7,cost_score:10,verified:true}
    ],
    true
  );
  assert.equal(result.ok,true);
  assert.equal(result.provider?.id,"free-ok");
});

test("number five golden production freezes exact educational truth",()=>{
  const g=buildNumberFiveGoldenProduction();
  assert.equal(g.status,"SPEC_READY_NOT_RENDERED");
  assert.equal(g.educational_truth.target_digit,"٥");
  assert.equal(g.educational_truth.target_quantity,5);
  assert.equal(g.educational_truth.student_facing_western_digit_forbidden,true);
  assert.equal(g.shots.length,5);
  for(const shot of g.shots){
    for(const overlay of shot.deterministic_overlays??[]){
      if(overlay.kind==="counted_objects") assert.equal(overlay.count,5);
      if(overlay.kind==="digit") assert.equal(overlay.value,"٥");
    }
  }
  assert.ok(g.golden_acceptance.some(x=>x.includes("خمسة عناصر")));
});

test("golden project persists independently of chat context",async()=>{
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-film-os-test-"));
  const env={...process.env,HAKIM_DATA_DIR:dir} as NodeJS.ProcessEnv;
  try{
    const created=await createGoldenNumberFiveProject(env);
    assert.match(created.project_id,/^film-[0-9a-f]{24}$/);
    assert.equal(created.status,"SHOT_CONTRACTS_READY");
    const restored=await getFilmProject(created.project_id,env);
    assert.equal(restored.project_id,created.project_id);
    assert.equal(restored.golden.educational_truth.target_digit,"٥");
    assert.equal(restored.revision,2);
  }finally{
    await fs.rm(dir,{recursive:true,force:true});
  }
});
