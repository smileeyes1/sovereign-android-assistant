import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import {randomSecret} from "./protocol.js";

export type RegisteredOAuthClient={
  version:"HAKIM_OAUTH_CLIENT_V1";
  client_id:string;
  client_name:string;
  redirect_uris:string[];
  token_endpoint_auth_method:"none";
  grant_types:["authorization_code","refresh_token"];
  response_types:["code"];
  created_at_ms:number;
  updated_at_ms:number;
};

const CLIENT_ID=/^hakim-client-[A-Za-z0-9_-]{20,80}$/;
const CONTROL=/[\u0000-\u001F\u007F]/g;
const MAX_CLIENTS=128;

function normalizeName(raw:unknown){
  if(typeof raw!=="string") return "AI client";
  const value=raw.replace(CONTROL," ").replace(/\s+/g," ").trim().slice(0,80);
  return value||"AI client";
}

function normalizeRedirect(raw:unknown){
  if(typeof raw!=="string"||raw.length<8||raw.length>2048) throw new Error("invalid_redirect_uri");
  let u:URL;
  try{u=new URL(raw);}catch{throw new Error("invalid_redirect_uri");}
  if(u.username||u.password||u.hash) throw new Error("invalid_redirect_uri");
  if(u.protocol==="https:") return u.toString();
  const loopback=u.protocol==="http:"&&
    (u.hostname==="127.0.0.1"||u.hostname==="localhost"||u.hostname==="[::1]"||u.hostname==="::1");
  if(loopback) return u.toString();
  throw new Error("invalid_redirect_uri");
}

export class OAuthClientRegistry{
  private readonly dir:string;
  constructor(dataDir:string){this.dir=path.join(dataDir,"oauth-clients-v1");}

  async init(){await fs.mkdir(this.dir,{recursive:true,mode:0o700});}

  private file(clientId:string){
    if(!CLIENT_ID.test(clientId)) throw new Error("invalid_client");
    return path.join(this.dir,clientId+".json");
  }

  async register(raw:unknown):Promise<RegisteredOAuthClient>{
    if(!raw||typeof raw!=="object"||Array.isArray(raw)) throw new Error("invalid_client_metadata");
    const input=raw as Record<string,unknown>;
    const redirectRaw=input.redirect_uris;
    if(!Array.isArray(redirectRaw)||redirectRaw.length<1||redirectRaw.length>8){
      throw new Error("invalid_redirect_uris");
    }
    const redirect_uris=[...new Set(redirectRaw.map(normalizeRedirect))];
    const authMethod=input.token_endpoint_auth_method??"none";
    if(authMethod!=="none") throw new Error("unsupported_token_endpoint_auth_method");
    if(input.grant_types!==undefined){
      if(!Array.isArray(input.grant_types)||
        input.grant_types.some(x=>x!=="authorization_code"&&x!=="refresh_token")){
        throw new Error("unsupported_grant_type");
      }
    }
    if(input.response_types!==undefined){
      if(!Array.isArray(input.response_types)||input.response_types.some(x=>x!=="code")){
        throw new Error("unsupported_response_type");
      }
    }

    await this.init();
    const existing=(await fs.readdir(this.dir)).filter(x=>x.endsWith(".json"));
    if(existing.length>=MAX_CLIENTS) throw new Error("client_registry_capacity_reached");

    const now=Date.now();
    const client:RegisteredOAuthClient={
      version:"HAKIM_OAUTH_CLIENT_V1",
      client_id:"hakim-client-"+randomSecret(24),
      client_name:normalizeName(input.client_name),
      redirect_uris,
      token_endpoint_auth_method:"none",
      grant_types:["authorization_code","refresh_token"],
      response_types:["code"],
      created_at_ms:now,
      updated_at_ms:now
    };
    const file=this.file(client.client_id);
    await fs.writeFile(file,JSON.stringify(client),{encoding:"utf8",mode:0o600,flag:"wx"});
    return client;
  }

  async get(clientId:string):Promise<RegisteredOAuthClient|null>{
    if(!CLIENT_ID.test(clientId)) return null;
    try{
      const raw=JSON.parse(await fs.readFile(this.file(clientId),"utf8")) as RegisteredOAuthClient;
      if(raw.version!=="HAKIM_OAUTH_CLIENT_V1"||raw.client_id!==clientId||
        !Array.isArray(raw.redirect_uris)||raw.redirect_uris.length<1){
        throw new Error("client_registry_corrupt");
      }
      return raw;
    }catch(e){
      if((e as NodeJS.ErrnoException).code==="ENOENT") return null;
      throw e;
    }
  }

  async resolve(clientId:string,redirectUri:string){
    const client=await this.get(clientId);
    if(!client) return null;
    let normalized:string;
    try{normalized=normalizeRedirect(redirectUri);}catch{return null;}
    if(!client.redirect_uris.includes(normalized)) return null;
    return client;
  }

  publicRegistration(client:RegisteredOAuthClient){
    return {
      client_id:client.client_id,
      client_id_issued_at:Math.floor(client.created_at_ms/1000),
      client_name:client.client_name,
      redirect_uris:client.redirect_uris,
      token_endpoint_auth_method:"none",
      grant_types:client.grant_types,
      response_types:client.response_types
    };
  }
}
