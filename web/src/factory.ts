import { Document, Packer, Paragraph, TextRun, AlignmentType, HeadingLevel } from "docx";
import { toPng } from "html-to-image";
import { jsPDF } from "jspdf";
import PptxGenJS from "pptxgenjs";
import * as XLSX from "xlsx";
import type { ArtifactFormat, GeneratedArtifact, TeacherArtifactSpec } from "./types";
import { mathVisual, toEastern } from "./templates";
import { MIME, assertQuality, validateBlob, validateSpec } from "./quality";

const ALL_FORMATS: ArtifactFormat[] = ["pdf", "html", "docx", "pptx", "xlsx", "png"];

export function requestedFormats(prompt: string): ArtifactFormat[] {
  const q = prompt.toLowerCase();
  if (["كل الصيغ", "جميع الصيغ", "كل الملفات", "بكل الصيغ"].some((x) => q.includes(x))) {
    return [...ALL_FORMATS];
  }
  const out: ArtifactFormat[] = [];
  if (/\bpdf\b|بي دي اف|بى دى اف|للطباعة/.test(q)) out.push("pdf");
  if (/\bhtml\b|صفحة ويب/.test(q)) out.push("html");
  if (/\bdocx\b|\bword\b|وورد/.test(q)) out.push("docx");
  if (/\bpptx\b|powerpoint|بوربوينت|عرض تقديمي/.test(q)) out.push("pptx");
  if (/\bxlsx\b|\bexcel\b|اكسل|إكسل/.test(q)) out.push("xlsx");
  if (/\bpng\b|صورة|صور/.test(q)) out.push("png");
  return out.length ? [...new Set(out)] : ["pdf"];
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function specToHtml(spec: TeacherArtifactSpec): string {
  const studentFields = spec.studentFields
    ? '<div class="student-fields">الاسم: ____________________ &nbsp;&nbsp; الصف: ______ &nbsp;&nbsp; التاريخ: ______</div>'
    : "";

  const problems = (spec.mathProblems ?? [])
    .map(
      (p) =>
        `<div class="problem"><span class="number">${toEastern(p.number)})</span><span class="math" dir="ltr">${escapeHtml(
          mathVisual(p),
        )}</span></div>`,
    )
    .join("");

  const sections = spec.sections
    .map(
      (section) =>
        `<section><h2>${escapeHtml(section.heading)}</h2>${section.lines
          .map((line) => `<p>${escapeHtml(line)}</p>`)
          .join("")}</section>`,
    )
    .join("");

  return `<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(spec.title)}</title>
<style>
@page{size:A4;margin:20mm}
*{box-sizing:border-box}
html,body{margin:0;padding:0;background:#fff;color:#111}
body{font-family:Arial,"Noto Naskh Arabic",sans-serif;direction:rtl}
.page{width:100%;min-height:257mm;padding:2mm}
h1{font-size:25pt;margin:0 0 8mm;text-align:right}
h2{font-size:18pt;margin:7mm 0 3mm}
.meta{font-size:13pt;margin-bottom:5mm}
.student-fields{font-size:15pt;border-bottom:1px solid #555;padding-bottom:5mm;margin-bottom:6mm}
.instruction{font-size:17pt;margin-bottom:5mm}
.problem{display:flex;flex-direction:row;align-items:center;gap:12mm;min-height:17mm;border-bottom:1px solid #bbb;font-size:24pt;page-break-inside:avoid}
.problem .number{width:16mm;text-align:right}
.problem .math{direction:ltr;unicode-bidi:isolate;letter-spacing:.5px;white-space:nowrap}
section{page-break-inside:avoid}
section p{font-size:15pt;line-height:1.7;margin:2mm 0}
.footer{margin-top:8mm;font-size:13pt;border-top:1px solid #aaa;padding-top:3mm}
@media screen{body{max-width:210mm;margin:auto;padding:12px}.page{box-shadow:0 2px 18px #0002}}
</style>
</head>
<body>
<main class="page" id="hakim-artifact">
<h1>${escapeHtml(spec.title)}</h1>
<div class="meta">${escapeHtml(spec.grade)} — ${escapeHtml(spec.subject)}</div>
${studentFields}
${spec.instruction ? `<div class="instruction">${escapeHtml(spec.instruction)}</div>` : ""}
${problems}
${sections}
${spec.footer ? `<div class="footer">${escapeHtml(spec.footer)}</div>` : ""}
</main>
</body></html>`;
}

function renderNode(spec: TeacherArtifactSpec): HTMLDivElement {
  const shell = document.createElement("div");
  shell.style.position = "fixed";
  shell.style.left = "-10000px";
  shell.style.top = "0";
  shell.style.width = "794px";
  shell.style.minHeight = "1123px";
  shell.style.background = "#fff";
  shell.style.color = "#111";
  shell.style.padding = "72px";
  shell.style.fontFamily = 'Arial, "Noto Naskh Arabic", sans-serif';
  shell.style.direction = "rtl";
  shell.style.zIndex = "-1";

  const h1 = document.createElement("h1");
  h1.textContent = spec.title;
  h1.style.fontSize = "42px";
  h1.style.margin = "0 0 28px";
  shell.appendChild(h1);

  const meta = document.createElement("div");
  meta.textContent = spec.grade + " — " + spec.subject;
  meta.style.fontSize = "22px";
  meta.style.marginBottom = "24px";
  shell.appendChild(meta);

  if (spec.studentFields) {
    const fields = document.createElement("div");
    fields.textContent = "الاسم: ____________________    الصف: ______    التاريخ: ______";
    fields.style.fontSize = "22px";
    fields.style.paddingBottom = "18px";
    fields.style.borderBottom = "1px solid #444";
    fields.style.marginBottom = "24px";
    shell.appendChild(fields);
  }

  if (spec.instruction) {
    const inst = document.createElement("div");
    inst.textContent = spec.instruction;
    inst.style.fontSize = "26px";
    inst.style.marginBottom = "20px";
    shell.appendChild(inst);
  }

  for (const p of spec.mathProblems ?? []) {
    const row = document.createElement("div");
    row.style.display = "flex";
    row.style.flexDirection = "row";
    row.style.alignItems = "center";
    row.style.gap = "42px";
    row.style.minHeight = "72px";
    row.style.borderBottom = "1px solid #bbb";

    const num = document.createElement("span");
    num.textContent = toEastern(p.number) + ")";
    num.style.fontSize = "28px";
    num.style.width = "60px";
    num.style.textAlign = "right";

    const math = document.createElement("span");
    math.textContent = mathVisual(p);
    math.dir = "ltr";
    math.style.direction = "ltr";
    math.style.unicodeBidi = "isolate";
    math.style.fontSize = "38px";
    math.style.fontWeight = "700";
    math.style.whiteSpace = "nowrap";

    row.append(num, math);
    shell.appendChild(row);
  }

  for (const section of spec.sections) {
    const h2 = document.createElement("h2");
    h2.textContent = section.heading;
    h2.style.fontSize = "28px";
    h2.style.margin = "28px 0 10px";
    shell.appendChild(h2);
    for (const line of section.lines) {
      const p = document.createElement("p");
      p.textContent = line;
      p.style.fontSize = "22px";
      p.style.lineHeight = "1.7";
      p.style.margin = "7px 0";
      shell.appendChild(p);
    }
  }

  if (spec.footer) {
    const footer = document.createElement("div");
    footer.textContent = spec.footer;
    footer.style.fontSize = "20px";
    footer.style.marginTop = "30px";
    footer.style.paddingTop = "12px";
    footer.style.borderTop = "1px solid #aaa";
    shell.appendChild(footer);
  }

  document.body.appendChild(shell);
  return shell;
}

async function renderPng(spec: TeacherArtifactSpec): Promise<Blob> {
  const node = renderNode(spec);
  try {
    const dataUrl = await toPng(node, {
      pixelRatio: 1.5,
      backgroundColor: "#ffffff",
      cacheBust: true,
    });
    const res = await fetch(dataUrl);
    return await res.blob();
  } finally {
    node.remove();
  }
}

async function renderPdf(spec: TeacherArtifactSpec): Promise<Blob> {
  const png = await renderPng(spec);
  const dataUrl = await blobToDataUrl(png);
  const pdf = new jsPDF({ orientation: "portrait", unit: "mm", format: "a4", compress: true });
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  pdf.addImage(dataUrl, "PNG", 0, 0, pageWidth, pageHeight, undefined, "FAST");
  return pdf.output("blob");
}

function ltrMath(text: string): string {
  return "\u200E" + text + "\u200E";
}

async function renderDocx(spec: TeacherArtifactSpec): Promise<Blob> {
  const children: Paragraph[] = [
    new Paragraph({
      alignment: AlignmentType.RIGHT,
      heading: HeadingLevel.TITLE,
      children: [new TextRun({ text: spec.title, bold: true, size: 40, rightToLeft: true })],
    }),
    new Paragraph({
      alignment: AlignmentType.RIGHT,
      children: [new TextRun({ text: spec.grade + " — " + spec.subject, size: 26, rightToLeft: true })],
    }),
  ];

  if (spec.studentFields) {
    children.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [
          new TextRun({
            text: "الاسم: ____________________    الصف: ______    التاريخ: ______",
            size: 26,
            rightToLeft: true,
          }),
        ],
      }),
    );
  }
  if (spec.instruction) {
    children.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: spec.instruction, size: 30, rightToLeft: true })],
      }),
    );
  }
  for (const p of spec.mathProblems ?? []) {
    children.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [
          new TextRun({ text: toEastern(p.number) + ")  ", size: 30, rightToLeft: true }),
          new TextRun({ text: ltrMath(mathVisual(p)), bold: true, size: 34 }),
        ],
      }),
    );
  }
  for (const section of spec.sections) {
    children.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        heading: HeadingLevel.HEADING_2,
        children: [new TextRun({ text: section.heading, bold: true, size: 32, rightToLeft: true })],
      }),
    );
    for (const line of section.lines) {
      children.push(
        new Paragraph({
          alignment: AlignmentType.RIGHT,
          children: [new TextRun({ text: line, size: 27, rightToLeft: true })],
        }),
      );
    }
  }
  if (spec.footer) {
    children.push(
      new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: spec.footer, size: 24, rightToLeft: true })],
      }),
    );
  }

  const doc = new Document({ sections: [{ properties: {}, children }] });
  return await Packer.toBlob(doc);
}

