import fs from "node:fs/promises";
import path from "node:path";
import type {DeviceCredential} from "./protocol.js";
import {createDeviceCredential,pairingUrl,randomSecret} from "./protocol.js";

export type AndroidPairSession = {
  id:string;
  credential:DeviceCredential;
  createdAt:number;
  expiresAt:number;
};

export class AndroidPairStore {
  private readonly dir:string;
  constructor(private readonly dataDir:string){
    this.dir=path.join(dataDir,"android-pair-sessions");
  }

  async init(){
    await fs.mkdir(this.dir,{recursive:true});
    await this.cleanupExpired();
  }

  private pathFor(id:string){
    if(!/^[A-Za-z0-9_-]{20,80}$/.test(id)) throw new Error("invalid_android_session");
    return path.join(this.dir,id+".json");
  }

  async create(ttlMs=24*60*60*1000):Promise<AndroidPairSession>{
    const now=Date.now();
    const session:AndroidPairSession={
      id:randomSecret(24),
      credential:createDeviceCredential(),
      createdAt:now,
      expiresAt:now+ttlMs
    };
    const p=this.pathFor(session.id);
    await fs.writeFile(p,JSON.stringify(session),{encoding:"utf8",mode:0o600,flag:"wx"});
    return session;
  }

  async get(id:string):Promise<AndroidPairSession>{
    const p=this.pathFor(id);
    const raw=await fs.readFile(p,"utf8");
    const s=JSON.parse(raw) as AndroidPairSession;
    if(s.id!==id||!s.credential||s.credential.v!==2) throw new Error("invalid_android_session");
    if(Date.now()>=s.expiresAt){
      await fs.rm(p,{force:true});
      throw new Error("android_session_expired");
    }
    return s;
  }

  async cleanupExpired(){
    let names:string[]=[];
    try{ names=await fs.readdir(this.dir); }catch{return;}
    const now=Date.now();
    await Promise.all(names.filter(n=>n.endsWith(".json")).map(async n=>{
      const p=path.join(this.dir,n);
      try{
        const raw=await fs.readFile(p,"utf8");
        const s=JSON.parse(raw) as AndroidPairSession;
        if(!s.expiresAt||now>=s.expiresAt) await fs.rm(p,{force:true});
      }catch{ await fs.rm(p,{force:true}); }
    }));
  }
}

export function androidPairHref(session:AndroidPairSession){
  return pairingUrl(session.credential);
}
