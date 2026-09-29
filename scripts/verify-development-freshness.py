#!/usr/bin/env python3
import json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = json.loads((ROOT / "governance" / "DEVELOPMENT_FRESHNESS.json").read_text())
BUILD = ROOT / "app" / "build.gradle"

def version_from_text(text):
    m = re.search(r"\bversionCode\s+(\d+)\b", text)
    return int(m.group(1)) if m else None

def git(*args, check=True):
    p = subprocess.run(["git", *args], cwd=ROOT, text=True, capture_output=True)
    if check and p.returncode:
        raise RuntimeError(p.stderr.strip() or "git command failed")
    return p.stdout

local = version_from_text(BUILD.read_text())
if local is None:
    print("FRESHNESS_GUARD=FAIL reason=local_version_missing", file=sys.stderr)
    sys.exit(2)

# Refresh all branch refs so a stale checkout/conversation cannot decide freshness.
git("fetch", "--no-tags", "--prune", "origin", "+refs/heads/*:refs/remotes/origin/*")

prefixes = POLICY["trusted_branch_prefixes"]
candidates = []
for ref in git("for-each-ref", "--format=%(refname:short)", "refs/remotes/origin/").splitlines():
    name = ref.removeprefix("origin/")
    if name == "HEAD":
        continue
    if not any(name == p or name.startswith(p) for p in prefixes):
        continue
    p = subprocess.run(["git", "show", f"{ref}:app/build.gradle"], cwd=ROOT, text=True, capture_output=True)
    if p.returncode:
        continue
    v = version_from_text(p.stdout)
    if v is not None:
        candidates.append((v, name))

remote_v, remote_branch = max(candidates, default=(0, "NONE"))
field_floor = int(POLICY["field_floor"]["version_code"])
effective = max(field_floor, remote_v)

print(f"FRESHNESS_LOCAL={local}")
print(f"FRESHNESS_REMOTE_MAX={remote_v}")
print(f"FRESHNESS_REMOTE_BRANCH={remote_branch}")
print(f"FRESHNESS_FIELD_FLOOR={field_floor}")
print(f"FRESHNESS_EFFECTIVE_FLOOR={effective}")

if local < effective:
    print(
        f"FRESHNESS_GUARD=FAIL reason=stale_baseline local={local} "
        f"required={effective} newest_branch={remote_branch}",
        file=sys.stderr,
    )
    sys.exit(1)

print("FRESHNESS_GUARD=PASS")
