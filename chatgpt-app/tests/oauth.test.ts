import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createDeviceCredential } from "../src/protocol.js";
import {
  FileCodeStore,isAllowedOAuthClientId,isAllowedOAuthRedirectUri,issueAccessToken,issueRefreshToken,
  makeAuthorizeContext,normalizeScopes,openAccessToken,openAuthorizeContext,openRefreshToken,
  pkceS256,requireProductionOAuthConfig,reviewCredentialsMatch,trustedOAuthHosts
} from "../src/oauth.js";

const secret="s".repeat(64);

test("PKCE S256 is deterministic and URL safe",()=>{
  const v="A".repeat(64);
  assert.match(pkceS256(v),/^[A-Za-z0-9_-]+$/);
  assert.equal(pkceS256(v),pkceS256(v));
});

test("OAuth trust is explicit and ChatGPT is only the default host",()=>{
  const def={} as NodeJS.ProcessEnv;
  assert.deepEqual([...trustedOAuthHosts(def)],["chatgpt.com"]);
  assert.equal(isAllowedOAuthClientId(def,"https://chatgpt.com/oauth/client.json"),true);
  assert.equal(isAllowedOAuthRedirectUri(def,"https://chatgpt.com/oauth/callback"),true);
  assert.equal(isAllowedOAuthClientId(def,"https://other.example/client.json"),false);
  const custom={HAKIM_TRUSTED_OAUTH_HOSTS:"chatgpt.com,assistant.example"} as NodeJS.ProcessEnv;
  assert.equal(isAllowedOAuthClientId(custom,"https://assistant.example/client.json"),true);
  assert.equal(isAllowedOAuthRedirectUri(custom,"https://assistant.example/oauth/callback"),true);
  assert.equal(isAllowedOAuthClientId(custom,"http://assistant.example/client.json"),false);
  assert.equal(isAllowedOAuthRedirectUri(custom,"https://evil.example/oauth/callback"),false);
});

test("scope normalization rejects unknown scopes",()=>{
  assert.deepEqual(normalizeScopes("hakim.read hakim.write offline_access"),["hakim.read","hakim.write","offline_access"]);
  assert.deepEqual(normalizeScopes(undefined),["hakim.read","hakim.write","offline_access"]);
  assert.throws(()=>normalizeScopes("hakim.admin"));
});

test("authorize context and access/refresh tokens fail closed under wrong secret",()=>{
  const credential=createDeviceCredential();
  const ctx=makeAuthorizeContext(secret,{credential,clientId:"https://chatgpt.com/oauth/client.json",redirectUri:"https://chatgpt.com/oauth/callback",state:"x",codeChallenge:"y",resource:"https://hakim.example",scopes:["hakim.read"]});
  assert.equal(openAuthorizeContext(secret,ctx).credential.topic,credential.topic);
  assert.throws(()=>openAuthorizeContext("x".repeat(64),ctx));
  const access=issueAccessToken(secret,{credential,clientId:"c",aud:"https://hakim.example",scopes:["hakim.read"]});
  assert.equal(openAccessToken(secret,access,"https://hakim.example").credential.resultTopic,credential.resultTopic);
  assert.throws(()=>openAccessToken(secret,access,"https://other.example"));
  const refresh=issueRefreshToken(secret,{credential,clientId:"c",aud:"https://hakim.example",scopes:["hakim.read"]});
  assert.equal(openRefreshToken(secret,refresh,"https://hakim.example").credential.topic,credential.topic);
});

test("authorization codes are atomically single-use",async()=>{
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-oauth-"));
  try{
    const store=new FileCodeStore(dir);
    const credential=createDeviceCredential();
    const code=await store.issue({credential,clientId:"c",redirectUri:"https://chatgpt.com/oauth/callback",codeChallenge:"challenge",resource:"https://hakim.example",scopes:["hakim.read"],expiresAt:Date.now()+60_000});
    const got=await store.consume(code);
    assert.equal(got.credential.topic,credential.topic);
    await assert.rejects(()=>store.consume(code),/invalid_or_consumed_code/);
  }finally{await fs.rm(dir,{recursive:true,force:true});}
});

test("production requires durable auth configuration",()=>{
  assert.throws(()=>requireProductionOAuthConfig({NODE_ENV:"production"} as NodeJS.ProcessEnv));
  assert.doesNotThrow(()=>requireProductionOAuthConfig({NODE_ENV:"production",HAKIM_OAUTH_SECRET:secret,HAKIM_DATA_DIR:"/data"} as NodeJS.ProcessEnv));
});

test("review credentials exist only when explicitly configured",()=>{
  const env={HAKIM_REVIEW_USER:"reviewer",HAKIM_REVIEW_PASSWORD:"R".repeat(32)} as NodeJS.ProcessEnv;
  assert.equal(reviewCredentialsMatch(env,"reviewer","R".repeat(32)),true);
  assert.equal(reviewCredentialsMatch(env,"wrong","R".repeat(32)),false);
  assert.equal(reviewCredentialsMatch({} as NodeJS.ProcessEnv,"reviewer","R".repeat(32)),false);
});
