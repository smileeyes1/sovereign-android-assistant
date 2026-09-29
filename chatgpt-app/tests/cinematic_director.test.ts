import test from "node:test";
import assert from "node:assert/strict";
import { buildCinematicPlan,cinematicCapabilities,CINEMATIC_DIRECTOR_VERSION } from "../src/cinematic-director.js";

test("cinematic director produces a complete non-rendering plan",()=>{
  const plan=buildCinematicPlan({
    goal:"فيلم قصير واقعي عن رحلة طفل إلى المدرسة",
    duration_sec:61,
    aspect:"16:9",
    realism:"cinematic"
  }) as any;
  assert.equal(plan.ok,true);
  assert.equal(plan.director_version,CINEMATIC_DIRECTOR_VERSION);
  assert.equal(plan.artifact_created,false);
  assert.equal(plan.duration_sec,61);
  assert.ok(Array.isArray(plan.shots)&&plan.shots.length>=2);
  assert.equal(plan.shots.reduce((n:number,s:any)=>n+s.duration_sec,0),61);
  assert.equal(plan.acceptance.render_success_without_artifact_forbidden,true);
  assert.equal(plan.continuity_bible.regenerate_failed_shot_only,true);
  assert.ok(plan.quality_gates.includes("EYELINE_180_RULE"));
  assert.ok(plan.quality_gates.includes("ARTIFACT_HASH_MATCH"));
});

test("child educational profile adds cognitive and factual gates",()=>{
  const plan=buildCinematicPlan({
    goal:"شرح المقارنة بين عددين",
    audience_age:7,
    duration_sec:45,
    educational:true
  }) as any;
  for(const gate of [
    "LEARNING_OBJECTIVE_ALIGNMENT","FACTUAL_ACCURACY",
    "CHILD_COGNITIVE_LOAD","AGE_APPROPRIATE_LANGUAGE","DISTRACTION_CONTROL"
  ]) assert.ok(plan.quality_gates.includes(gate),gate+" missing");
  assert.match(plan.edit_strategy.pace,/هادئ|واضح/);
});

test("cinematic capabilities fail closed when no render executor is configured",()=>{
  const cap=cinematicCapabilities({} as NodeJS.ProcessEnv) as any;
  assert.equal(cap.executor.connected,false);
  assert.equal(cap.executor.render_verified,false);
  assert.equal(cap.provider_policy.hardcoded_provider,false);
  assert.equal(cap.provider_policy.paid_without_explicit_approval,false);
  assert.equal(cap.success_policy.planning_is_not_rendering,true);
  assert.equal(cap.success_policy.artifact_required,true);
});

test("executor configuration never implies successful rendering",()=>{
  const cap=cinematicCapabilities({
    HAKIM_VIDEO_EXECUTOR_NAME:"gpu-worker",
    HAKIM_VIDEO_EXECUTOR_URL:"https://worker.example"
  } as NodeJS.ProcessEnv) as any;
  assert.equal(cap.executor.connected,true);
  assert.equal(cap.executor.render_verified,false);
});
