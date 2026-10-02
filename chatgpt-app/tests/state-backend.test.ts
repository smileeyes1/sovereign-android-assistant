import assert from "node:assert/strict";
import test from "node:test";
import { FileStateBackend } from "../src/state-backend.js";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

test("file backend preserves atomic/exclusive/list/remove semantics",async()=>{
  const root=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-state-backend-"));
  const backend=new FileStateBackend();
  const dir=path.join(root,"nested");
  const file=path.join(dir,"state.json");
  await backend.initDir(dir);
  await backend.writeTextAtomic(file,JSON.stringify({v:1}));
  assert.equal(await backend.readText(file),JSON.stringify({v:1}));
  assert.deepEqual(await backend.list(dir),["state.json"]);
  await assert.rejects(()=>backend.writeTextExclusive(file,"x"),(e:any)=>e?.code==="EEXIST");
  await backend.remove(file);
  await assert.rejects(()=>backend.readText(file),(e:any)=>e?.code==="ENOENT");
  await fs.rm(root,{recursive:true,force:true});
});

test("unsupported shared backend fails closed before serving",async()=>{
  const old=process.env.HAKIM_STATE_BACKEND;
  process.env.HAKIM_STATE_BACKEND="unverified-remote";
  try{
    const url=new URL("../src/state-backend.js?failclosed="+Date.now(),import.meta.url);
    await assert.rejects(()=>import(url.href),/unsupported_hakim_state_backend:unverified-remote/);
  }finally{
    if(old===undefined) delete process.env.HAKIM_STATE_BACKEND;
    else process.env.HAKIM_STATE_BACKEND=old;
  }
});
