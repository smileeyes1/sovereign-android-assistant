import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { Pool } from "pg";

export interface HakimStateBackend {
  readonly kind:string;
  initDir(dir:string):Promise<void>;
  readText(file:string):Promise<string>;
  writeTextAtomic(file:string,value:string):Promise<void>;
  writeTextExclusive(file:string,value:string):Promise<void>;
  list(dir:string):Promise<string[]>;
  remove(file:string):Promise<void>;
}

export class FileStateBackend implements HakimStateBackend {
  readonly kind="file";
  async initDir(dir:string){ await fs.mkdir(dir,{recursive:true}); }
  async readText(file:string){ return fs.readFile(file,"utf8"); }
  async writeTextAtomic(file:string,value:string){
    await fs.mkdir(path.dirname(file),{recursive:true});
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,value,{encoding:"utf8",mode:0o600}); await fs.rename(tmp,file);
  }
  async writeTextExclusive(file:string,value:string){
    await fs.mkdir(path.dirname(file),{recursive:true});
    await fs.writeFile(file,value,{encoding:"utf8",mode:0o600,flag:"wx"});
  }
  async list(dir:string){ return fs.readdir(dir); }
  async remove(file:string){ await fs.unlink(file); }
}

function pgKey(file:string){
  return path.resolve(file).replaceAll("\\","/");
}
function enoent(key:string){
  const e:any=new Error("ENOENT: no such state key, open '"+key+"'"); e.code="ENOENT"; return e;
}
function eexist(key:string){
  const e:any=new Error("EEXIST: state key already exists, open '"+key+"'"); e.code="EEXIST"; return e;
}

export class PostgresStateBackend implements HakimStateBackend {
  readonly kind="postgres";
  private ready:Promise<void>;
  constructor(private pool:Pool){
    this.ready=this.pool.query(`CREATE TABLE IF NOT EXISTS hakim_state_v1 (
      state_key TEXT PRIMARY KEY,
      state_value TEXT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )`).then(()=>undefined);
  }
  static fromEnv(){
    const connectionString=(process.env.HAKIM_POSTGRES_URL??"").trim();
    if(!connectionString) throw new Error("missing_HAKIM_POSTGRES_URL");
    return new PostgresStateBackend(new Pool({connectionString,max:8,connectionTimeoutMillis:5000,idleTimeoutMillis:30000}));
  }
  async initDir(_dir:string){ await this.ready; }
  async readText(file:string){
    await this.ready; const key=pgKey(file);
    const r=await this.pool.query("SELECT state_value FROM hakim_state_v1 WHERE state_key=$1",[key]);
    if(r.rowCount!==1) throw enoent(key); return String(r.rows[0].state_value);
  }
  async writeTextAtomic(file:string,value:string){
    await this.ready; const key=pgKey(file);
    await this.pool.query(`INSERT INTO hakim_state_v1(state_key,state_value) VALUES($1,$2)
      ON CONFLICT(state_key) DO UPDATE SET state_value=EXCLUDED.state_value,updated_at=now()`,[key,value]);
  }
  async writeTextExclusive(file:string,value:string){
    await this.ready; const key=pgKey(file);
    const r=await this.pool.query(`INSERT INTO hakim_state_v1(state_key,state_value) VALUES($1,$2)
      ON CONFLICT(state_key) DO NOTHING RETURNING state_key`,[key,value]);
    if(r.rowCount!==1) throw eexist(key);
  }
  async list(dir:string){
    await this.ready;
    const prefix=pgKey(dir).replace(/\/$/,"")+"/";
    const r=await this.pool.query(`SELECT state_key FROM hakim_state_v1
      WHERE state_key LIKE $1 ESCAPE '\\' ORDER BY state_key`,[prefix.replaceAll("\\","\\\\").replaceAll("%","\\%").replaceAll("_","\\_")+"%"]);
    const out=new Set<string>();
    for(const row of r.rows){
      const rest=String(row.state_key).slice(prefix.length);
      if(rest && !rest.includes("/")) out.add(rest);
    }
    return [...out];
  }
  async remove(file:string){
    await this.ready; const key=pgKey(file);
    const r=await this.pool.query("DELETE FROM hakim_state_v1 WHERE state_key=$1",[key]);
    if(r.rowCount!==1) throw enoent(key);
  }
}

export function createStateBackend():HakimStateBackend{
  const requested=(process.env.HAKIM_STATE_BACKEND??"file").trim().toLowerCase();
  if(requested==="file") return new FileStateBackend();
  if(requested==="postgres") return PostgresStateBackend.fromEnv();
  throw new Error("unsupported_hakim_state_backend:"+requested);
}

export const hakimStateBackend=createStateBackend();
