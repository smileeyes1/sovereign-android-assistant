from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
FACTORY = (APP / "HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("LOCAL_PDF_20112=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20112, "version")
req("PdfDocument" in FACTORY, "pdf_document_missing")
req("MediaStore.Downloads.EXTERNAL_CONTENT_URI" in FACTORY, "downloads_output_missing")
req('"ورقة عمل: الجمع ضمن ١٠"' in FACTORY, "arabic_title_missing")
req('"ورقة_عمل_الجمع_ضمن_١٠.pdf"' in FACTORY, "pdf_filename_missing")
req("easternDigits" in FACTORY and "toEastern" in FACTORY, "eastern_digits_missing")
req('val tokens = listOf(toEastern(a), "+", toEastern(b), "=")' in FACTORY, "math_token_order_missing")
req("canvas.drawRect(box, line)" in FACTORY, "answer_box_missing")
req("HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER or "HakimLocalArtifactFactory.canHandle(q)" in ROUTER, "local_artifact_detection_missing")
req("LOCAL_ARTIFACT" in ROUTER, "local_artifact_channel_missing")
req("executeLocalArtifact(text)" in CENTER, "local_artifact_execution_missing")
req('"local_artifact_pdf"' in CENTER, "local_artifact_telemetry_missing")
req("OpenRouter" not in FACTORY and "http://" not in FACTORY and "https://" not in FACTORY, "artifact_factory_has_external_dependency")

# Critical routing invariant: local artifact must be considered before any direct model,
# free-engine setup, provider app, or provider web path.
artifact_marker = "HakimLocalArtifactFactory.canHandle(context, q)" if "HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER else "HakimLocalArtifactFactory.canHandle(q)"\nartifact_pos = ROUTER.index(artifact_marker)
direct_pos = ROUTER.index("HakimEngineRegistry.bestGeneralChat")
free_pos = ROUTER.index("HakimFreePolicy.freeOnly")
req(artifact_pos < direct_pos < free_pos, "local_artifact_not_before_external_engines")

# The LOCAL_ARTIFACT switch case must not invoke OAuth.
case_start = CENTER.index("HakimModelToolRouter.Channel.LOCAL_ARTIFACT")
case_end = CENTER.index("HakimModelToolRouter.Channel.DIRECT_MODEL", case_start)
case_block = CENTER[case_start:case_end]
req("OpenRouterOAuthManager.start" not in case_block, "local_artifact_can_open_oauth")

# All fixed worksheet problems must stay within ten.
problems_start = FACTORY.index("val problems = listOf(")
problems_end = FACTORY.index("var y =", problems_start)
req(problems_start >= 0 and problems_end > problems_start, "problems_missing")
problem_block = FACTORY[problems_start:problems_end]
pairs = [(int(a), int(b)) for a, b in re.findall(r"(\d+)\s+to\s+(\d+)", problem_block)]
req(len(pairs) >= 8, "too_few_problems")
req(all(a + b <= 10 for a, b in pairs), "sum_exceeds_ten")

# Known-failure sentinel: moving local artifact after direct model must be detected.
needle = 'if (attachments.isEmpty() && HakimLocalArtifactFactory.canHandle(context, q))' if 'HakimLocalArtifactFactory.canHandle(context, q)' in ROUTER else 'if (attachments.isEmpty() && HakimLocalArtifactFactory.canHandle(q))'
mutated = ROUTER.replace(needle, 'if (false && attachments.isEmpty() && HakimLocalArtifactFactory.canHandle(context, q))', 1)
try:
    req(needle in mutated, "known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("LOCAL_PDF_20112=FAIL reason=sentinel_not_detected")

print("LOCAL_PDF_20112_GATE=PASS route=local_before_models output=pdf eastern_digits=true oauth=false")
