import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { DirectRelayStore } from "../src/direct-relay.js";
import { createDeviceCredential,encryptCarrier,encryptResult,makeEnvelope } from "../src/protocol.js";

async function withStore(fn:(store:DirectRelayStore)=>Promise<void>){
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-direct-relay-test-"));
  try{
    const store=new DirectRelayStore(dir);
    await store.init();
    await fn(store);
  }finally{
    await fs.rm(dir,{recursive:true,force:true});
  }
}

test("direct relay leases, acks and removes one encrypted command",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    await store.registerCredential(c);
    const envelope=makeEnvelope(c.relayKey,"status",{});
    const carrier=encryptCarrier(c.relayKey,envelope);
    await store.enqueueCommand(c,envelope.request_id,carrier,envelope.expires_at_ms);

    const leased=await store.leaseCommand(c.topic,c.relayKey,0);
    assert.equal(leased?.request_id,envelope.request_id);
    assert.equal(leased?.carrier,carrier);
    assert.equal(await store.leaseCommand(c.topic,c.relayKey,0),null);

    await store.ackCommand(c.topic,c.relayKey,envelope.request_id);
    assert.equal(await store.leaseCommand(c.topic,c.relayKey,0),null);
  });
});

test("direct relay persists encrypted results and deletes them after consumption",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    await store.registerCredential(c);
    const carrier=encryptResult(c.relayKey,{request_id:"chatgpt-12345678",status:"ok",result:{ok:true}});
    const id=await store.pushResult(c.resultTopic,c.relayKey,carrier);
    const results=await store.listResults(c);
    assert.equal(results.length,1);
    assert.equal(results[0]?.carrier,carrier);
    await store.deleteResult(c,id);
    assert.deepEqual(await store.listResults(c),[]);
  });
});

test("direct relay fails closed for a wrong relay key",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    await store.registerCredential(c);
    await assert.rejects(()=>store.leaseCommand(c.topic,"A".repeat(48),0),/relay_auth_failed/);
    const carrier=encryptResult(c.relayKey,{request_id:"chatgpt-12345678",status:"ok"});
    await assert.rejects(()=>store.pushResult(c.resultTopic,"B".repeat(48),carrier),/relay_auth_failed/);
  });
});

test("direct relay refuses a topic rebind to a different credential",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    await store.registerCredential(c);
    await assert.rejects(()=>store.registerCredential({...c,relayKey:"C".repeat(48)}),/relay_binding_conflict/);
  });
});


test("legacy paired device can claim its missing command binding once",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    assert.equal(await store.leaseCommand(c.topic,c.relayKey,0),null);
    await assert.rejects(
      ()=>store.leaseCommand(c.topic,"D".repeat(64),0),
      /relay_auth_failed/
    );
  });
});

test("lazy migration refuses non-Hakim topic namespaces",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    const foreign="foreign_topic_"+c.topic.slice(-24);
    await assert.rejects(
      ()=>store.leaseCommand(foreign,c.relayKey,0),
      /relay_auth_failed/
    );
  });
});

test("legacy result binding can self-migrate but remains key-pinned",async()=>{
  await withStore(async store=>{
    const c=createDeviceCredential();
    const carrier=encryptResult(c.relayKey,{request_id:"chatgpt-12345678",status:"ok"});
    await store.pushResult(c.resultTopic,c.relayKey,carrier);
    await assert.rejects(
      ()=>store.pushResult(c.resultTopic,"E".repeat(64),carrier),
      /relay_auth_failed/
    );
  });
});
