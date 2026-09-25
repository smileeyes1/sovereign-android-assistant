# جسر Google Workspace المملوك للمستخدم — حكيم ٢٠٢٠٤

الحالة: **مرشح مصدر؛ إعداد OAuth السحابي والتحقق الميداني غير مثبتين بعد.**

## الهوية المثبتة
- الحزمة: `ps.hakim.stable`
- شهادة D1 SHA-256: `D1:3E:7A:A8:27:1C:B6:D3:2A:EC:21:57:CC:5B:A4:FA:FD:22:69:57:EB:0C:73:1E:9C:EB:A8:27:BF:78:B0:D3`
- شهادة D1 SHA-1 المستخرجة مباشرة من APK موقّع ومطابقة SHA-256: `17:5D:2F:49:B1:F9:4C:B6:94:D1:79:92:7A:B8:EA:54:FC:68:4F:8C`

## عقد الوصول
- المستخدم يطلب Google Workspace صراحة؛ لا يُحوَّل PDF/Word/HTML العادي إلى السحابة تلقائيًا.
- النطاق الوحيد في ٢٠٢٠٤: `https://www.googleapis.com/auth/drive.file`.
- لا WebView مضمن لموافقة Google.
- رمز الوصول قصير العمر يبقى في الذاكرة فقط؛ لا SharedPreferences ولا AndroidKeyStore ولا مستودع.
- المصدر التعليمي يُنشأ محليًا أولًا من مواصفة حكيم نفسها.
- DOCX يمكن استيراده إلى Google Docs، وPPTX إلى Google Slides، وXLSX إلى Google Sheets؛ وطلب Drive العام يرفع PDF المحلي.
- الملف والحساب للمستخدم نفسه؛ لا حساب Google مركزي يحتفظ بالمخرجات.
- رفض/فشل Google لا يحذف المصدر المحلي ولا يحوّل المهمة إلى مدفوع.

## بوابة الحقيقة
وجود الكود لا يثبت أن OAuth Android مسجل أو Drive API مفعّل في مشروع Google. تبقى:
`oauth_android_client_registered=false`
`drive_api_enabled=false`
`field_verified=false`
حتى يتم التحقق الخارجي الفعلي من الإعداد ثم تشغيل الموافقة ورفع ملف من نفس APK الموقّع.
