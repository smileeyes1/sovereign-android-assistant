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

test("public ChatGPT tool catalog is privacy-minimized",()=>{
  const tools=chatgptToolList(true) as any[];
  assert.equal(tools.length,4);
  const byName=new Map(tools.map(t=>[t.name,t]));
  assert.deepEqual([...byName.keys()].sort(),[
    "get_device_status","get_request_result","navigate_device","open_target"
  ].sort());

  for(const name of ["get_device_status","get_request_result"]){
    const t:any=byName.get(name);
    assert.ok(t,name+" missing");
    assert.deepEqual(t.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
    assert.equal(t.annotations.readOnlyHint,true);
    assert.equal(t.annotations.openWorldHint,false);
    assert.equal(t.annotations.destructiveHint,false);
  }

  const open:any=byName.get("open_target");
  assert.deepEqual(open.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
  assert.equal(open.annotations.readOnlyHint,false);
  assert.equal(open.annotations.openWorldHint,true);
  assert.equal(open.annotations.destructiveHint,false);

  const nav:any=byName.get("navigate_device");
  assert.deepEqual(nav.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
  assert.deepEqual(nav.inputSchema.properties.kind.enum,["home","back","recents"]);
  assert.equal(nav.annotations.readOnlyHint,false);
  assert.equal(nav.annotations.openWorldHint,false);
  assert.equal(nav.annotations.destructiveHint,false);

  for(const forbidden of [
    "get_current_ui","list_notifications","capture_screenshot","perform_ui_action"
  ]) assert.equal(byName.has(forbidden),false,forbidden+" must not be public");
});

test("private tool catalog remains available only when explicitly selected",()=>{
  const tools=chatgptToolList(false) as any[];
  const byName=new Map(tools.map(t=>[t.name,t]));
  assert.equal(tools.length,7);
  for(const name of [
    "get_device_status","get_current_ui","list_notifications","capture_screenshot",
    "open_target","perform_ui_action","get_request_result"
  ]) assert.ok(byName.has(name),name+" missing in private catalog");
  assert.equal(byName.has("navigate_device"),false);
  const action:any=byName.get("perform_ui_action");
  assert.deepEqual(action.inputSchema.properties.kind.enum,[
    "home","back","recents","notifications","quick_settings",
    "click_text","set_text","tap","swipe"
  ]);
  assert.equal(action.annotations.destructiveHint,true);
});

test("public reviewer mode is isolated from real device transport and redacts content",async()=>{
  const reviewCredential:DeviceCredential={
    ...credential,
    topic:"hakim_review_abcdefghijklmnopqrstuvwxyz",
    resultTopic:"hakim_review_result_abcdefghijklmnop"
  };
  const server=createHakimServer(
    reviewCredential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource",
    true
  );
  const client=new Client({name:"hakim-review-ci",version:"1.0.0"},{capabilities:{}});
  const [clientTransport,serverTransport]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport),client.connect(clientTransport)]);
  try{
    const status=await client.callTool({name:"get_device_status",arguments:{}});
    assert.equal((status.structuredContent as any)?.demo,true);
    assert.equal((status.structuredContent as any)?.device?.name,"Hakim Review Device");
    assert.equal((status.structuredContent as any)?.privacy,"content_redacted");

    const launch=await client.callTool({name:"open_target",arguments:{package:"com.example.safe"}});
    assert.equal((launch.structuredContent as any)?.demo,true);
    assert.equal((launch.structuredContent as any)?.status,"approval_requested");

    const nav=await client.callTool({name:"navigate_device",arguments:{kind:"back"}});
    assert.equal((nav.structuredContent as any)?.demo,true);
    assert.equal((nav.structuredContent as any)?.status,"approval_requested");
    assert.equal((nav.structuredContent as any)?.validated_action,"back");

    const result=await client.callTool({name:"get_request_result",arguments:{operation_token:"review-12345678"}});
    assert.equal((result.structuredContent as any)?.privacy,"content_redacted");
    assert.equal("result" in ((result.structuredContent as any)??{}),false);
  }finally{
    await client.close();
    await server.close();
  }
});
