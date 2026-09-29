import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { chatgptToolList,createHakimServer } from "../src/server.js";
import { commandTtlMs } from "../src/relay.js";
import type { DeviceCredential } from "../src/protocol.js";

const credential:DeviceCredential={
  v:2,
  topic:"hakim_review_abcdefghijklmnopqrstuvwxyz",
  resultTopic:"hakim_review_result_abcdefghijklmnop",
  relayKey:"A".repeat(48),
  pairToken:"B".repeat(43)
};

const lanNames=["list_network_devices","authorize_network_device","control_network_device","revoke_network_device"];
const actions=[
  "home","back","up","down","left","right","enter",
  "play_pause","volume_up","volume_down","mute",
  "open_url","launch_package"
];

function restoreEnv(previous:string|undefined){
  if(previous===undefined) delete process.env.HAKIM_LAN_CONTROL;
  else process.env.HAKIM_LAN_CONTROL=previous;
}

test("LAN control fails closed until explicitly enabled",()=>{
  const previous=process.env.HAKIM_LAN_CONTROL;
  try{
    delete process.env.HAKIM_LAN_CONTROL;
    const names=chatgptToolList(true).map((t:any)=>t.name);
    for(const name of lanNames) assert.equal(names.includes(name),false,name+" exposed by default");
  }finally{restoreEnv(previous);}
});

test("enabled LAN catalog is pseudonymous and restricted",()=>{
  const previous=process.env.HAKIM_LAN_CONTROL;
  try{
    process.env.HAKIM_LAN_CONTROL="1";
    const tools=chatgptToolList(true) as any[];
    assert.equal(tools.length,12);
    const byName=new Map(tools.map(t=>[t.name,t]));

    const list:any=byName.get("list_network_devices");
    assert.ok(list);
    assert.deepEqual(list.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
    assert.equal(list.annotations.readOnlyHint,true);

    const authorize:any=byName.get("authorize_network_device");
    assert.ok(authorize);
    assert.deepEqual(authorize.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
    assert.equal(authorize.inputSchema.properties.device_id.pattern,"^lan-[0-9a-f]{16}$");
    assert.deepEqual(authorize.inputSchema.properties.adapter.enum,["adb"]);
    assert.equal(authorize.annotations.idempotentHint,true);

    const control:any=byName.get("control_network_device");
    assert.ok(control);
    assert.deepEqual(control.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
    assert.deepEqual(control.inputSchema.properties.action.enum,actions);
    assert.equal(control.inputSchema.properties.command,undefined);
    assert.equal(control.annotations.destructiveHint,false);

    const revoke:any=byName.get("revoke_network_device");
    assert.ok(revoke);
    assert.deepEqual(revoke.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
    assert.equal(revoke.inputSchema.properties.device_id.pattern,"^lan-[0-9a-f]{16}$");
    assert.deepEqual(revoke.inputSchema.properties.adapter.enum,["adb"]);
    assert.equal(revoke.annotations.idempotentHint,true);
    for(const forbidden of ["reboot","power","install","uninstall","factory_reset","shell"]){
      assert.equal(actions.includes(forbidden),false,forbidden+" must remain unavailable");
    }
  }finally{restoreEnv(previous);}
});

test("enabled reviewer LAN tools never touch a real device and preserve approval semantics",async()=>{
  const previous=process.env.HAKIM_LAN_CONTROL;
  process.env.HAKIM_LAN_CONTROL="1";
  const server=createHakimServer(
    credential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource",
    true
  );
  const client=new Client({name:"hakim-lan-review-ci",version:"1.0.0"},{capabilities:{}});
  const [clientTransport,serverTransport]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport),client.connect(clientTransport)]);
  try{
    const list=await client.callTool({name:"list_network_devices",arguments:{}});
    assert.equal((list.structuredContent as any)?.demo,true);

    const authorize=await client.callTool({
      name:"authorize_network_device",
      arguments:{device_id:"lan-0123456789abcdef",adapter:"adb"}
    });
    assert.equal((authorize.structuredContent as any)?.demo,true);
    assert.equal((authorize.structuredContent as any)?.status,"approval_requested");

    const control=await client.callTool({
      name:"control_network_device",
      arguments:{device_id:"lan-0123456789abcdef",action:"home"}
    });
    assert.equal((control.structuredContent as any)?.demo,true);
    assert.equal((control.structuredContent as any)?.status,"approval_requested");
    assert.equal((control.structuredContent as any)?.validated_action,"home");

    const revoke=await client.callTool({
      name:"revoke_network_device",
      arguments:{device_id:"lan-0123456789abcdef",adapter:"adb"}
    });
    assert.equal((revoke.structuredContent as any)?.demo,true);
    assert.equal((revoke.structuredContent as any)?.status,"approval_requested");
    assert.equal((revoke.structuredContent as any)?.validated_action,"revoke");
  }finally{
    await client.close();
    await server.close();
    restoreEnv(previous);
  }
});

test("LAN discovery can survive phone sleep but LAN effects stay short-lived",()=>{
  assert.equal(commandTtlMs("network_devices"),30*60_000);
  assert.equal(commandTtlMs("network_authorize"),60_000);
  assert.equal(commandTtlMs("network_control"),60_000);
  assert.equal(commandTtlMs("network_revoke"),60_000);
});

test("health metadata also gates LAN tool advertisement",()=>{
  const index=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");
  assert.match(index,/HAKIM_LAN_CONTROL/);
  assert.match(index,/list_network_devices/);
  assert.match(index,/process\.env\.HAKIM_LAN_CONTROL==="1"/);
});
