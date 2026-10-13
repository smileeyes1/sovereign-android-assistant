#!/usr/bin/env python3
"""Regression checks for the safely staged open-source, zero-credit cinema source."""
import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "cinema" / "open_source_factory"
KOTLIN = ROOT / "app" / "src" / "main" / "java" / "ps" / "hakim" / "phoneagent" / "HakimVideoFactory.kt"

def check(cond, label):
    if not cond:
        raise AssertionError(label)

for name in ("auto_make.py", "no_credit_factory.py"):
    source = (BASE / name).read_text(encoding="utf-8")
    ast.parse(source, filename=name)
    check("https://" not in source and "requests." not in source, name + ": external dependencies must not be added silently")

renderer = (BASE / "no_credit_factory.py").read_text(encoding="utf-8")
entry = (BASE / "auto_make.py").read_text(encoding="utf-8")
kotlin = KOTLIN.read_text(encoding="utf-8")

check("١ إلى ١٠" in renderer, "render scope disclosure missing")
check("ball_counts" in renderer and "AR_TRANSLATION" in renderer, "number rendering guards missing")
check("raqm" in renderer and "ffprobe" in renderer and "sha256" in renderer, "render QA gate missing")
check("cinematic" in entry and "ValueError" in entry, "unsupported realistic films must fail closed")
check('"runtime_connected_to_hakim", false' in kotlin, "unverified runtime advertised as connected")
check('"render_verified", false' in kotlin, "unverified film advertised as rendered")
check('val noCredits = payload.optBoolean("no_credits", true)' in kotlin, "no-credit default missing")
check('costClass == "paid" && !p.optBoolean("paid_approved", false)' in kotlin, "unapproved paid providers not blocked")
check('costClass != "local"' in kotlin and 'execution_probe_verified' in kotlin, "unverified local provider might be selected")
check('p.optBoolean("requires_credits", true)' in kotlin, "credit-requiring service might be selected by default")
print("OPEN_CINEMA_SOURCE_AND_ZERO_CREDIT_REGRESSION_PASS")
