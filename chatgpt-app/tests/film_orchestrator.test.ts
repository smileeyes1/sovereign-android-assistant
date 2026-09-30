import test from "node:test";
import assert from "node:assert/strict";
import { evaluateFilmTransition,nextFilmStage } from "../src/film-orchestrator.js";

test("Film OS forbids skipping production stages",()=>{
  const r=evaluateFilmTransition("VISION_LOCKED","SHOT_CONTRACTS_READY",{shot_contracts_ready:true});
  assert.equal(r.ok,false);
  assert.equal(r.reason,"only_next_stage_transition_allowed");
  assert.equal(nextFilmStage("VISION_LOCKED"),"WORLD_BIBLE_LOCKED");
});

test("Film OS requires evidence for each locking gate",()=>{
  assert.equal(evaluateFilmTransition("VISION_LOCKED","WORLD_BIBLE_LOCKED",{}).ok,false);
  assert.equal(evaluateFilmTransition("VISION_LOCKED","WORLD_BIBLE_LOCKED",{world_bible_locked:true}).ok,true);
  assert.equal(evaluateFilmTransition("SHOT_CONTRACTS_READY","ROUTED",{provider_routes_complete:true}).ok,true);
});

test("shot QA cannot begin until every expected shot rendered",()=>{
  assert.equal(evaluateFilmTransition("RENDERING","SHOT_QA",{
    rendered_shots_count:4,expected_shots_count:5
  }).ok,false);
  assert.equal(evaluateFilmTransition("RENDERING","SHOT_QA",{
    rendered_shots_count:5,expected_shots_count:5
  }).ok,true);
});

test("DELIVERED requires playback and identical tested/delivered hashes",()=>{
  const h="a".repeat(64);
  assert.equal(evaluateFilmTransition("MASTERING","DELIVERED",{
    master_created:true,full_playback_passed:true,
    artifact_sha256:h,tested_sha256:h,delivered_sha256:"b".repeat(64)
  }).ok,false);
  assert.equal(evaluateFilmTransition("MASTERING","DELIVERED",{
    master_created:true,full_playback_passed:true,
    artifact_sha256:h,tested_sha256:h,delivered_sha256:h
  }).ok,true);
});
