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

test("continuity V2 survives a new store instance with work checkpoint",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const a=new ContinuityStore(dir);
    await a.init();
    const saved=await a.saveCheckpoint(c,{
      goal_label:"إكمال تقرير المدرسة",
      stage:"التحقق",
      last_verified:"تم إنشاء المسودة واختبارها",
      next_step:"فحص النسخة النهائية",
      blocker:"",
      status:"active"
    });
    await a.recordRequested(c,"chatgpt-continuity-1234","browser_read");

    const b=new ContinuityStore(dir);
    await b.init();
    const state=await b.state(c);
    assert.equal(state.durable,true);
    assert.equal(state.continuity_version,"HAKIM_CONTINUITY_V2");
    assert.equal(state.work?.goal_id,saved.goal_id);
    assert.equal(state.work?.goal_label,"إكمال تقرير المدرسة");
    assert.equal(state.work?.stage,"التحقق");
    assert.equal(state.pending_count,1);
    assert.equal(state.operations[0]?.operation_token,"chatgpt-continuity-1234");
    assert.equal(state.operations[0]?.resumable,true);
  });
});

test("continuity journal records completion without device content or relay secrets",async()=>{
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

test("checkpoint update preserves goal identity and replaces only checkpoint metadata",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    const first=await store.saveCheckpoint(c,{
      goal_label:"إنهاء المهمة",
      stage:"تنفيذ",
      status:"active"
    });
    const second=await store.saveCheckpoint(c,{
      goal_id:first.goal_id,
      goal_label:"إنهاء المهمة",
      stage:"انتظار تحقق",
      last_verified:"الأمر سُلّم",
      next_step:"قراءة النتيجة بنفس الرمز",
      blocker:"بانتظار النتيجة",
      status:"waiting"
    });
    assert.equal(second.goal_id,first.goal_id);
    const state=await store.state(c);
    assert.equal(state.work?.status,"waiting");
    assert.equal(state.work?.next_step,"قراءة النتيجة بنفس الرمز");
  });
});

test("checkpoint rejects obvious credential material",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    await assert.rejects(
      ()=>store.saveCheckpoint(c,{
        goal_label:"اختبار",
        stage:"تنفيذ",
        last_verified:"access_token=should-never-be-stored",
        status:"active"
      }),
      /checkpoint_sensitive_content_rejected/
    );
  });
});

test("concurrent writes are serialized without losing operation tokens",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    await Promise.all(Array.from({length:24},(_,i)=>
      store.recordRequested(c,"chatgpt-concurrent-"+String(i).padStart(4,"0"),"status")
    ));
    const state=await store.state(c);
    assert.equal(state.pending_count,12);
    assert.equal(new Set(state.operations.map(x=>x.operation_token)).size,12);
  });
});

test("corrupt journal fails closed to empty state instead of replaying guesses",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    await store.recordRequested(c,"chatgpt-corrupt-1234","action");
    const files=await fs.readdir(dir);
    await fs.writeFile(path.join(dir,files[0]!),"{not-json","utf8");

    const restarted=new ContinuityStore(dir);
    await restarted.init();
    const state=await restarted.state(c);
    assert.equal(state.pending_count,0);
    assert.deepEqual(state.operations,[]);
    assert.equal(state.work,null);
  });
});


test("paired-device topic view returns the same bounded checkpoint",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new ContinuityStore(dir);
    await store.init();
    const saved=await store.saveCheckpoint(c,{
      goal_label:"متابعة المهمة",
      stage:"تحقق",
      last_verified:"تمت خطوة مثبتة",
      next_step:"متابعة النتيجة",
      status:"active"
    });
    const state=await store.stateForTopic(c.topic);
    assert.equal(state.work?.goal_id,saved.goal_id);
    assert.equal(state.work?.goal_label,"متابعة المهمة");
    assert.equal(state.pending_count,0);
  });
});
