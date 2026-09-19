from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
home = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")
net = (ROOT / "app/src/main/res/xml/network_security_config.xml").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")

m = re.search(r"versionCode\s+(\d+)", build)
assert m and int(m.group(1)) >= 20043
assert 'android:name=".UnifiedHomeActivity"' in manifest
assert 'android.intent.category.HOME' in manifest
assert 'android.intent.category.DEFAULT' in manifest
assert 'android:networkSecurityConfig="@xml/network_security_config"' in manifest
assert 'android:usesCleartextTraffic="false"' in manifest
assert 'http://localhost:8790/' in home
assert 'ACTION_HOME_SETTINGS' in home
assert '<base-config cleartextTrafficPermitted="false"' in net
assert '>localhost<' in net
assert '>127.0.0.1<' in net
print("HAKIM_HOME_LAUNCHER_20043=PASS")
