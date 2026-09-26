import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import type { DeviceCredential } from "./protocol.js";

const b64u=(b:Buffer)=>b.toString("base64url");
const fromB64u=(s:string)=>Buffer.from(s,"base64url");

export type OAuthCodeRecord={
  credential:DeviceCredential;
  clientId:string;
  redirectUri:string;
  codeChallenge:string;
  resource:string;
  scopes:string[];
  expiresAt:number;
};

type AccessPayload={
  typ:"access";
  credential:DeviceCredential;
  clientId:string;
  aud:string;
  scopes:string[];
  iat:number;
  exp:number;
};

type RefreshPayload={
  typ:"refresh";
  credential:DeviceCredential;
  clientId:string;
  aud:string;
  scopes:string[];
  iat:number;
  exp:number;
};

type AuthorizeContext={
  typ:"authorize_context";
  credential:DeviceCredential;
  clientId:string;
  redirectUri:string;
  state:string;
  codeChallenge:string;
  resource:string;
  scopes:string[];
  iat:number;
  exp:number;
};

function key(secret:string,label:string){
  if(secret.length<43) throw new Error("oauth_secret_too_short");
  return crypto.createHash("sha256").update(label+"\0"+secret,"utf8").digest();
}

function seal(secret:string,label:string,prefix:string,payload:unknown){
  const nonce=crypto.randomBytes(12);
  const cipher=crypto.createCipheriv("aes-256-gcm",key(secret,label),nonce);
  cipher.setAAD(Buffer.from(label,"utf8"));
  const ciphertext=Buffer.concat([cipher.update(Buffer.from(JSON.stringify(payload),"utf8")),cipher.final()]);
  const tag=cipher.getAuthTag();
  return prefix+b64u(Buffer.concat([nonce,ciphertext,tag]));
}

function open<T>(secret:string,label:string,prefix:string,token:string):T{
  if(!token.startsWith(prefix)) throw new Error("invalid_token_prefix");
  const packed=fromB64u(token.slice(prefix.length));
  if(packed.length<28) throw new Error("invalid_token_length");
  const nonce=packed.subarray(0,12);
  const tag=packed.subarray(packed.length-16);
  const ciphertext=packed.subarray(12,packed.length-16);
  const decipher=crypto.createDecipheriv("aes-256-gcm",key(secret,label),nonce);
  decipher.setAAD(Buffer.from(label,"utf8"));
  decipher.setAuthTag(tag);
  const payload=JSON.parse(Buffer.concat([decipher.update(ciphertext),decipher.final()]).toString("utf8")) as T;
  return payload;
}

function assertLive(exp:number){
  if(!Number.isFinite(exp)||exp<=Date.now()) throw new Error("token_expired");
}

export class FileCodeStore{
  private readonly dir:string;
  constructor(dataDir:string){this.dir=path.join(dataDir,"oauth-codes");}

  async init(){
    await fs.mkdir(this.dir,{recursive:true,mode:0o700});
  }

  async cleanupExpired(now=Date.now()){
    await this.init();
    const entries=await fs.readdir(this.dir,{withFileTypes:true}).catch(()=>[]);
    let removed=0;
    for(const entry of entries){
      if(!entry.isFile()||!entry.name.endsWith(".json")) continue;
      const file=path.join(this.dir,entry.name);
      try{
        const record=JSON.parse(await fs.readFile(file,"utf8")) as OAuthCodeRecord;
        if(!Number.isFinite(record.expiresAt)||record.expiresAt<=now){
          await fs.unlink(file).catch(()=>{});
          removed+=1;
        }
      }catch{
        await fs.unlink(file).catch(()=>{});
        removed+=1;
      }
    }
    return removed;
  }

  async issue(record:OAuthCodeRecord){
    await this.cleanupExpired();
    const code=b64u(crypto.randomBytes(32));
    const final=path.join(this.dir,code+".json");
    const temp=final+"."+process.pid+".tmp";
    await fs.writeFile(temp,JSON.stringify(record),{encoding:"utf8",mode:0o600,flag:"wx"});
    await fs.rename(temp,final);
    return code;
  }

  async consume(code:string):Promise<OAuthCodeRecord>{
    if(!/^[A-Za-z0-9_-]{40,100}$/.test(code)) throw new Error("invalid_code");
    await this.cleanupExpired();
    const final=path.join(this.dir,code+".json");
    const claimed=path.join(this.dir,code+"."+process.pid+"."+crypto.randomBytes(4).toString("hex")+".used");
    try{await fs.rename(final,claimed);}catch{throw new Error("invalid_or_consumed_code");}
    try{
      const record=JSON.parse(await fs.readFile(claimed,"utf8")) as OAuthCodeRecord;
      if(record.expiresAt<=Date.now()) throw new Error("code_expired");
      return record;
    }finally{
      await fs.unlink(claimed).catch(()=>{});
    }
  }
}

