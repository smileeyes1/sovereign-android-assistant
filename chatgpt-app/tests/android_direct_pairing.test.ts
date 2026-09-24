import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import {AndroidPairStore,androidPairHref} from "../src/android_pair.js";
import {LegacyAndroidStore,legacyPairCode,statelessLegacySession,newStatelessLegacyId,extractLatestSignedHealth} from "../src/legacy_android.js";

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


test("preview bypass is exact-name scoped and production remains fail-closed",async()=>{
  const source=await fs.readFile(new URL("../src/index.ts",import.meta.url),"utf8");
  assert.match(source,/process\.env\.RAILWAY_SERVICE_NAME === "hakim-android-pair-preview"/);
  assert.match(source,/if \(!androidPreviewMode\) requireProductionOAuthConfig\(process\.env\)/);
  assert.doesNotMatch(source,/androidPreviewMode\s*=\s*true/);
});


test("legacy Android pairing code matches the installed Hakim field contract",async()=>{
  const dir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-legacy-pair-"));
  try{
    const store=new LegacyAndroidStore(dir);
    await store.init();
    const s=await store.create(60_000);
    const code=legacyPairCode(s);
    const parts=code.split("|");
    assert.equal(parts.length,3);
    assert.match(parts[0]!,/^hakim_cmd_[A-Za-z0-9_-]{20,}$/);
    assert.match(parts[1]!,/^hakim_result_[A-Za-z0-9_-]{20,}$/);
    assert.match(parts[2]!,/^[0-9a-f]{64}$/);
  }finally{
    await fs.rm(dir,{recursive:true,force:true});
  }
});

test("legacy Android route is status-only after local pairing save",async()=>{
  const source=await fs.readFile(new URL("../src/index.ts",import.meta.url),"utf8");
  assert.match(source,/app\.get\("\/android\/legacy"/);
  assert.match(source,/رمز اقتران الجسر التنفيذي/);
  assert.match(source,/publishLegacyCommand\(session,\{type:"ping"\}\)/);
  assert.doesNotMatch(source,/publishLegacyCommand\(session,\{type:"open_url"/);
});


test("stateless Android pairing survives server restarts without stored state",()=>{
  const id=newStatelessLegacyId();
  const a=statelessLegacySession(id);
  const b=statelessLegacySession(id);
  assert.equal(a.commandTopic,b.commandTopic);
  assert.equal(a.resultTopic,b.resultTopic);
  assert.equal(a.authKey,b.authKey);
  assert.equal(legacyPairCode(a),legacyPairCode(b));
  assert.match(id,/^[A-Za-z0-9_-]{32,96}$/);
  assert.match(a.authKey,/^[0-9a-f]{64}$/);
});

test("different stateless Android ids derive isolated channels",()=>{
  const a=statelessLegacySession(newStatelessLegacyId());
  const b=statelessLegacySession(newStatelessLegacyId());
  assert.notEqual(a.commandTopic,b.commandTopic);
  assert.notEqual(a.resultTopic,b.resultTopic);
  assert.notEqual(a.authKey,b.authKey);
});

test("stable pairing routes derive credentials and do not depend on legacy store files",async()=>{
  const source=await fs.readFile(new URL("../src/index.ts",import.meta.url),"utf8");
  assert.match(source,/app\.get\("\/android\/legacy\/stable"/);
  const pageStart=source.indexOf('app.get("/android/legacy/stable/:id"');
  const statusStart=source.indexOf('app.get("/android/legacy/stable/:id/status"');
  assert.ok(pageStart>=0&&statusStart>pageStart);
  const pageBlock=source.slice(pageStart,statusStart);
  assert.match(pageBlock,/statelessLegacySession\(id\)/);
  assert.doesNotMatch(pageBlock,/legacyAndroidStore\.get/);
  const statusEnd=source.indexOf('app.get("/android/legacy"',statusStart+1);
  const statusBlock=source.slice(statusStart,statusEnd>statusStart?statusEnd:undefined);
  assert.match(statusBlock,/statelessLegacySession\(String\(req\.params\.id/);
  assert.doesNotMatch(statusBlock,/legacyAndroidStore\.get/);
});


test("ntfy transport rotates IPv4 routes without sticky keep-alive",async()=>{
  const source=await fs.readFile(new URL("../src/legacy_android.ts",import.meta.url),"utf8");
  assert.match(source,/dns\.resolve4\(u\.hostname\)/);
  assert.match(source,/agent:false/);
  assert.match(source,/ntfy_all_routes_failed/);
  assert.doesNotMatch(source,/keepAlive:true/);
  assert.match(source,/payloadObj\.request_id=requestId/);
});


test("signed recent connected health is accepted as online evidence",()=>{
  const s=statelessLegacySession("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
  const now=1_790_241_500_000;
  const data=JSON.stringify({
    request_id:"health-"+now,
    status:"health",
    time:now,
    package:"ps.hakim.stable",
    version_code:20106,
    service_running:true,
    service_connected:true,
    recovery:{state:"healthy"}
  });
  const requestId="health-"+now;
  const sig=crypto.createHmac("sha256",Buffer.from(s.authKey,"hex"))
    .update(requestId+"\n1\n1\n"+data,"utf8").digest("hex");
  const body=JSON.stringify({message:JSON.stringify({request_id:requestId,chunk:1,total:1,data,sig})});
  const health=extractLatestSignedHealth(s,body,now,120_000);
  assert.equal(health?.service_connected,true);
  assert.equal(health?.status,"health");
});

test("health fallback rejects tampered, stale, or disconnected evidence",()=>{
  const s=statelessLegacySession("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
  const now=1_790_241_500_000;
  const make=(time:number,connected:boolean,tamper=false)=>{
    const requestId="health-"+time;
    const data=JSON.stringify({request_id:requestId,status:"health",time,service_connected:connected});
    let sig=crypto.createHmac("sha256",Buffer.from(s.authKey,"hex"))
      .update(requestId+"\n1\n1\n"+data,"utf8").digest("hex");
    if(tamper) sig="0".repeat(64);
    return JSON.stringify({message:JSON.stringify({request_id:requestId,chunk:1,total:1,data,sig})});
  };
  assert.equal(extractLatestSignedHealth(s,make(now,true,true),now,120_000),null);
  assert.equal(extractLatestSignedHealth(s,make(now-121_000,true),now,120_000),null);
  assert.equal(extractLatestSignedHealth(s,make(now,false),now,120_000),null);
});

test("stable status route falls back only to signed recent health",async()=>{
  const source=await fs.readFile(new URL("../src/index.ts",import.meta.url),"utf8");
  assert.match(source,/pollLegacyHealth\(session,120_000\)/);
  assert.match(source,/evidence:"signed_recent_health"/);
  assert.match(source,/evidence:"signed_ping"/);
});
