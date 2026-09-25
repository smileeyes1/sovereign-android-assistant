from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
ROOT_BUILD = (ROOT / "build.gradle").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
GATEWAY = (APP / "HakimAttachmentGateway.kt").read_text(encoding="utf-8")
INTAKE = (APP / "HakimVerifiedIntake.kt").read_text(encoding="utf-8")
PROVIDER = (APP / "HakimFileProvider.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

def req(value, reason):
    if not value:
        raise SystemExit("HARDENING_20302=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20302, "version")
req("3.1.2-hardening-train-v1" in BUILD, "version_name")
req("androidx.activity:activity-ktx:1.13.0" in BUILD, "stable_activity_dependency")

agp = re.search(r"com\.android\.application' version '([0-9.]+)'", ROOT_BUILD)
req(agp is not None, "agp_missing")
req(tuple(map(int, agp.group(1).split("."))) >= (8,5,1), "agp_below_16k_floor")

req("class HakimFileProvider : FileProvider()" in PROVIDER, "dedicated_provider_class")
req('android:name=".HakimFileProvider"' in MANIFEST, "manifest_provider")
req('android:name="androidx.core.content.FileProvider"' not in MANIFEST, "direct_fileprovider_forbidden")
req('android.permission.CAMERA' in MANIFEST, "camera_capability_missing")
MAIN = (APP / "MainActivity.kt").read_text(encoding="utf-8")
req("requestSpecificPermissions" in MAIN, "targeted_permission_escalation_missing")
req("requestSpecificPermissions(needed)" in MAIN, "web_media_still_requests_blanket_permissions")

for token in [
    "ComponentActivity()",
    "PickMultipleVisualMedia",
    "PickVisualMediaRequest",
    "OpenMultipleDocuments",
    "showAttachmentChooser",
]:
    req(token in CENTER, "picker:" + token)

for token in [
    "MAX_ATTACHMENTS_PER_TASK = 20",
    "ContentResolver.SCHEME_CONTENT",
    "takePersistableUriPermission",
    "persistReadAccess",
]:
    req(token in GATEWAY, "gateway:" + token)

for token in [
    "MAX_MIRROR_CACHE_BYTES",
    "HEADER_PROBE_BYTES",
    "detectedContentKind",
    "detectKind",
    "validateDeclaredMime",
    "ZIP_CONTAINER",
]:
    req(token in INTAKE, "intake:" + token)

req("zipalign" in WORKFLOW and "-P 16" in WORKFLOW, "16k_zipalign_gate_missing")
req("tools/verify_hakim_d1_v2_structure.py" in WORKFLOW, "d1_v2_structure_gate_missing")

print("HARDENING_20302=PASS photo_picker=true scoped_provider=true mime_evidence=true cache_bound=true page16k_gate=true signing_structure=true")
