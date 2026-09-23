from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
CODE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimQuranicGovernance.kt").read_text(encoding="utf-8")
DIRECTOR = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimIntentDirector.kt").read_text(encoding="utf-8")
CONSTITUTION = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt").read_text(encoding="utf-8")
DOC = (ROOT / "governance/QURANIC_GOVERNANCE.md").read_text(encoding="utf-8")
REL = (ROOT / "governance/RELIGIOUS_INTEGRITY.md").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("QURANIC_GOVERNANCE_20104=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20104, "version")
req("applicationId 'ps.hakim.stable'" in BUILD, "package_identity")

for needed in [
    "الحكيم",
    "العليم والخبير",
    "الرقيب",
    "الحق",
    "الرحمن والرحيم",
    "الحجرات ٦",
    "الإسراء ٣٦",
    "النحل ٩٠",
    "النساء ٥٨",
    "المائدة ٢",
    "البقرة ٢٥٦",
]:
    req(needed in CODE + DOC, "anchor:" + needed)

for forbidden_claim in [
    '"technical_causality_claimed", true',
    '"letters_used_as_hidden_algorithm", true',
]:
    req(forbidden_claim not in CODE, "occult_claim:" + forbidden_claim)

for needed in [
    "لا تجعل أسماء الله أو حروف القرآن آلية تقنية أو غيبية",
    "اختر الوسائل الدنيوية بالعلم والدليل والاختبار",
]:
    req(needed in CODE, "boundary:" + needed)

req("HakimQuranicGovernance.compactInstruction()" in DIRECTOR, "director_not_governed")
req("HakimQuranicGovernance.compactInstruction()" in CONSTITUTION, "constitution_prompt_not_governed")
req("HakimQuranicGovernance.canonicalJson()" in CONSTITUTION, "canonical_state_missing")
req("حروفها إلى أوزان أو طلاسم" in REL, "religious_integrity_boundary_missing")

for needed in [
    "لا يفرض حكيم محتوى دينيًا",
    "لا يُولَّد من النموذج",
    "فشل التحقق",
]:
    req(needed in DOC + REL, "integrity:" + needed)

print("QURANIC_GOVERNANCE_GATE=PASS candidate>=20104 values=true occult=false technical_means=evidence")
