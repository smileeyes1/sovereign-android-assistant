import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createHakimServer } from "../src/server.js";
import type { DeviceCredential } from "../src/protocol.js";

const credential:DeviceCredential={
  v:2,
  topic:"hakim_cmd_abcdefghijklmnopqrstuvwxyz",
  resultTopic:"hakim_result_abcdefghijklmnopqrstuvwxyz",
  relayKey:"A".repeat(48),
  pairToken:"B".repeat(43)
};

test("tools/list exposes root OAuth security schemes for ChatGPT",async()=>{
  const server=createHakimServer(
    credential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource"
  );
  const client=new Client({name:"hakim-ci",version:"1.0.0"},{capabilities:{}});
  const [clientTransport,serverTransport]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport),client.connect(clientTransport)]);
  try{
    const {tools}=await client.listTools();
    assert.equal(tools.length,7);

    const byName=new Map(tools.map(t=>[t.name,t as any]));
    for(const name of ["status","ui","notifications","screenshot","check_request"]){
      const t=byName.get(name);
      assert.ok(t,name+" missing");
      assert.deepEqual(t.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
      assert.deepEqual(t._meta?.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
      assert.equal(t.annotations?.readOnlyHint,true);
    }
    for(const name of ["launch","action"]){
      const t=byName.get(name);
      assert.ok(t,name+" missing");
      assert.deepEqual(t.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
      assert.deepEqual(t._meta?.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
      assert.equal(t.annotations?.readOnlyHint,false);
    }
  }finally{
    await client.close();
    await server.close();
  }
});


test("review mode is isolated from real device transport",async()=>{
  const reviewCredential:DeviceCredential={
    ...credential,
    topic:"hakim_review_abcdefghijklmnopqrstuvwxyz",
    resultTopic:"hakim_review_result_abcdefghijklmnop"
  };
  const server=createHakimServer(
    reviewCredential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource"
  );
  const client=new Client({name:"hakim-review-ci",version:"1.0.0"},{capabilities:{}});
  const [clientTransport,serverTransport]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport),client.connect(clientTransport)]);
  try{
    const status=await client.callTool({name:"status",arguments:{}});
    assert.equal((status.structuredContent as any)?.demo,true);
    assert.equal((status.structuredContent as any)?.device?.name,"Hakim Review Device");

    const launch=await client.callTool({name:"launch",arguments:{package:"com.example.safe"}});
    assert.equal((launch.structuredContent as any)?.demo,true);
    assert.equal((launch.structuredContent as any)?.status,"approval_requested");
  }finally{
    await client.close();
    await server.close();
  }
});
