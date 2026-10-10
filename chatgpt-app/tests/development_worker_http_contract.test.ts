import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const source=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");

test("development worker endpoints require pinned OIDC and expose bounded fields only",()=>{
  assert.ok(source.includes('app.post("/development/v1/lease"'));
  assert.ok(source.includes('app.post("/development/v1/:requestId/complete"'));
  assert.ok(source.includes('app.post("/development/v1/:requestId/defer"'));
  assert.ok(source.includes('app.post("/development/v1/:requestId/review-pending"'));
  assert.ok(source.includes("developmentRequestStore.markReviewPending("));
  assert.ok(source.includes("verifyGitHubWorkerOidc(workerOidcBearer(req))"));
  assert.ok(source.includes("developmentRequestStore.claimNext(workerId)"));
  assert.ok(source.includes("developmentRequestStore.complete("));
  const view=source.split("function developmentWorkerView",2)[1]?.split("function directRelayKey",1)[0]??"";
  for(const allowed of [
    "request_id","requested_at_ms","current_version_code","trigger",
    "severity","fingerprint","evidence","state","lease_expires_at_ms","attempt_count","constraints"
  ]) assert.ok(view.includes(allowed),allowed);
  for(const forbidden of ["device_id","relayKey","resultTopic","evidence_summary","goal"]) {
    assert.equal(view.includes(forbidden),false,forbidden);
  }
});

test("health announces OIDC development worker contract",()=>{
  assert.ok(source.includes('development_intake:"HAKIM-DEVELOPMENT-INTAKE-2026-09-30-v2"'));
  assert.ok(source.includes('development_worker_auth:"github-actions-oidc"'));
});


test("development worker receives structured evidence but never free text",()=>{
  const store=fs.readFileSync(path.resolve(import.meta.dirname,"../src/development-request-store.ts"),"utf8");
  for(const token of [
    'code:"rollback_forward_requested"|"self_check_fail_closed"|"consecutive_runtime_failures"|"legacy_unstructured"',
    "failure_count:number",
    "self_check_status",
    "candidate_state:string",
    "action_code:string",
    "boundedEvidence("
  ]) assert.ok(store.includes(token),token);

  assert.equal(store.includes("evidence_summary:"),false);
  assert.equal(store.includes("goal:"),false);
});


test("review handoff is bounded and does not claim approved success",()=>{
  const store=fs.readFileSync(path.resolve(import.meta.dirname,"../src/development-request-store.ts"),"utf8");
  const reviewRoute=source.split('app.post("/development/v1/:requestId/review-pending"',2)[1]?.split('app.post("/development/v1/:requestId/defer"',1)[0]??"";
  assert.ok(reviewRoute.includes("verifyGitHubWorkerOidc(workerOidcBearer(req))"));
  assert.ok(reviewRoute.includes('new Set(["result_sha","pr_number"])'));
  assert.ok(reviewRoute.includes("markReviewPending("));
  assert.equal(reviewRoute.includes('state:"completed"'),false);
  assert.ok(store.includes('state:"review_pending"'));
  assert.ok(store.includes('development_success_requires_independent_review'));
});
