import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { chatgptToolList } from "../src/server.js";

const root=path.resolve(import.meta.dirname,"../..");
const pluginRoot=path.join(root,"plugins/hakim");
const plugin=JSON.parse(fs.readFileSync(path.join(pluginRoot,"plugin.json"),"utf8"));
const mcp=JSON.parse(fs.readFileSync(path.join(pluginRoot,"mcp.json"),"utf8"));
const submission=fs.readFileSync(path.join(pluginRoot,"SUBMISSION.md"),"utf8");

test("public plugin listing stays inside directory limits",()=>{
  const i=plugin.extensions["com.openai"].interface;
  assert.ok(i.displayName.length>0&&i.displayName.length<=30);
  assert.ok(i.shortDescription.length>0&&i.shortDescription.length<=30);
  assert.ok(i.longDescription.length>0&&i.longDescription.length<=4000);
  assert.ok(i.developerName.length>0&&i.developerName.length<=80);
  assert.equal(i.category,"Productivity");
  assert.ok(Array.isArray(i.capabilities)&&i.capabilities.length>0&&i.capabilities.length<=20);
  for(const c of i.capabilities) assert.ok(c.length>0&&c.length<=120&&!c.includes("\n"));
  for(const u of [i.websiteURL,i.privacyPolicyURL,i.termsOfServiceURL,i.supportURL]){
    assert.match(u,/^https:\/\//);
    assert.ok(u.length<=1024);
  }
  assert.ok(Array.isArray(i.defaultPrompt)&&i.defaultPrompt.length===3);
  assert.equal(new Set(i.defaultPrompt.map((x:string)=>x.trim().toLowerCase())).size,3);
  for(const p of i.defaultPrompt) assert.ok(p.length>0&&p.length<=128&&!p.includes("\n")&&!p.includes("@"));
});

test("portable MCP package points only to production HTTPS bridge",()=>{
  const server=mcp.mcpServers.hakim;
  assert.equal(server.type,"streamable-http");
  assert.equal(server.url,"https://hakim-chatgpt-bridge-production.up.railway.app/mcp");
});

test("public tool surface is narrow and fully annotated",()=>{
  const tools=chatgptToolList(true) as any[];
  assert.deepEqual(tools.map(t=>t.name),[
    "get_device_status","open_target","navigate_device","get_request_result"
  ]);
  for(const tool of tools){
    assert.equal(typeof tool.annotations?.readOnlyHint,"boolean",tool.name);
    assert.equal(typeof tool.annotations?.openWorldHint,"boolean",tool.name);
    assert.equal(typeof tool.annotations?.destructiveHint,"boolean",tool.name);
  }
  const serialized=JSON.stringify(tools);
  for(const forbidden of ["capture_screenshot","list_notifications","perform_ui_action"]){
    assert.equal(serialized.includes(forbidden),false);
  }
});

test("public async API exposes operation_token and hides request_id schema",()=>{
  const tools=chatgptToolList(true) as any[];
  const poll=tools.find(t=>t.name==="get_request_result");
  assert.ok(poll?.inputSchema?.properties?.operation_token);
  assert.equal(poll?.inputSchema?.properties?.request_id,undefined);
  assert.equal(JSON.stringify(tools).includes('"request_id"'),false);
});

test("submission packet has review cases and annotation justifications",()=>{
  assert.equal([...submission.matchAll(/^### P\d+\b/gm)].length,5);
  assert.equal([...submission.matchAll(/^### N\d+\b/gm)].length,3);
  for(const tool of ["get_device_status","open_target","navigate_device","get_request_result"]){
    assert.ok(submission.includes("### "+tool),tool+" justification missing");
  }
  for(const key of ["readOnlyHint","openWorldHint","destructiveHint"]){
    assert.ok(submission.includes(key),key+" justification missing");
  }
  assert.equal(submission.includes("Hakim-Review-Demo-Only-2026"),false);
  assert.ok(submission.includes("HAKIM_REVIEW_USER"));
  assert.ok(submission.includes("HAKIM_REVIEW_PASSWORD"));
});
