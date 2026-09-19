from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
def text(p): return (ROOT/p).read_text(encoding="utf-8")
def require(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)
manifest=text("app/src/main/AndroidManifest.xml")
build=text("app/build.gradle")
script=text("scripts/hakim-termux-app-recovery.sh")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))
activity=re.search(r'<activity\s+[^>]*android:name="\.HakimAgentsChatActivity".*?</activity>',manifest,re.S)
require(activity is not None,"نشاط حكيم الرئيسي مفقود")
block=activity.group(0)
require('android:exported="true"' in block,"نشاط حكيم الرئيسي لم يعد exported")
require('android.intent.action.VIEW' in block and 'android.intent.category.BROWSABLE' in block,"رابط الفتح المباشر غير معلن")
require('android:scheme="hakim"' in block and 'android:host="open"' in block,"hakim://open غير مثبت")
for token in ['boot_id','pidof "$PKG"','termux-notification','hakim://open','waiting_user_wake','auto_started_no_handoff_needed']:
    require(token in script,"عنصر handoff مفقود: "+token)
for forbidden in [' adb ','adb -',' su ','sudo','pm clear','force-stop','settings put','device_config put']:
    require(forbidden not in (' '+script+' '),"handoff يوسع السلطة أو يغير النظام: "+forbidden)
require('sleep "${HAKIM_BOOT_HANDOFF_GRACE:-45}"' in script,"لا توجد مهلة bounded قبل تدخل المستخدم")
vc=re.search(r'versionCode\s+(\d+)',build)
require(vc and int(vc.group(1))==20060,"الإصدار يجب أن يكون ٢٠٠٦٠")
require(policy['current_field_version']==20059,"خط الميدان يجب أن يسجل ٢٠٠٥٩ المثبت")
require(policy['current_candidate_version']==20060,"السياسة لا تسجل ٢٠٠٦٠")
require(policy['last_verified_field_version']==20055,"لا يجوز ترقية آخر خط مقبول قبل إغلاق reboot")
require(policy['field_evidence']['version_code']==20059 and policy['field_evidence']['stability']=='FAILED',"فشل reboot في ٢٠٠٥٩ غير محفوظ")
require(policy['field_evidence']['stability_evidence'].get('post_reboot_hakim_pid')=='absent',"دليل غياب PID بعد reboot مفقود")
print('HAKIM_20060_SAFE_DEEP_LINK=PASS')
print('HAKIM_20060_TERMUX_BOOT_HANDOFF=PASS')
print('HAKIM_20060_NO_PRIVILEGE_ESCALATION=PASS')
