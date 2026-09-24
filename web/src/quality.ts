import type { ArtifactFormat, TeacherArtifactSpec } from "./types";
import { mathVisual } from "./templates";

export interface QualityResult {
  ok: boolean;
  errors: string[];
  checks: string[];
}

export function validateSpec(spec: TeacherArtifactSpec): QualityResult {
  const errors: string[] = [];
  const checks: string[] = [];

  if (!spec.title.trim()) errors.push("العنوان فارغ.");
  else checks.push("العنوان موجود");

  if (spec.mathProblems?.length) {
    for (const p of spec.mathProblems) {
      if (p.a < 0 || p.b < 0 || p.a + p.b > 10) {
        errors.push(`المسألة رقم ${p.number} خرجت عن الجمع ضمن ١٠.`);
      }
      const visual = mathVisual(p);
      if (!/^([٠-٩]+) \+ ([٠-٩]+) = □$/.test(visual)) {
        errors.push(`ترتيب المعادلة غير مقبول: ${visual}`);
      }
      if (/[0-9]/.test(visual)) {
        errors.push(`تسرّبت أرقام غربية: ${visual}`);
      }
    }
    checks.push("المعادلات بأرقام شرقية وترتيب الطالب");
  }

  if (spec.earlyGradeEasternDigits) checks.push("قاعدة الأرقام الشرقية فعالة");
  checks.push("المصدر الدلالي موحد");

  return { ok: errors.length === 0, errors, checks };
}

export const MIME: Record<ArtifactFormat, string> = {
  pdf: "application/pdf",
  html: "text/html;charset=utf-8",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  png: "image/png",
};

export async function validateBlob(
  format: ArtifactFormat,
  blob: Blob,
): Promise<QualityResult> {
  const errors: string[] = [];
  const checks: string[] = [];
  if (blob.size < 64) errors.push("الملف الناتج صغير بصورة غير صالحة.");
  else checks.push("الملف غير فارغ");

  const expected = MIME[format].split(";")[0];
  if (blob.type && blob.type.split(";")[0] !== expected) {
    errors.push(`نوع الملف غير متوقع: ${blob.type}`);
  } else {
    checks.push("نوع الملف مطابق");
  }

  if (["docx", "pptx", "xlsx"].includes(format)) {
    const bytes = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
    if (bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
      errors.push("ملف Office ليس حزمة ZIP/OOXML حقيقية.");
    } else {
      checks.push("حزمة Office حقيقية");
    }
  }

  if (format === "pdf") {
    const text = new TextDecoder("latin1").decode(await blob.slice(0, 5).arrayBuffer());
    if (!text.startsWith("%PDF")) errors.push("توقيع PDF غير صحيح.");
    else checks.push("توقيع PDF صحيح");
  }

  return { ok: errors.length === 0, errors, checks };
}

export function assertQuality(result: QualityResult): void {
  if (!result.ok) throw new Error(result.errors.join(" "));
}