export function pkceS256(verifier:string){
  return b64u(crypto.createHash("sha256").update(verifier,"ascii").digest());
}

export function normalizeScopes(raw:string|undefined){
  const allowed=new Set(["hakim.read","hakim.write","offline_access"]);
  const requested=(raw??"hakim.read hakim.write offline_access").split(/\s+/).filter(Boolean);
  const unique=[...new Set(requested)];
  if(unique.length===0||unique.some(s=>!allowed.has(s))) throw new Error("invalid_scope");
  return unique;
}

export function isChatGPTClientId(clientId:string){
  try{
    const u=new URL(clientId);
    if(u.protocol!=="https:"||u.hostname!=="chatgpt.com"||u.search||u.hash) return false;
    return u.pathname==="/oauth/client.json" || /^\/oauth\/[A-Za-z0-9_-]+\/client\.json$/.test(u.pathname);
  }catch{return false;}
}

export function isChatGPTRedirectUri(redirectUri:string){
  try{
    const u=new URL(redirectUri);
    if(u.protocol!=="https:"||u.hostname!=="chatgpt.com"||u.port||u.username||u.password||u.hash) return false;
    return u.pathname.startsWith("/oauth/") || (u.pathname==="/connector_platform_oauth_redirect"&&!u.search);
  }catch{return false;}
}

export function makeAuthorizeContext(secret:string,value:Omit<AuthorizeContext,"typ"|"iat"|"exp">,ttlMs=10*60_000){
  const now=Date.now();
  return seal(secret,"HAKIM-OAUTH-CONTEXT-v1","HOC1.",{...value,typ:"authorize_context",iat:now,exp:now+ttlMs} satisfies AuthorizeContext);
}

export function openAuthorizeContext(secret:string,token:string):AuthorizeContext{
  const value=open<AuthorizeContext>(secret,"HAKIM-OAUTH-CONTEXT-v1","HOC1.",token);
  if(value.typ!=="authorize_context") throw new Error("invalid_context_type");
  assertLive(value.exp);
  return value;
}

export function issueAccessToken(secret:string,value:Omit<AccessPayload,"typ"|"iat"|"exp">,ttlMs=60*60_000){
  const now=Date.now();
  return seal(secret,"HAKIM-OAUTH-ACCESS-v1","HAT1.",{...value,typ:"access",iat:now,exp:now+ttlMs} satisfies AccessPayload);
}

export function openAccessToken(secret:string,token:string,resource:string):AccessPayload{
  const value=open<AccessPayload>(secret,"HAKIM-OAUTH-ACCESS-v1","HAT1.",token);
  if(value.typ!=="access"||value.aud!==resource) throw new Error("invalid_access_token");
  assertLive(value.exp);
  return value;
}

export function issueRefreshToken(secret:string,value:Omit<RefreshPayload,"typ"|"iat"|"exp">,ttlMs=30*24*60*60_000){
  const now=Date.now();
  return seal(secret,"HAKIM-OAUTH-REFRESH-v1","HRT1.",{...value,typ:"refresh",iat:now,exp:now+ttlMs} satisfies RefreshPayload);
}

export function openRefreshToken(secret:string,token:string,resource:string):RefreshPayload{
  const value=open<RefreshPayload>(secret,"HAKIM-OAUTH-REFRESH-v1","HRT1.",token);
  if(value.typ!=="refresh"||value.aud!==resource) throw new Error("invalid_refresh_token");
  assertLive(value.exp);
  return value;
}

export function reviewCredentialsMatch(env:NodeJS.ProcessEnv,user:string,password:string){
  const expectedUser=env.HAKIM_REVIEW_USER??"";
  const expectedPassword=env.HAKIM_REVIEW_PASSWORD??"";
  if(expectedUser.length<3||expectedPassword.length<24) return false;
  const left=crypto.createHash("sha256").update(user+"\0"+password,"utf8").digest();
  const right=crypto.createHash("sha256").update(expectedUser+"\0"+expectedPassword,"utf8").digest();
  return crypto.timingSafeEqual(left,right);
}

export function requireProductionOAuthConfig(env:NodeJS.ProcessEnv){
  if(env.NODE_ENV!=="production") return;
  if(!env.HAKIM_OAUTH_SECRET||env.HAKIM_OAUTH_SECRET.length<43) throw new Error("HAKIM_OAUTH_SECRET_required_in_production");
  if(!env.HAKIM_DATA_DIR) throw new Error("HAKIM_DATA_DIR_persistent_storage_required_in_production");
}
