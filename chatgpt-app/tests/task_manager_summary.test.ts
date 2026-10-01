import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const index=fs.readFileSync(new URL("../src/index.ts",import.meta.url),"utf8");

test("task manager relay summary is bounded and excludes private task content",()=>{
  const status=(index.split("function logSanitizedStatusProbe(")[1]??"")
    .split("function reviewAttemptAllowed(")[0]??"";
  assert.match(status,/task_manager:\{/);
  for(const field of ["total","active","waiting","completed","failed","top_state","top_priority","network_task_state"]){
    assert.equal(status.includes(field),true,field);
  }
  for(const forbidden of ["task.goal","task.acceptance","last_detail","last_evidence","blocker","next_action"]){
    assert.equal(status.includes(forbidden),false,forbidden);
  }
  assert.match(status,/boundedCount\(taskManager\?\.total\)/);
});

test("health evidence uses the same privacy-safe task manager fields",()=>{
  const health=(index.split('event:"hakim_health_evidence"')[1]??"")
    .split('console.log("HAKIM_HEALTH_EVIDENCE')[0]??"";
  assert.match(health,/task_manager:\{/);
  assert.match(health,/network_task_state/);
  assert.equal(health.includes("goal:"),false);
  assert.equal(health.includes("acceptance:"),false);
});
