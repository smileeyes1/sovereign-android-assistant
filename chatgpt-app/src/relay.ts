import type { DeviceCredential,HakimOp } from "./protocol.js";
import { decryptResult,encryptCarrier,makeEnvelope,normalizeRelayBaseUrl } from "./protocol.js";

function relayUrl(c:DeviceCredential,topic:string,suffix=""){
  return normalizeRelayBaseUrl(c.relayBaseUrl)+"/"+encodeURIComponent(topic)+suffix;
}

export async function publishCommand(c:DeviceCredential,op:HakimOp,payload:unknown){
  const envelope=makeEnvelope(c.relayKey,op,payload);
  const carrier=encryptCarrier(c.relayKey,envelope);
  const response=await fetch(relayUrl(c,c.topic),{
    method:"POST",
    headers:{"Content-Type":"text/plain; charset=utf-8"},
    body:carrier,
    signal:AbortSignal.timeout(10_000)
  });
  if(!response.ok) throw new Error("carrier_publish_failed");
  return envelope.request_id;
}

export async function pollResult(c:DeviceCredential,requestId:string,timeoutMs=8_000):Promise<unknown|null>{
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    const u=new URL(relayUrl(c,c.resultTopic,"/json"));
    u.searchParams.set("poll","1");
    u.searchParams.set("since","2m");
    const response=await fetch(u,{signal:AbortSignal.timeout(8_000)});
    if(response.ok){
      const body=await response.text();
      for(const line of body.split("\n")){
        if(!line.trim()) continue;
        try{
          const evt=JSON.parse(line);
          const msg=decryptResult(c.relayKey,String(evt.message??""));
          if(typeof msg==="object"&&msg!==null&&"request_id" in msg&&
             (msg as {request_id?:unknown}).request_id===requestId) return msg;
        }catch{}
      }
    }
    await new Promise(r=>setTimeout(r,800));
  }
  return null;
}

export async function pollPairAck(c:DeviceCredential,timeoutMs=10_000):Promise<boolean>{
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    const u=new URL(relayUrl(c,c.resultTopic,"/json"));
    u.searchParams.set("poll","1");
    u.searchParams.set("since","10m");
    const response=await fetch(u,{signal:AbortSignal.timeout(8_000)});
    if(response.ok){
      const body=await response.text();
      for(const line of body.split("\n")){
        if(!line.trim()) continue;
        try{
          const evt=JSON.parse(line);
          const msg=decryptResult(c.relayKey,String(evt.message??"")) as {
            status?:unknown,result?:{event?:unknown,secure_relay?:unknown}
          };
          if(msg?.status==="paired"&&msg?.result?.event==="paired"&&msg?.result?.secure_relay===true) return true;
        }catch{}
      }
    }
    await new Promise(r=>setTimeout(r,700));
  }
  return false;
}
