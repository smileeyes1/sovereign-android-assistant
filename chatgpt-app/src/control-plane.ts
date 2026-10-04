import crypto from "node:crypto";
import os from "node:os";
import path from "node:path";
import type { DeviceCredential,HakimOp } from "./protocol.js";
import { createDeviceCredential,decryptResult,pairingUrl } from "./protocol.js";
import { directRelayStore } from "./direct-relay.js";
import { pollPairAck } from "./relay.js";
import { hakimStateBackend as stateBackend } from "./state-backend.js";

export const CONTROL_PLANE_VERSION="HAKIM_CONTROL_PLANE_V1";

export const CONTROL_CHANNELS=["make","github","mcp","automation","web","device"] as const;
export type ControlChannel=typeof CONTROL_CHANNELS[number];
export const CONTROL_INTENTS=["pair_start","pair_check","state","status","launch","navigate"] as const;
export type ControlIntent=typeof CONTROL_INTENTS[number];
export const CONTROL_STATUSES=["requested","pending","running","verified","failed","rejected","expired"] as const;
export type ControlStatus=typeof CONTROL_STATUSES[number];
export const CAPABILITY_STATES=["available","degraded","blocked","unavailable"] as const;
export type CapabilityState=typeof CAPABILITY_STATES[number];
export const COST_CLASSES=["included","free","cost_blocked","unknown"] as const;
export type CostClass=typeof COST_CLASSES[number];

type EvidenceRecord={
  type:string;
  verdict:"pass"|"fail"|"info";
  at_ms:number;
  summary:string;
};
type OperationRecord={
  version:"HAKIM_CONTROL_PLANE_V1";
  operation_id:string;
  idempotency_hash:string;
  payload_hash:string;
  channel:ControlChannel;
  intent:ControlIntent;
  status:ControlStatus;
  created_at_ms:number;
  updated_at_ms:number;
  expires_at_ms:number;
  operation_token?:string;
  evidence:EvidenceRecord[];
};
type CapabilityRecord={
  capability:string;
  state:CapabilityState;
  channel:ControlChannel;
  cost_class:CostClass;
  last_verified_at_ms:number;
  evidence:string;
};
type PairingSecret={credential:DeviceCredential;nonce:string};
type PairingRecord={
  version:"HAKIM_CONTROL_PAIR_V1";
  sealed:string;
  paired:boolean;
  created_at_ms:number;
  updated_at_ms:number;
  expires_at_ms:number;
};

const IDEMPOTENCY=/^[A-Za-z0-9._:-]{8,128}$/;
const CAPABILITY=/^[a-z0-9._:-]{2,80}$/;
const SAFE_TEXT=/^[\p{L}\p{N} ._:\/-]{0,180}$/u;
const SECRET_HINT=/(?:bearer\s+|password|passwd|secret|api[_ -]?key|access[_ -]?token|refresh[_ -]?token|relay[_ -]?key)/i;
const MAX_EVIDENCE=12;
const MAX_OP_AGE_MS=30*24*60*60_000;

