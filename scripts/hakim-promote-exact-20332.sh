#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_SHA256="182b06dea9ea0e94fa6240d2d2663aa5e5af265283510c69e2a8050e0feb7cf7"
DEFAULT_NAME="Hakim-20332-CI1485-UNSIGNED-FROZEN.apk"
MODE="sign"
INPUT=""

for arg in "$@"; do
  case "$arg" in
    --install) MODE="install" ;;
    --sign-only) MODE="sign" ;;
    *) [[ -z "$INPUT" ]] && INPUT="$arg" || { echo "HAKIM_20332_PROMOTE=FAIL reason=unexpected_argument" >&2; exit 2; } ;;
  esac
done

sha256_file(){
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    python3 - "$1" <<'PY'
import hashlib,sys
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
print(h.hexdigest())
PY
  fi
}

if [[ -z "$INPUT" ]]; then
  for candidate in     "$PWD/$DEFAULT_NAME"     "$HOME/storage/downloads/$DEFAULT_NAME"     "$HOME/Downloads/$DEFAULT_NAME"     "$HOME/.hakim/candidates/$DEFAULT_NAME"
  do
    if [[ -f "$candidate" ]] && [[ "$(sha256_file "$candidate")" == "$EXPECTED_SHA256" ]]; then
      INPUT="$candidate"
      break
    fi
  done
fi

[[ -n "$INPUT" && -f "$INPUT" ]] || {
  echo "HAKIM_20332_PROMOTE=BLOCKED reason=frozen_unsigned_apk_not_found expected_sha256=$EXPECTED_SHA256" >&2
  exit 3
}
[[ "$(sha256_file "$INPUT")" == "$EXPECTED_SHA256" ]] || {
  echo "HAKIM_20332_PROMOTE=FAIL reason=frozen_unsigned_sha_mismatch" >&2
  exit 4
}

OUT_DIR="$HOME/.hakim/releases"
mkdir -p "$OUT_DIR"
chmod 700 "$HOME/.hakim" "$OUT_DIR" 2>/dev/null || true
SIGNED="$OUT_DIR/hakim-field-20332-ed475233.apk"

bash "$ROOT/scripts/hakim-sign-exact-20332-ci1485.sh" "$INPUT" "$SIGNED"

if [[ "$MODE" == "install" ]]; then
  bash "$ROOT/scripts/hakim-install-exact-20332-via-adb.sh" "$SIGNED" "$SIGNED.evidence.json"
  echo "HAKIM_20332_PROMOTE=PASS stage=field_installed_and_same_artifact_verified apk=$SIGNED evidence=$SIGNED.evidence.json"
else
  echo "HAKIM_20332_PROMOTE=PASS stage=d1_signed_ready_not_installed apk=$SIGNED evidence=$SIGNED.evidence.json"
fi
