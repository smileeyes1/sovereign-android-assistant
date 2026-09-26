from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
POLICY=(APP/"HakimArabicPolicy.kt").read_text(encoding="utf-8")
CONSTITUTION=(APP/"HakimConstitution.kt").read_text(encoding="utf-8")
MAIN=(APP/"MainActivity.kt").read_text(encoding="utf-8")
HOME=(APP/"UnifiedHomeActivity.kt").read_text(encoding="utf-8")
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
WORKFLOW=(ROOT/".github/workflows/android.yml").read_text(encoding="utf-8")

def req(v, reason):
    if not v:
        raise SystemExit("ARABIC_POLICY_20306=FAIL reason="+reason)

req('android:supportsRtl="true"' in MANIFEST,"manifest_rtl")
for token in [
    "ARABIC-FIRST-RTL-2026-09-26-v1",
    "arabic_default",
    "rtl_default",
    "right_alignment_default",
    "٠١٢٣٤٥٦٧٨٩",
    "math_bidi_isolation_required",
    "technical_ltr_exception",
    "STUDENT-EYE",
    "OVERLAP",
    "CLIP",
]:
    req(token in POLICY,"policy:"+token)
req("HakimArabicPolicy.install(context)" in CONSTITUTION,"constitution_install")
req("HakimArabicPolicy.promptContract()" in CONSTITUTION,"constitution_prompt")
req('"arabic_policy"' in CONSTITUTION,"constitution_status")
for name,text in [("main",MAIN),("home",HOME),("center",CENTER)]:
    req("HakimArabicPolicy.applyUiDefaults(root)" in text,"ui:"+name)
req("python3 tests/verify_20306_arabic_policy.py" in WORKFLOW,"workflow_gate")

print("ARABIC_POLICY_20306=PASS rtl=true arabic_default=true")
