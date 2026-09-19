#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
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

connected_ep() {
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}'
}

save_ep() {
  local ep="${1:-}"
  [ -n "$ep" ] || ep="$(connected_ep)"
  [ -n "$ep" ] || return 1
  printf '%s\n' "$ep" > "$STATE_FILE"
  chmod 600 "$STATE_FILE"
}

try_ep() {
  local ep="${1:-}"
  [ -n "$ep" ] || return 1
  adb connect "$ep" >/dev/null 2>&1 || true
  sleep 0.35
  local now
  now="$(connected_ep)"
  if [ -n "$now" ]; then save_ep "$now"; return 0; fi
  return 1
}

mdns_eps() {
  adb mdns services 2>/dev/null | awk '/_adb-tls-connect\._tcp/ {print $NF}' | sort -u
}

candidate_ips() {
  {
    ip -4 addr show wlan0 2>/dev/null | awk '/inet /{sub(/\/.*/,"",$2);print $2}'
    ifconfig wlan0 2>/dev/null | awk '/inet /{for(i=1;i<=NF;i++) if($i=="inet") print $(i+1)}'
    getprop dhcp.wlan0.ipaddress 2>/dev/null
    getprop dhcp.wifi.ipaddress 2>/dev/null
  } | awk '/^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/' | sort -u
}

listener_eps() {
  if command -v ss >/dev/null 2>&1; then
    ss -lnt 2>/dev/null | awk '
      NR>1 {
        a=$4; sub(/^.*:/,"",a);
        if (a ~ /^[0-9]+$/ && a>=30000 && a<=50000) print "127.0.0.1:" a
      }' | sort -u
  fi
}

scan_host() {
  local host="$1"
  python - "$host" <<'PY'
import socket,sys,concurrent.futures
host=sys.argv[1]
ports=range(30000,50001)
def chk(p):
    try:
        s=socket.socket(); s.settimeout(0.018)
        ok=(s.connect_ex((host,p))==0); s.close()
        return p if ok else None
    except Exception:
        return None
with concurrent.futures.ThreadPoolExecutor(max_workers=320) as ex:
    for p in ex.map(chk,ports,chunksize=64):
        if p: print(f"{host}:{p}")
PY
}

seed_hosts() {
  local x host
  for x in "$@"; do
    host="${x%%:*}"
    printf '%s\n' "$host"
  done
  if [ -f "$STATE_FILE" ]; then
    x="$(head -1 "$STATE_FILE" | tr -d '\r\n')"
    [ -n "$x" ] && printf '%s\n' "${x%%:*}"
  fi
  candidate_ips
  printf '%s\n' 127.0.0.1
}

connect_best() {
  start_adb
  local ep host
  ep="$(connected_ep)"
  if [ -n "$ep" ]; then save_ep "$ep"; return 0; fi

  if [ -f "$STATE_FILE" ]; then
    ep="$(head -1 "$STATE_FILE" | tr -d '\r\n')"
    try_ep "$ep" && return 0
  fi

  for ep in "$@"; do
    [ -n "$ep" ] || continue
    [[ "$ep" == *:* ]] && try_ep "$ep" && return 0
  done

  while IFS= read -r ep; do
    [ -n "$ep" ] || continue
    try_ep "$ep" && return 0
  done < <(mdns_eps)

  while IFS= read -r ep; do
    [ -n "$ep" ] || continue
    try_ep "$ep" && return 0
  done < <(listener_eps)

  while IFS= read -r host; do
    [ -n "$host" ] || continue
    while IFS= read -r ep; do
      [ -n "$ep" ] || continue
      try_ep "$ep" && return 0
    done < <(scan_host "$host" 2>/dev/null)
  done < <(seed_hosts "$@" | sort -u)

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
exec "$HOME/.omega/adb/hakim-adb-core.sh" "$@"
WRAP
  chmod 700 "$BIN"

  cat > "$BOOT_DIR/hakim-adb-reconnect" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
sleep 8
"$PREFIX/bin/hakim-adb" connect >/dev/null 2>&1 || true
BOOT
  chmod 700 "$BOOT_DIR/hakim-adb-reconnect"

  if [ -f "$HOME/.bashrc" ]; then
    grep -q 'HAKIM_ADB_AUTORECONNECT' "$HOME/.bashrc" || cat >> "$HOME/.bashrc" <<'RC'
# HAKIM_ADB_AUTORECONNECT
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
( "$PREFIX/bin/hakim-adb" connect >/dev/null 2>&1 || true ) &
RC
  else
    cat > "$HOME/.bashrc" <<'RC'
# HAKIM_ADB_AUTORECONNECT
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
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
