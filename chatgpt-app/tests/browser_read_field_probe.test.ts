import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const source=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");

test("browser read field probe records only bounded boolean evidence",()=>{
  assert.ok(source.includes('makeEnvelope(key,"browser_read",{},60_000)'));
  assert.ok(source.includes("BROWSER_READ_PROBE_COOLDOWN_MS=24*60*60_000"));
  assert.ok(source.includes('"HAKIM_BROWSER_READ_PROBE "'));
  assert.ok(source.includes("browserReadProbeRequests.delete(requestId)"));

  const block=source.split("if(browserReadProbeRequests.has(requestId)){",2)[1]
    ?.split("if(!statusProbeRequests.has(requestId)) return",1)[0]??"";
  for(const token of [
    "url",
    "title",
    "text_chars",
    "JSON.stringify(decoded)",
    "JSON.stringify(browserResult)",
    "console.log(carrier)",
    "console.log(key)"
  ]) assert.equal(block.includes(token),false,token);
  for(const token of ["ok","has_page","blocked","privacy_gate","error_present"]){
    assert.ok(block.includes(token),token);
  }
});
