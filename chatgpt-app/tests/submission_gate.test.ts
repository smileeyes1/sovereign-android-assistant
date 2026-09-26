import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root=path.resolve(import.meta.dirname,"../..");
const plugin=JSON.parse(fs.readFileSync(path.join(root,"plugins/hakim/plugin.json"),"utf8"));
const submission=fs.readFileSync(path.join(root,"plugins/hakim/SUBMISSION.md"),"utf8");
const index=fs.readFileSync(path.join(root,"chatgpt-app/src/index.ts"),"utf8");

test("directory listing fits current OpenAI limits",()=>{
  const ui=plugin.extensions["com.openai"].interface;
  assert.ok(ui.displayName.length>0&&ui.displayName.length<=30);
  assert.ok(ui.shortDescription.length>0&&ui.shortDescription.length<=30);
  assert.ok(ui.longDescription.length>0&&ui.longDescription.length<=4000);
  assert.ok(ui.developerName.length>0&&ui.developerName.length<=80);
  assert.ok(Array.isArray(ui.defaultPrompt));
  assert.ok(ui.defaultPrompt.length<=3);
  for(const prompt of ui.defaultPrompt){
    assert.ok(prompt.length>0&&prompt.length<=128);
    assert.equal(prompt.includes("@"),false);
  }
});

test("submission packet has exactly five positive and three negative cases",()=>{
  const positives=[...submission.matchAll(/^### P\d+\b/gm)];
  const negatives=[...submission.matchAll(/^### N\d+\b/gm)];
  assert.equal(positives.length,5);
  assert.equal(negatives.length,3);
});

test("submission uses one canonical OpenAI challenge environment variable",()=>{
  assert.equal(index.includes("OPENAI_APP_CHALLENGE"),false);
  assert.equal((index.match(/OPENAI_APPS_CHALLENGE/g)??[]).length,1);
  assert.equal((index.match(/\/\.well-known\/openai-apps-challenge/g)??[]).length,1);
});

test("review login is credential-gated and never enables dev bearer",()=>{
  assert.match(index,/HAKIM_REVIEW_USER/);
  assert.match(index,/HAKIM_REVIEW_PASSWORD/);
  assert.match(index,/reviewCredentialsMatch/);
  assert.equal(index.includes('HAKIM_ALLOW_DEV_BEARER="1"'),false);
});


test("privacy policy states data categories, recipients, retention and user controls",()=>{
  for(const phrase of [
    "ما الذي نعالجه",
    "المستلمون والمعالِجون",
    "الاحتفاظ",
    "تحكم المستخدم",
    "تنتهي بعد دقيقتين",
    "تنتهي بعد ساعة",
    "تنتهي بعد ٣٠ يومًا"
  ]){
    assert.equal(index.includes(phrase),true,"missing privacy requirement: "+phrase);
  }
  assert.equal(index.includes("لا يحتفظ الجسر بمحتوى الجهاز أو بنتائج الأدوات كقاعدة بيانات"),true);
});
