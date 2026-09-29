import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"..");
const server=fs.readFileSync(path.join(root,"src/server.ts"),"utf8");
const index=fs.readFileSync(path.join(root,"src/index.ts"),"utf8");

function literalPublishOps(source:string){
  return [...source.matchAll(/publishCommand\(credential,"([^"]+)"/g)].map(m=>m[1]!);
}

const EFFECTFUL_OPS=new Set([
  "launch","action","network_authorize","network_control","network_revoke"
]);

test("all model-facing literal mutation dispatches are an explicit frozen allowlist",()=>{
  assert.deepEqual(
    literalPublishOps(server).filter(op=>EFFECTFUL_OPS.has(op)),
    ["launch","action","action","network_authorize","network_control","network_revoke"]
  );
  assert.deepEqual(
    literalPublishOps(index).filter(op=>EFFECTFUL_OPS.has(op)),
    ["launch","action"]
  );
});

test("video planning remains an explicit read-only dispatch",()=>{
  assert.ok(literalPublishOps(server).includes("video_plan"));
  assert.equal(EFFECTFUL_OPS.has("video_plan"),false);
  const relay=fs.readFileSync(path.join(root,"src/relay.ts"),"utf8");
  assert.match(relay,/DEFERRED_READ_OPS=new Set<HakimOp>\([^\n]*"video_capabilities","video_plan"/);
});

test("every MCP mutation dispatch is guarded by live preflight in the same handler",()=>{
  const guards=[
    ["open_target","launch"],
    ["navigate_device","action"],
    ["perform_ui_action","action"],
    ["authorize_network_device","network_authorize"],
    ["control_network_device","network_control"],
    ["revoke_network_device","network_revoke"]
  ] as const;
  for(const [purpose,op] of guards){
    const dispatch=`publishCommand(credential,"${op}"`;
    const at=server.indexOf(dispatch,server.indexOf(`blockOnPreflight(preflight,"${purpose}")`));
    assert.ok(at>=0,`${purpose} dispatch missing or before guard`);
  }
});

test("every universal HTTPS mutation dispatch is guarded by fresh live preflight",()=>{
  for(const route of [
    'app.post("/gateway/v1/actions/open"',
    'app.post("/gateway/v1/actions/navigate"'
  ]){
    const start=index.indexOf(route);
    const end=index.indexOf("\napp.",start+10);
    const block=index.slice(start,end<0?undefined:end);
    const preflight=block.indexOf("fetchLivePreflight(credential,false)");
    const ready=block.indexOf("preflight.action_ready!==true");
    const dispatch=block.indexOf("publishCommand(credential");
    assert.ok(start>=0&&preflight>=0&&ready>preflight&&dispatch>ready,route+" bypasses closure gate");
  }
});

test("generic device reads cannot bypass current-state verification",()=>{
  const generic=server.indexOf("publishCommand(credential,internalOp as HakimOp");
  assert.ok(generic>=0);
  const before=server.slice(Math.max(0,generic-900),generic);
  assert.ok(before.includes('if(internalOp!=="status")'));
  assert.ok(before.includes("fetchLivePreflight(credential,reviewMode)"));
  assert.ok(before.includes('error:"hakim_live_preflight_failed"'));
});

test("execution closure contract remains fail-closed",()=>{
  assert.ok(server.includes("No device mutation may run from stale or unverified Hakim state"));
  assert.ok(index.includes('status:"approval_requested"'));
  assert.ok(index.includes('effect:"not_yet_verified"'));
  assert.ok(index.includes("replay_original_action:false"));
  assert.equal(index.includes('/gateway/v1/actions/shell'),false);
  assert.equal(index.includes('/gateway/v1/actions/root'),false);
});
