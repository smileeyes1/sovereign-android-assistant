#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX

HOME_HAKIM="$HOME/.hakim"
OMEGA="$HOME/.omega"
LOG_DIR="$OMEGA/logs"
LOG="$LOG_DIR/hakim-local-rescue.log"
mkdir -p "$HOME_HAKIM/bin" "$LOG_DIR"
chmod 700 "$HOME_HAKIM" "$HOME_HAKIM/bin" "$OMEGA" "$LOG_DIR" 2>/dev/null || true

say(){ printf '%s\n' "$*"; }
log(){ printf '%s %s\n' "$(date -Iseconds 2>/dev/null || date)" "$*" >>"$LOG"; }

ensure_adb(){
  command -v adb >/dev/null 2>&1 && return 0
  command -v pkg >/dev/null 2>&1 || return 1
  pkg install -y android-tools >/dev/null 2>&1
}

self_ip(){
  python3 - <<'PY' 2>/dev/null
import socket
for target in [("192.168.1.1",80),("8.8.8.8",53)]:
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    try:
        s.connect(target)
        ip=s.getsockname()[0]
        if ip and ip!="0.0.0.0":
            print(ip); break
    except Exception:
        pass
    finally:
        s.close()
PY
}

connected_ep(){
  local sip ep state rest host
  sip="$(self_ip)"
  while read -r ep state rest; do
    [ "$state" = "device" ] || continue
    host="${ep%%:*}"
    if [ "$host" = "127.0.0.1" ] || { [ -n "$sip" ] && [ "$host" = "$sip" ]; }; then
      printf '%s\n' "$ep"
      return 0
    fi
  done < <(adb devices 2>/dev/null | tail -n +2)
  return 1
}

connected(){ [ -n "$(connected_ep)" ]; }

recover(){
  adb start-server >/dev/null 2>&1 || true
  connected && return 0

  if command -v hakim-adb >/dev/null 2>&1; then
    timeout 12 hakim-adb connect >/dev/null 2>&1 || true
    connected && return 0
  fi

  if [ -x "$HOME/.hakim/adb-self.sh" ]; then
    timeout 10 "$HOME/.hakim/adb-self.sh" >/dev/null 2>&1 || true
    connected && return 0
  fi

  if [ -x "$HOME/.hakim/bin/hakim-control" ]; then
    timeout 12 "$HOME/.hakim/bin/hakim-control" adb_connect >/dev/null 2>&1 || true
    connected && return 0
  fi

  return 1
}

status(){
  say "HAKIM_LOCAL_RESCUE=STATUS"
  say "ADB_AVAILABLE=$(command -v adb >/dev/null 2>&1 && echo true || echo false)"
  say "ADB_CONNECTED=$(connected && echo true || echo false)"
  say "ADB_ENDPOINT=$(connected_ep || true)"
  say "TERMUX_CONTROL=$([ -x "$HOME/.hakim/bin/hakim-control" ] && echo true || echo false)"
  say "REMOTE_DESKTOP_REQUIRED=false"
  say "EXTERNAL_USAGE_QUOTA_REQUIRED=false"
  say "PAID_PROVIDER_REQUIRED=false"
}

main(){
  say 'حكيم: استعادة التحكم المحلي عبر Termux…'
  ensure_adb || { say 'HAKIM_LOCAL_RESCUE=BLOCKED reason=adb_dependency_missing'; exit 10; }
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true

  if recover; then
    log "recover=pass"
    status
    say 'HAKIM_LOCAL_RESCUE=PASS'
    exit 0
  fi

  log "recover=blocked local_android_pairing_required"
  status
  say 'HAKIM_LOCAL_RESCUE=BLOCKED reason=local_android_pairing_required'
  exit 2
}

case "${1:-recover}" in
  recover) main ;;
  status) status ;;
  *) echo 'usage: hakim-zero-burden-rescue.sh [recover|status]' >&2; exit 64 ;;
esac
