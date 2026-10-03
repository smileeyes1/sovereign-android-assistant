#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "scripts/hakim-sign-exact-20332-ci1485.sh"
s = p.read_text(encoding="utf-8")

required = [
    'EXPECTED_HEAD="ed475233746388e1b8b94324874adc743957a1ef"',
    'EXPECTED_CI_RUN="1485"',
    'EXPECTED_UNSIGNED_SHA256="182b06dea9ea0e94fa6240d2d2663aa5e5af265283510c69e2a8050e0feb7cf7"',
    'EXPECTED_PACKAGE="ps.hakim.stable"',
    'EXPECTED_VERSION_CODE="20332"',
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
