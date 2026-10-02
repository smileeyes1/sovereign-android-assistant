from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
constitution = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimConstitution.kt").read_text(encoding="utf-8")
selfcheck = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt").read_text(encoding="utf-8")

required = [
    'SOVEREIGN-QURAN-V5-2026-10-02',
    'conflict_resolution_narrow_change',
    'uncertainty_budget_enforced',
    'required_expected_evidence_test_state',
    'prevent_first',
    'minimal_escalation_only',
    'غياب الدليل لا يُملأ بالتخمين',
    'لا صلاحية بلا تفويض ولا اعتماد بلا اختبار مناسب',
    'لا جمع أو حفظ أو كشف زائد للبيانات',
    'المطلوب→المتوقع→الدليل→الاختبار→النتيجة→الحالة',
]
for token in required:
    if token not in constitution:
        raise SystemExit(f"GOVERNANCE_V5_MISSING:{token}")

for flag in [
    'conflict_resolution_narrow_change',
    'uncertainty_budget_enforced',
    'required_expected_evidence_test_state',
    'prevent_first',
    'minimal_escalation_only',
]:
    if flag not in selfcheck:
        raise SystemExit(f"SELF_CHECK_MISSING:{flag}")

if 'SOVEREIGN-QURAN-V5' not in selfcheck:
    raise SystemExit("SELF_CHECK_VERSION_NOT_V5")

print("SOVEREIGN_GOVERNANCE_V5=PASS")