function hash(value:string){
  return crypto.createHash("sha256").update(value,"utf8").digest("hex");
}
function stable(value:unknown):string{
  if(value===null||typeof value!=="object") return JSON.stringify(value);
  if(Array.isArray(value)) return "["+value.map(stable).join(",")+"]";
  const obj=value as Record<string,unknown>;
  return "{"+Object.keys(obj).sort().map(k=>JSON.stringify(k)+":"+stable(obj[k])).join(",")+"}";
}
function safeSummary(raw:string){
  const value=raw.replace(/[\u0000-\u001F\u007F]/g," ").replace(/\s+/g," ").trim().slice(0,180);
  if(SECRET_HINT.test(value)||!SAFE_TEXT.test(value)) throw new Error("control_plane_sensitive_summary_rejected");
  return value;
}
function safeEqual(a:string,b:string){
  const aa=Buffer.from(a,"utf8"),bb=Buffer.from(b,"utf8");
  return aa.length===bb.length&&crypto.timingSafeEqual(aa,bb);
}
function keyFrom(secret:string){
  if(secret.length<24) throw new Error("control_plane_secret_too_short");
  return crypto.createHash("sha256").update("HAKIM-CONTROL-PLANE-AT-REST-v1\0"+secret,"utf8").digest();
}
function seal(secret:string,value:unknown){
  const nonce=crypto.randomBytes(12);
  const cipher=crypto.createCipheriv("aes-256-gcm",keyFrom(secret),nonce);
  cipher.setAAD(Buffer.from("HAKIM-CONTROL-PAIR-v1","utf8"));
  const ciphertext=Buffer.concat([cipher.update(Buffer.from(JSON.stringify(value),"utf8")),cipher.final()]);
  return Buffer.concat([nonce,ciphertext,cipher.getAuthTag()]).toString("base64url");
}
function open<T>(secret:string,packed:string):T{
  const raw=Buffer.from(packed,"base64url");
  if(raw.length<29) throw new Error("control_plane_pairing_corrupt");
  const nonce=raw.subarray(0,12), tag=raw.subarray(raw.length-16), ciphertext=raw.subarray(12,raw.length-16);
  const decipher=crypto.createDecipheriv("aes-256-gcm",keyFrom(secret),nonce);
  decipher.setAAD(Buffer.from("HAKIM-CONTROL-PAIR-v1","utf8"));
  decipher.setAuthTag(tag);
  return JSON.parse(Buffer.concat([decipher.update(ciphertext),decipher.final()]).toString("utf8")) as T;
}

export class ControlPlaneStore{
  private readonly operationsDir:string;
  private readonly idempotencyDir:string;
  private readonly tokensDir:string;
  private readonly capabilityFile:string;
  private readonly pairingFile:string;
  private readonly locks=new Map<string,Promise<void>>();

  constructor(
    readonly root=path.join(process.env.HAKIM_DATA_DIR??os.tmpdir(),"control-plane-v1"),
    private readonly secret=(process.env.HAKIM_CONTROL_PLANE_SECRET??process.env.HAKIM_OAUTH_SECRET??"")
  ){
    this.operationsDir=path.join(root,"operations");
    this.idempotencyDir=path.join(root,"idempotency");
    this.tokensDir=path.join(root,"tokens");
    this.capabilityFile=path.join(root,"capabilities.json");
    this.pairingFile=path.join(root,"primary-device.json");
  }

  async init(){
    keyFrom(this.secret);
    await Promise.all([
      stateBackend.initDir(this.operationsDir),
      stateBackend.initDir(this.idempotencyDir),
      stateBackend.initDir(this.tokensDir)
    ]);
  }

  private async locked<T>(key:string,fn:()=>Promise<T>):Promise<T>{
    const prior=this.locks.get(key)??Promise.resolve();
    let release!:()=>void;
    const current=new Promise<void>(resolve=>{release=resolve;});
    const gate=prior.catch(()=>{}).then(()=>current);
    this.locks.set(key,gate);
    await prior.catch(()=>{});
    try{return await fn();}
    finally{
      release();
      if(this.locks.get(key)===gate) this.locks.delete(key);
    }
  }

  private operationFile(id:string){return path.join(this.operationsDir,id+".json");}
  private idemFile(h:string){return path.join(this.idempotencyDir,h+".json");}
  private tokenFile(token:string){return path.join(this.tokensDir,hash(token)+".json");}

  private async readJson<T>(file:string):Promise<T|null>{
    try{return JSON.parse(await stateBackend.readText(file)) as T;}
    catch(e){if((e as NodeJS.ErrnoException).code==="ENOENT") return null; throw e;}
  }
  private async writeJson(file:string,value:unknown){
    await stateBackend.writeTextAtomic(file,JSON.stringify(value));
  }

