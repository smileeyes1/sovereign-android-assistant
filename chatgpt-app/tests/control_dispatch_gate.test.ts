import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"..");
const dispatch=fs.readFileSync(path.join(root,"src/control-dispatch.ts"),"utf8");
const index=fs.readFileSync(path.join(root,"src/index.ts"),"utf8");

function literalPublishOps(source:string){
  return [...source.matchAll(/publishCommand\(credential,"([^"]+)"/g)].map(m=>m[1]!);
}

test("control plane dispatch surface is a frozen two-op allowlist",()=>{
  assert.deepEqual(literalPublishOps(dispatch),["launch","action"]);
  assert.equal(dispatch.includes('"shell"'),false);
  assert.equal(dispatch.includes('"root"'),false);
  assert.ok(dispatch.includes('kind:"home"|"back"|"recents"'));
});

test("control ingress cannot reach mutation dispatch before fresh preflight",()=>{
  const start=index.indexOf('app.post("/control/v1/ingress"');
  const end=index.indexOf('\napp.post("/development/v1/lease"',start);
  const block=index.slice(start,end);
  const ready=block.indexOf("preflight.action_ready!==true");
  const launch=block.indexOf("dispatchControlLaunch");
  const navigate=block.indexOf("dispatchControlNavigate");
  assert.ok(start>=0&&ready>=0&&launch>ready&&navigate>ready);
  assert.equal(block.includes('intent==="shell"'),false);
  assert.equal(block.includes('publishCommand(credential,"launch"'),false);
  assert.equal(block.includes('publishCommand(credential,"action"'),false);
});
