#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# HAKIM 20045 — local advanced reasoning runtime for Android/Termux.
# Provider-independent by construction: binds only to 127.0.0.1 and needs no API key.
# The ~1.28 GB model is NEVER downloaded unless HAKIM_ALLOW_MODEL_DOWNLOAD=YES is set.

ROOT="${HAKIM_LOCAL_ROOT:-$HOME/.hakim-local}"
SRC="$ROOT/src/llama.cpp"
BUILD="$SRC/build"
BIN="$BUILD/bin/llama-server"
MODELS="$ROOT/models"
MODEL="$MODELS/Qwen3-1.7B-Q4_K_M.gguf"
LOGS="$ROOT/logs"
RUN="$ROOT/run"
PIDFILE="$RUN/llama-server.pid"
LOGFILE="$LOGS/llama-server.log"
PORT="${HAKIM_LOCAL_PORT:-8080}"
HOST="127.0.0.1"

LLAMA_REPO="https://github.com/ggml-org/llama.cpp.git"
LLAMA_REF="v0.4.1"
LLAMA_COMMIT="b29c606"
MODEL_URL="https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/daeb8e2d528a760970442092f6bf1e55c3b659eb/Qwen3-1.7B-Q4_K_M.gguf?download=true"
MODEL_SHA256="d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
MODEL_APPROX_BYTES="1280000000"
MODEL_ALIAS="hakim-local-qwen3-1.7b-q4"

mkdir -p "$ROOT" "$MODELS" "$LOGS" "$RUN" "$ROOT/src"

say(){ printf '%s\n' "$*"; }
die(){ say "HAKIM_LOCAL=FAIL reason=$*"; exit 1; }

require_termux(){
  [[ -n "${PREFIX:-}" && "$PREFIX" == *"com.termux"* ]] || die "TERMUX_REQUIRED"
  [[ "$(uname -m)" == "aarch64" || "$(uname -m)" == "arm64" ]] || die "ARM64_REQUIRED"
}

verify_model(){
  [[ -f "$MODEL" ]] || return 1
  local got
  got="$(sha256sum "$MODEL" | awk '{print $1}')"
  [[ "$got" == "$MODEL_SHA256" ]]
}

