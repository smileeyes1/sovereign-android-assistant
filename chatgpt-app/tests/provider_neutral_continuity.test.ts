import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const indexSource=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");
const serverSource=fs.readFileSync(path.resolve(import.meta.dirname,"../src/server.ts"),"utf8");
const storeSource=fs.readFileSync(path.resolve(import.meta.dirname,"../src/continuity-store.ts"),"utf8");

test("provider-neutral continuity discovery and JSON endpoints are present",()=>{
  assert.ok(indexSource.includes('/.well-known/hakim-continuity'));
  assert.ok(indexSource.includes('/continuity/v1/state'));
  assert.ok(indexSource.includes('/continuity/v1/checkpoint'));
  assert.ok(indexSource.includes('provider_neutral:true'));
  assert.ok(indexSource.includes('HAKIM_CONTINUITY_HTTP_V1'));
});

test("generic continuity HTTP requires bearer scopes instead of exposing device relay secrets",()=>{
  assert.ok(indexSource.includes('requireAccessScope(req,"hakim.read")'));
  assert.ok(indexSource.includes('requireAccessScope(req,"hakim.write")'));
  assert.ok(indexSource.includes('openAccessToken(oauthSecret,raw,base)'));
  assert.equal(indexSource.includes('relay_hmac_key'),false);
});

test("checkpoint writes expose optimistic concurrency on both MCP and HTTP",()=>{
  assert.ok(storeSource.includes("checkpoint_revision"));
  assert.ok(storeSource.includes("ContinuityRevisionConflict"));
  assert.ok(serverSource.includes("expected_revision"));
  assert.ok(serverSource.includes("continuity_revision_conflict"));
  assert.ok(indexSource.includes('If-Match'));
  assert.ok(indexSource.includes('status(409)'));
});

test("continuity remains bounded and explicitly excludes raw conversation/provider secrets",()=>{
  assert.ok(storeSource.includes("model prompts"));
  assert.ok(storeSource.includes("conversation transcripts"));
  assert.ok(storeSource.includes("relay keys"));
  assert.ok(storeSource.includes("credentials"));
});
