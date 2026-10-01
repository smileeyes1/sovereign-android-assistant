import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const index=fs.readFileSync(new URL("../src/index.ts",import.meta.url),"utf8");

test("device DNS protection telemetry is bounded and content-free",()=>{
  const status=(index.split("function logSanitizedStatusProbe(")[1]??"")
    .split("function logSanitizedHealthBeacon",1)[0]??"";
  const health=(index.split("function logSanitizedHealthBeacon")[1]??"")
    .split("function reviewAttemptAllowed",1)[0]??"";

  for(const scope of [status,health]){
    assert.match(scope,/device_protection:\{/);
    for(const field of [
      "enabled","consent_granted","active","state","upstream_verified_recently",
      "dns_proxy_success_count","dns_proxy_failure_count","full_bypass_prevention"
    ]) assert.equal(scope.includes(field),true,field);

    for(const forbidden of [
      "dns_query","query_name","domain_name","packet_body","dns_payload",
      "last_error","cookie","password","typed_value"
    ]) assert.equal(scope.toLowerCase().includes(forbidden),false,forbidden);
  }
});

test("task manager exposes device DNS task state without task content",()=>{
  for(const scope of [
    (index.split("function logSanitizedStatusProbe(")[1]??"").split("function logSanitizedHealthBeacon",1)[0]??"",
    (index.split("function logSanitizedHealthBeacon")[1]??"").split("function reviewAttemptAllowed",1)[0]??""
  ]){
    assert.match(scope,/device_dns_task_state/);
    assert.equal(scope.includes("device_dns_goal"),false);
    assert.equal(scope.includes("device_dns_acceptance"),false);
  }
});
