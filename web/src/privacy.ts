import type { EducationRole } from "./types";

const protectedLabels = [
  "رقم الهوية",
  "رقم هويه",
  "هوية الطالب",
  "هويه الطالب",
  "رقم الطالب",
  "الرقم الوطني",
  "كشف علامات",
  "كشف العلامات",
  "قائمة الطلبة",
  "اسماء الطلبة",
  "أسماء الطلبة",
];

const email = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
const phone = /(?:^|\D)(?:\+?970|0)?5[0-9]{8}(?:\D|$)/;
const labeledId = /(?:هوية|هويه|رقم\s*الطالب)\D{0,12}\d{7,10}/;

export function containsProtectedStudentData(text: string): boolean {
  const q = text.trim();
  if (!q) return false;
  return (
    protectedLabels.some((label) => q.includes(label)) ||
    email.test(q) ||
    phone.test(q) ||
    labeledId.test(q)
  );
}

export interface ExternalDecision {
  allowed: boolean;
  reason?: string;
}

export function externalAiDecision(role: EducationRole, prompt: string): ExternalDecision {
  if (role === "student") {
    return {
      allowed: false,
      reason: "وضع الطالب يعمل محليًا افتراضيًا ولا يرسل طلبه إلى ذكاء خارجي.",
    };
  }
  if (containsProtectedStudentData(prompt)) {
    return {
      allowed: false,
      reason: "يبدو أن النص يتضمن بيانات طالب حساسة؛ بقيت محليًا ولم تُرسل خارجيًا.",
    };
  }
  return { allowed: true };
}

export function externalAttachmentAllowed(role: EducationRole): ExternalDecision {
  if (role === "student") {
    return {
      allowed: false,
      reason: "وضع الطالب لا يرسل المرفقات إلى خدمات خارجية افتراضيًا.",
    };
  }
  return { allowed: true };
}
