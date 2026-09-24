import https from "node:https";
import dns from "node:dns/promises";
import crypto from "node:crypto";

export type NtfyTextOptions={
  method?:"GET"|"POST";
  headers?:Record<string,string>;
  body?:string;
  timeoutMs?:number;
};

async function httpsOnce(url:string,opts:NtfyTextOptions={},forcedAddress?:string){
  const u=new URL(url);
  const requestOptions:https.RequestOptions={
    protocol:"https:",
    hostname:forcedAddress??u.hostname,
    port:u.port?Number(u.port):443,
    path:u.pathname+u.search,
    method:opts.method??"GET",
    headers:{...(opts.headers??{}),...(forcedAddress?{Host:u.hostname}:{})},
    servername:u.hostname,
    family:forcedAddress?4:undefined,
    agent:false,
    timeout:opts.timeoutMs??3500
  };
  return await new Promise<{status:number;body:string}>((resolve,reject)=>{
    const req=https.request(requestOptions,res=>{
      const chunks:Buffer[]=[];
      res.on("data",chunk=>chunks.push(Buffer.isBuffer(chunk)?chunk:Buffer.from(chunk)));
      res.on("end",()=>resolve({status:res.statusCode??0,body:Buffer.concat(chunks).toString("utf8")}));
    });
    req.on("timeout",()=>req.destroy(new Error("ntfy_timeout")));
    req.on("error",reject);
    if(opts.body) req.write(opts.body);
    req.end();
  });
}

export async function ntfyText(url:string,opts:NtfyTextOptions={}){
  const u=new URL(url);
  const errors:string[]=[];
  let addresses:string[]=[];
  try{
    addresses=await dns.resolve4(u.hostname);
  }catch(e){
    errors.push("dns4:"+(e instanceof Error?e.message:String(e)));
  }

  const candidates=[...new Set(addresses)].slice(0,4);
  for(const address of candidates){
    try{
      const r=await httpsOnce(url,{...opts,timeoutMs:Math.min(opts.timeoutMs??3500,3500)},address);
      if(r.status>0) return r;
    }catch(e){
      errors.push(address+":"+(e instanceof Error?e.message:String(e)));
    }
  }

  try{
    return await httpsOnce(url,{...opts,timeoutMs:opts.timeoutMs??5000});
  }catch(e){
    errors.push("host:"+(e instanceof Error?e.message:String(e)));
    throw new Error("ntfy_all_routes_failed:"+errors.slice(-5).join("|"));
  }
}

export async function probeNtfyIpv4(){
  const topic="hakim_diag_"+crypto.randomBytes(8).toString("base64url");
  const started=Date.now();
  try{
    const response=await ntfyText("https://ntfy.sh/"+encodeURIComponent(topic),{
      method:"POST",
      headers:{"Content-Type":"text/plain; charset=utf-8"},
      body:"hakim-ipv4-probe",
      timeoutMs:5_000
    });
    return {
      ok:response.status>=200&&response.status<300,
      status:response.status,
      latency_ms:Date.now()-started,
      body:response.body.slice(0,300)
    };
  }catch(e){
    return {
      ok:false,
      status:0,
      latency_ms:Date.now()-started,
      error:e instanceof Error?e.message:String(e)
    };
  }
}
