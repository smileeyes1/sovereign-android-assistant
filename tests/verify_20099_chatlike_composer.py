from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
LOOP = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimExecutiveLoop.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("CHATLIKE_COMPOSER_20099=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20099, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for needed in [
    "operationsExpanded = false",
    "maxLines = 1",
    "TextUtils.TruncateAt.END",
    "latestOperationText",
    "WindowInsetsCompat.Type.ime()",
    "toolsRow.visibility = if (imeVisible) View.GONE else View.VISIBLE",
    "if (imeVisible && operationsExpanded)",
    "minLines = 1",
    "maxLines = 4",
    "attachmentStatus.visibility = View.GONE",
]:
    req(needed in CENTER, "ui:" + needed)

req("fun latestOperationText" in LOOP, "compact_operation_api_missing")
req("operations.text = HakimExecutiveLoop.operationText(this)" in CENTER, "expandable_details_missing")
req("operations.text = HakimExecutiveLoop.latestOperationText(this)" in CENTER, "compact_default_missing")

# Known field failure: full operation history may exist, but must not be the default collapsed view.
collapsed = CENTER.split("private fun refreshOperations()",1)[1]
req("if (operationsExpanded)" in collapsed, "operations_always_expanded")

print("CHATLIKE_COMPOSER_GATE=PASS candidate>=20099 progress=compact expandable=true ime=secondary_tools_hidden composer=preserved")
