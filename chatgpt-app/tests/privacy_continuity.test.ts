import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const source=fs.readFileSync(path.resolve(import.meta.dirname,"../src/index.ts"),"utf8");

test("privacy disclosure matches durable continuity retention",()=>{
  assert.ok(source.includes("سجل الاستمرارية"));
  assert.ok(source.includes("٣٠ يومًا"));
  assert.ok(source.includes("أوامر القراءة الآمنة المشفرة"));
  assert.ok(source.includes("النتائج المشفرة"));
  assert.ok(source.includes("لا تُصمم هذه النقطة لحفظ محتوى الشاشة أو الصفحة"));
  assert.equal(source.includes("لا يحتفظ الجسر بمحتوى الجهاز أو بنتائج الأدوات كقاعدة بيانات"),false);
});
