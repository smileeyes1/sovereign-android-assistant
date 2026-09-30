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

test("Android-only network protection launcher is bounded and secret-free",async(t)=>{
  const port=41000+Math.floor(Math.random()*1000);
  const dataDir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-mobile-task-ci-"));
  const child=spawn(process.execPath,["--import","tsx","src/index.ts"],{
    cwd:path.resolve(import.meta.dirname,".."),
    env:{
      ...process.env,
      NODE_ENV:"test",
      PORT:String(port),
      HAKIM_ALLOW_DEV_BEARER:"1",
      HAKIM_OAUTH_SECRET:"T".repeat(64),
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
  const r=await fetch(base+"/mobile-task/network-protection",{redirect:"manual"});
  assert.equal(r.status,200);
  const html=await r.text();

  assert.match(html,/hakim:\/\/task\/network-protection/);
  assert.match(html,/هذا الرابط لا يغيّر أي إعداد بذاته/);
  assert.doesNotMatch(html,/HAKIM-B2\./);
  assert.doesNotMatch(html,/relay[_-]?key/i);
  assert.doesNotMatch(html,/oauth[_-]?secret/i);
  assert.doesNotMatch(html,/password/i);
  assert.doesNotMatch(html,/\?command=/i);
  assert.doesNotMatch(html,/\?action=/i);
});