  async beginOperation(input:{
    channel:ControlChannel;
    intent:ControlIntent;
    idempotency_key:string;
    args?:unknown;
    ttl_ms?:number;
  }){
    if(!CONTROL_CHANNELS.includes(input.channel)) throw new Error("control_channel_invalid");
    if(!CONTROL_INTENTS.includes(input.intent)) throw new Error("control_intent_invalid");
    if(!IDEMPOTENCY.test(input.idempotency_key)) throw new Error("control_idempotency_invalid");
    const ttl=Math.max(10_000,Math.min(30*60_000,Math.trunc(input.ttl_ms??60_000)));
    const idempotencyHash=hash("HAKIM-IDEMPOTENCY-v1\0"+input.channel+"\0"+input.idempotency_key);
    const payloadHash=hash(stable({intent:input.intent,args:input.args??{}}));
    return this.locked("idem:"+idempotencyHash,async()=>{
      const current=await this.readJson<{operation_id:string;payload_hash:string}>(this.idemFile(idempotencyHash));
      if(current){
        if(!safeEqual(current.payload_hash,payloadHash)) throw new Error("control_idempotency_conflict");
        const operation=await this.readOperation(current.operation_id);
        if(!operation) throw new Error("control_idempotency_orphaned");
        return {operation,reused:true};
      }
      const now=Date.now();
      const operation:OperationRecord={
        version:CONTROL_PLANE_VERSION,
        operation_id:"hcp-"+crypto.randomBytes(12).toString("hex"),
        idempotency_hash:idempotencyHash,
        payload_hash:payloadHash,
        channel:input.channel,
        intent:input.intent,
        status:"requested",
        created_at_ms:now,
        updated_at_ms:now,
        expires_at_ms:now+ttl,
        evidence:[]
      };
      await stateBackend.writeTextExclusive(this.operationFile(operation.operation_id),JSON.stringify(operation));
      try{
        await stateBackend.writeTextExclusive(this.idemFile(idempotencyHash),JSON.stringify({
          operation_id:operation.operation_id,payload_hash:payloadHash,created_at_ms:now
        }));
      }catch(e){
        await stateBackend.remove(this.operationFile(operation.operation_id)).catch(()=>{});
        if((e as NodeJS.ErrnoException).code==="EEXIST"){
          const existing=await this.readJson<{operation_id:string;payload_hash:string}>(this.idemFile(idempotencyHash));
          if(existing&&safeEqual(existing.payload_hash,payloadHash)){
            const record=await this.readOperation(existing.operation_id);
            if(record) return {operation:record,reused:true};
          }
          throw new Error("control_idempotency_conflict");
        }
        throw e;
      }
      return {operation,reused:false};
    });
  }

  async readOperation(id:string){
    if(!/^hcp-[0-9a-f]{24}$/.test(id)) throw new Error("control_operation_id_invalid");
    const record=await this.readJson<OperationRecord>(this.operationFile(id));
    if(!record) return null;
    if(record.status!=="verified"&&record.status!=="failed"&&record.status!=="rejected"&&record.expires_at_ms<=Date.now()){
      record.status="expired";
      record.updated_at_ms=Date.now();
      await this.writeJson(this.operationFile(id),record);
    }
    return record;
  }

  async markDispatched(id:string,operationToken:string){
    if(!/^[A-Za-z0-9._:-]{8,128}$/.test(operationToken)) throw new Error("control_operation_token_invalid");
    return this.locked("op:"+id,async()=>{
      const record=await this.readOperation(id);
      if(!record) throw new Error("control_operation_not_found");
      if(["verified","failed","rejected","expired"].includes(record.status)) return record;
      record.operation_token=operationToken;
      record.status="pending";
      record.updated_at_ms=Date.now();
      record.evidence=this.appendEvidence(record.evidence,{type:"dispatch",verdict:"info",at_ms:Date.now(),summary:"device command queued"});
      await this.writeJson(this.operationFile(id),record);
      await this.writeJson(this.tokenFile(operationToken),{operation_id:id});
      return record;
    });
  }

