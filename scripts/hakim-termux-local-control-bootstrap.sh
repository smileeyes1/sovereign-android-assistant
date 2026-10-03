#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
: "${TMPDIR:=/data/data/com.termux/files/usr/tmp}"
export TMPDIR
mkdir -p "$TMPDIR" 2>/dev/null || true
set -euo pipefail

BASE="$HOME/.hakim/termux-control"
BIN_DIR="$HOME/.hakim/bin"
CONTROL="$BIN_DIR/hakim-control"
PROPS_DIR="$HOME/.termux"
PROPS="$PROPS_DIR/termux.properties"
BACKUP="$BASE/termux.properties.before"
BOOT_DIR="$HOME/.termux/boot"
BOOT_HOOK="$BOOT_DIR/hakim-local-control"
PACKAGE="ps.hakim.stable"
RUN_PERMISSION="com.termux.permission.RUN_COMMAND"

mkdir -p "$BASE" "$BIN_DIR" "$PROPS_DIR" "$BOOT_DIR"
chmod 700 "$HOME/.hakim" "$BASE" "$BIN_DIR" "$BOOT_DIR" 2>/dev/null || true

say(){ printf '%s\n' "$*"; }

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

ensure_adb(){
  command -v adb >/dev/null 2>&1 && return 0
  pkg install -y android-tools >/dev/null 2>&1
}

enable_external_apps(){
  if [ ! -e "$BACKUP" ]; then
    if [ -f "$PROPS" ]; then cp -p "$PROPS" "$BACKUP"; else : > "$BACKUP"; fi
    chmod 600 "$BACKUP" 2>/dev/null || true
  fi
  local tmp="$BASE/termux.properties.tmp"
  if [ -f "$PROPS" ]; then
    grep -vE '^[[:space:]]*allow-external-apps[[:space:]]*=' "$PROPS" > "$tmp" || true
  else
    : > "$tmp"
  fi
  printf 'allow-external-apps=true\n' >> "$tmp"
  mv -f "$tmp" "$PROPS"
  chmod 600 "$PROPS" 2>/dev/null || true
  command -v termux-reload-settings >/dev/null 2>&1 && termux-reload-settings >/dev/null 2>&1 || true
}

install_control(){
  cat > "$CONTROL" <<'CTRL'
#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
: "${TMPDIR:=/data/data/com.termux/files/usr/tmp}"
export TMPDIR
mkdir -p "$TMPDIR" 2>/dev/null || true
set -euo pipefail
PROFILE="${1:-status}"

connected_ep(){
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}'
}
adb_connect(){
  adb start-server >/dev/null 2>&1 || true
  local ep
  ep="$(connected_ep || true)"
  if [ -n "$ep" ]; then
    echo "HAKIM_TERMUX_ADB=PASS endpoint=$ep"
    return 0
  fi

  if [ -x "$HOME/.hakim/adb-self.sh" ]; then
    timeout 8 "$HOME/.hakim/adb-self.sh" >/dev/null 2>&1 || true
    ep="$(connected_ep || true)"
    if [ -n "$ep" ]; then
      echo "HAKIM_TERMUX_ADB=PASS endpoint=$ep"
      return 0
    fi
  fi

  if [ -s "$HOME/.omega/adb/last-endpoint" ]; then
    ep="$(head -1 "$HOME/.omega/adb/last-endpoint" | tr -d '\r\n')"
    [ -n "$ep" ] && timeout 6 adb connect "$ep" >/dev/null 2>&1 || true
    ep="$(connected_ep || true)"
    if [ -n "$ep" ]; then
      echo "HAKIM_TERMUX_ADB=PASS endpoint=$ep"
      return 0
    fi
  fi

  echo "HAKIM_TERMUX_ADB=OFFLINE"
  return 2
}

case "$PROFILE" in
  status)
    printf 'HAKIM_TERMUX_CONTROL=PASS\n'
    printf 'TERMUX_PREFIX=%s\n' "${PREFIX:-/data/data/com.termux/files/usr}"
    printf 'ADB_AVAILABLE=%s\n' "$(command -v adb >/dev/null 2>&1 && echo true || echo false)"
    printf 'HAKIM_ADB_AVAILABLE=%s\n' "$(command -v hakim-adb >/dev/null 2>&1 && echo true || echo false)"
    printf 'ADB_CONNECTED=%s\n' "$([ -n "$(connected_ep)" ] && echo true || echo false)"
    ;;
  adb_status)
    if command -v hakim-adb >/dev/null 2>&1; then hakim-adb status; else adb devices -l; fi
    ;;
  adb_connect)
    adb_connect
    ;;
  adb_selftest)
    command -v hakim-adb >/dev/null 2>&1 || { echo "HAKIM_TERMUX_SELFTEST=BLOCKED reason=hakim_adb_missing"; exit 3; }
    hakim-adb selftest
    ;;
  hakim_status)
    ep="$(connected_ep || true)"
    [ -n "$ep" ] || { echo "HAKIM_TERMUX_APP=OFFLINE"; exit 2; }
    ver="$(adb -s "$ep" shell dumpsys package ps.hakim.stable 2>/dev/null | sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1 | tr -d '\r')"
    pid="$(adb -s "$ep" shell pidof ps.hakim.stable 2>/dev/null | tr -d '\r')"
    echo "HAKIM_TERMUX_APP=PASS versionCode=${ver:-unknown} running=$([ -n "$pid" ] && echo true || echo false)"
    ;;
  hakim_restart)
    ep="$(connected_ep || true)"
    [ -n "$ep" ] || adb_connect >/dev/null 2>&1 || { echo "HAKIM_TERMUX_RESTART=BLOCKED reason=self_adb_offline"; exit 2; }
    ep="$(connected_ep || true)"
    adb -s "$ep" shell am force-stop ps.hakim.stable >/dev/null 2>&1 || true
    sleep 1
    adb -s "$ep" shell monkey -p ps.hakim.stable -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || {
      echo "HAKIM_TERMUX_RESTART=FAIL"
      exit 3
    }
    echo "HAKIM_TERMUX_RESTART=PASS"
    ;;
  local_rescue)
    command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
    adb_connect
    ;;
  resilience_status)
    if [ -x "$HOME/.hakim/bin/hakim-termux-resilience" ]; then
      "$HOME/.hakim/bin/hakim-termux-resilience" status
    elif [ -x "$HOME/.hakim/termux-resilience/hakim-termux-resilience.sh" ]; then
      "$HOME/.hakim/termux-resilience/hakim-termux-resilience.sh" status
    else
      echo "HAKIM_TERMUX_RESILIENCE=NOT_INSTALLED"
    fi
    ;;
  *)
    echo "HAKIM_TERMUX_CONTROL=BLOCKED reason=profile_not_allowed" >&2
    exit 64
    ;;
