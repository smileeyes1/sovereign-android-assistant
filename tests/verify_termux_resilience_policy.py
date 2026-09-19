from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = (ROOT / "scripts/hakim-termux-resilience.sh").read_text(encoding="utf-8")

def require(cond: bool, msg: str) -> None:
    if not cond:
        raise SystemExit(msg)

require("settings_enable_monitor_phantom_procs" in SCRIPT, "P0: إعداد phantom-process الرسمي غير موجود")
require("PREV_MONITOR_PHANTOM" in SCRIPT and "rollback()" in SCRIPT, "P0: لا يوجد baseline/rollback")
require("termux-wake-lock" in SCRIPT, "P0: wake-lock غير مستخدم")
require("max_phantom_processes" in SCRIPT, "P0: لا يتم رصد حد phantom-processes")
require("device_config put activity_manager max_phantom_processes" not in SCRIPT, "P0: ممنوع تغيير max_phantom_processes")
require("verifier_verify_adb_installs" not in SCRIPT, "P0: ممنوع تعطيل verifier")
require("package_verifier_enable" not in SCRIPT, "P0: ممنوع تعطيل package verifier")
require("pm disable" not in SCRIPT and "pm disable-user" not in SCRIPT, "P0: ممنوع تعطيل حزم الحماية")
require("su " not in SCRIPT and "\nsu\n" not in SCRIPT, "P0: الأداة لا تحتاج root")
require("settings delete global settings_enable_monitor_phantom_procs" in SCRIPT, "P0: rollback لا يعيد حالة null")
require("self_adb_offline" in SCRIPT and "adb_missing" in SCRIPT, "P0: fail-closed عند غياب ADB")
require("sdk_below_34" in SCRIPT, "P0: لا يوجد قيد Android 14+")
require("\\ntry:" not in SCRIPT, "P0: استخراج endpoint يحتوي escape مكسور")

print("TERMUX_RESILIENCE_POLICY=PASS")
