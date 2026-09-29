import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const source=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");

test("status probe exposes only bounded AutoUpdater readiness metadata",()=>{
  const fn=source.split("function logSanitizedStatusProbe",2)[1]?.split("function logSanitizedHealthBeacon",1)[0]??"";
  for(const token of [
    "auto_update","ready_in_downloads","push_received",
    "push_seen","last_check_after_20315_signal","last_push_after_20315_signal",
    "last_discovered_version","last_exported_version",
    "fault_containment","critical_path_silent_failures_forbidden",
    "bounded_retry","circuit_breaker","high_impact_fail_closed",
    "raw_exception_message_persisted","open_circuits","critical_blocks",
    "high_impact_blocked","recovery_required","self_check"
  ]) assert.ok(fn.includes(token),token);

  assert.ok(source.includes("UPDATE_20315_SIGNAL_AT_MS=1790705524*1000"));
  assert.equal(fn.includes("last_check_at:typeof"),false,"raw last_check timestamp must not be logged");
  assert.equal(fn.includes("last_push_at:typeof"),false,"raw last_push timestamp must not be logged");

  for(const forbidden of [
    "last_update_error","last_verified_at",
    "download_url","attachment","relay_key",
    "JSON.stringify(decoded)","JSON.stringify(result)",
    "console.log(carrier)","console.log(key)",
    "last_fault_component","last_fault_operation","last_fault_type",
    "last_fault_at","last_blocked_at","event_count"
  ]) assert.equal(fn.includes(forbidden),false,forbidden);
});

test("fault containment probe is bounded and privacy-minimized",()=>{
  const fn=source.split("function logSanitizedStatusProbe",2)[1]?.split("function logSanitizedHealthBeacon",1)[0]??"";
  assert.ok(fn.includes('["PASS","PASS_WITH_WARNINGS","FAIL_CLOSED","NOT_TESTED"]'));
  assert.ok(fn.includes("Number.isInteger(raw)&&raw>=0&&raw<=1024"));
  assert.equal(fn.includes("containment?.last_fault_"),false);
  assert.equal(fn.includes("containment?.event_count"),false);
});
