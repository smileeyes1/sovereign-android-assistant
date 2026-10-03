#!/usr/bin/env python3
from pathlib import Path
s=Path("scripts/hakim-promote-exact-20333.sh").read_text(encoding="utf-8")
required=[
    'EXPECTED_SHA256="944c08b4b57d8c23504206d3a61e06c183ab8a3bbe7fde0c48c9c644ad8f445a"',
    '--install',
    '--sign-only',
    'hakim-sign-exact-20333-ci1509.sh',
    'hakim-install-exact-20333-via-adb.sh',
    'stage=d1_signed_ready_not_installed',
    'stage=field_installed_and_same_artifact_verified',
    '$HOME/.hakim/releases',
]
missing=[x for x in required if x not in s]
if missing:
    raise SystemExit("EXACT_PROMOTION_20333=FAIL missing="+",".join(missing))
for forbidden in ["adb uninstall","pm clear","--downgrade","curl ","wget ","git clone"]:
    if forbidden in s:
        raise SystemExit("EXACT_PROMOTION_20333=FAIL forbidden="+forbidden)
if 'MODE="sign"' not in s:
    raise SystemExit("EXACT_PROMOTION_20333=FAIL install_must_not_be_default")
print("EXACT_PROMOTION_20333=PASS")
