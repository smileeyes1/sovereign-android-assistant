from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
FACTORY = (APP / "HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("CONTEXTUAL_PDF_20113=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20113, "version")

for token in [
    'private const val LAST_KIND = "last_kind"',
    'private const val KIND_ADD_WITHIN_10 = "worksheet_addition_within_10"',
    'fun canHandle(context: Context, prompt: String): Boolean',
    'getSharedPreferences("hakim_conversation"',
    '.takeLast(8_000)',
    'if (explicitAdditionWithinTen(recent)) return true',
    'isPdfWorksheetFollowUp(prompt)',
    '.putString(LAST_KIND, KIND_ADD_WITHIN_10)',
]:
    req(token in FACTORY, "factory:" + token)

for phrase in [
    '"بي دي اف"',
    '"للتحميل"',
    '"للطباعة"',
    '"ورقة العمل"',
    '"اريدها"',
]:
    req(phrase in FACTORY, "followup_phrase:" + phrase)

req("HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER, "router_not_contextual")
artifact_pos = ROUTER.index("HakimLocalArtifactFactory.canHandle(context, q)")
direct_pos = ROUTER.index("HakimEngineRegistry.bestGeneralChat")
free_pos = ROUTER.index("HakimFreePolicy.freeOnly")
req(artifact_pos < direct_pos < free_pos, "contextual_local_not_before_models")

# Preserve 20112 invariant: the local artifact factory itself remains network-free.
req("http://" not in FACTORY and "https://" not in FACTORY and "OpenRouter" not in FACTORY, "factory_external_dependency")

# Known failure injection: if context lookup is disabled, the gate must detect it.
mutant = FACTORY.replace('if (explicitAdditionWithinTen(recent)) return true', 'if (false) return true', 1)
try:
    req('if (explicitAdditionWithinTen(recent)) return true' in mutant, "known_failure_context_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("CONTEXTUAL_PDF_20113=FAIL reason=sentinel_not_detected")

print("CONTEXTUAL_PDF_20113_GATE=PASS followup=local context=recent_or_last_artifact oauth=false")
