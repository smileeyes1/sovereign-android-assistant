import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const source=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");

test("health evidence logger is redacted, bounded and wired to result intake",()=>{
  assert.ok(source.includes("function logSanitizedHealthBeacon(key:string,carrier:string)"));
  assert.ok(source.includes('if(decoded?.status!=="health") return'));
  assert.ok(source.includes('/^[0-9a-f]{64}$/i.test(result.apk_sha256)'));
  assert.ok(source.includes('"HAKIM_HEALTH_EVIDENCE "'));
  assert.ok(source.includes("logSanitizedHealthBeacon(key,carrier)"));

  for(const token of [
    "version_code",
    "apk_sha256",
    "constitution",
    "sovereign_acceptance_gate",
    "governance_catalog",
    "not_bound_to_custom_8000_limit",
    "adaptive_rule_selection",
    "full_constitution_retained",
    "full_rule_count",
    "same_tested_delivered_artifact_required",
    "regression_gate_supported"
  ]) assert.ok(source.includes(token),token);

  const fn=source.split("function logSanitizedHealthBeacon",2)[1]?.split("function reviewAttemptAllowed",1)[0]??"";
  for(const forbidden of [
    "goal_id",
    "last_selected_domains",
    "learning",
    "network_diagnostics",
    "notifications",
    "browser_read",
    "typed_value",
    "JSON.stringify(decoded)",
    "JSON.stringify(result)",
    "console.log(carrier)",
    "console.log(key)"
  ]) assert.equal(fn.includes(forbidden),false,forbidden);
  assert.ok(fn.includes('console.log("HAKIM_HEALTH_EVIDENCE "+JSON.stringify(safe))'));
});
