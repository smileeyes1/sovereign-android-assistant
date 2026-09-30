import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {commandTtlMs} from "../src/relay.js";
import {ALLOWED_OPS} from "../src/protocol.js";

const indexSource=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");
const bindingSource=fs.readFileSync(path.resolve(import.meta.dirname,"../src/device-binding-store.ts"),"utf8");

test("client_authorize is a bounded effectful operation rather than a long-lived read",()=>{
  assert.ok(ALLOWED_OPS.includes("client_authorize"));
  assert.equal(commandTtlMs("client_authorize"),60_000);
});

test("broker refuses channel replacement while the bound phone lacks 20317 approval support",()=>{
  assert.ok(indexSource.includes("Hakim 20317 or newer is required for multi-client approval."));
  assert.ok(indexSource.includes("لن نستبدل قناته لإضافة عميل جديد"));
  assert.ok(bindingSource.includes("version_code>=20317"));
});

test("new AI client reuse sends an on-phone approval and issues no token before approval",()=>{
  assert.ok(indexSource.includes('publishCommand(current.credential,"client_authorize"'));
  assert.ok(indexSource.includes("pollResult(current.credential,requestId,55_000)"));
  assert.ok(indexSource.includes("clientAuthorizationApproved(result)"));
  assert.ok(indexSource.includes('message.status==="rejected"'));
  assert.ok(indexSource.includes("لم يصدر أي رمز وصول ولم تتغير قناة حكيم"));
});

test("multi-client broker reuses canonical credential instead of creating a replacement channel",()=>{
  assert.ok(indexSource.includes("credential=currentBinding?.credential??createDeviceCredential()"));
  assert.ok(indexSource.includes("const reuseBinding=!!currentBinding&&brokerReady"));
  assert.ok(indexSource.includes("if(!reuseBinding)"));
  assert.ok(indexSource.includes("deviceBindingStore.sameCredential(current.credential,context.credential)"));
});

test("dynamic registration is metadata-only and separately rate-limited",()=>{
  assert.ok(indexSource.includes('registration_endpoint:base+"/oauth/register"'));
  assert.ok(indexSource.includes('app.post("/oauth/register"'));
  assert.ok(indexSource.includes("registrationAttemptAllowed"));
  assert.ok(indexSource.includes("oauthClientRegistry.register(req.body)"));
});
