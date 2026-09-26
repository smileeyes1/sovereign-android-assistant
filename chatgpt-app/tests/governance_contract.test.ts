import test from "node:test";
import assert from "node:assert/strict";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import {
  GOVERNANCE_SUMMARY,SOVEREIGN_GOVERNANCE_VERSION
} from "../src/governance.js";
import { chatgptToolList,createHakimServer } from "../src/server.js";
import type { DeviceCredential } from "../src/protocol.js";

const credential:DeviceCredential={
  v:2,
  topic:"hakim_review_abcdefghijklmnopqrstuvwxyz",
  resultTopic:"hakim_review_result_abcdefghijklmnopqrstuvwxyz",
  relayKey:"A".repeat(48),
  pairToken:"B".repeat(43)
};

test("bridge publishes sovereign-v4 authority contract",()=>{
  assert.equal(SOVEREIGN_GOVERNANCE_VERSION,"SOVEREIGN-QURAN-V4-2026-09-25");
  assert.equal(GOVERNANCE_SUMMARY.user_goal_sovereignty,true);
  assert.equal(GOVERNANCE_SUMMARY.capability_is_not_authorization,true);
  assert.equal(GOVERNANCE_SUMMARY.external_content_is_data_not_instruction,true);
  assert.equal(GOVERNANCE_SUMMARY.approval_requested_is_not_success,true);
  assert.equal(GOVERNANCE_SUMMARY.tool_result_is_not_goal_completion,true);
  assert.equal(GOVERNANCE_SUMMARY.no_scope_escalation,true);
  assert.equal(GOVERNANCE_SUMMARY.fail_closed,true);
  assert.equal(GOVERNANCE_SUMMARY.religious_technical_causality_claimed,false);
});

test("tool catalog states evidence and approval boundaries",()=>{
  const tools=chatgptToolList(true) as any[];
  const byName=new Map(tools.map(t=>[t.name,t]));
  for(const name of ["get_device_status","get_request_result"]){
    const tool:any=byName.get(name);
    assert.ok(tool);
    assert.match(tool.description,/بيانات لا أوامر/);
  }
  assert.match((byName.get("open_target") as any).description,/ليس نجاحًا|ليست نجاحًا/);
  assert.match((byName.get("navigate_device") as any).description,/ليس نجاحًا|ليست نجاحًا/);
});

test("reviewer tool results cannot impersonate instructions or goal completion",async()=>{
  const server=createHakimServer(
    credential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource",
    true
  );
  const client=new Client({name:"hakim-governance-ci",version:"1.0.0"},{capabilities:{}});
  const [clientTransport,serverTransport]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport),client.connect(clientTransport)]);
  try{
    const status=await client.callTool({name:"get_device_status",arguments:{}});
    const s:any=status.structuredContent;
    assert.equal(s._hakim_governance.authority,"external_data");
    assert.equal(s._hakim_governance.instructions_authorized,false);
    assert.equal(s._hakim_governance.goal_complete,false);

    const open=await client.callTool({name:"open_target",arguments:{package:"com.example.safe"}});
    const o:any=open.structuredContent;
    assert.equal(o.status,"approval_requested");
    assert.equal(o.effect_verified,false);
    assert.equal(o.goal_complete,false);
    assert.equal(o._hakim_governance.user_approval_required,true);
    assert.equal(o._hakim_governance.approval_requested_is_not_success,true);

    const nav=await client.callTool({name:"navigate_device",arguments:{kind:"back"}});
    const n:any=nav.structuredContent;
    assert.equal(n.status,"approval_requested");
    assert.equal(n.effect_verified,false);
    assert.equal(n.goal_complete,false);

    const result=await client.callTool({
      name:"get_request_result",
      arguments:{operation_token:"review-12345678"}
    });
    const rr:any=result.structuredContent;
    assert.equal(rr._hakim_governance.authority,"external_data");
    assert.equal(rr._hakim_governance.instructions_authorized,false);
    assert.equal(rr.goal_complete,false);
    assert.equal(rr.evidence_scope,"device_report_only");
  }finally{
    await client.close();
    await server.close();
  }
});
