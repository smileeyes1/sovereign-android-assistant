from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
POLICY = json.loads((ROOT / "governance/HAKIM_FIELD_SIGNING_IDENTITY.json").read_text(encoding="utf-8"))
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
SCRIPT = (ROOT / "scripts/verify-field-signer.sh").read_text(encoding="utf-8")
UPDATER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/AutoUpdater.kt").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

CURRENT_D1 = "D1:3E:7A:A8:27:1C:B6:D3:2A:EC:21:57:CC:5B:A4:FA:FD:22:69:57:EB:0C:73:1E:9C:EB:A8:27:BF:78:B0:D3"
HISTORICAL_F4 = "F4:2D:71:B0:30:8A:54:3E:25:30:99:C0:23:01:BF:DB:FE:FA:45:F8:75:5B:12:AB:22:A3:C9:03:30:5E:44:2E"
FORBIDDEN_4C = "4C:50:85:2E:B0:85:3C:C1:7D:F1:FC:54:0D:5D:B1:75:8F:16:41:1A:F6:B4:AE:3E:52:1F:26:81:0C:55:B6:99"

assert POLICY["schema_version"] >= 5
assert POLICY["canonical_package"] == "ps.hakim.stable"
assert POLICY["certificate_sha256"] == CURRENT_D1
field = int(POLICY["current_field_version"])
evidence = POLICY["field_evidence"]
assert field >= 20049
assert evidence["version_code"] == field
assert evidence["signer_sha256"] == CURRENT_D1
assert re.fullmatch(r"[0-9a-f]{64}", evidence["apk_sha256"])
assert int(evidence["supersedes_direct_field_version"]) < field
assert field in POLICY["known_matching_versions"]
assert POLICY["historical_previous_lineage"]["certificate_sha256"] == HISTORICAL_F4
assert POLICY["known_nonmatching_certificate_sha256"] == FORBIDDEN_4C
assert 20022 in POLICY["known_matching_versions"]
assert 20025 in POLICY["known_matching_versions"]
assert 20049 in POLICY["known_matching_versions"]
assert 20017 in POLICY["historical_previous_lineage"]["known_signed_versions"]
assert POLICY["continuity"]["one_app_only"] is True
assert POLICY["continuity"]["preserve_app_data_required"] is True
assert POLICY["continuity"]["uninstall_or_clear_data_forbidden"] is True
assert POLICY["continuity"]["inplace_update_requires_exact_current_signer"] is True
assert POLICY["continuity"]["unknown_source_permission_not_required_by_policy"] is True

assert "applicationId 'ps.hakim.stable'" in BUILD
version = re.search(r"versionCode\s+(\d+)", BUILD)
assert version and int(version.group(1)) >= 20026
current = int(version.group(1))
assert current > POLICY["current_field_version"]
assert POLICY["current_candidate_version"] == current

assert "field_signer_mismatch" in SCRIPT
assert "known_companion_signer_rejected" in SCRIPT
assert "apksigner" in SCRIPT and "--print-certs" in SCRIPT
assert CURRENT_D1.replace(":", "").lower() in UPDATER
assert HISTORICAL_F4.replace(":", "").lower() not in UPDATER
assert "MAX_APK_BYTES = 32L * 1024L * 1024L" in UPDATER
assert "ACTION_MANAGE_UNKNOWN_APP_SOURCES" not in UPDATER
assert "canRequestPackageInstalls" not in UPDATER
assert "verify-field-signer.sh" in WORKFLOW
assert 'VERSION_CODE="$(sed -nE' in WORKFLOW
assert 'FIELD_APK="app/build/outputs/apk/release/hakim-field-${VERSION_CODE}.apk"' in WORKFLOW
assert 'name: hakim-field-${{ steps.field_sign.outputs.version_code }}' in WORKFLOW
assert "NOT-INSTALLABLE" in WORKFLOW
print("SIGNING_CONTINUITY_POLICY=PASS")
