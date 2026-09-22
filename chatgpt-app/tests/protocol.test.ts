import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { CARRIER_AAD,createDeviceCredential,decodeBearer,decryptResult,encodeBearer,encryptCarrier,encryptResult,makeEnvelope,pairingUrl } from "../src/protocol.js";

test("bearer round trip",()=>{
  const c=createDeviceCredential();
  assert.deepEqual(decodeBearer(encodeBearer(c)),c);
});

test("pairing url uses topics, not callback secrets or result URLs",()=>{
  const c=createDeviceCredential();
  const u=pairingUrl(c);
  assert.match(u,/^hakim:\/\/pair\?/);
  assert.match(u,/relay_topic=/);
  assert.match(u,/relay_result_topic=/);
  assert.match(u,/relay_key=/);
  assert.equal(u.includes("result_url="),false);
  assert.equal(u.includes("callback"),false);
});

test("envelope HMAC matches Android canonical format",()=>{
  const c=createDeviceCredential();
  const e=makeEnvelope(c.relayKey,"status",{x:1});
  const canonical=[e.request_id,e.op,String(e.expires_at_ms),e.payload_b64].join("\n");
  const sig=crypto.createHmac("sha256",c.relayKey).update(canonical).digest("hex");
  assert.equal(e.signature,sig);
});

test("command carrier decrypts with Android-compatible AES-GCM layout",()=>{
  const c=createDeviceCredential();
  const e=makeEnvelope(c.relayKey,"ui",{});
  const carrier=encryptCarrier(c.relayKey,e);
  const packed=Buffer.from(carrier.slice(4),"base64url");
  const nonce=packed.subarray(0,12),tag=packed.subarray(packed.length-16),ciphertext=packed.subarray(12,packed.length-16);
  const key=crypto.createHash("sha256").update(CARRIER_AAD+"\0"+c.relayKey).digest();
  const d=crypto.createDecipheriv("aes-256-gcm",key,nonce);
  d.setAAD(Buffer.from(CARRIER_AAD)); d.setAuthTag(tag);
  const raw=Buffer.concat([d.update(ciphertext),d.final()]).toString("utf8");
  assert.deepEqual(JSON.parse(raw),e);
});

test("results are end-to-end encrypted with the relay key",()=>{
  const c=createDeviceCredential();
  const payload={request_id:"chatgpt-12345678",status:"ok",result:{private:"hidden"}};
  const carrier=encryptResult(c.relayKey,payload);
  assert.match(carrier,/^HR1\./);
  assert.equal(carrier.includes("hidden"),false);
  assert.deepEqual(decryptResult(c.relayKey,carrier),payload);
});

test("tampered result fails closed",()=>{
  const c=createDeviceCredential();
  const carrier=encryptResult(c.relayKey,{request_id:"chatgpt-12345678"});
  const last=carrier.at(-1)!;
  const tampered=carrier.slice(0,-1)+(last==="A"?"B":"A");
  assert.throws(()=>decryptResult(c.relayKey,tampered));
});
