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
    "last_discovered_version","last_exported_version"
  ]) assert.ok(fn.includes(token),token);

  assert.ok(source.includes("UPDATE_20315_SIGNAL_AT_MS=1790705524*1000"));
  assert.equal(fn.includes("last_check_at:typeof"),false,"raw last_check timestamp must not be logged");
  assert.equal(fn.includes("last_push_at:typeof"),false,"raw last_push timestamp must not be logged");

  for(const forbidden of [
    "last_update_error","last_verified_at",
    "download_url","attachment","relay_key",
    "JSON.stringify(decoded)","JSON.stringify(result)",
    "console.log(carrier)","console.log(key)"
  ]) assert.equal(fn.includes(forbidden),false,forbidden);
});
