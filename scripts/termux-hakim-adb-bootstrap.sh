#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
: "${TMPDIR:=/data/data/com.termux/files/usr/tmp}"
export TMPDIR
mkdir -p "$TMPDIR" 2>/dev/null || true
set -u

STATE_DIR="$HOME/.omega/adb"
STATE_FILE="$STATE_DIR/last-endpoint"
BIN="$PREFIX/bin/hakim-adb"
BOOT_DIR="$HOME/.termux/boot"
mkdir -p "$STATE_DIR" "$BOOT_DIR" "$PREFIX/bin"
chmod 700 "$STATE_DIR" 2>/dev/null || true

ensure_deps() {
  command -v adb >/dev/null 2>&1 && command -v python >/dev/null 2>&1 && return 0
  pkg install -y android-tools python >/dev/null 2>&1 || return 1
}

start_adb() { adb start-server >/dev/null 2>&1; }

self_ip() {
  python3 - <<'PY' 2>/dev/null
import socket
for target in [("192.168.1.1",80),("8.8.8.8",53)]:
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    try:
        s.connect(target)
        ip=s.getsockname()[0]
        if ip and ip!="0.0.0.0":
            print(ip)
            break
    except Exception:
        pass
    finally:
        s.close()
PY
}

connected_ep() {
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

save_ep() {
  local ep="${1:-}"
  [ -n "$ep" ] || ep="$(connected_ep || true)"
  [ -n "$ep" ] || return 1
  printf '%s\n' "$ep" > "$STATE_FILE"
  chmod 600 "$STATE_FILE"
}

try_ep() {
  local ep="${1:-}" sip host now
  [ -n "$ep" ] || return 1
  sip="$(self_ip)"
  host="${ep%%:*}"
  if [ "$host" != "127.0.0.1" ] && { [ -z "$sip" ] || [ "$host" != "$sip" ]; }; then
    return 1
  fi
  timeout 6 adb connect "$ep" >/dev/null 2>&1 || true
  sleep 0.35
  now="$(connected_ep || true)"
  if [ -n "$now" ]; then save_ep "$now"; return 0; fi
  return 1
}

mdns_eps() {
  adb mdns services 2>/dev/null | awk '/_adb-tls-connect\._tcp/ {print $NF}' | sort -u
}

bounded_self_recover() {
  if [ -x "$HOME/.hakim/adb-self.sh" ]; then
    timeout 8 "$HOME/.hakim/adb-self.sh" >/dev/null 2>&1 || true
    [ -n "$(connected_ep)" ] && return 0
  fi
  return 1
}

connect_best() {
  start_adb
  local ep host
  ep="$(connected_ep || true)"
  if [ -n "$ep" ]; then save_ep "$ep"; return 0; fi

  if [ -f "$STATE_FILE" ]; then
    ep="$(head -1 "$STATE_FILE" | tr -d '\r\n')"
    try_ep "$ep" && return 0
  fi

  for ep in "$@"; do
    [ -n "$ep" ] || continue
    [[ "$ep" == *:* ]] && try_ep "$ep" && return 0
  done

  bounded_self_recover && { save_ep "$(connected_ep)"; return 0; }

  while IFS= read -r ep; do
    [ -n "$ep" ] || continue
    try_ep "$ep" && return 0
  done < <(mdns_eps)

  return 1
}

pair_now() {
  local code="${1:-}"
  [ -n "$code" ] || { printf 'أدخل رمز الاقتران: '; read -r code; }
  local ep
  ep="$(adb mdns services 2>/dev/null | awk '/_adb-tls-pairing\._tcp/ {print $NF; exit}')"
  if [ -z "$ep" ]; then
    echo 'PAIR_PORT_REQUIRED: افتح «إقران الجهاز باستخدام رمز الاقتران» فقط ثم أعد الأمر.'
    return 2
  fi
  adb pair "$ep" "$code" || return 2
  sleep 1
  connect_best
}

selftest() {
  connect_best "$@" || { echo 'NO_CONNECT'; return 2; }
  local first
  first="$(connected_ep)"
  echo "CONNECTED=$first"
  adb disconnect "$first" >/dev/null 2>&1 || true
  sleep 1
  connect_best "$first" || { echo 'RECONNECT_AFTER_DISCONNECT=FAIL'; return 3; }
  echo 'RECONNECT_AFTER_DISCONNECT=PASS'
  adb kill-server >/dev/null 2>&1 || true
  sleep 1
  connect_best "$first" || { echo 'RECONNECT_AFTER_SERVER_RESTART=FAIL'; return 4; }
  echo 'RECONNECT_AFTER_SERVER_RESTART=PASS'
  adb devices -l
}

install_wrapper() {
  cat > "$BIN" <<'WRAP'
#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
: "${TMPDIR:=/data/data/com.termux/files/usr/tmp}"
export TMPDIR
mkdir -p "$TMPDIR" 2>/dev/null || true
exec "$HOME/.omega/adb/hakim-adb-core.sh" "$@"
WRAP
  chmod 700 "$BIN"

  cat > "$BOOT_DIR/hakim-adb-reconnect" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
: "${TMPDIR:=/data/data/com.termux/files/usr/tmp}"
export TMPDIR
mkdir -p "$TMPDIR" 2>/dev/null || true
sleep 8
"$PREFIX/bin/hakim-adb" connect >/dev/null 2>&1 || true
BOOT
  chmod 700 "$BOOT_DIR/hakim-adb-reconnect"

  if [ -f "$HOME/.bashrc" ]; then
    grep -q 'HAKIM_ADB_AUTORECONNECT' "$HOME/.bashrc" || cat >> "$HOME/.bashrc" <<'RC'
# HAKIM_ADB_AUTORECONNECT
( "$PREFIX/bin/hakim-adb" connect >/dev/null 2>&1 || true ) &
RC
  else
    cat > "$HOME/.bashrc" <<'RC'
# HAKIM_ADB_AUTORECONNECT
( "$PREFIX/bin/hakim-adb" connect >/dev/null 2>&1 || true ) &
RC
  fi
}

main() {
  ensure_deps || { echo 'DEPENDENCY_INSTALL_FAILED'; exit 10; }
  local cmd="${1:-connect}"; shift || true
  case "$cmd" in
    connect)
      connect_best "$@" && { echo 'ADB_LOCAL=PASS'; adb devices -l; } || { echo 'ADB_LOCAL=NO_CONNECT'; exit 2; }
      ;;
    pair) pair_now "${1:-}" ;;
    status)
      start_adb
      echo "LAST_ENDPOINT=$(cat "$STATE_FILE" 2>/dev/null || true)"
      adb devices -l
      ;;
    selftest) selftest "$@" ;;
    install)
      install_wrapper
      connect_best "$@" && { echo 'INSTALL=PASS'; adb devices -l; } || { echo 'INSTALL=READY_BUT_NOT_CONNECTED'; exit 2; }
      ;;
    *) echo 'الاستخدام: hakim-adb {connect|status|selftest|pair رمز|install [endpoint]}' ;;
  esac
}
main "$@"
