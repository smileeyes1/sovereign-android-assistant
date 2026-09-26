import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"../..");
const src=path.join(root,"chatgpt-app/src");

function allFiles(dir:string):string[]{
  const out:string[]=[];
  for(const name of fs.readdirSync(dir)){
    const p=path.join(dir,name);
    const st=fs.statSync(p);
    if(st.isDirectory()) out.push(...allFiles(p));
    else if(/\.(ts|js|json|md)$/.test(name)) out.push(p);
  }
  return out;
}

test("bridge never depends on an OpenAI API key or ChatGPT session secret",()=>{
  const raw=allFiles(src).map(p=>fs.readFileSync(p,"utf8")).join("\n");
  assert.equal(raw.includes("process.env.OPENAI_API_KEY"),false);
  assert.equal(raw.includes("CHATGPT_SESSION"),false);
  assert.equal(raw.includes("__Secure-next-auth.session-token"),false);
  assert.equal(raw.includes("sk-proj-"),false);
});

test("plugin freezes ChatGPT-hosted model policy",()=>{
  const raw=fs.readFileSync(path.join(root,"plugins/hakim/MODEL_POLICY.md"),"utf8");
  assert.match(raw,/ChatGPT is the conversational and reasoning host/);
  assert.match(raw,/does not select, unlock, proxy, imitate, or bill a language model/);
  assert.match(raw,/must not bypass plan, rate, regional, workspace, safety, or product limits/);
});
