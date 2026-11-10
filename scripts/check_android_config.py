#!/usr/bin/env python3
"""Offline, dependency-free regression checks for the Android build configuration."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
app = (root / "app" / "build.gradle").read_text(encoding="utf-8")
top = (root / "build.gradle").read_text(encoding="utf-8")

def field(name):
    match = re.search(r"(?m)^\s*" + re.escape(name) + r"\s+['\"]?([^'\"\s]+)", app)
    if not match:
        raise AssertionError(f"Missing Android field: {name}")
    return match.group(1)

def check():
    assert field("applicationId") == "ps.hakim.stable", "Production applicationId changed"
    assert field("namespace") == "ps.hakim.phoneagent", "Android namespace changed"
    minimum, target, compile_sdk = (int(field(k)) for k in ("minSdk", "targetSdk", "compileSdk"))
    assert 26 <= minimum <= target <= compile_sdk, "Invalid Android SDK ordering"
    assert int(field("versionCode")) > 0, "Invalid versionCode"
    assert field("versionName"), "Missing versionName"
    assert "JavaVersion.VERSION_17" in app, "Java 17 compatibility missing"
    assert re.search(r"jvmTarget\s*=\s*['\"]17['\"]", app), "Kotlin JVM 17 missing"
    assert "com.android.application" in top and "org.jetbrains.kotlin.android" in top
    print("PASS: Android configuration invariants")

if __name__ == "__main__":
    try:
        check()
    except (AssertionError, ValueError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
