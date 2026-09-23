import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

async function waitFor(url:string,timeoutMs=15_000){
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    try{
      const r=await fetch(url);
      if(r.ok) return;
    }catch{}
    await new Promise(r=>setTimeout(r,150));
  }
  throw new Error("server_start_timeout");
}

test("real HTTP /mcp tools/list exposes ChatGPT OAuth metadata",async(t)=>{
  const port=39000+Math.floor(Math.random()*1000);
  const dataDir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-http-ci-"));
  const child=spawn(process.execPath,["--import","tsx","src/index.ts"],{
    cwd:path.resolve(import.meta.dirname,".."),
    env:{
      ...process.env,
      NODE_ENV:"test",
      PORT:String(port),
      HAKIM_ALLOW_DEV_BEARER:"1",
      HAKIM_OAUTH_SECRET:"T".repeat(64),
      HAKIM_DATA_DIR:dataDir
    },
    stdio:["ignore","pipe","pipe"]
  });
  t.after(async()=>{
    child.kill("SIGTERM");
    await fs.rm(dataDir,{recursive:true,force:true});
  });

  const base=`http://127.0.0.1:${port}`;
  await waitFor(base+"/health");

  const pair=await fetch(base+"/pair");
  assert.equal(pair.status,200);
  const html=await pair.text();
  const m=html.match(/HAKIM-B2\.[A-Za-z0-9_-]+/);
  assert.ok(m,"development bearer missing");
  const bearer=m[0]!;

  const response=await fetch(base+"/mcp",{
    method:"POST",
    headers:{
      "Authorization":"Bearer "+bearer,
      "Content-Type":"application/json",
      "Accept":"application/json, text/event-stream"
    },
    body:JSON.stringify({jsonrpc:"2.0",id:1,method:"tools/list",params:{}})
  });
  assert.equal(response.status,200);
  const body=await response.json() as any;
  assert.equal(body.jsonrpc,"2.0");
  assert.equal(body.id,1);
  assert.equal(body.result.tools.length,7);

  const byName=new Map(body.result.tools.map((x:any)=>[x.name,x]));
  for(const name of ["status","ui","notifications","screenshot","check_request"]){
    const tool:any=byName.get(name);
    assert.deepEqual(tool.securitySchemes,[{type:"oauth2",scopes:["hakim.read"]}]);
    assert.equal(tool.annotations.readOnlyHint,true);
  }
  for(const name of ["launch","action"]){
    const tool:any=byName.get(name);
    assert.deepEqual(tool.securitySchemes,[{type:"oauth2",scopes:["hakim.write"]}]);
    assert.equal(tool.annotations.readOnlyHint,false);
  }
});