pid_alive(){
  [[ -f "$PIDFILE" ]] || return 1
  local p
  p="$(cat "$PIDFILE" 2>/dev/null || true)"
  [[ "$p" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$p" 2>/dev/null
}

install_deps(){
  require_termux
  pkg update -y
  pkg install -y git cmake clang make curl libandroid-spawn
  say "HAKIM_LOCAL_DEPS=PASS"
}

fetch_source(){
  require_termux
  if [[ ! -d "$SRC/.git" ]]; then
    rm -rf "$SRC.tmp"
    git clone --filter=blob:none "$LLAMA_REPO" "$SRC.tmp"
    mv "$SRC.tmp" "$SRC"
  fi
  git -C "$SRC" fetch --tags --force origin "$LLAMA_REF"
  git -C "$SRC" checkout --detach "$LLAMA_REF"
  local head
  head="$(git -C "$SRC" rev-parse --short=7 HEAD)"
  [[ "$head" == "$LLAMA_COMMIT" ]] || die "LLAMA_COMMIT_MISMATCH:$head"
  say "HAKIM_LLAMA_SOURCE=PASS ref=$LLAMA_REF commit=$head"
}

build_runtime(){
  fetch_source
  cmake -S "$SRC" -B "$BUILD" -DCMAKE_BUILD_TYPE=Release -DGGML_OPENMP=OFF
  cmake --build "$BUILD" -j "${HAKIM_BUILD_JOBS:-2}" --target llama-server
  [[ -x "$BIN" ]] || die "LLAMA_SERVER_MISSING"
  say "HAKIM_LLAMA_BUILD=PASS"
}

download_model(){
  require_termux
  [[ "${HAKIM_ALLOW_MODEL_DOWNLOAD:-NO}" == "YES" ]] ||
    die "MODEL_DOWNLOAD_NOT_AUTHORIZED approx_bytes=$MODEL_APPROX_BYTES set_HAKIM_ALLOW_MODEL_DOWNLOAD=YES"
  if verify_model; then
    say "HAKIM_MODEL=PASS already_verified=true"
    return
  fi
  local part="$MODEL.part"
  say "HAKIM_MODEL_DOWNLOAD=START approx_bytes=$MODEL_APPROX_BYTES"
  curl -fL --retry 4 --retry-delay 3 -C - "$MODEL_URL" -o "$part"
  local got
  got="$(sha256sum "$part" | awk '{print $1}')"
  [[ "$got" == "$MODEL_SHA256" ]] || {
    rm -f "$part"
    die "MODEL_SHA256_MISMATCH:$got"
  }
  mv "$part" "$MODEL"
  say "HAKIM_MODEL=PASS sha256=$MODEL_SHA256"
}

stop_server(){
  if pid_alive; then
    local p
    p="$(cat "$PIDFILE")"
    kill "$p" 2>/dev/null || true
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      kill -0 "$p" 2>/dev/null || break
      sleep 0.3
    done
    kill -0 "$p" 2>/dev/null && kill -9 "$p" 2>/dev/null || true
  fi
  rm -f "$PIDFILE"
  say "HAKIM_LOCAL_SERVER=STOPPED"
}

start_server(){
  require_termux
  [[ -x "$BIN" ]] || die "LLAMA_SERVER_NOT_BUILT"
  verify_model || die "MODEL_NOT_VERIFIED"
  if pid_alive; then
    doctor_server
    return
  fi
  command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
  nohup "$BIN" \
    -m "$MODEL" \
    --alias "$MODEL_ALIAS" \
    --host "$HOST" \
    --port "$PORT" \
    -c "${HAKIM_LOCAL_CONTEXT:-4096}" \
    -t "${HAKIM_LOCAL_THREADS:-4}" \
    >"$LOGFILE" 2>&1 &
  local p=$!
  printf '%s' "$p" > "$PIDFILE"

  for _ in $(seq 1 90); do
    if ! kill -0 "$p" 2>/dev/null; then
      tail -n 80 "$LOGFILE" >&2 || true
      rm -f "$PIDFILE"
      die "SERVER_EXITED_EARLY"
    fi
    if curl -fsS --max-time 2 "http://$HOST:$PORT/v1/models" |
       grep -Fq "$MODEL_ALIAS"; then
      say "HAKIM_LOCAL_SERVER=PASS endpoint=http://$HOST:$PORT/v1/chat/completions pid=$p"
      return
    fi
    sleep 1
  done
  tail -n 80 "$LOGFILE" >&2 || true
  stop_server
  die "SERVER_READINESS_TIMEOUT"
}

doctor_server(){
  require_termux
  local source=false binary=false model=false process=false api=false
  [[ -d "$SRC/.git" ]] && source=true
  [[ -x "$BIN" ]] && binary=true
  verify_model && model=true
  pid_alive && process=true
  if curl -fsS --max-time 2 "http://$HOST:$PORT/v1/models" |
     grep -Fq "$MODEL_ALIAS"; then api=true; fi
  printf '{"runtime":"HAKIM_LOCAL_20045","loopback_only":true,"host":"%s","port":%s,"source":%s,"binary":%s,"model_verified":%s,"process":%s,"api_ready":%s}\n' \
    "$HOST" "$PORT" "$source" "$binary" "$model" "$process" "$api"
  [[ "$api" == true ]]
}

smoke_test(){
  doctor_server >/dev/null || die "LOCAL_API_NOT_READY"
  local models response
  models="$(curl -fsS --max-time 5 "http://$HOST:$PORT/v1/models")"
  grep -Fq "$MODEL_ALIAS" <<<"$models" || die "MODEL_ALIAS_NOT_EXPOSED"
  response="$(curl -fsS --max-time 120 \
    -H 'Content-Type: application/json' \
    -d '{"model":"'"$MODEL_ALIAS"'","temperature":0,"stream":false,"max_tokens":32,"messages":[{"role":"user","content":"أجب بكلمة واحدة فقط: حاضر"}]}' \
    "http://$HOST:$PORT/v1/chat/completions")"
  grep -Fq '"choices"' <<<"$response" || die "CHAT_COMPLETION_INVALID"
  say "HAKIM_LOCAL_SMOKE=PASS"
}

status(){
  doctor_server || true
  say "MODEL_PATH=$MODEL"
  say "MODEL_SHA256=$MODEL_SHA256"
  say "LOG_PATH=$LOGFILE"
}

usage(){
  cat <<'EOF'
حكيم — محرك الاستدلال المحلي ٢٠٠٤٥
الاستخدام:
  hakim-local-model-termux.sh install-deps
  hakim-local-model-termux.sh build
  HAKIM_ALLOW_MODEL_DOWNLOAD=YES hakim-local-model-termux.sh download-model
  hakim-local-model-termux.sh start
  hakim-local-model-termux.sh smoke
  hakim-local-model-termux.sh stop
  hakim-local-model-termux.sh status

تهيئة كاملة مع النموذج (تنزيل ~1.28GB ويتطلب تفويض البيانات صراحة):
  HAKIM_ALLOW_MODEL_DOWNLOAD=YES hakim-local-model-termux.sh bootstrap
EOF
}

case "${1:-status}" in
  install-deps) install_deps ;;
  build) install_deps; build_runtime ;;
  download-model) download_model ;;
  start) start_server ;;
  smoke) smoke_test ;;
  stop) stop_server ;;
  status|doctor) status ;;
  bootstrap)
    install_deps
    build_runtime
    download_model
    start_server
    smoke_test
    ;;
  *) usage; exit 2 ;;
esac