async function renderPptx(spec: TeacherArtifactSpec): Promise<Blob> {
  const pptx = new PptxGenJS();
  pptx.author = "Hakim";
  pptx.subject = spec.subject;
  pptx.title = spec.title;
  pptx.company = "Hakim";
  pptx.defineLayout({ name: "A4P", width: 8.27, height: 11.69 });
  pptx.layout = "A4P";

  const addBase = () => {
    const slide = pptx.addSlide();
    slide.background = { color: "FFFFFF" };
    slide.addText(spec.title, {
      x: 0.5,
      y: 0.35,
      w: 7.25,
      h: 0.55,
      fontFace: "Arial",
      fontSize: 23,
      bold: true,
      align: "right",
      margin: 0,
      rtlMode: true,
      color: "111111",
    } as never);
    slide.addText(spec.grade + " — " + spec.subject, {
      x: 0.5,
      y: 0.95,
      w: 7.25,
      h: 0.35,
      fontFace: "Arial",
      fontSize: 13,
      align: "right",
      margin: 0,
      rtlMode: true,
    } as never);
    return slide;
  };

  let slide = addBase();
  let y = 1.5;

  if (spec.studentFields) {
    slide.addText("الاسم: ____________________    الصف: ______    التاريخ: ______", {
      x: 0.5, y, w: 7.25, h: 0.45, fontFace: "Arial", fontSize: 13, align: "right", rtlMode: true,
    } as never);
    y += 0.55;
  }
  if (spec.instruction) {
    slide.addText(spec.instruction, {
      x: 0.5, y, w: 7.25, h: 0.5, fontFace: "Arial", fontSize: 15, align: "right", rtlMode: true,
    } as never);
    y += 0.65;
  }

  for (const p of spec.mathProblems ?? []) {
    if (y > 10.6) {
      slide = addBase();
      y = 1.55;
    }
    slide.addText(toEastern(p.number) + ")", {
      x: 7.0, y, w: 0.65, h: 0.5, fontFace: "Arial", fontSize: 18, align: "right", rtlMode: true,
    } as never);
    slide.addText(mathVisual(p), {
      x: 3.1, y, w: 3.6, h: 0.5, fontFace: "Arial", fontSize: 23, bold: true, align: "right", rtlMode: false,
    } as never);
    y += 0.72;
  }

  for (const section of spec.sections) {
    const needed = 0.5 + section.lines.length * 0.45;
    if (y + needed > 10.7) {
      slide = addBase();
      y = 1.55;
    }
    slide.addText(section.heading, {
      x: 0.5, y, w: 7.25, h: 0.4, fontFace: "Arial", fontSize: 17, bold: true, align: "right", rtlMode: true,
    } as never);
    y += 0.45;
    for (const line of section.lines) {
      slide.addText(line, {
        x: 0.6, y, w: 7.0, h: 0.38, fontFace: "Arial", fontSize: 13, align: "right", rtlMode: true,
      } as never);
      y += 0.42;
    }
  }

  const output = await pptx.write({ outputType: "blob" });
  return output as Blob;
}

