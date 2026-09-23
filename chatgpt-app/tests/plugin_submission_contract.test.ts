import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { chatgptToolList } from "../src/server.js";

const root=path.resolve(import.meta.dirname,"../..");
const pluginRoot=path.join(root,"plugins/hakim-device");
const plugin=JSON.parse(fs.readFileSync(path.join(pluginRoot,"plugin.json"),"utf8"));
const mcp=JSON.parse(fs.readFileSync(path.join(pluginRoot,"mcp.json"),"utf8"));
const listing=JSON.parse(fs.readFileSync(path.join(pluginRoot,"submission/listing.json"),"utf8"));
const cases=JSON.parse(fs.readFileSync(path.join(pluginRoot,"submission/test-cases.json"),"utf8"));

test("public plugin listing stays inside submission limits",()=>{
  const i=plugin.extensions["com.openai"].interface;
  assert.ok(i.displayName.length>0&&i.displayName.length<=30);
  assert.ok(i.shortDescription.length>0&&i.shortDescription.length<=30);
  assert.ok(i.longDescription.length>0&&i.longDescription.length<=4000);
  assert.ok(i.developerName.length>0&&i.developerName.length<=80);
  assert.equal(i.category,"Productivity");
  assert.ok(Array.isArray(i.capabilities)&&i.capabilities.length<=20);
  for(const c of i.capabilities) assert.ok(c.length>0&&c.length<=120&&!c.includes("\n"));
  for(const u of [i.websiteURL,i.privacyPolicyURL,i.termsOfServiceURL,i.supportURL]){
    assert.match(u,/^https:\/\//);
    assert.ok(u.length<=1024);
  }
  assert.equal(listing.starter_prompts.length,3);
  assert.equal(new Set(listing.starter_prompts.map((x:string)=>x.trim().toLowerCase())).size,3);
  for(const p of listing.starter_prompts){
    assert.ok(p.length>0&&p.length<=128&&!p.includes("\n")&&!p.includes("@"));
  }
});

test("portable MCP package points only to production HTTPS bridge",()=>{
  const server=mcp.mcpServers.hakim;
  assert.equal(server.type,"streamable-http");
  assert.equal(server.url,"https://hakim-chatgpt-bridge-production.up.railway.app/mcp");
});

test("public tool surface is narrow and fully annotated",()=>{
  const tools=chatgptToolList(true);
  const names=tools.map((t:any)=>t.name);
  assert.deepEqual(names,[
    "get_device_status",
    "open_target",
    "navigate_device",
    "get_request_result"
  ]);
  for(const tool of tools){
    assert.equal(typeof tool.annotations?.readOnlyHint,"boolean",tool.name);
    assert.equal(typeof tool.annotations?.openWorldHint,"boolean",tool.name);
    assert.equal(typeof tool.annotations?.destructiveHint,"boolean",tool.name);
  }
  assert.equal(names.includes("capture_screenshot"),false);
  assert.equal(names.includes("list_notifications"),false);
  assert.equal(names.includes("perform_ui_action"),false);
});

test("public async API uses operation_token rather than internal request_id",()=>{
  const tools=chatgptToolList(true) as any[];
  const poll=tools.find(t=>t.name==="get_request_result");
  assert.ok(poll);
  assert.ok(poll.inputSchema.properties.operation_token);
  assert.equal(poll.inputSchema.properties.request_id,undefined);
  const all=JSON.stringify(tools);
  assert.equal(all.includes('"request_id"'),false);
});

test("review suite has required five positive and three negative cases",()=>{
  assert.equal(cases.positive.length,5);
  assert.equal(cases.negative.length,3);
  for(const c of cases.positive){
    assert.ok(c.prompt&&c.expected_tool&&c.expected_behavior&&c.expected_result_shape);
  }
  for(const c of cases.negative){
    assert.ok(c.prompt&&c.expected_behavior&&c.reason);
  }
});
