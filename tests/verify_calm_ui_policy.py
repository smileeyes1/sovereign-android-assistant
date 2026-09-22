from pathlib import Path
p=Path("app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
must=["جاهز لتحقيق مقصدك","ماذا تريد أن أنجز؟","actionButton(\"أنجز\")","actionButton(\"التفاصيل\")","visibility = View.GONE"]
for x in must:
    assert x in p, x
for x in ["نفّذ الغاية كاملة","إلى شات جي بي تي","إلى أي تطبيق","فتح/بحث في حكيم","ن★ التكيفية:"]:
    assert x not in p, x
print("CALM_UI_POLICY=PASS")
