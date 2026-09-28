import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { ContinuityStore } from "../src/continuity-store.js";
import { createDeviceCredential } from "../src/protocol.js";

async function withDir(fn:(dir:string)=>Promise<void>){
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-continuity-test-"));
  try{ await fn(dir); }finally{ await fs.rm(dir,{recursive:true,force:true}); }
}

test("continuity journal survives a new store instance",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const a=new ContinuityStore(dir);
    await a.init();
    await a.recordRequested(c,"chatgpt-continuity-1234","browser_read");

    const b=new ContinuityStore(dir);
    await b.init();
    const state=await b.state(c);
    assert.equal(state.durable,true);
    assert.equal(state.pending_count,1);
    assert.equal(state.operations[0]?.operation_token,"chatgpt-continuity-1234");
    assert.equal(state.operations[0]?.operation,"browser_read");
    assert.equal(state.operations[0]?.status,"requested");
    assert.equal(state.operations[0]?.resumable,true);
  });
});

test("continuity journal records completion without device content",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    const id="chatgpt-continuity-5678";
    await store.recordRequested(c,id,"status");
    await store.recordObserved(c,id,{request_id:id,status:"ok",result:{ok:true,secret:"must-not-persist"}});

    const state=await store.state(c);
    assert.equal(state.pending_count,0);
    assert.equal(state.operations[0]?.status,"ok");
    assert.equal(state.operations[0]?.resumable,false);

    const files=await fs.readdir(dir);
    const raw=await fs.readFile(path.join(dir,files[0]!),"utf8");
    assert.equal(raw.includes("must-not-persist"),false);
    assert.equal(raw.includes(c.relayKey),false);
    assert.equal(raw.includes(c.resultTopic),false);
  });
});

test("continuity journal does not duplicate an operation token",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    const id="chatgpt-continuity-9012";
    await store.recordRequested(c,id,"action");
    await store.recordRequested(c,id,"action");
    const state=await store.state(c);
    assert.equal(state.operations.filter(x=>x.operation_token===id).length,1);
  });
});