function renderXlsx(spec: TeacherArtifactSpec): Blob {
  const rows: (string | number)[][] = [
    [spec.title],
    [spec.grade, spec.subject],
  ];
  if (spec.studentFields) rows.push(["الاسم", "", "الصف", "", "التاريخ", ""]);
  if (spec.instruction) rows.push([spec.instruction]);

  for (const p of spec.mathProblems ?? []) {
    rows.push([toEastern(p.number), toEastern(p.a), "+", toEastern(p.b), "=", "□"]);
  }
  for (const section of spec.sections) {
    rows.push([section.heading]);
    for (const line of section.lines) rows.push([line]);
  }

  const ws = XLSX.utils.aoa_to_sheet(rows);
  ws["!cols"] = [{ wch: 18 }, { wch: 14 }, { wch: 8 }, { wch: 14 }, { wch: 8 }, { wch: 14 }];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "المادة التعليمية");
  (wb as unknown as { Workbook: { Views: Array<{ RTL: boolean }> } }).Workbook = {
    Views: [{ RTL: true }],
  };
  const bytes = XLSX.write(wb, { type: "array", bookType: "xlsx" });
  return new Blob([bytes], { type: MIME.xlsx });
}

function renderHtml(spec: TeacherArtifactSpec): Blob {
  return new Blob([specToHtml(spec)], { type: MIME.html });
}

