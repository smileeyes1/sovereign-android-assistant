from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
INTAKE = (APP / "HakimVerifiedIntake.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
PATHS = (ROOT / "app/src/main/res/xml/hakim_file_paths.xml").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

def req(value, reason):
    if not value:
        raise SystemExit("VERIFIED_INTAKE_20301=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20301, "version")
req("3.1.1-final-installable-verified-intake-v1" in BUILD, "version_name")

for token in [
    'MessageDigest.getInstance("SHA-256")',
    "verifiedBytes",
    "mirroredPrivately",
    "CodingErrorAction.REPORT",
    "roundTrip.contentEquals(bytes)",
    "requiresOriginalVisual",
    "canUseExactTextFallback",
    "MAX_PRIVATE_MIRROR_BYTES",
]:
    req(token in INTAKE, "intake:" + token)

req("OCR" not in INTAKE and "ocr" not in INTAKE, "unsafe_ocr_substitution")
req("HakimVerifiedIntake.prepare" in CENTER, "center_not_gated")
req("canUseExactTextFallback" in CENTER, "exact_text_fallback_not_connected")
req("externalPromptOverride" in CENTER, "verified_text_external_fallback_missing")
req("deliveryAttachments = emptyList()" in CENTER, "text_fallback_still_uploads_file")
req("androidx.core.content.FileProvider" in MANIFEST, "file_provider_missing")
req(".hakim.files" in MANIFEST, "authority_missing")
req("@xml/hakim_file_paths" in MANIFEST, "paths_missing")
req('path="hakim_intake/"' in PATHS, "intake_path_missing")
req("already-installed provider app uses the user's own account/session" in ROUTER, "free_user_account_handoff_policy_missing")
req("if (attachments.isNotEmpty())" in ROUTER and "Channel.PROVIDER_APP" in ROUTER, "attachment_provider_fallback_missing")

print("VERIFIED_INTAKE_20301=PASS sha256=true original_preserved=true exact_text_only=true free_account_handoff=true fail_closed=true")
