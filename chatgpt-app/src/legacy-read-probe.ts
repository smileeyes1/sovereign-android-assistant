import crypto from "node:crypto";

type Candidate={
  commandTopic?:string;
  resultTopic?:string;
  relayKey:string;
  attempted:boolean;
};

type LegacyProbeReport={
  matched:boolean;
  signed_result:boolean;
  local_page:boolean;
  local_host:string|null;
  local_title:string|null;
  status:string|null;
};

function sha(value:string){
  return crypto.createHash("sha256").update(value,"utf8").digest("hex");
}

function keyBytes(key:string){
  const v=key.trim();
  return v.length%2===0&&/^[0-9a-fA-F]+$/.test(v)
    ? Buffer.from(v,"hex")
    : Buffer.from(v,"utf8");
}

function hmacHex(key:string,data:string){
  return crypto.createHmac("sha256",keyBytes(key)).update(data,"utf8").digest("hex");
}

function safeHexEqual(a:string,b:string){
  if(!/^[0-9a-f]{64}$/i.test(a)||!/^[0-9a-f]{64}$/i.test(b)) return false;
  return crypto.timingSafeEqual(Buffer.from(a.toLowerCase(),"hex"),Buffer.from(b.toLowerCase(),"hex"));
}

function privateHost(raw:string){
  try{
    const u=new URL(raw);
    const h=u.hostname;
    if(/^10\./.test(h)) return h;
    const m172=/^172\.(\d+)\./.exec(h);
    if(m172&&Number(m172[1])>=16&&Number(m172[1])<=31) return h;
    if(/^192\.168\./.test(h)) return h;
    return null;
  }catch{
    return null;
  }
}

async function publish(topic:string,body:string){
  const r=await fetch("https://ntfy.sh/"+encodeURIComponent(topic),{
    method:"POST",
    headers:{"Content-Type":"text/plain; charset=utf-8"},
    body,
    signal:AbortSignal.timeout(7_000)
  });
  if(!r.ok) throw new Error("legacy_probe_publish_failed");
}

async function pollSignedSnapshot(resultTopic:string,key:string,requestId:string,timeoutMs=8_000):Promise<unknown|null>{
  const deadline=Date.now()+timeoutMs;
  const chunks=new Map<number,string>();
  let expectedTotal=0;
  while(Date.now()<deadline){
    const u=new URL("https://ntfy.sh/"+encodeURIComponent(resultTopic)+"/json");
    u.searchParams.set("poll","1");
    u.searchParams.set("since","10s");
    let response:Response;
    try{
      response=await fetch(u,{signal:AbortSignal.timeout(Math.min(5_000,Math.max(1_000,deadline-Date.now())))});
    }catch{
      await new Promise(r=>setTimeout(r,350));
      continue;
    }
    if(response.ok){
      const body=await response.text();
      for(const line of body.split("\n")){
        if(!line.trim()) continue;
        try{
          const evt=JSON.parse(line) as {message?:unknown};
          const wrapper=JSON.parse(String(evt.message??"")) as {
            request_id?:unknown;chunk?:unknown;total?:unknown;data?:unknown;sig?:unknown;
          };
          if(wrapper.request_id!==requestId) continue;
          const chunk=Number(wrapper.chunk);
          const total=Number(wrapper.total);
          const part=typeof wrapper.data==="string"?wrapper.data:"";
          const sig=typeof wrapper.sig==="string"?wrapper.sig:"";
          if(!Number.isInteger(chunk)||!Number.isInteger(total)||chunk<1||total<1||chunk>total||total>64) continue;
          const expected=hmacHex(key,requestId+"\n"+chunk+"\n"+total+"\n"+part);
          if(!safeHexEqual(expected,sig)) continue;
          if(expectedTotal!==0&&expectedTotal!==total) continue;
          expectedTotal=total;
          chunks.set(chunk,part);
        }catch{}
      }
      if(expectedTotal>0&&chunks.size===expectedTotal){
        let raw="";
        for(let i=1;i<=expectedTotal;i++){
          const part=chunks.get(i);
          if(part===undefined){raw="";break;}
          raw+=part;
        }
        if(raw){
          try{return JSON.parse(raw);}catch{return null;}
        }
      }
    }
    await new Promise(r=>setTimeout(r,350));
  }
  return null;
}

export class LegacyReadProbe{
  private readonly candidates=new Map<string,Candidate>();
  private readonly onReport:(report:LegacyProbeReport)=>void;

  constructor(onReport:(report:LegacyProbeReport)=>void){
    this.onReport=onReport;
  }

  observeCommand(topic:string,relayKey:string){
    if(!topic||!relayKey) return;
    const id=sha(relayKey);
    const c=this.candidates.get(id)??{relayKey,attempted:false};
    c.commandTopic=topic;
    this.candidates.set(id,c);
    this.maybeRun(c);
  }

  observeResult(topic:string,relayKey:string){
    if(!topic||!relayKey) return;
    const id=sha(relayKey);
    const c=this.candidates.get(id)??{relayKey,attempted:false};
    c.resultTopic=topic;
    this.candidates.set(id,c);
    this.maybeRun(c);
  }

  private maybeRun(c:Candidate){
    if(c.attempted||!c.commandTopic||!c.resultTopic) return;
    c.attempted=true;
    void this.run(c).then(this.onReport).catch(()=>{
      this.onReport({
        matched:false,signed_result:false,local_page:false,
        local_host:null,local_title:null,status:null
      });
    });
  }

  private async run(c:Candidate):Promise<LegacyProbeReport>{
    const requestId="legacy-read-"+Date.now().toString(36)+"-"+crypto.randomBytes(4).toString("hex");
    const cmd={request_id:requestId,type:"snapshot",issued_at:Date.now()};
    const payload=Buffer.from(JSON.stringify(cmd),"utf8").toString("base64url");
    const sig=hmacHex(c.relayKey,payload);
    await publish(c.commandTopic!,JSON.stringify({payload,sig}));
    const result=await pollSignedSnapshot(c.resultTopic!,c.relayKey,requestId);
    if(!result||typeof result!=="object"){
      return {matched:false,signed_result:false,local_page:false,local_host:null,local_title:null,status:null};
    }
    const root=result as Record<string,unknown>;
    const page=(root.page&&typeof root.page==="object")?root.page as Record<string,unknown>:{};
    const url=typeof page.url==="string"?page.url:"";
    const host=privateHost(url);
    const title=host&&typeof page.title==="string"?page.title.replace(/\s+/g," ").trim().slice(0,120):null;
    return {
      matched:true,
      signed_result:true,
      local_page:host!==null,
      local_host:host,
      local_title:title||null,
      status:typeof root.status==="string"?root.status.slice(0,40):null
    };
  }
}
