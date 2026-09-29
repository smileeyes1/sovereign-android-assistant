import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"..");
const server=fs.readFileSync(path.join(root,"src/server.ts"),"utf8");

test("Hakim requires fresh live state before device operations",()=>{
  assert.match(server,/LIVE_PREFLIGHT_VERSION="HAKIM_LIVE_PREFLIGHT_V1"/);
  assert.match(server,/MIN_FIELD_VERSION=20315/);
  assert.match(server,/const fetchLivePreflight=async\(\)=>/);
  assert.match(server,/publishCommand\(credential,"status",\{\}\)/);
  assert.match(server,/pollResult\(credential,requestId,8_000\)/);
  assert.match(server,/packageName==="ps\.hakim\.stable"/);
  assert.match(server,/versionCode>=MIN_FIELD_VERSION/);
  assert.match(server,/relayState==="direct_connected"/);
  assert.match(server,/fabricState==="ONLINE"/);
  assert.match(server,/selfCheck!=="FAIL_CLOSED"/);
  assert.match(server,/action_ready:runtimeReady&&!highImpactBlocked/);
});

test("every device mutation is guarded by mandatory preflight",()=>{
  for(const purpose of [
    "open_target",
    "navigate_device",
    "perform_ui_action",
    "authorize_network_device",
    "control_network_device",
    "revoke_network_device"
  ]){
    assert.ok(
      server.includes(`blockOnPreflight(preflight,"${purpose}")`),
      purpose+" missing live preflight"
    );
  }
  assert.match(server,/error:"hakim_live_preflight_failed"/);
  assert.match(server,/No device mutation may run from stale or unverified Hakim state/);
});

test("non-status device reads also refresh runtime state first",()=>{
  assert.match(server,/if\(internalOp!=="status"\)/);
  assert.match(server,/Read current Hakim runtime state before any device read/);
});

test("status is the canonical cross-chat bootstrap and includes continuity",()=>{
  assert.match(server,/حالة حكيم الحية — ابدأ هنا/);
  assert.match(server,/const continuity=await continuityStore\.state\(credential\)/);
  assert.match(server,/Treat this live state as authoritative for the current turn/);
});
