import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {DeviceBindingStore} from "../src/device-binding-store.js";
import {createDeviceCredential} from "../src/protocol.js";

async function withDir(fn:(dir:string)=>Promise<void>){
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-binding-test-"));
  try{await fn(dir);}finally{await fs.rm(dir,{recursive:true,force:true});}
}

const secret="x".repeat(64);

test("live command and result observations adopt one encrypted canonical binding",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new DeviceBindingStore(dir,secret);
    await store.init();

    assert.equal(await store.observe("command",c.topic,c.relayKey),null);
    await store.noteVersion(c.resultTopic,c.relayKey,20317);
    const bound=await store.observe("result",c.resultTopic,c.relayKey);
    assert.ok(bound);
    assert.equal(bound?.credential.topic,c.topic);
    assert.equal(bound?.credential.resultTopic,c.resultTopic);
    assert.equal(bound?.version_code,20317);
    assert.match(bound?.binding_id??"",/^hb1_/);
    assert.equal(await store.supportsClientAuthorization(),true);

    const raw=await fs.readFile(path.join(dir,"device-binding-v1","current.sealed"),"utf8");
    assert.equal(raw.includes(c.relayKey),false);
    assert.equal(raw.includes(c.topic),false);
    assert.equal(raw.includes(c.resultTopic),false);
  });
});

test("passive traffic with another credential cannot replace the established device",async()=>{
  await withDir(async dir=>{
    const first=createDeviceCredential();
    const other=createDeviceCredential();
    const store=new DeviceBindingStore(dir,secret);
    await store.init();
    await store.observe("command",first.topic,first.relayKey);
    const initial=await store.observe("result",first.resultTopic,first.relayKey);
    assert.ok(initial);

    await store.observe("command",other.topic,other.relayKey);
    await store.observe("result",other.resultTopic,other.relayKey);
    const current=await store.current();
    assert.equal(current?.binding_id,initial?.binding_id);
    assert.equal(current?.credential.topic,first.topic);
  });
});

test("approved pairing rotation preserves broker identity but changes credential",async()=>{
  await withDir(async dir=>{
    const first=createDeviceCredential();
    const next=createDeviceCredential();
    const store=new DeviceBindingStore(dir,secret);
    await store.init();
    await store.observe("command",first.topic,first.relayKey);
    const initial=await store.observe("result",first.resultTopic,first.relayKey);
    assert.ok(initial);

    const promoted=await store.promoteApprovedPairing(next);
    assert.equal(promoted.previous?.credential.topic,first.topic);
    assert.equal(promoted.current.binding_id,initial?.binding_id);
    assert.equal(promoted.current.credential.topic,next.topic);
    assert.equal(promoted.current.source,"approved_oauth_pairing");
  });
});

test("20316 remains single-client gated and 20317 enables approval capability",async()=>{
  await withDir(async dir=>{
    const c=createDeviceCredential();
    const store=new DeviceBindingStore(dir,secret);
    await store.init();
    await store.observe("command",c.topic,c.relayKey);
    await store.noteVersion(c.resultTopic,c.relayKey,20316);
    await store.observe("result",c.resultTopic,c.relayKey);
    assert.equal(await store.supportsClientAuthorization(),false);
    await store.noteVersion(c.resultTopic,c.relayKey,20317);
    assert.equal(await store.supportsClientAuthorization(),true);
  });
});
