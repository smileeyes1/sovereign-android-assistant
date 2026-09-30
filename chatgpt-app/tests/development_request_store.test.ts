import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { DevelopmentRequestStore } from "../src/development-request-store.js";
import { encryptResult } from "../src/protocol.js";

async function withDir(fn:(dir:string)=>Promise<void>){
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-development-intake-test-"));
  try{await fn(dir);}finally{await fs.rm(dir,{recursive:true,force:true});}
}

const key="A".repeat(48);
const topic="hakim_result_"+"B".repeat(24);

function request(overrides:Record<string,unknown>={}){
  return {
    schema_version:1,
    control_version:"HAKIM-DEVELOPMENT-CONTROL-2026-09-30-v1",
    request_id:"dev-mg123abc-"+"a".repeat(12),
    requested_at_ms:Date.now(),
    package:"ps.hakim.stable",
    current_version_code:20316,
    trigger:"self_check_failed",
    severity:"critical",
    fingerprint:"b".repeat(64),
    evidence_summary:"this free text must not be persisted",
    goal:"this free text must not be persisted",
    constraints:{
      source_mutation_on_device:false,
      github_secret_on_device:false,
      isolated_branch_required:true,
      ci_required:true,
      regression_test_required:true,
      verified_baseline_required:true,
      field_install_requires_separate_authorization:true,
      same_artifact_field_evidence_required:true,
      no_permission_expansion:true
    },
    ...overrides
  };
}

function carrier(devRequest:Record<string,unknown>){
  return encryptResult(key,{
    request_id:"health-test",
    status:"health",
    result:{development_request:devRequest}
  });
}

test("development intake stores only bounded structured evidence",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    await store.init();
    const saved=await store.capture(topic,key,carrier(request()));
    assert.equal(saved?.trigger,"self_check_failed");
    assert.equal(saved?.severity,"critical");
    assert.equal(saved?.current_version_code,20316);
    assert.equal(saved?.constraints.source_mutation_on_device,false);
    assert.equal(saved?.constraints.github_secret_on_device,false);

    const rows=await store.list();
    assert.equal(rows.length,1);
    const files=await fs.readdir(path.join(dir,"development-intake-v2"));
    const raw=await fs.readFile(path.join(dir,"development-intake-v2",files[0]!),"utf8");
    assert.equal(raw.includes("this free text must not be persisted"),false);
    assert.equal(raw.includes(key),false);
    assert.equal(raw.includes(topic),false);
  });
});

test("development intake rejects weakened isolation constraints",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    const weakened=request({
      constraints:{
        source_mutation_on_device:true,
        github_secret_on_device:false,
        isolated_branch_required:true,
        ci_required:true,
        regression_test_required:true,
        verified_baseline_required:true,
        field_install_requires_separate_authorization:true,
        same_artifact_field_evidence_required:true,
        no_permission_expansion:true
      }
    });
    await assert.rejects(()=>store.capture(topic,key,carrier(weakened)),/development_constraints_weakened/);
    assert.equal((await store.list()).length,0);
  });
});

test("non-health results never become development requests",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    const other=encryptResult(key,{status:"ok",result:{development_request:request()}});
    assert.equal(await store.capture(topic,key,other),null);
    assert.equal((await store.list()).length,0);
  });
});


test("development request lease is resumable and completion is owner-bound",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    await store.init();
    await store.capture(topic,key,carrier(request({request_id:"dev-mg123def-"+"c".repeat(12)})));
    const first=await store.claimNext("gh:run-12345678",60_000);
    assert.equal(first?.state,"leased");
    assert.equal(first?.request_id,"dev-mg123def-"+"c".repeat(12));
    assert.equal(await store.claimNext("gh:run-87654321",60_000),null);

    await assert.rejects(
      ()=>store.complete(first!.request_id,"gh:run-87654321","success"),
      /development_lease_owner_mismatch/
    );

    const done=await store.complete(first!.request_id,"gh:run-12345678","success",{
      result_sha:"d".repeat(40),
      pr_number:321
    });
    assert.equal(done.state,"completed");
    assert.equal(done.outcome,"success");
    assert.equal(done.result_sha,"d".repeat(40));
    assert.equal(done.pr_number,321);
  });
});


test("expired development lease returns to the queue",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    await store.init();
    const id="dev-mg123ghi-"+"e".repeat(12);
    await store.capture(topic,key,carrier(request({request_id:id,fingerprint:"f".repeat(64)})));
    const first=await store.claimNext("gh:run-11111111",60_000);
    assert.equal(first?.request_id,id);
    const file=path.join(dir,"development-intake-v2",id+".json");
    const raw=JSON.parse(await fs.readFile(file,"utf8"));
    raw.lease_expires_at_ms=Date.now()-1;
    await fs.writeFile(file,JSON.stringify(raw),"utf8");
    const reclaimed=await store.claimNext("gh:run-22222222",60_000);
    assert.equal(reclaimed?.request_id,id);
    assert.equal(reclaimed?.lease_owner,"gh:run-22222222");
  });
});


test("deferred development request waits then returns to the queue",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    await store.init();
    const id="dev-mg123jkl-"+"1".repeat(12);
    await store.capture(topic,key,carrier(request({request_id:id,fingerprint:"2".repeat(64)})));
    const first=await store.claimNext("gh:run-33333333",60_000);
    assert.equal(first?.request_id,id);
    assert.equal(first?.attempt_count,1);

    const deferred=await store.defer(
      id,
      "gh:run-33333333",
      "free_engine_unavailable",
      5*60_000
    );
    assert.equal(deferred.state,"pending");
    assert.equal(deferred.last_wait_reason,"free_engine_unavailable");
    assert.ok((deferred.next_attempt_at_ms??0)>Date.now());
    assert.equal(await store.claimNext("gh:run-44444444",60_000),null);

    const file=path.join(dir,"development-intake-v2",id+".json");
    const raw=JSON.parse(await fs.readFile(file,"utf8"));
    raw.next_attempt_at_ms=Date.now()-1;
    await fs.writeFile(file,JSON.stringify(raw),"utf8");

    const second=await store.claimNext("gh:run-44444444",60_000);
    assert.equal(second?.request_id,id);
    assert.equal(second?.attempt_count,2);
  });
});

test("defer is owner-bound and delay-bounded",async()=>{
  await withDir(async dir=>{
    const store=new DevelopmentRequestStore(dir);
    await store.init();
    const id="dev-mg123mno-"+"3".repeat(12);
    await store.capture(topic,key,carrier(request({request_id:id,fingerprint:"4".repeat(64)})));
    await store.claimNext("gh:run-55555555",60_000);
    await assert.rejects(
      ()=>store.defer(id,"gh:run-66666666","safe_patch_not_found",5*60_000),
      /development_lease_owner_mismatch/
    );
    await assert.rejects(
      ()=>store.defer(id,"gh:run-55555555","safe_patch_not_found",60_000),
      /development_defer_delay_invalid/
    );
  });
});
