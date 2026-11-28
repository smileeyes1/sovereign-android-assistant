#!/usr/bin/env python3
"""Reject native ADB code in the experimental field APK. No certification claim."""
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as apk:
    dex = [name for name in apk.namelist() if name.startswith("classes") and name.endswith(".dex")]
    if not dex:
        raise SystemExit("FIELD_ADB_DEX_GATE=FAIL no dex")
    markers = (b"Lio/github/muntashirakon/adb/", b"Lio/github/muntashirakon/adb;")
    for name in dex:
        data = apk.read(name)
        if not data.startswith(b"dex\n"):
            raise SystemExit("FIELD_ADB_DEX_GATE=FAIL invalid dex")
        if any(marker in data for marker in markers):
            raise SystemExit("FIELD_ADB_DEX_GATE=FAIL library present in " + name)
print("FIELD_ADB_DEX_GATE=PASS")
