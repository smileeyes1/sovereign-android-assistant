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

start_remote_maintenance() {
  if pgrep -f '@wonderwhy-er/desktop-commander.*remote' >/dev/null 2>&1; then
    return 0
  fi
  nohup npx --yes "@wonderwhy-er/desktop-commander@$RDC_VERSION" remote >>"$RDC_LOG" 2>&1 </dev/null &
  printf '%s\n' "$!" > "$OMEGA/remote-desktop-commander.pid"
  chmod 600 "$OMEGA/remote-desktop-commander.pid" 2>/dev/null || true
  sleep 4
  pgrep -f '@wonderwhy-er/desktop-commander.*remote' >/dev/null 2>&1
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
  start_remote_maintenance && RDC=STARTED || true
  recover_adb && ADB=PASS || true

  say "REMOTE_MAINTENANCE=$RDC"
  say "ADB_LOCAL=$ADB"

  if [ "$RDC" = STARTED ] || [ "$ADB" = PASS ]; then
    say 'RESCUE=READY'
    exit 0
  fi

  # لا نعيد الاقتران تلقائيا ولا نلغي أي بيانات. إن بقيت القناتان مغلقتين، الموافقة المحلية لأندرويد هي المانع الوحيد.
  say 'RESCUE=LOCAL_ANDROID_APPROVAL_NEEDED'
  exit 2
}

main "$@"
