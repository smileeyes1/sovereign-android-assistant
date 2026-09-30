import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { chatgptToolList,createHakimServer } from "../src/server.js";
import type { DeviceCredential } from "../src/protocol.js";

const credential:DeviceCredential={
  v:2,
  topic:"hakim_review_resume_abcdefghijklmnopqrstuvwxyz",
  resultTopic:"hakim_review_result_resume_abcdefghijklmnop",
  relayKey:"A".repeat(48),
  pairToken:"B".repeat(43)
};

test("new independent client can bootstrap Hakim without prior chat context",async()=>{
  const tools=chatgptToolList(true) as any[];
  assert.equal(tools[0]?.name,"resume_hakim");
  const advertised=tools.find(t=>t.name==="resume_hakim");
  assert.ok(advertised);
  assert.equal(advertised.annotations.readOnlyHint,true);
  assert.deepEqual(advertised.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);

  const server=createHakimServer(
    credential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource",
    true
  );
  const client=new Client({name:"fresh-chat-simulation",version:"1.0.0"},{capabilities:{}});
  const [clientTransport,serverTransport]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport),client.connect(clientTransport)]);
  try{
    const result=await client.callTool({name:"resume_hakim",arguments:{}});
    const body=result.structuredContent as any;
    assert.deepEqual(body.authoritative_order,[
      "fresh_live_preflight",
      "durable_continuity_checkpoint",
      "current_bridge_capabilities_and_version",
      "prior_conversation_descriptions"
    ]);
    assert.equal(body.preflight?.fresh,true);
    assert.equal(body.preflight?.runtime_ready,true);
    assert.equal(body.continuity?.durable,true);
    assert.equal(body.video?.director_version,"HAKIM_CINEMA_V1_2026-09-30");
    assert.equal(body.zero_burden,
      "No user restatement is required when this tool and the durable Hakim connection are available.");
  }finally{
    await client.close();
    await server.close();
  }
});
