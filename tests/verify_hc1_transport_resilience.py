from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
relay = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")
pair = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimPairingActivity.kt").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)

version = re.search(r"versionCode\s+(\d+)", build)
require(version and int(version.group(1)) >= 20028, "P0: إصلاح HC1 بلا رقم إصدار جديد")
require("listenerExecutor" in relay and "workerExecutor" in relay, "P0: الاستماع والنتائج ما زالا في طابور واحد")
require("listenerExecutor.execute { loop(app) }" in relay, "P0: مستمع HC1 ليس على منفذ مستقل")
require(relay.count("workerExecutor.execute") >= 2, "P0: ack/approval لا يستخدمان منفذ العمل المستقل")
require("pollViaResultWebhook" in relay and '"hc1_poll"' in relay, "P0: fallback لصندوق النتائج مفقود")
require('^hakim-cmd-' in relay, "P0: proxy غير مقيد بمواضيع حكيم")
require('"relay_proxy_cursor"' in relay and 'ifBlank { "10m" }' in relay, "P0: cursor polling المقتصد مفقود")
require("sawInvalidLine" in relay and "!sawInvalidLine" in relay, "P0: proxy يقبل ردًا غير موثوق")
require('HakimHealthBeacon.sendAsync(applicationContext, "pairing_success")' in pair, "P0: نبضة الاقتران المستقلة مفقودة")
require("REQUEST_INSTALL_PACKAGES" not in manifest, "P0: عادت صلاحية المصادر غير المعروفة")
print("HC1_TRANSPORT_RESILIENCE=PASS")
