from pathlib import Path
p=Path("app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt").read_text(encoding="utf-8")
checks={
"TOL_TOKEN":"TOL_TOKEN" in p,
"contract":"TOL_CONTRACT" in p and "الأثر المثبت هو الحكم" in p,
"scope":"scopedText" in p and "effectiveText" in p,
"prompt_injection":"appendLine(TOL_CONTRACT)" in p,
"persist":"putBoolean(\"tol_active\", tolActive)" in p,
"no_authority_expansion":"tol_does_not_expand_authority" in p,
"gate_preserved":"high_impact_gate" in p,
}
bad=[k for k,v in checks.items() if not v]
if bad: raise SystemExit("TOL_POLICY_FAIL:"+",".join(bad))
print("TOL_POLICY=PASS")
