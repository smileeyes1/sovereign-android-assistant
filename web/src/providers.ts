import type { TeacherArtifactSpec, UserSettings } from "./types";
import { externalAiDecision } from "./privacy";
import { getSecret } from "./storage";

const SYSTEM = `
أنت محرك صياغة داخل «حكيم | مصنع المعلم الفلسطيني».
أعد JSON فقط بلا Markdown وفق المخطط:
{
 "schemaVersion":"1",
 "kind":"worksheet|quiz|lesson_plan|weekly_plan|activity|learning_card|document",
 "title":"...",
 "subject":"...",
 "grade":"...",
 "instruction":"...",
 "studentFields":true,
 "earlyGradeEasternDigits":true,
 "mathProblems":[{"number":1,"a":4,"b":3}],
 "sections":[{"heading":"...","lines":["..."]}],
 "footer":"...",
 "sourceNote":"..."
}
راعِ العربية، السياق الفلسطيني، والعمر. لا تدّع اعتمادًا رسميًا. للصفوف الأولى اجعل الأرقام الشرقية عند العرض، وإذا كانت المهمة جمعًا ضمن ١٠ فلا تتجاوز أي مسألة مجموع ١٠. لا تضع بيانات شخصية لطلاب.
`.trim();

function extractJson(text: string): string {
  const cleaned = text.trim().replace(/^\`\`\`(?:json)?/i, "").replace(/\`\`\`$/i, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start < 0 || end <= start) throw new Error("لم يُرجع محرك الذكاء مواصفة JSON صالحة.");
  return cleaned.slice(start, end + 1);
}

function normalizeSpec(raw: unknown): TeacherArtifactSpec {
  if (!raw || typeof raw !== "object") throw new Error("المواصفة الناتجة غير صالحة.");
  const x = raw as Partial<TeacherArtifactSpec>;
  return {
    schemaVersion: "1",
    kind: x.kind ?? "document",
    title: String(x.title ?? "مادة تعليمية"),
    subject: String(x.subject ?? "حسب الطلب"),
    grade: String(x.grade ?? "حسب الطلب"),
    instruction: x.instruction ? String(x.instruction) : undefined,
    studentFields: Boolean(x.studentFields),
    earlyGradeEasternDigits: x.earlyGradeEasternDigits !== false,
    mathProblems: Array.isArray(x.mathProblems)
      ? x.mathProblems.map((p, i) => ({
          number: Number(p.number ?? i + 1),
          a: Number(p.a ?? 0),
          b: Number(p.b ?? 0),
        }))
      : undefined,
    sections: Array.isArray(x.sections)
      ? x.sections.map((s) => ({
          heading: String(s.heading ?? ""),
          lines: Array.isArray(s.lines) ? s.lines.map(String) : [],
        }))
      : [],
    footer: x.footer ? String(x.footer) : undefined,
    sourceNote: x.sourceNote ? String(x.sourceNote) : undefined,
  };
}

async function gemini(prompt: string): Promise<TeacherArtifactSpec> {
  const key = await getSecret("gemini");
  if (!key) throw new Error("لم يُربط مورد Gemini الشخصي بعد.");

  const list = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(key)}`);
  if (!list.ok) throw new Error("تعذر التحقق من مورد Gemini الشخصي.");
  const catalog = (await list.json()) as {
    models?: Array<{ name?: string; supportedGenerationMethods?: string[] }>;
  };
  const candidates = (catalog.models ?? []).filter((m) =>
    m.supportedGenerationMethods?.includes("generateContent"),
  );
  const selected =
    candidates.find((m) => /flash/i.test(m.name ?? "")) ??
    candidates.find((m) => /gemini/i.test(m.name ?? "")) ??
    candidates[0];
  if (!selected?.name) throw new Error("لا يوجد نموذج توليد متاح في حساب المستخدم.");

  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/${selected.name}:generateContent?key=${encodeURIComponent(key)}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: SYSTEM + "\n\nطلب المستخدم:\n" + prompt }] }],
        generationConfig: { responseMimeType: "application/json", temperature: 0.25 },
      }),
    },
  );
  if (!res.ok) throw new Error("تعذر استخدام مورد Gemini الشخصي حاليًا.");
  const json = (await res.json()) as {
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
  };
  const text = json.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("") ?? "";
  return normalizeSpec(JSON.parse(extractJson(text)));
}

async function openRouter(prompt: string): Promise<TeacherArtifactSpec> {
  const key = await getSecret("openrouter");
  if (!key) throw new Error("لم يُربط مورد OpenRouter الشخصي بعد.");

  const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${key}`,
      "X-Title": "Hakim Palestinian Teacher Factory",
    },
    body: JSON.stringify({
      model: "openrouter/free",
      messages: [
        { role: "system", content: SYSTEM },
        { role: "user", content: prompt },
      ],
      temperature: 0.25,
    }),
  });
  if (!res.ok) throw new Error("تعذر استخدام المورد المجاني الشخصي حاليًا.");
  const json = (await res.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const text = json.choices?.[0]?.message?.content ?? "";
  return normalizeSpec(JSON.parse(extractJson(text)));
}

export async function generateSpecWithUserProvider(
  prompt: string,
  settings: UserSettings,
): Promise<TeacherArtifactSpec> {
  const decision = externalAiDecision(settings.role, prompt);
  if (!decision.allowed) throw new Error(decision.reason);
  if (settings.provider === "gemini") return gemini(prompt);
  if (settings.provider === "openrouter") return openRouter(prompt);
  throw new Error("لم يُربط ذكاء خارجي؛ المصنع المحلي ما زال متاحًا.");
}
