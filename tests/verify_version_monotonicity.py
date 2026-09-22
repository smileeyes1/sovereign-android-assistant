from pathlib import Path
import re, os
g=Path("app/build.gradle").read_text(encoding="utf-8")
m=re.search(r"versionCode\s+(\d+)",g)
assert m, "VERSION_CODE_MISSING"
candidate=int(m.group(1))
floor=int(os.environ.get("HAKIM_VERSION_FLOOR","20088"))
assert candidate>=floor, f"VERSION_ROLLBACK_BLOCKED: candidate={candidate} floor={floor}"
print(f"VERSION_MONOTONICITY=PASS candidate={candidate} floor={floor}")
