from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
DOC = (ROOT / "governance/QURANIC_VALUES_CONSTITUTION.md").read_text(encoding="utf-8")
VALUES = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimQuranicValues.kt").read_text(encoding="utf-8")
MATRIX = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimWisdomMatrix.kt").read_text(encoding="utf-8")
DIRECTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")
CONSTITUTION = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt").read_text(encoding="utf-8")
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("QURANIC_WISDOM_20103=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20103, "version")
req("versionName '2.1.03-quranic-wisdom-governance'" in BUILD, "version_name")

for needed in [
    "القرآن الكريم أصل الهدى",
    "لا يجعل الوحي آلية تقنية",
    "لا تُستخدم حسابات الحروف/الأعداد/الأوفاق",
    "لا تنبؤ بالغيب",
    "الوسائل التقنية",
    "النحل ١٦:٩٠",
    "الإسراء ١٧:٣٦",
    "الحجرات ٤٩:٦",
]:
    req(needed in DOC, "doc:" + needed)

for needed in [
    "لا تنسب للوحي أثرًا تقنيًا غير مثبت",
    "لا تدّعِ عصمة أو علم غيب",
    "لا تستخدم الحروف أو حساب الجمل أو الأوفاق",
    "الوسائل التقنية تُختار بالعلم والعقل والدليل والاختبار",
]:
    req(needed in VALUES, "runtime:" + needed)

req("HakimQuranicValues.instruction()" in DIRECTOR, "director_missing_values")
req("HakimQuranicValues.instruction()" in CONSTITUTION, "constitution_missing_values")
req("لا أسماء/حروف كآلية خفية أو سحرية" in CONSTITUTION, "mysticism_guard_missing")

for needed in [
    "!c.supported",
    "!c.authorized",
    "!c.directReturn",
    "!c.officialChannel",
    "quality",
    "reliability",
    "privacy",
    "costEfficiency",
    "latency",
    "reversibility",
]:
    req(needed in MATRIX, "matrix:" + needed)

req("HakimWisdomMatrix.choose(candidates)" in REGISTRY, "registry_not_using_matrix")

print("QURANIC_WISDOM_20103=PASS values=bounded non_mystical=true matrix=fail_closed")
