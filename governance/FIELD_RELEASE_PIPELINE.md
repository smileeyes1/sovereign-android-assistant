# خط الإصدار الميداني المستدام

المصدر يبني في CI فقط. الهاتف لا يعيد بناء Android محليًا إذا كان AAPT2 غير متوافق مع معماريته.

## السلسلة الحتمية
١. استخرج LAST_VERIFIED_BASELINE من دليل ميداني، لا من اسم مجلد أو ذاكرة محادثة.
٢. ارفض candidate <= baseline.
٣. شغّل الاختبارات وCI.
٤. نزّل artifact المحدد بالـ run والـ digest وتحقق من SHA-256 قبل الفك.
٥. اختر release/app-release-unsigned.apk فقط.
٦. وقّع محليًا على الهاتف بمفتاح D1؛ المفتاح وكلمة المرور لا يغادران الهاتف.
٧. تحقق قبل التثبيت: package=ps.hakim.stable، versionCode أعلى من baseline، signer=D1.
٨. التحديث فقط in-place؛ ممنوع uninstall أو clear-data أو حزمة بديلة.
٩. لا يُعلن النجاح بعد طلب التثبيت؛ يلزم POST_INSTALL: الإصدار + التوقيع + تشغيل التطبيق + UI_OBSERVED.
١٠. أي فشل وسيلة يذهب إلى DIAGNOSE→REROUTE؛ لا يعاد المسار نفسه بلا معلومة جديدة.

## حالات الدليل
SOURCE_VERIFIED → CI_VERIFIED → ARTIFACT_VERIFIED → D1_SIGNED → PREINSTALL_VERIFIED → INSTALL_REQUESTED → OS_INSTALLED → UI_OBSERVED → PROMOTED_BASELINE.
لا يجوز القفز بين الحالات بالاستنتاج.


## الرجوع الأمامي
إذا فشل مرشح بعد تثبيته فلا يُستخدم downgrade تلقائي على Android. يُنشأ مرشح جديد بإصدار أعلى من **آخر مصدر ميداني ثبتت مطابقته**، ثم يمر من CI وتوقيع D1 والتحقق قبل التثبيت وبعده. إذا كان مصدر آخر نجاح ميداني غير مثبت المطابقة، يتوقف الرجوع الآلي عند مانع SOURCE_MAPPING_NOT_PROVEN ولا يُخمن المصدر.
