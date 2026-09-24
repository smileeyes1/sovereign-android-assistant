export type EducationRole =
  | "general"
  | "teacher"
  | "student"
  | "school_admin"
  | "supervisor"
  | "staff"
  | "guardian";

export type ArtifactFormat = "pdf" | "html" | "docx" | "pptx" | "xlsx" | "png";

export type ArtifactKind =
  | "worksheet"
  | "quiz"
  | "lesson_plan"
  | "weekly_plan"
  | "activity"
  | "learning_card"
  | "document";

export interface MathProblem {
  number: number;
  a: number;
  b: number;
}

export interface ArtifactSection {
  heading: string;
  lines: string[];
}

export interface TeacherArtifactSpec {
  schemaVersion: "1";
  kind: ArtifactKind;
  title: string;
  subject: string;
  grade: string;
  instruction?: string;
  studentFields: boolean;
  earlyGradeEasternDigits: boolean;
  mathProblems?: MathProblem[];
  sections: ArtifactSection[];
  footer?: string;
  sourceNote?: string;
}

export interface GeneratedArtifact {
  id: string;
  format: ArtifactFormat;
  name: string;
  mime: string;
  blob: Blob;
  createdAt: number;
  specDigest: string;
}

export interface UserSettings {
  role: EducationRole;
  provider: "none" | "gemini" | "openrouter";
  freeOnly: true;
}

export const ROLE_LABELS: Record<EducationRole, string> = {
  general: "استخدام عام",
  teacher: "معلم/ة",
  student: "طالب/ة",
  school_admin: "إدارة مدرسية",
  supervisor: "إشراف تربوي",
  staff: "موظف/ة",
  guardian: "ولي أمر",
};

export const FORMAT_LABELS: Record<ArtifactFormat, string> = {
  pdf: "PDF",
  html: "HTML",
  docx: "Word",
  pptx: "PowerPoint",
  xlsx: "Excel",
  png: "PNG",
};