esac
CTRL
  chmod 700 "$CONTROL"

  cat > "$BOOT_HOOK" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/bash
: "${PREFIX:=/data/data/com.termux/files/usr}"
export PREFIX
: "${TMPDIR:=/data/data/com.termux/files/usr/tmp}"
export TMPDIR
mkdir -p "$TMPDIR" 2>/dev/null || true
sleep 8
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
adb start-server >/dev/null 2>&1 || true
if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{ok=1} END{exit !ok}'; then
  [ -x "$HOME/.hakim/adb-self.sh" ] && timeout 8 "$HOME/.hakim/adb-self.sh" >/dev/null 2>&1 || true
fi
if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{ok=1} END{exit !ok}'; then
  if [ -s "$HOME/.omega/adb/last-endpoint" ]; then
    ep="$(head -1 "$HOME/.omega/adb/last-endpoint" | tr -d '\r\n')"
    [ -n "$ep" ] && timeout 6 adb connect "$ep" >/dev/null 2>&1 || true
  fi
fi
BOOT
  chmod 700 "$BOOT_HOOK"
}

status(){
  local ext="false" wrapper="false" boot="false" grant="unknown" adb_state="offline"
  grep -qE '^[[:space:]]*allow-external-apps[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$PROPS" 2>/dev/null && ext="true"
  [ -x "$CONTROL" ] && wrapper="true"
  [ -x "$BOOT_HOOK" ] && boot="true"
  if command -v adb >/dev/null 2>&1; then
    local ep
    ep="$(connected_ep || true)"
    if [ -n "$ep" ]; then
      adb_state="online"
      if adb -s "$ep" shell dumpsys package "$PACKAGE" 2>/dev/null | grep -A80 'runtime permissions:' | grep -q "$RUN_PERMISSION: granted=true"; then
        grant="true"
      else
        grant="false"
      fi
    fi
  fi
  echo "HAKIM_TERMUX_BOOTSTRAP=STATUS"
  echo "ALLOW_EXTERNAL_APPS=$ext"
  echo "CONTROL_WRAPPER=$wrapper"
  echo "BOOT_HOOK=$boot"
  echo "RUN_COMMAND_GRANTED=$grant"
  echo "LOCAL_ADB=$adb_state"
}

install(){
  enable_external_apps
  install_control
  ensure_adb || true
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1 || true
  status
  echo "HAKIM_TERMUX_BOOTSTRAP=PASS permission_gate=ANDROID_USER_PROMPT_IN_HAKIM"
  echo "EXTERNAL_USAGE_QUOTA_REQUIRED=false"
  echo "PAID_PROVIDER_REQUIRED=false"
  echo "LOCAL_FIRST_AFTER_BOOTSTRAP=true"
}

rollback(){
  if [ -e "$BACKUP" ]; then
    if [ -s "$BACKUP" ]; then cp -p "$BACKUP" "$PROPS"; else rm -f "$PROPS"; fi
  else
    if [ -f "$PROPS" ]; then
      local tmp="$BASE/termux.properties.rollback"
      grep -vE '^[[:space:]]*allow-external-apps[[:space:]]*=' "$PROPS" > "$tmp" || true
      mv -f "$tmp" "$PROPS"
    fi
  fi
  command -v termux-reload-settings >/dev/null 2>&1 && termux-reload-settings >/dev/null 2>&1 || true
  rm -f "$CONTROL" "$BOOT_HOOK"
  if command -v adb >/dev/null 2>&1; then
    local ep
    ep="$(connected_ep || true)"
    [ -n "$ep" ] && adb -s "$ep" shell pm revoke "$PACKAGE" "$RUN_PERMISSION" >/dev/null 2>&1 || true
  fi
  echo "HAKIM_TERMUX_BOOTSTRAP=ROLLED_BACK"
}

case "${1:-install}" in
  install) install ;;
  status) status ;;
  rollback) rollback ;;
  *) echo "usage: $0 [install|status|rollback]" >&2; exit 2 ;;
esac
