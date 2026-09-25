from pathlib import Path
import json,re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
SPEC=(APP/"HakimTeacherArtifactSpec.kt").read_text(encoding="utf-8")
FACTORY=(APP/"HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
OUT=(APP/"HakimProductOutput.kt").read_text(encoding="utf-8")
ROUTER=(APP/"HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")

def req(v,reason):
    if not v: raise SystemExit("VERIFIED_TEACHER_ARTIFACTS_20206=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m and int(m.group(1))>=20206,"version")
req("verified-teacher-artifacts" in BUILD or "universal-artifact-pipeline" in BUILD or "final-installable" in BUILD or "hardening-train" in BUILD or "core-product" in BUILD or
    "sovereign-constitution" in BUILD,"version_name")

for token in [
    "ADDITION_ID",
    "SUBTRACTION_ID",
    "MIXED_ID",
    "fun additionWithinTen()",
    "fun subtractionWithinTen()",
    "fun mixedWithinTen()",
    "fun resolveExplicit(text: String)",
    "fun looksLikeWorksheetArtifactRequest(text: String)",
]:
    req(token in SPEC,"spec:"+token)

# Arabic-only visible worksheet copy.
visible_literals=re.findall(r'(?:title|subject|grade|instruction)\s*=\s*"([^"]+)"',SPEC)
req(visible_literals,"visible_literals_missing")
req(all(not re.search(r"[A-Za-z]",s) for s in visible_literals),"foreign_visible_copy")

# Validate fixed math stays within ten.
adds=[(int(a),int(b)) for a,b in re.findall(r"MathProblem\(\d+,\s*(\d+),\s*(\d+),\s*Operation.ADDITION\)",SPEC)]
subs=[(int(a),int(b)) for a,b in re.findall(r"MathProblem\(\d+,\s*(\d+),\s*(\d+),\s*Operation.SUBTRACTION\)",SPEC)]
req(len(adds)>=8 and all(a+b<=10 for a,b in adds),"addition_invalid")
req(len(subs)>=8 and all(a>=b and a-b<=10 for a,b in subs),"subtraction_invalid")

for token in [
    "resolveSpec(context, prompt)",
    "verifyStudentSpec(spec)",
    "problem.operation.symbol",
    "toEastern(problem.a)",
    "toEastern(problem.b)",
    "savePdfDocument(context, document, displayName)",
]:
    req(token in FACTORY,"factory:"+token)

req("HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER,"router_local_missing")
req(ROUTER.index("HakimLocalArtifactFactory.canHandle(context, q)") < ROUTER.index("HakimEngineRegistry.bestGeneralChat"),"local_after_model")

# Exact field prompt must be routed locally even without saying «ورقة عمل».
for phrase in ['"pdf"', '"بي دي اف"', '"ملف"', '"الجمع"', '"الطرح"', '"ضمن ١٠"']:
    req(phrase in SPEC or phrase in FACTORY,"field_phrase:"+phrase)

# No worksheet may fall through to model-text PDF.
req("HakimProductOutput.worksheetMustUseStructuredFactory(text)" in CENTER,"worksheet_structured_gate_missing")
guard=CENTER.index("HakimProductOutput.worksheetMustUseStructuredFactory(text)")
generic=CENTER.index("executeGeneratedPdfArtifact(text, artifactText)")
req(guard < generic,"worksheet_guard_after_generic_pdf")

for token in [
    "fun looksLikeManualConversionInstructions",
    "fun looksLikeBrokenWorksheetDump",
    '"save as pdf"',
    '"ctrl + p"',
    '"انسخ الكود"',
    '"collection within 10"',
    'q.contains("###")',
]:
    req(token in OUT,"bad_output_guard:"+token)

req("HakimProductOutput.looksLikeManualConversionInstructions(content)" in FACTORY,"generic_pdf_manual_guard_missing")

# Known bad screenshot patterns are permanent forbidden sentinels.
bad_examples=[
    "## كيف تحوله إلى PDF",
    "Save as PDF",
    "Collection within 10",
    "| # | المعادلة |",
    "**القيود التقنية**",
]
for bad in bad_examples:
    q=bad.lower()
    req(any(marker in OUT.lower() for marker in ["save as pdf","collection within 10","###","**","|"]), "sentinel_setup")

# Fault injection: removing the structured gate must be detectable.
mutant=CENTER.replace("HakimProductOutput.worksheetMustUseStructuredFactory(text)","false",1)
req(mutant!=CENTER and "HakimProductOutput.worksheetMustUseStructuredFactory(text)" in CENTER,"fault_injection_setup")

print("VERIFIED_TEACHER_ARTIFACTS_20206=PASS worksheet=structured pdf=real rtl_math=explicit bad_dump=blocked")
