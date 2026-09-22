import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { CARRIER_AAD, createDeviceCredential, decodeBearer, decryptResult, encodeBearer, encryptCarrier, encryptResult, makeEnvelope, pairingUrl } from "../src/protocol.js";

test("bearer round trip",()=>{
  const c=createDeviceCredential();
  assert.deepEqual(decodeBearer(encodeBearer(c)),c);
});

test("pairing url carries Android relay fields",()=>{
  const c=createDeviceCredential();
  const u=pairingUrl("https://example.test",c);
  assert.match(u,/^hakim:\/\/pair\?/);
  assert.match(u,/relay_topic=/);
  assert.match(u,/result_url=/);
  assert.match(u,/relay_key=/);
});

test("envelope HMAC matches Android canonical format",()=>{
  const c=createDeviceCredential();
  const e=makeEnvelope(c.relayKey,"status",{x:1});
  const canonical=[e.request_id,e.op,String(e.expires_at_ms),e.payload_b64].join("\n");
  const sig=crypto.createHmac("sha256",c.relayKey).update(canonical).digest("hex");
  assert.equal(e.signature,sig);
});

test("carrier decrypts with Android-compatible AES-GCM layout",()=>{
  const c=createDeviceCredential();
  const e=makeEnvelope(c.relayKey,"ui",{});
  const carrier=encryptCarrier(c.relayKey,e);
  assert.ok(carrier.startsWith("HC1."));
  const packed=Buffer.from(carrier.slice(4),"base64url");
  const nonce=packed.subarray(0,12);
  const tag=packed.subarray(packed.length-16);
  const ciphertext=packed.subarray(12,packed.length-16);
  const key=crypto.createHash("sha256").update(CARRIER_AAD+"\0"+c.relayKey).digest();
  const d=crypto.createDecipheriv("aes-256-gcm",key,nonce);
  d.setAAD(Buffer.from(CARRIER_AAD));
  d.setAuthTag(tag);
  const raw=Buffer.concat([d.update(ciphertext),d.final()]).toString("utf8");
  assert.deepEqual(JSON.parse(raw),e);
});

test("results are encrypted end-to-end on the public carrier",()=>{
  const c=createDeviceCredential();
  const payload={request_id:"chatgpt-12345678",status:"ok",result:{secret:"not-public"}};
  const carrier=encryptResult(c.callbackSecret,payload);
  assert.match(carrier,/^HR1\./);
  assert.equal(carrier.includes("not-public"),false);
  assert.deepEqual(decryptResult(c.callbackSecret,carrier),payload);
});

test("tampered result carrier fails closed",()=>{
  const c=createDeviceCredential();
  const carrier=encryptResult(c.callbackSecret,{request_id:"chatgpt-12345678"});
  const tail=carrier.at(-1)!;
  const tampered=carrier.slice(0,-1)+(tail==="A"?"B":"A");
  assert.throws(()=>decryptResult(c.callbackSecret,tampered));
});
