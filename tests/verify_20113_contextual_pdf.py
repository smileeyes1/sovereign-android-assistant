from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
FACTORY = (APP / "HakimLocalArtifactFactory.kt").read_text(encoding="utf-8")
SPEC = (APP / "HakimTeacherArtifactSpec.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("CONTEXTUAL_PDF_20113=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20113, "version")

for token in [
    'private const val LAST_SPEC_ID = "last_spec_id"',
    'fun resolveSpec(context: Context, prompt: String): HakimTeacherArtifactSpec?',
    'getSharedPreferences("hakim_conversation"',
    '.takeLast(8_000)',
    'HakimTeacherArtifactSpec.byId(lastId)',
    'HakimTeacherArtifactSpec.looksLikeArtifactFollowUp(prompt)',
    '.putString(LAST_SPEC_ID, spec.id)',
]:
    req(token in FACTORY, "factory:" + token)

for phrase in [
    '"بي دي اف"',
    '"للتحميل"',
    '"للطباعة"',
    '"ورقة العمل"',
    '"اريدها"',
]:
    req(phrase in SPEC or phrase in FACTORY, "followup_phrase:" + phrase)

req("HakimLocalArtifactFactory.canHandle(context, q)" in ROUTER, "router_not_contextual")
artifact_pos = ROUTER.index("HakimLocalArtifactFactory.canHandle(context, q)")
direct_pos = ROUTER.index("HakimEngineRegistry.bestGeneralChat")
free_pos = ROUTER.index("HakimFreePolicy.freeOnly")
req(artifact_pos < direct_pos < free_pos, "contextual_local_not_before_models")
req("http://" not in FACTORY and "https://" not in FACTORY and "OpenRouter" not in FACTORY, "factory_external_dependency")

mutant = FACTORY.replace("HakimTeacherArtifactSpec.byId(lastId)?.let { return it }", "/* removed context memory */", 1)
try:
    req("HakimTeacherArtifactSpec.byId(lastId)?.let { return it }" in mutant, "known_failure_context_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("CONTEXTUAL_PDF_20113=FAIL reason=sentinel_not_detected")

print("CONTEXTUAL_PDF_20113_GATE=PASS followup=local context=recent_or_last_artifact oauth=false")
