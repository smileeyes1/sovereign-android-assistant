from pathlib import Path
p=Path("governance/NO_HYPOTHETICAL_GATE.md").read_text(encoding="utf-8")
need=["احتمال وجود بوابة بشرية ليس بوابة","GATE_EVIDENCE","READY وSIGNED وDISPATCHED وINSTALL_REQUESTED","DIAGNOSE → REROUTE → EXECUTE","OS_INSTALLED","UI_OBSERVED","attempted_actions"]
for x in need: assert x in p, x
print("NO_HYPOTHETICAL_GATE_POLICY=PASS")
