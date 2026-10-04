import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { ControlPlaneStore,ingressTokenMatches } from "../src/control-plane.js";

async function withStore(fn:(store:ControlPlaneStore,dir:string)=>Promise<void>){
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-control-plane-test-"));
  try{
    const store=new ControlPlaneStore(dir,"test-control-plane-secret-1234567890");
    await store.init();
    await fn(store,dir);
  }finally{await fs.rm(dir,{recursive:true,force:true});}
}

test("control plane deduplicates the same intent by idempotency key",async()=>{
  await withStore(async store=>{
    const a=await store.beginOperation({channel:"make",intent:"status",idempotency_key:"idem-status-0001",args:{}});
    const b=await store.beginOperation({channel:"make",intent:"status",idempotency_key:"idem-status-0001",args:{}});
    assert.equal(a.reused,false);
    assert.equal(b.reused,true);
    assert.equal(a.operation.operation_id,b.operation.operation_id);
  });
});

test("control plane rejects idempotency key reuse with a different payload",async()=>{
  await withStore(async store=>{
    await store.beginOperation({channel:"make",intent:"navigate",idempotency_key:"idem-nav-000001",args:{kind:"home"}});
    await assert.rejects(
      ()=>store.beginOperation({channel:"make",intent:"navigate",idempotency_key:"idem-nav-000001",args:{kind:"back"}}),
      /control_idempotency_conflict/
    );
  });
});

test("public state stores hashes and bounded evidence, not command arguments or secrets",async()=>{
  await withStore(async (store,dir)=>{
    const secretTarget="https://example.test/private-user-target";
    const op=await store.beginOperation({
      channel:"make",intent:"launch",idempotency_key:"idem-launch-0001",args:{url:secretTarget}
    });
    await store.addEvidence(op.operation.operation_id,{type:"preflight",verdict:"pass",summary:"runtime ready"});
    const state=await store.state();
    assert.equal(state.operations[0]?.intent,"launch");
    const raw=await fs.readFile(path.join(dir,"operations",op.operation.operation_id+".json"),"utf8");
    assert.equal(raw.includes(secretTarget),false);
    assert.equal(raw.includes("password"),false);
  });
});

test("capability registry keeps only bounded operational metadata",async()=>{
  await withStore(async store=>{
    await store.setCapability({
      capability:"make.ingress",state:"available",channel:"make",cost_class:"included",evidence:"authenticated typed ingress"
    });
    const state=await store.state();
    assert.equal(state.capabilities[0]?.capability,"make.ingress");
    assert.equal(state.capabilities[0]?.state,"available");
  });
});

test("ingress token comparison fails closed",()=>{
  const token="A".repeat(48);
  assert.equal(ingressTokenMatches(token,token),true);
  assert.equal(ingressTokenMatches("B".repeat(48),token),false);
  assert.equal(ingressTokenMatches(undefined,token),false);
  assert.equal(ingressTokenMatches(token,undefined),false);
});

test("control plane rejects shell as an intent before anything reaches the phone",async()=>{
  await withStore(async store=>{
    await assert.rejects(
      ()=>store.beginOperation({channel:"make",intent:"shell" as any,idempotency_key:"idem-shell-0001",args:{command:"rm -rf /"}}),
      /control_intent_invalid/
    );
  });
});

test("operation evidence rejects credential-shaped text",async()=>{
  await withStore(async store=>{
    const op=await store.beginOperation({channel:"make",intent:"status",idempotency_key:"idem-evidence-01"});
    await assert.rejects(
      ()=>store.addEvidence(op.operation.operation_id,{type:"check",verdict:"info",summary:"access_token=do-not-store"}),
      /control_plane_sensitive_summary_rejected/
    );
  });
});
