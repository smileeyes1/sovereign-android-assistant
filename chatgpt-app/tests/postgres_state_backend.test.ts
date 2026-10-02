import assert from "node:assert/strict";
import test from "node:test";
import { Pool } from "pg";
import { PostgresStateBackend } from "../src/state-backend.js";

const url=(process.env.HAKIM_TEST_POSTGRES_URL??"").trim();
test("postgres backend shared-store contract",{skip:!url},async()=>{
  const poolA=new Pool({connectionString:url,max:4});
  const poolB=new Pool({connectionString:url,max:4});
  const a=new PostgresStateBackend(poolA), b=new PostgresStateBackend(poolB);
  const root="/hakim-contract-"+Date.now()+"-"+Math.random().toString(16).slice(2);
  const exclusive=root+"/exclusive.json";
  await a.initDir(root);
  await Promise.all([a.writeTextAtomic(root+"/visible.json","one"),b.initDir(root)]);
  assert.equal(await b.readText(root+"/visible.json"),"one");
  const settled=await Promise.allSettled([
    a.writeTextExclusive(exclusive,"a"),
    b.writeTextExclusive(exclusive,"b")
  ]);
  assert.equal(settled.filter(x=>x.status==="fulfilled").length,1,"exactly one exclusive writer must win");
  assert.equal(settled.filter(x=>x.status==="rejected").length,1,"exactly one exclusive writer must lose");
  const names=await b.list(root);
  assert.deepEqual(names.sort(),["exclusive.json","visible.json"]);
  await b.writeTextAtomic(root+"/visible.json","two");
  assert.equal(await a.readText(root+"/visible.json"),"two");
  await a.remove(root+"/visible.json");
  await assert.rejects(()=>b.readText(root+"/visible.json"),(e:any)=>e?.code==="ENOENT");
  await b.remove(exclusive);
  await poolA.end(); await poolB.end();
});
