#!/usr/bin/env python3
from pathlib import Path
s=Path("scripts/hakim-promote-exact-20332.sh").read_text(encoding="utf-8")
required=[
    'EXPECTED_SHA256="182b06dea9ea0e94fa6240d2d2663aa5e5af265283510c69e2a8050e0feb7cf7"',
    '--install',
    '--sign-only',
    'hakim-sign-exact-20332-ci1485.sh',
    'hakim-install-exact-20332-via-adb.sh',
    'stage=d1_signed_ready_not_installed',
    'stage=field_installed_and_same_artifact_verified',
    '$HOME/.hakim/releases',
]
missing=[x for x in required if x not in s]
if missing:
    raise SystemExit("EXACT_PROMOTION_20332=FAIL missing="+",".join(missing))
for forbidden in ["adb uninstall","pm clear","--downgrade","curl ","wget ","git clone"]:
    if forbidden in s:
        raise SystemExit("EXACT_PROMOTION_20332=FAIL forbidden="+forbidden)
if 'MODE="sign"' not in s:
    raise SystemExit("EXACT_PROMOTION_20332=FAIL install_must_not_be_default")
print("EXACT_PROMOTION_20332=PASS")
