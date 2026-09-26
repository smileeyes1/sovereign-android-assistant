import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"../..");
const relay=fs.readFileSync(path.join(root,"app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt"),"utf8");
const pairing=fs.readFileSync(path.join(root,"app/src/main/java/ps/hakim/phoneagent/HakimPairingActivity.kt"),"utf8");
const accessibility=fs.readFileSync(path.join(root,"app/src/main/java/ps/hakim/phoneagent/HakimAccessibilityService.kt"),"utf8");
const server=fs.readFileSync(path.join(root,"chatgpt-app/src/server.ts"),"utf8");

test("Android and bridge share the v2 encrypted result contract",()=>{
  assert.match(relay,/KEY_RESULT_TOPIC = "relay_result_topic"/);
  assert.match(relay,/RESULT_PREFIX = "HR1\."/);
  assert.match(relay,/RESULT_AAD = "HAKIM-RESULT-v1"/);
  assert.match(relay,/https:\/\/ntfy\.sh\/\$resultTopic/);
  assert.match(pairing,/getQueryParameter\("relay_result_topic"\)/);
  assert.equal(relay.includes("KEY_RESULT_URL"),false);
  assert.equal(relay.includes("result_url"),false);
  assert.equal(pairing.includes("result_url"),false);
});

test("state-changing relay operations remain approval-gated",()=>{
  assert.match(relay,/READ_ONLY_OPS = setOf\("status", "ui", "notifications", "screenshot"\)/);
  assert.match(relay,/ALLOWED_OPS = READ_ONLY_OPS \+ setOf\("action", "launch"\)/);
  assert.match(relay,/showApproval\(context, requestId, op\)/);
});

test("public action payload matches Android accessibility contract",()=>{
  const kinds=["home","back","recents","notifications","quick_settings","click_text","set_text","tap","swipe"];
  for(const kind of kinds){
    assert.ok(accessibility.includes('"' + kind + '" ->'),"Android action missing: "+kind);
    assert.ok(server.includes('"' + kind + '"'),"MCP action missing: "+kind);
  }
  assert.match(server,/payload:Record<string,unknown>=\{action:kind\}/);
  assert.match(server,/publishCommand\(credential,"action",payload\)/);
  assert.equal(server.includes('publishCommand(credential,"action",{kind,args'),false);
});