  async markOutcome(id:string,status:"verified"|"failed"|"rejected",type:string,summary:string){
    return this.locked("op:"+id,async()=>{
      const record=await this.readOperation(id);
      if(!record) throw new Error("control_operation_not_found");
      record.status=status;
      record.updated_at_ms=Date.now();
      record.evidence=this.appendEvidence(record.evidence,{
        type:safeSummary(type),verdict:status==="verified"?"pass":"fail",at_ms:Date.now(),summary:safeSummary(summary)
      });
      await this.writeJson(this.operationFile(id),record);
      return record;
    });
  }

  async addEvidence(id:string,input:{type:string;verdict:"pass"|"fail"|"info";summary:string}){
    return this.locked("op:"+id,async()=>{
      const record=await this.readOperation(id);
      if(!record) throw new Error("control_operation_not_found");
      record.evidence=this.appendEvidence(record.evidence,{
        type:safeSummary(input.type),verdict:input.verdict,at_ms:Date.now(),summary:safeSummary(input.summary)
      });
      record.updated_at_ms=Date.now();
      await this.writeJson(this.operationFile(id),record);
      return record;
    });
  }

  private appendEvidence(current:EvidenceRecord[],next:EvidenceRecord){
    return [...current,next].slice(-MAX_EVIDENCE);
  }

  async observeEncryptedResult(relayKey:string,carrier:string){
    let msg:unknown;
    try{msg=decryptResult(relayKey,carrier);}catch{return null;}
    if(!msg||typeof msg!=="object") return null;
    const root=msg as Record<string,unknown>;
    const token=typeof root.request_id==="string"?root.request_id:"";
    if(!/^[A-Za-z0-9._:-]{8,128}$/.test(token)) return null;
    const mapping=await this.readJson<{operation_id:string}>(this.tokenFile(token));
    if(!mapping) return null;
    const rawStatus=typeof root.status==="string"?root.status.toLowerCase():"complete";
    const ok=!["error","failed","rejected","expired"].includes(rawStatus);
    return this.markOutcome(mapping.operation_id,ok?"verified":"failed","device_result",ok?"device result observed":"device result failed");
  }

  async setCapability(input:{
    capability:string;
    state:CapabilityState;
    channel:ControlChannel;
    cost_class?:CostClass;
    evidence:string;
  }){
    if(!CAPABILITY.test(input.capability)) throw new Error("control_capability_invalid");
    if(!CAPABILITY_STATES.includes(input.state)) throw new Error("control_capability_state_invalid");
    if(!CONTROL_CHANNELS.includes(input.channel)) throw new Error("control_channel_invalid");
    const record:CapabilityRecord={
      capability:input.capability,
      state:input.state,
      channel:input.channel,
      cost_class:input.cost_class??"unknown",
      last_verified_at_ms:Date.now(),
      evidence:safeSummary(input.evidence)
    };
    return this.locked("capabilities",async()=>{
      const list=(await this.readJson<CapabilityRecord[]>(this.capabilityFile))??[];
      const next=[...list.filter(x=>x.capability!==record.capability),record]
        .sort((a,b)=>a.capability.localeCompare(b.capability))
        .slice(-64);
      await this.writeJson(this.capabilityFile,next);
      return record;
    });
  }

  private async pairingRecord(){return this.readJson<PairingRecord>(this.pairingFile);}
  private pairingSecret(record:PairingRecord){return open<PairingSecret>(this.secret,record.sealed);}
  private credentialFrom(record:PairingRecord){return this.pairingSecret(record).credential;}

  async startPairing(bridgeBase:string){
    return this.locked("pairing",async()=>{
      const now=Date.now();
      const current=await this.pairingRecord();
      if(current&&current.paired){
        return {status:"paired" as const,paired:true,pairing_url:null,expires_at_ms:null};
      }
      if(current&&current.expires_at_ms>now){
        const value=this.pairingSecret(current);
        return {status:"pending" as const,paired:false,pairing_path:"/control/v1/pair/"+value.nonce,expires_at_ms:current.expires_at_ms};
      }
      const credential=createDeviceCredential();
      const nonce=crypto.randomBytes(24).toString("base64url");
      await directRelayStore.registerCredential(credential,10*60_000);
      const record:PairingRecord={
        version:"HAKIM_CONTROL_PAIR_V1",
        sealed:seal(this.secret,{credential,nonce} satisfies PairingSecret),
        paired:false,
        created_at_ms:now,
        updated_at_ms:now,
        expires_at_ms:now+10*60_000
      };
      await this.writeJson(this.pairingFile,record);
      return {status:"pending" as const,paired:false,pairing_path:"/control/v1/pair/"+nonce,expires_at_ms:record.expires_at_ms};
    });
  }

