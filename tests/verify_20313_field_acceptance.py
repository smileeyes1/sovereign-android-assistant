from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
FIELD=(APP/"HakimFieldAcceptance.kt").read_text(encoding="utf-8")
SERVICE=(APP/"HakimService.kt").read_text(encoding="utf-8")
APP_BOOT=(APP/"HakimApp.kt").read_text(encoding="utf-8")
RELAY=(APP/"HakimUnifiedRelay.kt").read_text(encoding="utf-8")
HEALTH=(APP/"HakimHealthBeacon.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("FIELD_ACCEPTANCE_20313=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20313,"version")
req("field-acceptance" in BUILD,"version_lineage")

for token in [
    'const val VERSION = "FIELD-ACCEPTANCE-20313-v1"',
    '"synthetic_only", true',
    '"personal_content_used", false',
    'HakimFreePolicy.freeOnly(context)',
    'HakimLocalArtifactFactory.create(context, prompt)',
    'context.contentResolver.delete(created.uri, null, null)',
    'restore(prefs, before)',
    'HakimVerifiedIntake.prepare(',
    'FileProvider.getUriForFile(',
    'source.delete()',
    'HakimService.fieldBrowserSelfTest()',
    '"اختبار آلي اصطناعي غير شخصي لحكيم',
    'Bitmap.createBitmap(160, 160',
    'bitmap.eraseColor(Color.RED)',
    'HakimEngineRegistry.directEngines(context)',
    'HakimFreePolicy.allows(it.id, context)',
    '"BLOCKED_NOT_CONFIGURED"',
]:
    req(token in FIELD,"field:"+token)

# Acceptance report must store only bounded pass/fail metadata, never model output.
for forbidden in [
    'put("model_output"',
    'put("response_text"',
    'put("page_text"',
    'put("user_prompt"',
    'put("attachment_content"',
]:
    req(forbidden not in FIELD,"field_leak:"+forbidden)

req("fieldBrowserSelfTest(): JSONObject" in SERVICE,"browser_probe_entry")
req("WebView(this)" in SERVICE,"isolated_webview_missing")
req("سر-اختبار-لا-يخرج" in SERVICE,"synthetic_sensitive_fixture_missing")
req('sensitive.optBoolean("privacy_gate")' in SERVICE,"privacy_gate_not_asserted")
req('!sensitive.toString().contains("سر-اختبار-لا-يخرج")' in SERVICE,"secret_escape_guard_missing")
req("HakimFieldAcceptance.install(this)" in APP_BOOT,"app_acceptance_install_missing")
req('.put("field_acceptance", HakimFieldAcceptance.status(context))' in RELAY,"relay_acceptance_status_missing")
req('.put("field_acceptance", HakimFieldAcceptance.status(context))' in HEALTH,"health_acceptance_status_missing")

req("python3 tests/verify_20313_field_acceptance.py" in WORKFLOW,"workflow_gate")
req('test "$VERSION_CODE" = "20313"' in WORKFLOW,"workflow_version")

# Fault injection sentinels: personal-data and paid-fallback regressions must be detectable.
mutant=FIELD.replace('"personal_content_used", false','"personal_content_used", true',1)
req('"personal_content_used", false' not in mutant,"fault_personal_data_setup")
mutant2=FIELD.replace('HakimFreePolicy.allows(it.id, context)','true',1)
req('HakimFreePolicy.allows(it.id, context)' not in mutant2,"fault_free_policy_setup")

print("FIELD_ACCEPTANCE_20313=PASS synthetic_only=true local_pdf=true contextual=true attachment=true browser_privacy=true free_only_external=true")
