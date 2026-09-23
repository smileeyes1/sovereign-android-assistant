import crypto from "node:crypto";

export const CARRIER_PREFIX = "HC1.";
export const CARRIER_AAD = "HAKIM-CARRIER-v1";
export const RESULT_PREFIX = "HR1.";
export const RESULT_AAD = "HAKIM-RESULT-v1";
export const DEFAULT_RELAY_BASE_URL = "https://ntfy.sh";
export const ALLOWED_OPS = ["status","ui","notifications","screenshot","action","launch"] as const;
export type HakimOp = typeof ALLOWED_OPS[number];

export type DeviceCredential = {
  v: 2;
  topic: string;
  resultTopic: string;
  relayKey: string;
  pairToken: string;
  relayBaseUrl: string;
};

const b64u=(b:Buffer)=>b.toString("base64url");
const fromB64u=(s:string)=>Buffer.from(s,"base64url");
export const randomSecret=(bytes=32)=>b64u(crypto.randomBytes(bytes));

export function normalizeRelayBaseUrl(raw?:string):string{
  const u=new URL((raw||DEFAULT_RELAY_BASE_URL).trim());
  if(u.protocol!=="https:"||u.username||u.password||u.search||u.hash) throw new Error("invalid_relay_base_url");
  const path=u.pathname.replace(/\/+$/,"");
  return u.origin+(path===""||path==="/"?"":path);
}

export function createDeviceCredential(relayBaseUrl=DEFAULT_RELAY_BASE_URL):DeviceCredential{
  return {
    v:2,
    topic:"hakim_cmd_"+randomSecret(18),
    resultTopic:"hakim_result_"+randomSecret(18),
    relayKey:randomSecret(48),
    pairToken:randomSecret(32),
    relayBaseUrl:normalizeRelayBaseUrl(relayBaseUrl)
  };
}

export function encodeBearer(c:DeviceCredential):string{
  return "HAKIM-B2."+b64u(Buffer.from(JSON.stringify(c),"utf8"));
}

export function decodeBearer(raw:string):DeviceCredential{
  if(!raw.startsWith("HAKIM-B2.")) throw new Error("invalid_bearer");
  const value=JSON.parse(fromB64u(raw.slice(9)).toString("utf8")) as Partial<DeviceCredential>;
  if(value.v!==2) throw new Error("unsupported_bearer");
  if(!value.topic||!/^[A-Za-z0-9_-]{20,120}$/.test(value.topic)) throw new Error("invalid_topic");
  if(!value.resultTopic||!/^[A-Za-z0-9_-]{20,120}$/.test(value.resultTopic)) throw new Error("invalid_result_topic");
  if(!value.relayKey||!/^[A-Za-z0-9_-]{40,100}$/.test(value.relayKey)) throw new Error("invalid_key");
  if(!value.pairToken||!/^[A-Za-z0-9_-]{32,256}$/.test(value.pairToken)) throw new Error("invalid_pair_token");
  return {
    v:2,
    topic:value.topic,
    resultTopic:value.resultTopic,
    relayKey:value.relayKey,
    pairToken:value.pairToken,
    relayBaseUrl:normalizeRelayBaseUrl(value.relayBaseUrl)
  };
}

export function makeEnvelope(relayKey:string,op:HakimOp,payload:unknown,ttlMs=60_000){
  const requestId="hakim-"+randomSecret(12);
  const expiresAt=Date.now()+ttlMs;
  const payloadB64=b64u(Buffer.from(JSON.stringify(payload??{}),"utf8"));
  const canonical=[requestId,op,String(expiresAt),payloadB64].join("\n");
  const signature=crypto.createHmac("sha256",relayKey).update(canonical,"utf8").digest("hex");
  return {request_id:requestId,op,expires_at_ms:expiresAt,payload_b64:payloadB64,signature};
}

function aesSeal(prefix:string,aad:string,secret:string,payload:unknown):string{
  const nonce=crypto.randomBytes(12);
  const key=crypto.createHash("sha256").update(aad+"\0"+secret,"utf8").digest();
  const cipher=crypto.createCipheriv("aes-256-gcm",key,nonce);
  cipher.setAAD(Buffer.from(aad,"utf8"));
  const body=Buffer.from(JSON.stringify(payload),"utf8");
  const ciphertext=Buffer.concat([cipher.update(body),cipher.final()]);
  const tag=cipher.getAuthTag();
  return prefix+b64u(Buffer.concat([nonce,ciphertext,tag]));
}

function aesOpen(prefix:string,aad:string,secret:string,carrier:string):unknown{
  if(!carrier.startsWith(prefix)) throw new Error("invalid_carrier_prefix");
  const packed=fromB64u(carrier.slice(prefix.length));
  if(packed.length<28) throw new Error("invalid_carrier_length");
  const nonce=packed.subarray(0,12);
  const tag=packed.subarray(packed.length-16);
  const ciphertext=packed.subarray(12,packed.length-16);
  const key=crypto.createHash("sha256").update(aad+"\0"+secret,"utf8").digest();
  const decipher=crypto.createDecipheriv("aes-256-gcm",key,nonce);
  decipher.setAAD(Buffer.from(aad,"utf8"));
  decipher.setAuthTag(tag);
  const raw=Buffer.concat([decipher.update(ciphertext),decipher.final()]).toString("utf8");
  return JSON.parse(raw);
}

export function encryptCarrier(relayKey:string,envelope:object):string{
  return aesSeal(CARRIER_PREFIX,CARRIER_AAD,relayKey,envelope);
}
export function encryptResult(relayKey:string,payload:unknown):string{
  return aesSeal(RESULT_PREFIX,RESULT_AAD,relayKey,payload);
}
export function decryptResult(relayKey:string,carrier:string):unknown{
  return aesOpen(RESULT_PREFIX,RESULT_AAD,relayKey,carrier);
}

export function pairingUrl(c:DeviceCredential):string{
  const q=new URLSearchParams({
    token:c.pairToken,
    relay_topic:c.topic,
    relay_result_topic:c.resultTopic,
    relay_key:c.relayKey,
    relay_base_url:c.relayBaseUrl
  });
  return "hakim://pair?"+q.toString();
}
