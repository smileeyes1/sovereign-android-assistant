from pathlib import Path

p=Path("app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")

must=[
    "جاهز",
    "اكتب رسالتك إلى حكيم",
    'actionButton("أنجز")',
    'actionButton("إرفاق")',
    'actionButton("صوت")',
    'actionButton("الإعدادات")',
]
for x in must:
    assert x in p, x

for x in [
    'actionButton("المتصفح")',
    'actionButton("إدارة")',
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

assert ("لم يُعتمد النجاح" in p) or ("لن أعتبر المهمة ناجحة" in p) or ("ليس نجاحًا للمهمة" in p)
assert "ScrollView" in p
assert "appendConversation" in p
print("CALM_UI_POLICY=PASS visible_conversation=true")
