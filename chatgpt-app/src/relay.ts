import type { DeviceCredential,HakimOp } from "./protocol.js";
import { decryptResult,encryptCarrier,makeEnvelope } from "./protocol.js";

export async function publishCommand(c:DeviceCredential,op:HakimOp,payload:unknown){
  const envelope=makeEnvelope(c.relayKey,op,payload);
  const carrier=encryptCarrier(c.relayKey,envelope);
  const response=await fetch("https://ntfy.sh/"+encodeURIComponent(c.topic),{
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
    const u=new URL("https://ntfy.sh/"+encodeURIComponent(c.resultTopic)+"/json");
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
