from pathlib import Path
p=Path("governance/NO_PREMATURE_STOP.md").read_text(encoding="utf-8")
must=["فشل الوسيلة لا يساوي فشل المهمة","DIAGNOSE → REROUTE → EXECUTE","GATE فقط","WAIT فقط","لا تتوقف عند شرح","لا تعرض سبب فشل داخلي","لا COMPLETE إلا بدليل","لا تعيد محاولة مطابقة"]
for x in must: assert x in p,x
print("NO_PREMATURE_STOP_POLICY=PASS")
