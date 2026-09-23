from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
POLICY = json.loads((ROOT / "governance/HAKIM_FIELD_SIGNING_IDENTITY.json").read_text(encoding="utf-8"))
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
SCRIPT = (ROOT / "scripts/verify-field-signer.sh").read_text(encoding="utf-8")
UPDATER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/AutoUpdater.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

EXPECTED = "D1:3E:7A:A8:27:1C:B6:D3:2A:EC:21:57:CC:5B:A4:FA:FD:22:69:57:EB:0C:73:1E:9C:EB:A8:27:BF:78:B0:D3"
FORBIDDEN = "4C:50:85:2E:B0:85:3C:C1:7D:F1:FC:54:0D:5D:B1:75:8F:16:41:1A:F6:B4:AE:3E:52:1F:26:81:0C:55:B6:99"

assert POLICY["canonical_package"] == "ps.hakim.stable"
assert POLICY["certificate_sha256"] == EXPECTED
assert POLICY["known_nonmatching_certificate_sha256"] == FORBIDDEN
assert POLICY["certificate_sha256"] != POLICY["known_nonmatching_certificate_sha256"]
assert "applicationId 'ps.hakim.stable'" in BUILD
version = re.search(r"versionCode\s+(\d+)", BUILD)
assert version and int(version.group(1)) >= 20017
assert "field_signer_mismatch" in SCRIPT
assert "known_companion_signer_rejected" in SCRIPT
assert "apksigner" in SCRIPT and "--print-certs" in SCRIPT
assert EXPECTED.replace(":", "").lower() in UPDATER
assert "MAX_APK_BYTES = 32L * 1024L * 1024L" in UPDATER
assert "verify-field-signer.sh" in WORKFLOW
assert 'id: candidate_version' in WORKFLOW
assert 'steps.candidate_version.outputs.code' in WORKFLOW
assert 'hakim-field-${{ steps.candidate_version.outputs.code }}.apk' in WORKFLOW
assert 'hakim-field-20018.apk' not in WORKFLOW
assert "NOT-INSTALLABLE" in WORKFLOW
print("SIGNING_CONTINUITY_POLICY=PASS")
