import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import type {DeviceCredential} from "./protocol.js";
import {randomSecret} from "./protocol.js";

type ObservationKind="command"|"result";

type PendingObservation={
  version:"HAKIM_DEVICE_OBSERVATION_V1";
  key_hash:string;
  relay_key:string;
  command_topic?:string;
  result_topic?:string;
  version_code?:number;
  updated_at_ms:number;
};

export type BoundDevice={
  version:"HAKIM_DEVICE_BINDING_V1";
  binding_id:string;
  credential:DeviceCredential;
  source:"observed_live_channel"|"approved_oauth_pairing";
  bound_at_ms:number;
  updated_at_ms:number;
  version_code:number|null;
};

const TOPIC=/^[A-Za-z0-9_-]{20,120}$/;
const KEY=/^[A-Za-z0-9_-]{40,100}$/;
const BINDING_ID=/^hb1_[A-Za-z0-9_-]{32,80}$/;

function sha(value:string){
  return crypto.createHash("sha256").update(value,"utf8").digest("hex");
}
function safeEqual(a:string,b:string){
  const left=Buffer.from(a,"utf8");
  const right=Buffer.from(b,"utf8");
  return left.length===right.length&&crypto.timingSafeEqual(left,right);
}

export class DeviceBindingStore{
  private readonly root:string;
  private readonly currentFile:string;
  private readonly key:Buffer;
  private readonly locks=new Map<string,Promise<void>>();

  constructor(dataDir:string,oauthSecret:string){
    if(oauthSecret.length<43) throw new Error("oauth_secret_too_short");
    this.root=path.join(dataDir,"device-binding-v1");
    this.currentFile=path.join(this.root,"current.sealed");
    this.key=crypto.createHash("sha256")
      .update("HAKIM-DEVICE-BINDING-STORE-v1\0"+oauthSecret,"utf8")
      .digest();
  }

  async init(){
    await fs.mkdir(path.join(this.root,"observations"),{recursive:true,mode:0o700});
  }

  private seal(value:unknown){
    const nonce=crypto.randomBytes(12);
    const cipher=crypto.createCipheriv("aes-256-gcm",this.key,nonce);
    cipher.setAAD(Buffer.from("HAKIM-DEVICE-BINDING-STORE-v1","utf8"));
    const ciphertext=Buffer.concat([
      cipher.update(Buffer.from(JSON.stringify(value),"utf8")),
      cipher.final()
    ]);
    const tag=cipher.getAuthTag();
    return Buffer.concat([nonce,ciphertext,tag]).toString("base64url");
  }

  private open<T>(raw:string):T{
    const packed=Buffer.from(raw.trim(),"base64url");
    if(packed.length<28) throw new Error("binding_store_corrupt");
    const nonce=packed.subarray(0,12);
    const tag=packed.subarray(packed.length-16);
    const ciphertext=packed.subarray(12,packed.length-16);
    const decipher=crypto.createDecipheriv("aes-256-gcm",this.key,nonce);
    decipher.setAAD(Buffer.from("HAKIM-DEVICE-BINDING-STORE-v1","utf8"));
    decipher.setAuthTag(tag);
    return JSON.parse(Buffer.concat([
      decipher.update(ciphertext),decipher.final()
    ]).toString("utf8")) as T;
  }

