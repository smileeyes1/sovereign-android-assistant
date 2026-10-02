from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPDATER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/AutoUpdater.kt").read_text(encoding="utf-8")

assert 'json?poll=1&since=all' in UPDATER
assert 'since=6h' not in UPDATER
assert 'FIELD_CERT_SHA256' in UPDATER
assert 'sha256(target) != expectedSha' in UPDATER
assert 'archiveCerts == expectedCerts && currentCerts == expectedCerts' in UPDATER
assert 'version <= currentVersion' in UPDATER
assert 'size !in 1..MAX_APK_BYTES' in UPDATER
print("OFFLINE_UPDATE_DISCOVERY=PASS")
