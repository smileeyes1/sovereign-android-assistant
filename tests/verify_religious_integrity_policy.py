from pathlib import Path
p=Path("governance/RELIGIOUS_INTEGRITY.md").read_text(encoding="utf-8")
must=["لا يُولَّد من النموذج","لا يُنسب إلى النبي","يُفصل الرأي والخلاف المعتبر","الدليل العلمي والهندسي","لا إكراه","فشل التحقق من المصدر","المسلم وغير المسلم","قابل للتتبع"]
for x in must: assert x in p, x
print("RELIGIOUS_INTEGRITY_POLICY=PASS")
