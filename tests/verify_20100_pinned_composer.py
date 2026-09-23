from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CENTER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("PINNED_COMPOSER_20100=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20100, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for needed in [
    "WindowCompat.setDecorFitsSystemWindows(window, false)",
    "private lateinit var composerArea: LinearLayout",
    "private lateinit var executeRow: LinearLayout",
    "WindowInsetsCompat.Type.ime()",
    "WindowInsetsCompat.Type.systemBars()",
    "val safeBottom = maxOf(bars.bottom, if (imeVisible) ime.bottom else 0)",
    "composerArea.setPadding(0, 0, 0, safeBottom)",
    "titleView.visibility = if (imeVisible) View.GONE else View.VISIBLE",
    "status.visibility = if (imeVisible) View.GONE else View.VISIBLE",
    "toolsRow.visibility = if (imeVisible) View.GONE else View.VISIBLE",
    "command.requestRectangleOnScreen(rect, true)",
]:
    req(needed in CENTER, "ui:" + needed)

req("minHeight = 160" not in CENTER, "conversation_min_height_still_forces_overflow")
req("root.addView(\n            composerArea" in CENTER, "composer_not_separate_bottom_area")
req("composerArea.addView(executeRow)" in CENTER, "execute_row_not_pinned_with_composer")
req("composerArea.addView(toolsRow)" in CENTER, "tools_not_part_of_composer_area")

# Known field failures: system nav and IME must both be handled explicitly.
req("bars.bottom" in CENTER, "system_navigation_inset_missing")
req("ime.bottom" in CENTER, "ime_inset_missing")

print("PINNED_COMPOSER_GATE=PASS candidate>=20100 ime+systemBars=inset_aware composer=bottom_area")
