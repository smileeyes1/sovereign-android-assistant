import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";

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

  async initDir(dir:string){
    await fs.mkdir(dir,{recursive:true});
  }

  async readText(file:string){
    return fs.readFile(file,"utf8");
  }

  async writeTextAtomic(file:string,value:string){
    await fs.mkdir(path.dirname(file),{recursive:true});
    const tmp=file+"."+crypto.randomBytes(6).toString("hex")+".tmp";
    await fs.writeFile(tmp,value,{encoding:"utf8",mode:0o600});
    await fs.rename(tmp,file);
  }

  async writeTextExclusive(file:string,value:string){
    await fs.mkdir(path.dirname(file),{recursive:true});
    await fs.writeFile(file,value,{encoding:"utf8",mode:0o600,flag:"wx"});
  }

  async list(dir:string){
    return fs.readdir(dir);
  }

  async remove(file:string){
    await fs.unlink(file);
  }
}

/**
 * Storage selection is deliberately fail-closed.
 * A shared backend must be explicitly implemented and tested before it can be selected.
 */
export function createStateBackend():HakimStateBackend{
  const requested=(process.env.HAKIM_STATE_BACKEND??"file").trim().toLowerCase();
  if(requested==="file") return new FileStateBackend();
  throw new Error("unsupported_hakim_state_backend:"+requested);
}

export const hakimStateBackend=createStateBackend();
