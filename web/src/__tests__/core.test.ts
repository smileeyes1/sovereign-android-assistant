import { describe, expect, it } from "vitest";
import { additionWithinTenSpec, mathVisual, toEastern, localTemplateFor } from "../templates";
import { containsProtectedStudentData, externalAiDecision, externalAttachmentAllowed } from "../privacy";
import { validateSpec } from "../quality";

describe("قالب الجمع ضمن ١٠", () => {
  it("يعرض المعادلة بعين الطالب وبأرقام شرقية", () => {
    expect(mathVisual({ a: 4, b: 3 })).toBe("٤ + ٣ = □");
    expect(mathVisual({ a: 4, b: 3 })).not.toBe("٧ = ٣ + ٤");
    expect(mathVisual({ a: 4, b: 3 })).not.toMatch(/[0-9]/);
    expect(toEastern("0123456789")).toBe("٠١٢٣٤٥٦٧٨٩");
  });

  it("يبقي كل مسائل القالب ضمن عشرة", () => {
    const spec = additionWithinTenSpec();
    expect(spec.mathProblems).toHaveLength(10);
    expect(spec.mathProblems?.every((p) => p.a + p.b <= 10)).toBe(true);
    expect(validateSpec(spec).ok).toBe(true);
  });

  it("يكشف فشلًا معلومًا بدل تمريره", () => {
    const broken = additionWithinTenSpec();
    broken.mathProblems = [{ number: 1, a: 8, b: 7 }];
    const result = validateSpec(broken);
    expect(result.ok).toBe(false);
    expect(result.errors.join(" ")).toContain("ضمن ١٠");
  });

  it("يتعرف على الطلب المحلي", () => {
    const spec = localTemplateFor("أنشئ ورقة عمل الجمع ضمن ١٠ للصف الأول بكل الصيغ");
    expect(spec?.title).toBe("ورقة عمل: الجمع ضمن ١٠");
  });
});

describe("خصوصية الطالب", () => {
  it("يكشف مؤشرات البيانات الحساسة", () => {
    expect(containsProtectedStudentData("رقم الهوية 123456789")).toBe(true);
    expect(containsProtectedStudentData("قائمة الطلبة للصف")).toBe(true);
    expect(containsProtectedStudentData("student@example.com")).toBe(true);
  });

  it("يغلق الذكاء والمرفقات الخارجية في وضع الطالب", () => {
    expect(externalAiDecision("student", "اشرح الجمع").allowed).toBe(false);
    expect(externalAttachmentAllowed("student").allowed).toBe(false);
  });

  it("يمنع المعلم أيضًا من إرسال بيانات طالب حساسة", () => {
    expect(externalAiDecision("teacher", "هوية الطالب 123456789").allowed).toBe(false);
  });
});
