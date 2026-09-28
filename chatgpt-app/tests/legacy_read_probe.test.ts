import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const src=fs.readFileSync(new URL("../src/legacy-read-probe.ts",import.meta.url),"utf8");
const index=fs.readFileSync(new URL("../src/index.ts",import.meta.url),"utf8");

test("legacy compatibility probe is strictly read-only",()=>{
  assert.match(src,/type:"snapshot"/);
  for(const forbidden of [
    'type:"open_url"',
    'type:"tap_text"',
    'type:"tap_index"',
    'type:"set_text"',
    'type:"set_text_index"',
    'type:"press_enter"',
    'type:"reload"',
    'type:"back"',
    'type:"forward"',
    'type:"scroll"'
  ]) assert.equal(src.includes(forbidden),false,forbidden);
});

test("legacy probe requires signed result chunks",()=>{
  assert.match(src,/wrapper\.sig/);
  assert.match(src,/hmacHex\(key,requestId\+"\\n"\+chunk/);
  assert.match(src,/safeHexEqual\(expected,sig\)/);
});

test("private browser reads do not activate the legacy diagnostic probe",()=>{
  assert.equal(index.includes("LegacyReadProbe"),false);
  assert.equal(index.includes("legacyReadProbe.observeCommand"),false);
  assert.equal(index.includes("legacyReadProbe.observeResult"),false);
  assert.equal(index.includes("HAKIM_LEGACY_READ_PROBE"),false);
});

test("status probe follows the lease without competing diagnostic probes or raw logs",()=>{
  assert.equal(index.includes("maybeMakeUiProbe"),false);
  assert.equal(index.includes("HAKIM_UI_NETWORK_PROBE"),false);
  const route=index.split('app.get("/device/v1/commands"',1)[1].split('app.post("/device/v1/commands/:requestId/ack"',1)[0];
  assert.match(route,/const probe=maybeMakeStatusProbe\(topic,key\)/);
  const log=index.split("function logSanitizedStatusProbe(",1)[1].split("function reviewAttemptAllowed(",1)[0];
  assert.match(log,/statusProbeRequests\.has\(requestId\)/);
  assert.match(log,/network_diagnostics_available/);
  assert.match(log,/version_name_has_extender_survey/);
  for(const forbidden of ["rawNodes","network_matches","access_points","local_title","local_host","observed_dns","gateway:","version_name:result"]){
    assert.equal(log.includes(forbidden),false,forbidden);
  }
});
