#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/.hakim-sovereign"
BIN="$ROOT/bin"
LOG="$ROOT/logs"
RUN="$ROOT/run"
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_SRC="$SRC_DIR/hakim-sovereign-runtime.py"
RUNTIME="$BIN/hakim-sovereign-runtime.py"
PID="$RUN/runtime.pid"

mkdir -p "$BIN" "$LOG" "$RUN"
chmod 700 "$ROOT" "$BIN" "$LOG" "$RUN"

if [[ ! -f "$RUNTIME_SRC" ]]; then
  echo "RUNTIME_SOURCE_MISSING=$RUNTIME_SRC" >&2
  exit 2
fi

cp "$RUNTIME_SRC" "$RUNTIME"
chmod 700 "$RUNTIME"

start_runtime() {
  if [[ -f "$PID" ]]; then
    old="$(cat "$PID" 2>/dev/null || true)"
    if [[ -n "$old" ]] && kill -0 "$old" 2>/dev/null; then
      echo "HAKIM_SOVEREIGN_RUNTIME_ALREADY_RUNNING pid=$old"
      return 0
    fi
  fi
  nohup python3 "$RUNTIME" --state-dir "$ROOT" --host 127.0.0.1 --port 8765     >>"$LOG/runtime.log" 2>&1 &
  echo $! > "$PID"
  chmod 600 "$PID"
}

start_runtime

for _ in $(seq 1 30); do
  if python3 - <<'PY' >/dev/null 2>&1
import json, urllib.request
with urllib.request.urlopen("http://127.0.0.1:8765/health",timeout=1.5) as r:
    obj=json.load(r)
assert obj.get("ok") is True
assert obj.get("loopback_only") is True
assert obj.get("external_provider_required") is False
PY
  then
    echo "HAKIM_SOVEREIGN_ENV=READY"
    echo "STATE_DIR=$ROOT"
    echo "ENDPOINT=http://127.0.0.1:8765"
    exit 0
  fi
  sleep 1
done

echo "HAKIM_SOVEREIGN_ENV=NOT_READY" >&2
tail -80 "$LOG/runtime.log" >&2 || true
exit 3
