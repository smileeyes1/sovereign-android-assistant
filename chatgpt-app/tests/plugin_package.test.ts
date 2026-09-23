import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"../..");
const pluginRoot=path.join(root,"plugins/hakim");

test("portable Hakim plugin package is well formed",()=>{
  const manifest=JSON.parse(fs.readFileSync(path.join(pluginRoot,"plugin.json"),"utf8"));
  const mcp=JSON.parse(fs.readFileSync(path.join(pluginRoot,"mcp.json"),"utf8"));
  assert.equal(manifest.$schema,"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json");
  assert.equal(manifest.name,"hakim");
  assert.equal(manifest.extensions["com.openai"].interface.displayName,"Hakim");
  assert.equal(mcp.$schema,"https://agent-plugins.org/schemas/1.0.0/mcp.schema.json");
  assert.equal(mcp.mcpServers.hakim.type,"streamable-http");
  assert.equal(mcp.mcpServers.hakim.url,"https://hakim-chatgpt-bridge-production.up.railway.app/mcp");
});

test("plugin package contains the three governing skills",()=>{
  for(const name of ["device-observe","safe-execution","executive-twin"]){
    const p=path.join(pluginRoot,"skills",name,"SKILL.md");
    assert.equal(fs.existsSync(p),true,name+" skill missing");
    const raw=fs.readFileSync(p,"utf8");
    assert.match(raw,/^---[\s\S]*name:/);
    assert.match(raw,/description:/);
  }
});

test("marketplace entry points to the Hakim package",()=>{
  const market=JSON.parse(fs.readFileSync(path.join(root,".agents/plugins/marketplace.json"),"utf8"));
  assert.equal(market.plugins[0].name,"hakim");
  assert.equal(market.plugins[0].policy.authentication,"ON_INSTALL");
});


test("branding assets exist and are square SVG",()=>{
  const manifest=JSON.parse(fs.readFileSync(path.join(pluginRoot,"plugin.json"),"utf8"));
  const ui=manifest.extensions["com.openai"].interface;
  for(const field of ["logo","composerIcon"]){
    const rel=ui[field];
    assert.match(rel,/^\.\/assets\/.+\.svg$/);
    const abs=path.join(pluginRoot,rel.slice(2));
    assert.equal(fs.existsSync(abs),true,field+" asset missing");
    const svg=fs.readFileSync(abs,"utf8");
    assert.match(svg,/^<svg\b/);
    const vb=svg.match(/viewBox="0 0 (\d+) (\d+)"/);
    assert.ok(vb,field+" viewBox missing");
    assert.equal(vb![1],vb![2],field+" must be square");
    assert.ok(Number(vb![1])>=48);
  }
});
