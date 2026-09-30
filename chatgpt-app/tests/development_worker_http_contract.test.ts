import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const source=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");

test("development worker endpoints require pinned OIDC and expose bounded fields only",()=>{
  assert.ok(source.includes('app.post("/development/v1/lease"'));
  assert.ok(source.includes('app.post("/development/v1/:requestId/complete"'));
  assert.ok(source.includes('app.post("/development/v1/:requestId/defer"'));
  assert.ok(source.includes("verifyGitHubWorkerOidc(workerOidcBearer(req))"));
  assert.ok(source.includes("developmentRequestStore.claimNext(workerId)"));
  assert.ok(source.includes("developmentRequestStore.complete("));
  const view=source.split("function developmentWorkerView",2)[1]?.split("function directRelayKey",1)[0]??"";
  for(const allowed of [
    "request_id","requested_at_ms","current_version_code","trigger",
    "severity","fingerprint","state","lease_expires_at_ms","attempt_count","constraints"
  ]) assert.ok(view.includes(allowed),allowed);
  for(const forbidden of ["device_id","relayKey","resultTopic","evidence_summary","goal"]) {
    assert.equal(view.includes(forbidden),false,forbidden);
  }
});

test("health announces OIDC development worker contract",()=>{
  assert.ok(source.includes('development_intake:"HAKIM-DEVELOPMENT-INTAKE-2026-09-30-v2"'));
  assert.ok(source.includes('development_worker_auth:"github-actions-oidc"'));
});
