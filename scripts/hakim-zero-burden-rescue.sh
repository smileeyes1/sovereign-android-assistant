#!/data/data/com.termux/files/usr/bin/bash
set -u

OMEGA="$HOME/.omega"
ADB_DIR="$OMEGA/adb"
LOG_DIR="$OMEGA/logs"
mkdir -p "$ADB_DIR" "$LOG_DIR"
chmod 700 "$OMEGA" "$ADB_DIR" "$LOG_DIR" 2>/dev/null || true

ADB_LOG="$LOG_DIR/hakim-adb-rescue.log"
RDC_LOG="$LOG_DIR/remote-desktop-commander.log"
CORE="$ADB_DIR/hakim-adb-core.sh"
CORE_COMMIT="b7f87cc4a0a620e5ca7ff4c582c758295355db26"
CORE_URL="https://raw.githubusercontent.com/smileeyes1/sovereign-android-assistant/$CORE_COMMIT/scripts/termux-hakim-adb-bootstrap.sh"
SEED_ENDPOINT="192.168.1.11:40409"
RDC_VERSION="0.2.48"
RDC_PATTERN='@wonderwhy-er/desktop-commander.*remote'

say() { printf '%s\n' "$*"; }

ensure_base() {
  command -v curl >/dev/null 2>&1 || pkg install -y curl >/dev/null 2>&1 || return 1
  command -v adb >/dev/null 2>&1 || pkg install -y android-tools >/dev/null 2>&1 || return 1
  command -v python >/dev/null 2>&1 || pkg install -y python >/dev/null 2>&1 || return 1
  if ! command -v npx >/dev/null 2>&1; then
    pkg install -y nodejs-lts >/dev/null 2>&1 || pkg install -y nodejs >/dev/null 2>&1 || return 1
  fi
}

install_adb_core() {
  curl -fsSL "$CORE_URL" -o "$CORE.tmp" || return 1
  bash -n "$CORE.tmp" || return 1
  mv -f "$CORE.tmp" "$CORE"
  chmod 700 "$CORE"
  return 0
}

stop_stale_remote_maintenance() {
  local pids i
  pids="$(pgrep -f "$RDC_PATTERN" 2>/dev/null || true)"
  [ -n "$pids" ] || return 0
  printf '%s\n' "$(date -Iseconds) HAKIM_RDC_RESTART reason=rescue_refresh pids=$(printf '%s' "$pids" | tr '\n' ',')" >>"$RDC_LOG"
  kill $pids 2>/dev/null || true
  for i in 1 2 3 4 5; do
    pgrep -f "$RDC_PATTERN" >/dev/null 2>&1 || return 0
    sleep 1
  done
  pids="$(pgrep -f "$RDC_PATTERN" 2>/dev/null || true)"
  [ -z "$pids" ] || kill -9 $pids 2>/dev/null || true
  sleep 1
}

show_fresh_pairing_instructions() {
  say 'REMOTE_MAINTENANCE=PAIRING_REQUIRED'
  say 'حكيم: يلزم تحقق جهاز جديد. استخدم الرمز الظاهر أدناه فقط؛ الرمز القديم المنتهي لا يُستخدم.'
  grep -E 'https://mcp\.desktopcommander\.app/device/verify|Enter this code|^[[:space:]]*[A-Z0-9]{4}-[A-Z0-9]{4}[[:space:]]*$|Code expires' "$RDC_LOG" 2>/dev/null | tail -n 8 || true
}

start_remote_maintenance() {
  # مسار rescue ينعش النقل نفسه؛ وجود PID وحده ليس دليل اتصال.
  # لا نحذف هوية الجهاز أو الرموز المحفوظة. إذا احتاج المزود تحققًا جديدًا نعرض الرمز الحديث محليًا.
  stop_stale_remote_maintenance
  printf '%s\n' "$(date -Iseconds) HAKIM_RDC_START version=$RDC_VERSION" >>"$RDC_LOG"
  nohup npx --yes "@wonderwhy-er/desktop-commander@$RDC_VERSION" remote >>"$RDC_LOG" 2>&1 </dev/null &
  local pid="$!" i
  printf '%s\n' "$pid" > "$OMEGA/remote-desktop-commander.pid"
  chmod 600 "$OMEGA/remote-desktop-commander.pid" 2>/dev/null || true

  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    if grep -Eq 'Device ready|Device marked as online|Channel subscribed' "$RDC_LOG" 2>/dev/null; then
      say 'REMOTE_MAINTENANCE=ONLINE'
      return 0
    fi
    if grep -Eq 'Please complete authentication|Enter this code when prompted|Code expires in [0-9]+ minutes' "$RDC_LOG" 2>/dev/null; then
      show_fresh_pairing_instructions
      return 2
    fi
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done

  if ! kill -0 "$pid" 2>/dev/null && ! pgrep -f "$RDC_PATTERN" >/dev/null 2>&1; then
    say 'REMOTE_MAINTENANCE=FAILED'
    tail -n 8 "$RDC_LOG" 2>/dev/null | sed -E 's#https?://[^[:space:]]+#[رابط]#g' || true
    return 1
  fi

  say 'REMOTE_MAINTENANCE=WAITING_UNPROVEN'
  say 'حكيم: العملية تعمل لكن الاتصال البعيد لم يُثبت بعد؛ لن يُعلن نجاحًا وهميًا.'
  return 3
}

recover_adb() {
  : > "$ADB_LOG"
  "$CORE" install "$SEED_ENDPOINT" >>"$ADB_LOG" 2>&1 || true
  "$CORE" selftest "$SEED_ENDPOINT" >>"$ADB_LOG" 2>&1 || true
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{ok=1} END{exit !ok}'
}

main() {
  say 'حكيم: بدء الاستعادة الذاتية…'
  ensure_base || { say 'RESCUE=DEPENDENCY_FAILED'; exit 10; }
  install_adb_core || { say 'RESCUE=CORE_DOWNLOAD_FAILED'; exit 11; }

  RDC=FAIL
  ADB=FAIL
  start_remote_maintenance
  rdc_rc=$?
  case "$rdc_rc" in
    0) RDC=ONLINE ;;
    2) RDC=PAIRING_REQUIRED ;;
    3) RDC=WAITING_UNPROVEN ;;
    *) RDC=FAIL ;;
  esac
  recover_adb && ADB=PASS || true

  say "REMOTE_MAINTENANCE_STATE=$RDC"
  say "ADB_LOCAL=$ADB"

  if [ "$RDC" = ONLINE ] || [ "$ADB" = PASS ]; then
    say 'RESCUE=READY'
    exit 0
  fi
  if [ "$RDC" = PAIRING_REQUIRED ]; then
    say 'RESCUE=LOCAL_VERIFY_NEEDED'
    exit 3
  fi

  # لا نلغي أي بيانات أو نمنح صلاحيات خفية. إن بقيت القناتان مغلقتين، الموافقة المحلية لأندرويد هي المانع الوحيد.
  say 'RESCUE=LOCAL_ANDROID_APPROVAL_NEEDED'
  exit 2
}

main "$@"
