import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {OAuthClientRegistry} from "../src/oauth-client-registry.js";

async function withDir(fn:(dir:string)=>Promise<void>){
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-oauth-registry-"));
  try{await fn(dir);}finally{await fs.rm(dir,{recursive:true,force:true});}
}

test("registered AI client is public PKCE-style metadata with no client secret",async()=>{
  await withDir(async dir=>{
    const store=new OAuthClientRegistry(dir);
    await store.init();
    const client=await store.register({
      client_name:"Future AI Adapter",
      redirect_uris:["https://agent.example/callback"],
      token_endpoint_auth_method:"none",
      grant_types:["authorization_code","refresh_token"],
      response_types:["code"]
    });
    const publicRecord=store.publicRegistration(client) as Record<string,unknown>;
    assert.match(client.client_id,/^hakim-client-/);
    assert.equal(publicRecord.token_endpoint_auth_method,"none");
    assert.equal("client_secret" in publicRecord,false);
    assert.deepEqual(publicRecord.redirect_uris,["https://agent.example/callback"]);
    assert.ok(await store.resolve(client.client_id,"https://agent.example/callback"));
    assert.equal(await store.resolve(client.client_id,"https://evil.example/callback"),null);
  });
});

test("registration allows HTTPS and loopback HTTP but rejects remote insecure and credential-bearing redirects",async()=>{
  await withDir(async dir=>{
    const store=new OAuthClientRegistry(dir);
    await store.init();
    await store.register({
      client_name:"Local adapter",
      redirect_uris:[
        "https://agent.example/callback",
        "http://127.0.0.1:8765/callback",
        "http://localhost:9123/callback"
      ]
    });
    await assert.rejects(()=>store.register({
      client_name:"Bad",
      redirect_uris:["http://agent.example/callback"]
    }),/invalid_redirect_uri/);
    await assert.rejects(()=>store.register({
      client_name:"Bad",
      redirect_uris:["https://user:pass@agent.example/callback"]
    }),/invalid_redirect_uri/);
    await assert.rejects(()=>store.register({
      client_name:"Bad",
      redirect_uris:["https://agent.example/callback#token"]
    }),/invalid_redirect_uri/);
  });
});

test("registered redirect matching is exact after normalization",async()=>{
  await withDir(async dir=>{
    const store=new OAuthClientRegistry(dir);
    await store.init();
    const client=await store.register({
      client_name:"Exact adapter",
      redirect_uris:["https://agent.example/callback?mode=one"]
    });
    assert.ok(await store.resolve(client.client_id,"https://agent.example/callback?mode=one"));
    assert.equal(await store.resolve(client.client_id,"https://agent.example/callback?mode=two"),null);
    assert.equal(await store.resolve(client.client_id,"https://agent.example/callback"),null);
  });
});
