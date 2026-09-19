from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")

def require(ok,msg):
    if not ok: raise SystemExit("P0: "+msg)

expected_permissions={
"android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE",
"android.permission.FOREGROUND_SERVICE","android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
"android.permission.RECEIVE_BOOT_COMPLETED","android.permission.POST_NOTIFICATIONS",
"android.permission.CAMERA","android.permission.RECORD_AUDIO","android.permission.MODIFY_AUDIO_SETTINGS",
"android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_FINE_LOCATION",
"android.permission.ACCESS_WIFI_STATE","android.permission.CHANGE_WIFI_MULTICAST_STATE",
"android.permission.WRITE_EXTERNAL_STORAGE"}
actual_permissions=set(re.findall(r'<uses-permission\s+android:name="([^"]+)"',manifest))
require(actual_permissions==expected_permissions,"تغير سطح أذونات ٢٠٠٥٦")

for activity in ("HakimProfessionalHubActivity","HakimQuranActivity"):
    m=re.search(r'<activity\s+[^>]*android:name="\.'+activity+r'"[^>]*>',manifest,flags=re.S)
    require(m is not None,"النشاط الاحترافي مفقود: "+activity)
    require('android:exported="false"' in m.group(0),"النشاط الاحترافي أصبح exported: "+activity)

expected_dependencies={
"implementation 'com.squareup.okhttp3:okhttp:4.12.0'",
"implementation 'androidx.core:core-ktx:1.15.0'",
"implementation 'com.github.MuntashirAkon:libadb-android:3.1.1'",
"implementation 'org.conscrypt:conscrypt-android:2.5.3'"}
actual_dependencies={line.strip() for line in build.splitlines()
    if line.strip().startswith(("implementation ","api ","runtimeOnly ","compileOnly ","kapt ","annotationProcessor "))}
require(actual_dependencies==expected_dependencies,"تغيرت اعتماديات ٢٠٠٥٦")
m=re.search(r"versionCode\s+(\d+)",build)
require(m and int(m.group(1))>=20056,"بوابة الأمن لا تغطي خط الاحتراف ٢٠٠٥٦ فأعلى")
print("HAKIM_20056_PERMISSION_SURFACE=PASS")
print("HAKIM_20056_PRIVATE_ACTIVITIES=PASS")
print("HAKIM_20056_DEPENDENCY_SURFACE=PASS")
