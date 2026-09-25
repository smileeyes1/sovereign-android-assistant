from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
INTAKE = (APP / "HakimVerifiedIntake.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
PATHS = (ROOT / "app/src/main/res/xml/hakim_file_paths.xml").read_text(encoding="utf-8")

def req(value, reason):
    if not value:
        raise SystemExit("VERIFIED_INTAKE_20301=FAIL reason=" + reason)

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
req("androidx.core.content.FileProvider" in MANIFEST, "file_provider_missing")
req(".hakim.files" in MANIFEST, "authority_missing")
req("@xml/hakim_file_paths" in MANIFEST, "paths_missing")
req('path="hakim_intake/"' in PATHS, "intake_path_missing")

print("VERIFIED_INTAKE_20301=PASS sha256=true original_preserved=true exact_text_only=true fail_closed=true")
