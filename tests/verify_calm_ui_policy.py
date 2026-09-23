from pathlib import Path

p=Path("app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")

must=[
    "جاهز لتحقيق مقصدك",
    "ماذا تريد أن أنجز؟",
    'actionButton("أنجز")',
    'actionButton("إرفاق")',
    'actionButton("صوت")',
    'actionButton("المتصفح")',
    'actionButton("إدارة")',
    "visibility = View.GONE",
]
for x in must:
    assert x in p, x

for x in [
    'actionButton("شات جي بي تي")',
    'actionButton("جيميني")',
    'actionButton("كلود")',
    'actionButton("ديب سيك")',
    "نفّذ الغاية كاملة",
    "إلى شات جي بي تي",
    "إلى أي تطبيق",
    "ن★ التكيفية:",
    "التحديث التلقائي يحتاج السماح",
    "فحص/تهيئة التحديث التلقائي",
    "openInstallPermissionSettings",
]:
    assert x not in p, x

assert "لم يُعتمد النجاح" in p
print("CALM_UI_POLICY=PASS")
