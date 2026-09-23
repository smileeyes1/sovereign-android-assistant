import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { pkceS256 } from "../src/oauth.js";

async function waitFor(url:string,timeoutMs=15_000){
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline){
    try{ const r=await fetch(url); if(r.ok) return; }catch{}
    await new Promise(r=>setTimeout(r,120));
  }
  throw new Error("server_start_timeout");
}

function form(obj:Record<string,string>){
  return new URLSearchParams(obj).toString();
}

test("reviewer OAuth reaches safe demo tools without a real device",async(t)=>{
  const port=41000+Math.floor(Math.random()*1000);
  const dataDir=await fs.mkdtemp(path.join(os.tmpdir(),"hakim-review-ci-"));
  const reviewUser="openai-reviewer-ci";
  const reviewPassword="R".repeat(40);
  const child=spawn(process.execPath,["--import","tsx","src/index.ts"],{
    cwd:path.resolve(import.meta.dirname,".."),
    env:{
      ...process.env,
      NODE_ENV:"test",
      PORT:String(port),
      HAKIM_OAUTH_SECRET:"S".repeat(64),
      HAKIM_DATA_DIR:dataDir,
      HAKIM_PUBLIC_SAFE:"1",
      HAKIM_REVIEW_USER:reviewUser,
      HAKIM_REVIEW_PASSWORD:reviewPassword
    },
    stdio:["ignore","pipe","pipe"]
  });
  t.after(async()=>{
    child.kill("SIGTERM");
    await fs.rm(dataDir,{recursive:true,force:true});
  });

  const base=`http://127.0.0.1:${port}`;
  await waitFor(base+"/health");

  const verifier="V".repeat(64);
  const redirectUri="https://chatgpt.com/oauth/callback";
  const clientId="https://chatgpt.com/oauth/client.json";
  const authorize=new URL(base+"/oauth/authorize");
  authorize.searchParams.set("response_type","code");
  authorize.searchParams.set("client_id",clientId);
  authorize.searchParams.set("redirect_uri",redirectUri);
  authorize.searchParams.set("state","review-state");
  authorize.searchParams.set("code_challenge",pkceS256(verifier));
  authorize.searchParams.set("code_challenge_method","S256");
  authorize.searchParams.set("resource",base);
  authorize.searchParams.set("scope","hakim.read hakim.write offline_access");

  const page=await fetch(authorize);
  assert.equal(page.status,200);
  const html=await page.text();
  assert.match(html,/name="review_user"/);
  assert.match(html,/name="review_password"/);
  const context=html.match(/name="context" value="(HOC1\.[A-Za-z0-9_-]+)"/)?.[1];
  assert.ok(context,"authorize context missing");

  const denied=await fetch(base+"/oauth/authorize",{
    method:"POST",
    headers:{"Content-Type":"application/x-www-form-urlencoded"},
    body:form({context:context!,review_user:reviewUser,review_password:"wrong"}),
    redirect:"manual"
  });
  assert.equal(denied.status,403);

  const approved=await fetch(base+"/oauth/authorize",{
    method:"POST",
    headers:{"Content-Type":"application/x-www-form-urlencoded"},
    body:form({context:context!,review_user:reviewUser,review_password:reviewPassword}),
    redirect:"manual"
  });
  assert.equal(approved.status,302);
  const location=approved.headers.get("location");
  assert.ok(location);
  const redirected=new URL(location!);
  assert.equal(redirected.origin,"https://chatgpt.com");
  assert.equal(redirected.searchParams.get("state"),"review-state");
  const code=redirected.searchParams.get("code");
  assert.ok(code);

  const tokenResponse=await fetch(base+"/oauth/token",{
    method:"POST",
    headers:{"Content-Type":"application/x-www-form-urlencoded"},
    body:form({
      grant_type:"authorization_code",
      code:code!,
      client_id:clientId,
      redirect_uri:redirectUri,
      resource:base,
      code_verifier:verifier
    })
  });
  assert.equal(tokenResponse.status,200);
  const tokenBody=await tokenResponse.json() as any;
  assert.match(tokenBody.access_token,/^HAT1\./);
  assert.match(tokenBody.refresh_token,/^HRT1\./);

  async function call(name:string,args:Record<string,unknown>={}){
    const response=await fetch(base+"/mcp",{
      method:"POST",
      headers:{
        "Authorization":"Bearer "+tokenBody.access_token,
        "Content-Type":"application/json",
        "Accept":"application/json, text/event-stream"
      },
      body:JSON.stringify({jsonrpc:"2.0",id:name,method:"tools/call",params:{name,arguments:args}})
    });
    assert.equal(response.status,200);
    const raw=await response.text();
    const contentType=response.headers.get("content-type")??"";
    if(contentType.includes("application/json")) return JSON.parse(raw) as any;
    const dataLine=raw.split("\n").find(line=>line.startsWith("data: "));
    assert.ok(dataLine,"MCP SSE data line missing");
    return JSON.parse(dataLine!.slice(6)) as any;
  }

  const status=await call("get_device_status");
  const statusText=status.result?.content?.[0]?.text??"";
  const statusPayload=JSON.parse(statusText);
  assert.equal(statusPayload.demo,true);
  assert.equal(statusPayload.device.name,"Hakim Review Device");
  assert.equal(statusPayload.device.connected,true);
  assert.equal(statusPayload.privacy,"content_redacted");

  const launch=await call("open_target",{package:"com.android.chrome"});
  const launchText=launch.result?.content?.[0]?.text??"";
  const launchPayload=JSON.parse(launchText);
  assert.equal(launchPayload.demo,true);
  assert.equal(launchPayload.status,"approval_requested");
  assert.match(launchPayload.operation_token,/^review-/);
  assert.match(launchPayload.note,/No real device action occurs/);
});