async function blobToDataUrl(blob: Blob): Promise<string> {
  return await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error("تعذر قراءة الملف."));
    reader.onload = () => resolve(String(reader.result));
    reader.readAsDataURL(blob);
  });
}

function cleanFileBase(title: string): string {
  return title
    .trim()
    .replace(/[\\/:*?"<>|]/g, "")
    .replace(/\s+/g, "_")
    .slice(0, 80) || "مادة_تعليمية";
}

async function digestSpec(spec: TeacherArtifactSpec): Promise<string> {
  const bytes = new TextEncoder().encode(JSON.stringify(spec));
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")
    .slice(0, 16);
}

async function render(format: ArtifactFormat, spec: TeacherArtifactSpec): Promise<Blob> {
  switch (format) {
    case "pdf":
      return renderPdf(spec);
    case "html":
      return renderHtml(spec);
    case "docx":
      return renderDocx(spec);
    case "pptx":
      return renderPptx(spec);
    case "xlsx":
      return renderXlsx(spec);
    case "png":
      return renderPng(spec);
  }
}

export async function generateArtifacts(
  spec: TeacherArtifactSpec,
  formats: ArtifactFormat[],
): Promise<GeneratedArtifact[]> {
  assertQuality(validateSpec(spec));
  const digest = await digestSpec(spec);
  const base = cleanFileBase(spec.title);
  const createdAt = Date.now();
  const out: GeneratedArtifact[] = [];

  for (const format of [...new Set(formats)]) {
    const raw = await render(format, spec);
    const blob = raw.type ? raw : new Blob([raw], { type: MIME[format] });
    assertQuality(await validateBlob(format, blob));
    out.push({
      id: crypto.randomUUID(),
      format,
      name: `${base}.${format}`,
      mime: MIME[format],
      blob,
      createdAt,
      specDigest: digest,
    });
  }

  if (new Set(out.map((x) => x.specDigest)).size !== 1) {
    throw new Error("المخرجات لم تعد من المصدر الدلالي نفسه.");
  }
  return out;
}
