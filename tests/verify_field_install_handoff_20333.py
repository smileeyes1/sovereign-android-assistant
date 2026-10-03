#!/usr/bin/env python3
from pathlib import Path

root=Path(__file__).resolve().parents[1]
s=(root/"scripts/hakim-install-exact-20333-via-adb.sh").read_text(encoding="utf-8")

required=[
    'EXPECTED_PACKAGE="ps.hakim.stable"',
    'EXPECTED_VERSION_CODE="20333"',
    'EXPECTED_SOURCE_HEAD="9ef17d8c9cd176296795bdf7db35778e8eaceabf"',
    'EXPECTED_CI_RUN="1509"',
    'EXPECTED_UNSIGNED_SHA256="944c08b4b57d8c23504206d3a61e06c183ab8a3bbe7fde0c48c9c644ad8f445a"',
    "verify-field-signer.sh",
    "signed_sha_mismatch",
    "not_a_forward_update",
    'install -r --no-streaming',
    "installed_same_artifact_sha_mismatch",
    "current_field_signer_mismatch",
    "verify-field-signer.sh",
    "current_signer_d1=true",
    "installed_signer_d1=true",
    "first_install_time_preserved=true",
    "uid_preserved=true",
    "first_install_time_changed",
    "package_uid_changed",
    "installed_field_signer_mismatch",
    "installed_version_below_verified_floor",
    "EXPECTED_MIN_CURRENT_VERSION_CODE=\"20317\"",
    "versionCode=$EXPECTED_VERSION_CODE",
    "pidof",
    "same_artifact=true",
    "data_clear=false",
    "uninstall=false",
]
missing=[x for x in required if x not in s]
if missing:
    raise SystemExit("FIELD_INSTALL_HANDOFF=FAIL missing="+",".join(missing))

for forbidden in ["adb uninstall","pm clear","install -d","--downgrade","su ","root "]:
    if forbidden in s:
        raise SystemExit("FIELD_INSTALL_HANDOFF=FAIL forbidden="+forbidden)

print("FIELD_INSTALL_HANDOFF=PASS")
