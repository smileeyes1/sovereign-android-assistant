import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"..");
const indexSource=fs.readFileSync(path.join(root,"src/index.ts"),"utf8");
const preflightSource=fs.readFileSync(path.join(root,"src/live-preflight.ts"),"utf8");

test("universal gateway exposes discovery bootstrap OpenAPI and bounded operation status",()=>{
  assert.ok(indexSource.includes('UNIVERSAL_GATEWAY_VERSION="HAKIM_UNIVERSAL_GATEWAY_V1"'));
  assert.ok(indexSource.includes('app.get("/.well-known/hakim-gateway"'));
  assert.ok(indexSource.includes('app.get("/gateway/v1/openapi.json"'));
  assert.ok(indexSource.includes('app.get("/gateway/v1/bootstrap"'));
  assert.ok(indexSource.includes('app.get("/gateway/v1/operations/:operationToken"'));
  assert.ok(indexSource.includes("provider_neutral:true"));
});

test("bootstrap combines fresh live preflight with durable continuity",()=>{
  const start=indexSource.indexOf('app.get("/gateway/v1/bootstrap"');
  const end=indexSource.indexOf('app.post("/gateway/v1/actions/open"',start);
  const block=indexSource.slice(start,end);
  assert.ok(block.includes('requireAccessScope(req,"hakim.read")'));
  assert.ok(block.includes("fetchLivePreflight(credential,false)"));
  assert.ok(block.includes("continuityStore.state(credential)"));
  assert.ok(block.includes("setContinuityEtag"));
  assert.ok(block.includes("authoritative for this turn"));
});

test("every provider-neutral mutation performs a fresh preflight before dispatch",()=>{
  for(const route of [
    'app.post("/gateway/v1/actions/open"',
    'app.post("/gateway/v1/actions/navigate"'
  ]){
    const start=indexSource.indexOf(route);
    assert.ok(start>=0,route+" missing");
    const next=indexSource.indexOf("\napp.",start+10);
    const block=indexSource.slice(start,next<0?undefined:next);
    const pre=block.indexOf("fetchLivePreflight(credential,false)");
    const publish=block.indexOf("publishCommand(credential");
    assert.ok(pre>=0&&publish>pre,route+" must preflight before dispatch");
    assert.ok(block.includes('error:"hakim_live_preflight_failed"'));
    assert.ok(block.includes("continuityStore.recordRequested"));
  }
});

test("universal gateway remains bounded and does not create arbitrary device execution",()=>{
  assert.ok(indexSource.includes('GATEWAY_NAV_KINDS=new Set(["home","back","recents"])'));
  assert.ok(indexSource.includes("exactly_one_target_required"));
  assert.equal(indexSource.includes('/gateway/v1/actions/shell'),false);
  assert.equal(indexSource.includes('/gateway/v1/actions/root'),false);
  assert.equal(indexSource.includes('/gateway/v1/actions/arbitrary'),false);
});

test("operation continuation never replays the original mutation",()=>{
  const start=indexSource.indexOf('app.get("/gateway/v1/operations/:operationToken"');
  const block=indexSource.slice(start,indexSource.indexOf("\napp.",start+10));
  assert.ok(block.includes("pollResult(credential,requestId"));
  assert.ok(block.includes("continuityStore.recordObserved"));
  assert.ok(block.includes("replay_original_action:false"));
  assert.equal(block.includes('publishCommand(credential'),false);
});

test("provider-neutral gateway does not weaken OAuth client trust boundary",()=>{
  assert.ok(indexSource.includes("Only ChatGPT CIMD clients are accepted."));
  assert.ok(indexSource.includes("isChatGPTClientId(clientId)"));
  assert.ok(indexSource.includes("external_provider_policy"));
  assert.equal(indexSource.includes("relay_hmac_key"),false);
});

test("one centralized live-preflight contract protects MCP and gateway",()=>{
  assert.ok(preflightSource.includes('LIVE_PREFLIGHT_VERSION="HAKIM_LIVE_PREFLIGHT_V1"'));
  assert.ok(preflightSource.includes("MIN_FIELD_VERSION=20315"));
  assert.ok(preflightSource.includes('relayState==="direct_connected"'));
  assert.ok(preflightSource.includes('fabricState==="ONLINE"'));
  assert.ok(preflightSource.includes('selfCheck!=="FAIL_CLOSED"'));
});
