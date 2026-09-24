import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {AndroidPairStore,androidPairHref} from "../src/android_pair.js";

test("android direct pairing store is private, temporary, and produces a Hakim deep link",async()=>{
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-android-pair-"));
  try{
    const store=new AndroidPairStore(dir);
    await store.init();
    const session=await store.create(60_000);
    assert.match(session.id,/^[A-Za-z0-9_-]{20,80}$/);
    assert.equal(session.credential.v,2);
    const href=androidPairHref(session);
    assert.match(href,/^hakim:\/\/pair\?/);
    assert.match(href,/relay_topic=/);
    assert.match(href,/relay_result_topic=/);
    assert.match(href,/relay_key=/);
    const loaded=await store.get(session.id);
    assert.equal(loaded.id,session.id);
    assert.equal(loaded.credential.relayKey,session.credential.relayKey);
  }finally{
    await fs.rm(dir,{recursive:true,force:true});
  }
});

test("android bootstrap route remains read-only",async()=>{
  const source=await fs.readFile(new URL("../src/index.ts",import.meta.url),"utf8");
  assert.match(source,/app\.get\("\/android"/);
  assert.match(source,/app\.get\("\/android\/session\/:id\/status"/);
  assert.match(source,/publishCommand\(session\.credential,"status",\{\}\)/);
  assert.doesNotMatch(source,/android\/session\/.*open_target/);
  assert.doesNotMatch(source,/android\/session\/.*navigate_device/);
  assert.doesNotMatch(source,/android\/session\/.*perform_ui_action/);
});
