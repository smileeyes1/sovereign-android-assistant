import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createHakimServer } from "../src/server.js";
import type { DeviceCredential } from "../src/protocol.js";

const credential:DeviceCredential={
  v:2,
  topic:"hakim_cmd_film_persistence_abcdefghijklmno",
  resultTopic:"hakim_result_film_persistence_abcdefgh",
  relayKey:"A".repeat(48),
  pairToken:"B".repeat(43)
};

async function openClient(name:string){
  const server=createHakimServer(
    credential,
    ["hakim.read","hakim.write"],
    "https://example.test/.well-known/oauth-protected-resource",
    true
  );
  const client=new Client({name,version:"1.0.0"},{capabilities:{}});
  const [ct,st]=InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(st),client.connect(ct)]);
  return {server,client};
}

test("Film OS project survives a completely new MCP session",async()=>{
  const previous=process.env.HAKIM_DATA_DIR;
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-film-os-mcp-"));
  process.env.HAKIM_DATA_DIR=dir;
  try{
    const first=await openClient("film-session-one");
    let projectId="";
    try{
      const created=await first.client.callTool({
        name:"create_film_project",
        arguments:{preset:"number5_grade1"}
      });
      const body=created.structuredContent as any;
      projectId=body.project_id;
      assert.match(projectId,/^film-[0-9a-f]{24}$/);
      assert.equal(body.status,"SHOT_CONTRACTS_READY");
      assert.equal(body.golden.educational_truth.target_digit,"٥");
      assert.equal(body.golden.educational_truth.target_quantity,5);
    }finally{
      await first.client.close();
      await first.server.close();
    }

    const second=await openClient("film-session-two-fresh-context");
    try{
      const restored=await second.client.callTool({
        name:"get_film_project",
        arguments:{project_id:projectId}
      });
      const body=restored.structuredContent as any;
      assert.equal(body.project_id,projectId);
      assert.equal(body.status,"SHOT_CONTRACTS_READY");
      assert.equal(body.golden.educational_truth.target_digit,"٥");
      assert.equal(body.golden.educational_truth.student_facing_western_digit_forbidden,true);
      assert.equal(body.golden.shots.length,5);
    }finally{
      await second.client.close();
      await second.server.close();
    }
  }finally{
    if(previous===undefined) delete process.env.HAKIM_DATA_DIR;
    else process.env.HAKIM_DATA_DIR=previous;
    await fs.rm(dir,{recursive:true,force:true});
  }
});
