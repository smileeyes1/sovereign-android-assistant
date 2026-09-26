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

test("legacy probe report never logs topics or relay key",()=>{
  const marker='HAKIM_LEGACY_READ_PROBE';
  const i=index.indexOf(marker);
  assert.ok(i>=0);
  const section=index.slice(i-250,i+800);
  assert.equal(section.includes("relayKey"),false);
  assert.equal(section.includes("commandTopic"),false);
  assert.equal(section.includes("resultTopic"),false);
});

test("legacy probe exposes only private-host metadata for page identity",()=>{
  assert.match(src,/local_host:host/);
  assert.match(src,/const title=host&&typeof page\.title/);
  assert.equal(src.includes("page.text"),false);
});