  private async atomicSealed(file:string,value:unknown){
    await fs.mkdir(path.dirname(file),{recursive:true,mode:0o700});
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,this.seal(value),{encoding:"utf8",mode:0o600});
    await fs.rename(tmp,file);
  }

  private validateCredential(c:DeviceCredential){
    if(c.v!==2||!TOPIC.test(c.topic)||!TOPIC.test(c.resultTopic)||!KEY.test(c.relayKey)){
      throw new Error("invalid_device_binding_credential");
    }
  }

  private normalize(raw:BoundDevice):BoundDevice{
    this.validateCredential(raw.credential);
    if(raw.version!=="HAKIM_DEVICE_BINDING_V1"||!BINDING_ID.test(raw.binding_id)){
      throw new Error("invalid_device_binding_record");
    }
    return {
      ...raw,
      version_code:Number.isInteger(raw.version_code)&&Number(raw.version_code)>0?Number(raw.version_code):null
    };
  }

  async current():Promise<BoundDevice|null>{
    try{
      return this.normalize(this.open<BoundDevice>(await fs.readFile(this.currentFile,"utf8")));
    }catch(e){
      if((e as NodeJS.ErrnoException).code==="ENOENT") return null;
      if(e instanceof Error&&[
        "binding_store_corrupt","invalid_device_binding_credential","invalid_device_binding_record"
      ].includes(e.message)) throw e;
      return null;
    }
  }

  private observationFile(keyHash:string){
    return path.join(this.root,"observations",keyHash+".sealed");
  }

  private async withLock<T>(key:string,fn:()=>Promise<T>):Promise<T>{
    const previous=this.locks.get(key)??Promise.resolve();
    let release!:()=>void;
    const current=new Promise<void>(resolve=>{release=resolve;});
    const gate=previous.catch(()=>{}).then(()=>current);
    this.locks.set(key,gate);
    await previous.catch(()=>{});
    try{return await fn();}
    finally{
      release();
      if(this.locks.get(key)===gate) this.locks.delete(key);
    }
  }

  async observe(kind:ObservationKind,topic:string,relayKey:string):Promise<BoundDevice|null>{
    if(!TOPIC.test(topic)||!KEY.test(relayKey)) throw new Error("invalid_device_observation");
    const keyHash=sha(relayKey);
    return this.withLock(keyHash,async()=>{
      const current=await this.current();
      if(current&&safeEqual(current.credential.relayKey,relayKey)){
        const expected=kind==="command"?current.credential.topic:current.credential.resultTopic;
        if(!safeEqual(expected,topic)) throw new Error("device_binding_topic_conflict");
        return current;
      }

      const file=this.observationFile(keyHash);
      let pending:PendingObservation={
        version:"HAKIM_DEVICE_OBSERVATION_V1",
        key_hash:keyHash,
        relay_key:relayKey,
        updated_at_ms:Date.now()
      };
      try{
        const loaded=this.open<PendingObservation>(await fs.readFile(file,"utf8"));
        if(loaded.version!=="HAKIM_DEVICE_OBSERVATION_V1"||
          loaded.key_hash!==keyHash||!safeEqual(loaded.relay_key,relayKey)){
          throw new Error("device_observation_conflict");
        }
        pending=loaded;
      }catch(e){
        if((e as NodeJS.ErrnoException).code!=="ENOENT") throw e;
      }
      if(kind==="command") pending.command_topic=topic;
      else pending.result_topic=topic;
      pending.updated_at_ms=Date.now();
      await this.atomicSealed(file,pending);

      if(!pending.command_topic||!pending.result_topic) return current;
      if(current) return current; // Never replace an established binding from passive traffic.

      const credential:DeviceCredential={
        v:2,
        topic:pending.command_topic,
        resultTopic:pending.result_topic,
        relayKey,
        pairToken:randomSecret(32)
      };
      const bound:BoundDevice={
        version:"HAKIM_DEVICE_BINDING_V1",
        binding_id:"hb1_"+randomSecret(32),
        credential,
        source:"observed_live_channel",
        bound_at_ms:Date.now(),
        updated_at_ms:Date.now(),
        version_code:Number.isInteger(pending.version_code)&&Number(pending.version_code)>0
          ?Number(pending.version_code):null
      };
      await this.atomicSealed(this.currentFile,bound);
      await fs.unlink(file).catch(()=>{});
      return bound;
    });
  }

  async promoteApprovedPairing(c:DeviceCredential):Promise<{current:BoundDevice;previous:BoundDevice|null}>{
    this.validateCredential(c);
    return this.withLock("promote",async()=>{
      const previous=await this.current();
      const now=Date.now();
      const next:BoundDevice={
        version:"HAKIM_DEVICE_BINDING_V1",
        binding_id:previous?.binding_id??("hb1_"+randomSecret(32)),
        credential:c,
        source:"approved_oauth_pairing",
        bound_at_ms:previous?.bound_at_ms??now,
        updated_at_ms:now,
        version_code:null
      };
      await this.atomicSealed(this.currentFile,next);
      return {current:next,previous};
    });
  }

  async noteVersion(resultTopic:string,relayKey:string,versionCode:number){
    if(!TOPIC.test(resultTopic)||!KEY.test(relayKey)||!Number.isInteger(versionCode)||versionCode<=0) return;
    const keyHash=sha(relayKey);
    await this.withLock(keyHash,async()=>{
      const current=await this.current();
      if(current&&safeEqual(current.credential.resultTopic,resultTopic)&&
        safeEqual(current.credential.relayKey,relayKey)){
        if(current.version_code===versionCode) return;
        current.version_code=versionCode;
        current.updated_at_ms=Date.now();
        await this.atomicSealed(this.currentFile,current);
        return;
      }
      const file=this.observationFile(keyHash);
      try{
        const pending=this.open<PendingObservation>(await fs.readFile(file,"utf8"));
        if(pending.version!=="HAKIM_DEVICE_OBSERVATION_V1"||
          !safeEqual(pending.relay_key,relayKey)||
          (pending.result_topic&&pending.result_topic!==resultTopic)) return;
        pending.result_topic=resultTopic;
        pending.version_code=versionCode;
        pending.updated_at_ms=Date.now();
        await this.atomicSealed(file,pending);
      }catch{}
    });
  }

  async supportsClientAuthorization(){
    const current=await this.current();
    return !!current&&typeof current.version_code==="number"&&current.version_code>=20317;
  }

  sameCredential(a:DeviceCredential,b:DeviceCredential){
    return a.topic===b.topic&&a.resultTopic===b.resultTopic&&safeEqual(a.relayKey,b.relayKey);
  }

  publicStatus(current:BoundDevice|null){
    return {
      active:!!current,
      binding_id:current?.binding_id??null,
      source:current?.source??null,
      version_code:current?.version_code??null,
      multi_client_authorization_ready:
        !!current&&typeof current.version_code==="number"&&current.version_code>=20317,
      secrets_exposed:false
    };
  }
}
