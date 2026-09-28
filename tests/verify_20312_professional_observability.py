from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
BUILD=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
LOOP=(APP/"HakimExecutiveLoop.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(ok, reason):
    if not ok:
        raise SystemExit("PRO_OBSERVABILITY_20312=FAIL reason="+reason)

m=re.search(r"versionCode\s+(\d+)",BUILD)
req(m is not None and int(m.group(1))==20312,"version")
for token in ["network-diagnostics","lan-survey","extender-survey","professional-observability","continuity"]:
    req(token in BUILD,"version_lineage:"+token)

local=LOOP.split("fun operationText",1)[1].split("fun providerInstruction",1)[0]
probe=LOOP.split("fun publicStatus",1)[1].split("fun operationText",1)[0]

for token in [
    'p.getString("goal"',
    '"المقصد: $goal"',
    '"آخر تقدم مثبت:"',
    '"الخطوة التالية:"',
    '"قناة التنفيذ:"',
    'Phase.GATED.name',
    'Phase.WAITING_EXTERNAL.name',
    'HakimExecutionFabric.status(context)'
]:
    req(token in local,"local_panel:"+token)

for forbidden in ['getString("goal"', 'getString("events"', 'getString("acceptance"', 'last_material_gain']:
    req(forbidden not in probe,"relay_privacy:"+forbidden)

req('python3 tests/verify_20312_professional_observability.py' in WORKFLOW,"workflow_test")
req('test "$VERSION_CODE" = "20312"' in WORKFLOW,"workflow_version")

print("PRO_OBSERVABILITY_20312=PASS local_goal=true last_gain=true blocker=true next_step=true execution_channel=true relay_privacy=true")
