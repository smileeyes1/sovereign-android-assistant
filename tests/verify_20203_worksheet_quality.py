from pathlib import Path
import re, json

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
FACTORY=(APP/"HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
ROUTER=(APP/"HakimModelToolRouter.kt").read_text(encoding="utf-8")
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def req(v,reason):
    if not v:
        raise SystemExit("WORKSHEET_QUALITY_20203=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))>=20203,"version")

# exact field intent must route to local artifact even without repeating the range
for phrase in ['q.contains("ورقة عمل")','q.contains("ورقه عمل")','q.contains("الجمع")','q.contains("جمع")','"بي دي اف"','"للتحميل"','"للطباعة"']:
    req(phrase in FACTORY,"intent:"+phrase)
req("isPdfAdditionWorksheet(prompt)" in FACTORY,"direct_pdf_addition_missing")
req("HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER,"router_not_local_first")
req(ROUTER.index("HakimLocalArtifactFactory.canHandle(context, q)") < ROUTER.index("HakimEngineRegistry.bestGeneralChat"),"model_before_local_artifact")

# visual/student-eye invariants
for token in [
    'PdfDocument.PageInfo.Builder(595, 842, 1)',
    'canvas.drawText("ورقة عمل: الجمع ضمن $easternLimit"',
    'canvas.drawText("الاسم: __________________________"',
    'canvas.drawText("الصف: __________      التاريخ: __________"',
    'canvas.drawText("أوجد ناتج الجمع، ثم اكتب الإجابة في المربع."',
    'val tokens = listOf(toEastern(a), "+", toEastern(b), "=")',
    'val box = RectF(x - 30f, y - 36f, x + 30f, y + 18f)',
    'toEastern(number)',
]:
    req(token in FACTORY,"visual:"+token)

# one page, eight rows, no answer leakage or long row underline
req("pageNumber += 1" not in FACTORY[FACTORY.index("private fun createAdditionWorksheetPdf"):FACTORY.index("fun createTextPdf")],"worksheet_multipage")
req("take(8)" in FACTORY,"question_count")
req("canvas.drawLine(65f, y + 25f" not in FACTORY,"long_row_line_regression")

# screenshots exposed these unacceptable visible tokens; keep them out of deterministic worksheet renderer
worksheet=FACTORY[FACTORY.index("private fun createAdditionWorksheetPdf"):FACTORY.index("fun createTextPdf")]
for forbidden in ["Joining","Collection","commander","slug","solution","answer","الإجابة الصحيحة","الحل"]:
    req(forbidden.lower() not in worksheet.lower(),"visible_leak:"+forbidden)

# fixed problems must not exceed the declared small-number range
small=re.search(r"if \(maxSum <= 10\) \{(.*?)\} else",FACTORY,re.S)
req(small is not None,"small_problem_set")
pairs=[(int(a),int(b)) for a,b in re.findall(r"(\d+)\s+to\s+(\d+)",small.group(1))]
req(len(pairs)>=8,"too_few_small_problems")
req(all(a+b<=10 for a,b in pairs),"sum_over_ten")

print("WORKSHEET_QUALITY_20203=PASS one_page=true arabic=true eastern_digits=true local_first=true no_answer_leak=true")
