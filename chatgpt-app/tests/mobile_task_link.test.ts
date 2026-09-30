import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

async function waitFor(url:string,timeoutMs=15_000){
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    try{ const r=await fetch(url); if(r.ok) return; }catch{}
    await new Promise(r=>setTimeout(r,150));
  }
  throw new Error("server_start_timeout");
}

test("mobile network-protection launcher is Android-only and mutation-free",async(t)=>{
  const port=40100+Math.floor(Math.random()*700);
  const dataDir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-mobile-task-ci-"));
  const child=spawn(process.execPath,["--import","tsx","src/index.ts"],{
    cwd:path.resolve(import.meta.dirname,".."),
    env:{
      ...process.env,
      NODE_ENV:"test",
      PORT:String(port),
      HAKIM_OAUTH_SECRET:"M".repeat(64),
      HAKIM_DATA_DIR:dataDir,
      HAKIM_PUBLIC_SAFE:"1"
    },
    stdio:["ignore","pipe","pipe"]
  });
  t.after(async()=>{
    child.kill("SIGTERM");
    await fs.rm(dataDir,{recursive:true,force:true});
  });

  const base=`http://127.0.0.1:${port}`;
  await waitFor(base+"/health");
  const response=await fetch(base+"/mobile-task/network-protection");
  assert.equal(response.status,200);
  const html=await response.text();

  assert.match(html,/hakim:\/\/task\/network-protection/);
  assert.match(html,/لا يغيّر أي إعداد بذاته/);
  assert.match(html,/موافقة محلية/);
  assert.doesNotMatch(html,/relay[_ -]?key/i);
  assert.doesNotMatch(html,/password/i);
  assert.doesNotMatch(html,/192\.168\.1\.1/);
});
