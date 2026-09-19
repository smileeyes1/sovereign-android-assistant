#!/data/data/com.termux/files/usr/bin/bash
set -u
BASE="$HOME/.hakim/termux-resilience"
STATE="$BASE/state.env"
LOG="$BASE/resilience.log"
mkdir -p "$BASE"; chmod 700 "$BASE" 2>/dev/null || true
log(){ printf "%s %s\n" "$(date -Iseconds)" "$*" >>"$LOG"; }
EP="$(python3 -c 'import json,os; p=os.path.expanduser("~/.hakim/adb/self-state.json");\ntry: print(json.load(open(p)).get("endpoint",""))\nexcept Exception: print("")')"
if ! command -v adb >/dev/null 2>&1; then echo "HAKIM_RESILIENCE=BLOCKED reason=adb_missing"; exit 20; fi
if [ -z "$EP" ] || ! adb -s "$EP" get-state >/dev/null 2>&1; then echo "HAKIM_RESILIENCE=BLOCKED reason=self_adb_offline"; exit 21; fi
read_monitor(){ adb -s "$EP" shell settings get global settings_enable_monitor_phantom_procs 2>/dev/null | tr -d "\r"; }
read_sdk(){ adb -s "$EP" shell getprop ro.build.version.sdk 2>/dev/null | tr -d "\r"; }
status(){
  sdk="$(read_sdk)"; cur="$(read_monitor)"; maxp="$(adb -s "$EP" shell device_config get activity_manager max_phantom_processes 2>/dev/null | tr -d "\r")"
  echo "ANDROID_SDK=$sdk"; echo "MONITOR_PHANTOM=$cur"; echo "MAX_PHANTOM_PROCESSES=$maxp"
  echo "TERMUX_BUCKET=$(adb -s "$EP" shell am get-standby-bucket com.termux 2>/dev/null | tr -d "\r")"
  echo "TERMUX_BOOT_BUCKET=$(adb -s "$EP" shell am get-standby-bucket com.termux.boot 2>/dev/null | tr -d "\r")"
  echo "TOTAL_PROCESSES=$(ps -A 2>/dev/null | wc -l | tr -d " ")"
}
rollback(){
  [ -s "$STATE" ] || { echo "HAKIM_RESILIENCE=BLOCKED reason=no_baseline"; exit 22; }
  . "$STATE"
  if [ "$PREV_MONITOR_PHANTOM" = "null" ] || [ -z "$PREV_MONITOR_PHANTOM" ]; then
    adb -s "$EP" shell settings delete global settings_enable_monitor_phantom_procs >/dev/null 2>&1 || true
  else
    adb -s "$EP" shell settings put global settings_enable_monitor_phantom_procs "$PREV_MONITOR_PHANTOM" >/dev/null
  fi
  echo "HAKIM_RESILIENCE=ROLLED_BACK monitor_phantom=$(read_monitor)"; log "rollback $(read_monitor)"
}
apply(){
  sdk="$(read_sdk)"; before="$(read_monitor)"
  case "$sdk" in ""|*[!0-9]*) echo "HAKIM_RESILIENCE=BLOCKED reason=unknown_sdk"; exit 23;; esac
  if [ ! -s "$STATE" ]; then printf "PREV_MONITOR_PHANTOM=%q\nCAPTURED_AT=%q\n" "$before" "$(date -Iseconds)" >"$STATE"; chmod 600 "$STATE"; fi
  if [ "$sdk" -lt 34 ]; then echo "HAKIM_RESILIENCE=NO_CHANGE reason=sdk_below_34"; status; exit 0; fi
  if [ "$before" != "false" ]; then adb -s "$EP" shell settings put global settings_enable_monitor_phantom_procs false >/dev/null; fi
  after="$(read_monitor)"
  if [ "$after" != "false" ]; then echo "HAKIM_RESILIENCE=BLOCKED reason=setting_not_applied before=$before after=$after"; log "apply_failed before=$before after=$after"; exit 24; fi
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  echo "HAKIM_RESILIENCE=PASS"; log "apply_ok before=$before after=$after sdk=$sdk"; status
}
case "${1:-apply}" in
  apply) apply ;;
  status) status ;;
  rollback) rollback ;;
  *) echo "usage: $0 [apply|status|rollback]" >&2; exit 2 ;;
esac
