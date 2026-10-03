#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "scripts/hakim-sign-exact-20333-ci1509.sh"
s = p.read_text(encoding="utf-8")

required = [
    'EXPECTED_HEAD="9ef17d8c9cd176296795bdf7db35778e8eaceabf"',
    'EXPECTED_CI_RUN="1509"',
    'EXPECTED_UNSIGNED_SHA256="944c08b4b57d8c23504206d3a61e06c183ab8a3bbe7fde0c48c9c644ad8f445a"',
    'EXPECTED_PACKAGE="ps.hakim.stable"',
    'EXPECTED_VERSION_CODE="20333"',
    'HAKIM_FIELD_KEYSTORE_PATH',
    '--ks-pass env:HAKIM_FIELD_KEYSTORE_PASSWORD',
    '--key-pass env:HAKIM_FIELD_KEY_PASSWORD',
    '--v1-signing-enabled false',
    '--v2-signing-enabled true',
    '--v3-signing-enabled true',
    'verify-field-signer.sh',
    'verify_hakim_d1_v2_structure.py',
    '-c -P 16 -v 4',
    "Verified using v2 scheme",
    "Verified using v3 scheme",
    'aapt',
    "package: name='$EXPECTED_PACKAGE'",
    "versionCode='$EXPECTED_VERSION_CODE'",
    '"same_unsigned_ci_artifact_verified":True',
    'HAKIM_D1_EXACT_SIGN=PASS',
]
missing = [x for x in required if x not in s]
if missing:
    raise SystemExit("D1_EXACT_SIGN_HANDOFF=FAIL missing=" + ",".join(missing))

for forbidden in [
    "curl ", "wget ", "git clone", "adb uninstall", "pm clear",
    "HAKIM_FIELD_KEYSTORE_B64=", "base64 --decode",
]:
    if forbidden in s:
        raise SystemExit("D1_EXACT_SIGN_HANDOFF=FAIL forbidden=" + forbidden)

# No embedded password-like literals; credentials are only read from environment.
for name in [
    "HAKIM_FIELD_KEYSTORE_PASSWORD",
    "HAKIM_FIELD_KEY_PASSWORD",
]:
    if f'{name}="' in s or f"{name}='" in s:
        raise SystemExit("D1_EXACT_SIGN_HANDOFF=FAIL embedded_secret=" + name)

print("D1_EXACT_SIGN_HANDOFF=PASS")
