from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"

BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
SPEC = (APP / "HakimTeacherArtifactSpec.kt").read_text(encoding="utf-8")
FACTORY = (APP / "HakimMultiFormatArtifactFactory.kt").read_text(encoding="utf-8")
LOCAL = (APP / "HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
FORMATS = (APP / "HakimArtifactFormatRegistry.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
STATE = json.loads((ROOT / "governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
PROMOTION = json.loads((ROOT / "governance/PRODUCT_V1_PROMOTION_STATE.json").read_text(encoding="utf-8"))
SELLABLE = json.loads((ROOT / "governance/SELLABLE_PRODUCT_STATE.json").read_text(encoding="utf-8"))

def req(v, reason):
    if not v:
        raise SystemExit("MULTIFORMAT_TEACHER_FACTORY=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m and int(m.group(1)) >= 20203, "version")
req("versionName" in BUILD, "version_name_present")

for token in [
    "HakimTeacherArtifactSpec",
    "worksheet_addition_within_10",
    "MathProblem",
    "problems = listOf(",
]:
    req(token in SPEC, "spec:" + token)

pairs = [(int(a), int(b)) for a, b in re.findall(r"MathProblem\(\d+,\s*(\d+),\s*(\d+)\)", SPEC)]
req(len(pairs) == 10, "spec_problem_count")
req(all(a + b <= 10 for a, b in pairs), "spec_sum_exceeds_ten")

for token in [
    "renderHtml",
    "renderDocx",
    "renderPptx",
    "renderXlsx",
    "renderPng",
    "ZipOutputStream",
    "ZipInputStream",
    "verifyZip",
    '"word/document.xml"',
    '"ppt/presentation.xml"',
    '"xl/workbook.xml"',
    "Bitmap.CompressFormat.PNG",
]:
    req(token in FACTORY, "factory:" + token)

for fmt in ["Format.PDF", "Format.HTML", "Format.DOCX", "Format.PPTX", "Format.XLSX", "Format.PNG"]:
    req(fmt in FORMATS, "format:" + fmt)

req("HakimTeacherArtifactSpec.additionWithinTen()" in LOCAL, "pdf_not_using_canonical_spec")
req("HakimMultiFormatArtifactFactory.canHandle(context, q)" in ROUTER, "router_multiformat_missing")
req("HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER, "router_pdf_fallback_missing")
req(ROUTER.index("HakimMultiFormatArtifactFactory.canHandle(context, q)") < ROUTER.index("HakimEngineRegistry.bestGeneralChat"), "multiformat_after_model")
req("HakimMultiFormatArtifactFactory.createAll(this, text)" in CENTER, "ui_factory_execution_missing")
req('"local_artifact_multiformat"' in CENTER, "multiformat_telemetry_missing")
req("python3 tests/verify_20203_multiformat_teacher_factory.py" in WORKFLOW, "ci_gate")

req(STATE["android"]["candidate"]["version_code"] >= 20203, "state_version")
req(STATE["android"]["candidate"]["field_verified"] is False, "state_field")
req(STATE["android"]["candidate"]["promoted"] is False, "state_promoted")
req(PROMOTION["candidate_version"] >= 20203 and PROMOTION["promoted"] is False, "promotion")
req(SELLABLE["candidate_version"] >= 20203 and SELLABLE["sellable"] is False, "sellable")

mf = STATE.get("multi_format_factory", {})
req(mf.get("source_integrated") is True, "state_source_integrated")
req(mf.get("field_verified") is False, "state_field_verified")
req(set(mf.get("local_source_formats", [])) == {"PDF","HTML","DOCX","PPTX","XLSX","PNG"}, "state_formats")

# Known-failure sentinel: if the OOXML structural verification disappears, this gate must fail.
mutated = FACTORY.replace("verifyZip(", "REMOVED_verifyZip(", 1)
req(mutated != FACTORY and "verifyZip(" in FACTORY, "sentinel_setup")

print("MULTIFORMAT_TEACHER_FACTORY=PASS version=20203 formats=PDF,HTML,DOCX,PPTX,XLSX,PNG field=false")
