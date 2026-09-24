import type { TeacherArtifactSpec } from "./types";

export const EASTERN = "٠١٢٣٤٥٦٧٨٩";

export function toEastern(value: string | number): string {
  return String(value).replace(/[0-9]/g, (d) => EASTERN[Number(d)]);
}

export function additionWithinTenSpec(): TeacherArtifactSpec {
  const pairs = [
    [1, 2],
    [3, 4],
    [5, 2],
    [6, 3],
    [4, 4],
    [7, 2],
    [1, 8],
    [5, 5],
    [2, 6],
    [3, 6],
  ];
  return {
    schemaVersion: "1",
    kind: "worksheet",
    title: "ورقة عمل: الجمع ضمن ١٠",
    subject: "الرياضيات",
    grade: "الصف الأول",
    instruction: "أوجد ناتج الجمع، ثم اكتب الإجابة في المربع.",
    studentFields: true,
    earlyGradeEasternDigits: true,
    mathProblems: pairs.map(([a, b], index) => ({ number: index + 1, a, b })),
    sections: [],
    footer: "أحسنت المحاولة.",
  };
}

function genericSpec(
  kind: TeacherArtifactSpec["kind"],
  title: string,
  prompt: string,
  sections: TeacherArtifactSpec["sections"],
): TeacherArtifactSpec {
  return {
    schemaVersion: "1",
    kind,
    title,
    subject: "حسب الطلب",
    grade: "حسب الصف المطلوب",
    studentFields: kind === "worksheet" || kind === "quiz" || kind === "learning_card",
    earlyGradeEasternDigits: true,
    sections: [
      ...sections,
      {
        heading: "مقصد المستخدم",
        lines: [prompt.trim() || "لم يُذكر وصف إضافي."],
      },
    ],
    footer: "مادة قابلة للتعديل قبل الاستخدام.",
  };
}

export function localTemplateFor(prompt: string): TeacherArtifactSpec | null {
  const q = prompt.trim().toLowerCase();

  if (
    (q.includes("جمع") && (q.includes("ضمن ١٠") || q.includes("ضمن 10"))) ||
    q.includes("٤ + ٣") ||
    q.includes("4 + 3")
  ) {
    return additionWithinTenSpec();
  }

  if (q.includes("تحضير") || q.includes("خطة درس") || q.includes("درس")) {
    return genericSpec("lesson_plan", "تحضير درس", prompt, [
      { heading: "الهدف", lines: ["هدف تعلّم واحد واضح وقابل للملاحظة."] },
      { heading: "التمهيد", lines: ["موقف قصير يربط المعرفة السابقة بالمفهوم الجديد."] },
      { heading: "النشاط الرئيس", lines: ["نشاط محسوس أو شبه محسوس ثم انتقال إلى المجرد حسب العمر."] },
      { heading: "تقويم سريع", lines: ["مهمة ختامية قصيرة تكشف تحقق الهدف."] },
      { heading: "بديل عند التعثر", lines: ["تبسيط الخطوة وإعادة التمثيل بطريقة مختلفة."] },
    ]);
  }

  if (q.includes("خطة أسبوع") || q.includes("خطة اسبوع")) {
    return genericSpec("weekly_plan", "خطة أسبوعية", prompt, [
      { heading: "نواتج الأسبوع", lines: ["نواتج محددة قابلة للتحقق."] },
      { heading: "التوزيع", lines: ["الحصص والأنشطة موزعة دون ازدحام."] },
      { heading: "التقويم", lines: ["تقويم بنائي موجز خلال الأسبوع."] },
    ]);
  }

  if (q.includes("اختبار") || q.includes("كويز") || q.includes("تقويم")) {
    return genericSpec("quiz", "اختبار قصير", prompt, [
      { heading: "تعليمات", lines: ["اقرأ السؤال ثم أجب في المساحة المخصصة."] },
      { heading: "الأسئلة", lines: ["١) سؤال قصير مرتبط مباشرة بالهدف.", "٢) سؤال تطبيق.", "٣) سؤال تحقق سريع."] },
    ]);
  }

  if (q.includes("بطاقة") || q.includes("فلاش")) {
    return genericSpec("learning_card", "بطاقة تعلّم", prompt, [
      { heading: "الفكرة", lines: ["معلومة واحدة مركزة."] },
      { heading: "مثال", lines: ["مثال قصير وواضح."] },
      { heading: "تحقق", lines: ["سؤال واحد للتأكد من الفهم."] },
    ]);
  }

  if (q.includes("نشاط")) {
    return genericSpec("activity", "نشاط تعليمي", prompt, [
      { heading: "الغاية", lines: ["نشاط مرتبط بهدف واضح."] },
      { heading: "التنفيذ", lines: ["خطوات قليلة مناسبة للعمر والزمن."] },
      { heading: "التحقق", lines: ["سؤال أو ملاحظة سريعة لقياس الأثر."] },
    ]);
  }

  if (q.includes("ورقة عمل") || q.includes("ورقه عمل")) {
    return genericSpec("worksheet", "ورقة عمل", prompt, [
      { heading: "تعليمات", lines: ["أكمل المهام في المساحات المخصصة."] },
      { heading: "المهام", lines: ["١) مهمة أولى.", "٢) مهمة ثانية.", "٣) مهمة تطبيقية."] },
    ]);
  }

  return null;
}

export function mathVisual(problem: { a: number; b: number }): string {
  return `${toEastern(problem.a)} + ${toEastern(problem.b)} = □`;
}
