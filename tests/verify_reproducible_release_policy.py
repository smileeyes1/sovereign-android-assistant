#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")

required = [
    "buildTypes {",
    "release {",
    "vcsInfo.include false",
]
missing = [x for x in required if x not in gradle]
if missing:
    raise SystemExit("REPRODUCIBLE_RELEASE_GATE=FAIL missing=" + ",".join(missing))

print("REPRODUCIBLE_RELEASE_GATE=PASS vcs_info_embedded=false")
