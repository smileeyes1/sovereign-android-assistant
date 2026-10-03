from pathlib import Path
import json, re, subprocess, sys

ROOT=Path(__file__).resolve().parents[1]
state=json.loads((ROOT/"governance/HAKIM_ACTIVE_STATE.json").read_text(encoding="utf-8"))
gradle=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def fail(reason):
    raise SystemExit("SOURCE_ANCESTRY_GATE=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",gradle)
if not m:
    fail("candidate_version_missing")
candidate=int(m.group(1))
parent=state["android"]["latest_source_parent"]
parent_version=int(parent["version_code"])
parent_commit=str(parent["commit"]).strip()

if candidate <= parent_version:
    fail(f"candidate_not_newer candidate={candidate} parent={parent_version}")

try:
    subprocess.run(
        ["git","cat-file","-e",parent_commit+"^{commit}"],
        cwd=ROOT,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL
    )
except subprocess.CalledProcessError:
    fail("parent_commit_not_present_in_checkout")

try:
    subprocess.run(
        ["git","merge-base","--is-ancestor",parent_commit,"HEAD"],
        cwd=ROOT,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL
    )
except subprocess.CalledProcessError:
    fail("head_not_descendant_of_verified_parent")

head=subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip()
if head == parent_commit:
    fail("candidate_equals_parent")

print(f"SOURCE_ANCESTRY_GATE=PASS parent={parent_commit} parent_version={parent_version} candidate={candidate} head={head}")
