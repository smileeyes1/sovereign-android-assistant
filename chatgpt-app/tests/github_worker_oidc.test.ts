import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { verifyGitHubWorkerOidc } from "../src/github-worker-oidc.js";

function makeToken(overrides:Record<string,unknown>={}){
  const {publicKey,privateKey}=crypto.generateKeyPairSync("rsa",{modulusLength:2048});
  const jwk=publicKey.export({format:"jwk"}) as Record<string,unknown>;
  jwk.kid="test-key";
  jwk.alg="RS256";
  jwk.use="sig";
  const now=Math.floor(Date.now()/1000);
  const header={alg:"RS256",kid:"test-key",typ:"JWT"};
  const payload={
    iss:"https://token.actions.githubusercontent.com",
    aud:"hakim-development-worker",
    exp:now+300,
    iat:now,
    nbf:now-5,
    repository:"smileeyes1/sovereign-android-assistant",
    ref:"refs/heads/main",
    job_workflow_ref:"smileeyes1/sovereign-android-assistant/.github/workflows/hakim-development-worker.yml@refs/heads/main",
    event_name:"workflow_dispatch",
    run_id:"123456789",
    run_attempt:"1",
    jti:"11111111-2222-3333-4444-555555555555",
    ...overrides
  };
  const enc=(value:unknown)=>Buffer.from(JSON.stringify(value),"utf8").toString("base64url");
  const a=enc(header);
  const b=enc(payload);
  const sig=crypto.sign("RSA-SHA256",Buffer.from(a+"."+b,"utf8"),privateKey).toString("base64url");
  return {token:a+"."+b+"."+sig,jwks:{keys:[jwk]}};
}

test("accepts only the pinned Hakim development workflow identity",async()=>{
  const {token,jwks}=makeToken();
  const identity=await verifyGitHubWorkerOidc(token,jwks);
  assert.equal(identity.repository,"smileeyes1/sovereign-android-assistant");
  assert.equal(identity.ref,"refs/heads/main");
  assert.equal(identity.run_id,"123456789");
});

test("rejects token from another repository",async()=>{
  const {token,jwks}=makeToken({repository:"evil/example"});
  await assert.rejects(()=>verifyGitHubWorkerOidc(token,jwks),/worker_oidc_repository_invalid/);
});

test("rejects wrong workflow and wrong event",async()=>{
  const a=makeToken({job_workflow_ref:"smileeyes1/sovereign-android-assistant/.github/workflows/other.yml@refs/heads/main"});
  await assert.rejects(()=>verifyGitHubWorkerOidc(a.token,a.jwks),/worker_oidc_workflow_invalid/);
  const b=makeToken({event_name:"pull_request"});
  await assert.rejects(()=>verifyGitHubWorkerOidc(b.token,b.jwks),/worker_oidc_event_invalid/);
});

test("rejects expired token",async()=>{
  const now=Math.floor(Date.now()/1000);
  const {token,jwks}=makeToken({exp:now-120,iat:now-240});
  await assert.rejects(()=>verifyGitHubWorkerOidc(token,jwks,now),/worker_oidc_expired/);
});
