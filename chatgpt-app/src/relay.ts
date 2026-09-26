import type { DeviceCredential,HakimOp } from "./protocol.js";
import { decryptResult,encryptCarrier,makeEnvelope } from "./protocol.js";
import { directRelayStore } from "./direct-relay.js";

const ntfyFallbackEnabled=()=>process.env.HAKIM_NTFY_FALLBACK!=="0";
const sleep=(ms:number)=>new Promise(resolve=>setTimeout(resolve,ms));

async function publishNtfy(c:DeviceCredential,carrier:string){
  const response=await fetch("https://ntfy.sh/"+encodeURIComponent(c.topic),{
    method:"POST",
    headers:{"Content-Type":"text/plain; charset=utf-8"},
    body:carrier,
    signal:AbortSignal.timeout(10_000)
  });
  if(!response.ok) throw new Error("carrier_publish_failed");
}

export async function publishCommand(c:DeviceCredential,op:HakimOp,payload:unknown){
  const envelope=makeEnvelope(c.relayKey,op,payload);
  const carrier=encryptCarrier(c.relayKey,envelope);
  await directRelayStore.enqueueCommand(c,envelope.request_id,carrier,envelope.expires_at_ms);
  if(ntfyFallbackEnabled()){
    void publishNtfy(c,carrier).catch(()=>{});
  }
  return envelope.request_id;
}

async function pollDirectMatch(
  c:DeviceCredential,
  predicate:(msg:unknown)=>boolean,
  timeoutMs:number
):Promise<unknown>{
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    const entries=await directRelayStore.listResults(c);
    for(const entry of entries){
      try{
        const msg=decryptResult(c.relayKey,entry.carrier);
        if(predicate(msg)){
          await directRelayStore.deleteResult(c,entry.id);
          return msg;
        }
      }catch{
        await directRelayStore.deleteResult(c,entry.id);
      }
    }
    await sleep(250);
  }
  throw new Error("direct_result_timeout");
}

async function pollNtfyMatch(
  c:DeviceCredential,
  predicate:(msg:unknown)=>boolean,
  timeoutMs:number
):Promise<unknown>{
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    const u=new URL("https://ntfy.sh/"+encodeURIComponent(c.resultTopic)+"/json");
    u.searchParams.set("poll","1");
    u.searchParams.set("since","10m");
    const response=await fetch(u,{signal:AbortSignal.timeout(Math.min(8_000,Math.max(1_000,deadline-Date.now())))});
    if(response.ok){
      const body=await response.text();
      for(const line of body.split("\n")){
        if(!line.trim()) continue;
        try{
          const evt=JSON.parse(line);
          const msg=decryptResult(c.relayKey,String(evt.message??""));
          if(predicate(msg)) return msg;
        }catch{}
      }
    }
    await sleep(500);
  }
  throw new Error("legacy_result_timeout");
}

async function firstMatch(
  c:DeviceCredential,
  predicate:(msg:unknown)=>boolean,
  timeoutMs:number
):Promise<unknown|null>{
  const attempts=[pollDirectMatch(c,predicate,timeoutMs)];
  if(ntfyFallbackEnabled()) attempts.push(pollNtfyMatch(c,predicate,timeoutMs));
  try{
    return await Promise.any(attempts);
  }catch{
    return null;
  }
}

export async function pollResult(c:DeviceCredential,requestId:string,timeoutMs=8_000):Promise<unknown|null>{
  return firstMatch(c,msg=>{
    return typeof msg==="object"&&msg!==null&&"request_id" in msg&&
      (msg as {request_id?:unknown}).request_id===requestId;
  },timeoutMs);
}

export async function pollPairAck(c:DeviceCredential,timeoutMs=10_000):Promise<boolean>{
  const msg=await firstMatch(c,value=>{
    const candidate=value as {
      status?:unknown;
      result?:{event?:unknown;secure_relay?:unknown};
    };
    return candidate?.status==="paired"&&
      candidate?.result?.event==="paired"&&
      candidate?.result?.secure_relay===true;
  },timeoutMs);
  return msg!==null;
}