  async pairingDeepLink(nonce:string,bridgeBase:string){
    if(!/^[A-Za-z0-9_-]{24,80}$/.test(nonce)) throw new Error("control_pairing_nonce_invalid");
    const record=await this.pairingRecord();
    if(!record||record.paired||record.expires_at_ms<=Date.now()) throw new Error("control_pairing_not_available");
    const value=this.pairingSecret(record);
    if(!safeEqual(value.nonce,nonce)) throw new Error("control_pairing_nonce_invalid");
    return pairingUrl(value.credential,bridgeBase);
  }

  async confirmPairing(waitMs=2_000){
    return this.locked("pairing",async()=>{
      const record=await this.pairingRecord();
      if(!record) return {paired:false,status:"not_started" as const};
      if(record.paired) return {paired:true,status:"paired" as const};
      if(record.expires_at_ms<=Date.now()) return {paired:false,status:"expired" as const};
      const credential=this.credentialFrom(record);
      const paired=await pollPairAck(credential,Math.max(0,Math.min(8_000,Math.trunc(waitMs))));
      if(!paired) return {paired:false,status:"pending" as const};
      await directRelayStore.markCredentialPaired(credential);
      record.paired=true;
      record.updated_at_ms=Date.now();
      record.expires_at_ms=Number.MAX_SAFE_INTEGER;
      await this.writeJson(this.pairingFile,record);
      await this.setCapability({capability:"phone.direct",state:"available",channel:"device",cost_class:"included",evidence:"paired direct relay"});
      return {paired:true,status:"paired" as const};
    });
  }

  async pairedCredential(){
    const record=await this.pairingRecord();
    if(!record||!record.paired) throw new Error("control_phone_not_paired");
    return this.credentialFrom(record);
  }

  async state(){
    const capabilities=(await this.readJson<CapabilityRecord[]>(this.capabilityFile))??[];
    let names:string[]=[];
    try{names=(await stateBackend.list(this.operationsDir)).filter(x=>x.endsWith(".json")).sort().reverse().slice(0,64);}
    catch{}
    const operations:OperationRecord[]=[];
    for(const name of names){
      const record=await this.readJson<OperationRecord>(path.join(this.operationsDir,name));
      if(record&&Date.now()-record.updated_at_ms<=MAX_OP_AGE_MS) operations.push(record);
    }
    const pairing=await this.pairingRecord();
    return {
      version:CONTROL_PLANE_VERSION,
      durable:true,
      pairing:{paired:pairing?.paired===true,status:!pairing?"not_started":pairing.paired?"paired":pairing.expires_at_ms>Date.now()?"pending":"expired"},
      capabilities,
      operations:operations
        .sort((a,b)=>b.updated_at_ms-a.updated_at_ms)
        .slice(0,20)
        .map(x=>({
          operation_id:x.operation_id,channel:x.channel,intent:x.intent,status:x.status,
          created_at_ms:x.created_at_ms,updated_at_ms:x.updated_at_ms,expires_at_ms:x.expires_at_ms,
          operation_token:x.operation_token??null,evidence:x.evidence
        })),
      privacy:"No raw device content, typed values, credentials, relay keys, prompts, cookies or conversation transcripts are exposed."
    };
  }
}

export function ingressTokenMatches(raw:string|undefined,expected:string|undefined){
  if(!raw||!expected||expected.length<32) return false;
  return safeEqual(raw,expected);
}

export const controlPlaneStore=new ControlPlaneStore();
