import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { chatgptToolList,createHakimServer } from "../src/server.js";
import type { DeviceCredential } from "../src/protocol.js";

const credential:DeviceCredential={
  v:2,
  topic:"hakim_cmd_abcdefghijklmnopqrstuvwxyz",
  resultTopic:"hakim_result_abcdefghijklmnopqrstuvwxyz",
  relayKey:"A".repeat(48),
  pairToken:"B".repeat(43)
};

test("ChatGPT raw tools/list catalog exposes root OAuth security schemes",()=>{
  const tools=chatgptToolList() as any[];
  assert.equal(tools.length,7);

  const byName=new Map(tools.map(t=>[t.name,t]));
  for(const name of ["get_device_status","get_current_ui","list_notifications","capture_screenshot","get_request_result"]){
    const t=byName.get(name);
    assert.ok(t,name+" missing");
    assert.deepEqual(t.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
    assert.deepEqual(t._meta?.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
    assert.equal(t.annotations?.readOnlyHint,true);
  }
  for(const name of ["open_target","perform_ui_action"]){
    const t=byName.get(name);
    assert.ok(t,name+" missing");
    assert.deepEqual(t.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
    assert.deepEqual(t._meta?.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
    assert.equal(t.annotations?.readOnlyHint,false);
    assert.equal(t.annotations?.openWorldHint,false);
  }
  assert.equal(byName.get("open_target")?.annotations?.destructiveHint,false);
  assert.equal(byName.get("perform_ui_action")?.annotations?.destructiveHint,true);
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
    const status=await client.callTool({name:"get_device_status",arguments:{}});
    assert.equal((status.structuredContent as any)?.demo,true);
    assert.equal((status.structuredContent as any)?.device?.name,"Hakim Review Device");

    const launch=await client.callTool({name:"open_target",arguments:{package:"com.example.safe"}});
    assert.equal((launch.structuredContent as any)?.demo,true);
    assert.equal((launch.structuredContent as any)?.status,"approval_requested");
  }finally{
    await client.close();
    await server.close();
  }
});


test("public UI action schema is bounded to Android-supported actions",()=>{
  const tools=chatgptToolList() as any[];
  const action=tools.find(t=>t.name==="perform_ui_action");
  assert.ok(action);
  assert.deepEqual(action.inputSchema.properties.kind.enum,[
    "home","back","recents","notifications","quick_settings",
    "click_text","set_text","tap","swipe"
  ]);
  assert.equal(action.inputSchema.properties.args.additionalProperties,false);
  assert.equal(action.annotations.destructiveHint,true);
  assert.equal(action.annotations.openWorldHint,true);
  const open=tools.find(t=>t.name==="open_target");
  assert.equal(open.annotations.destructiveHint,false);
  assert.equal(open.annotations.openWorldHint,true);
});
