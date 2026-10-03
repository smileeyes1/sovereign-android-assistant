#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_SHA256="944c08b4b57d8c23504206d3a61e06c183ab8a3bbe7fde0c48c9c644ad8f445a"
DEFAULT_NAME="Hakim-20333-CI1509-UNSIGNED-FROZEN.apk"
MODE="sign"
INPUT=""

for arg in "$@"; do
  case "$arg" in
    --install) MODE="install" ;;
    --sign-only) MODE="sign" ;;
    *) [[ -z "$INPUT" ]] && INPUT="$arg" || { echo "HAKIM_20333_PROMOTE=FAIL reason=unexpected_argument" >&2; exit 2; } ;;
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
  echo "HAKIM_20333_PROMOTE=BLOCKED reason=frozen_unsigned_apk_not_found expected_sha256=$EXPECTED_SHA256" >&2
  exit 3
}
[[ "$(sha256_file "$INPUT")" == "$EXPECTED_SHA256" ]] || {
  echo "HAKIM_20333_PROMOTE=FAIL reason=frozen_unsigned_sha_mismatch" >&2
  exit 4
}

OUT_DIR="$HOME/.hakim/releases"
mkdir -p "$OUT_DIR"
chmod 700 "$HOME/.hakim" "$OUT_DIR" 2>/dev/null || true
SIGNED="$OUT_DIR/hakim-field-20333-9ef17d8c.apk"

bash "$ROOT/scripts/hakim-sign-exact-20333-ci1509.sh" "$INPUT" "$SIGNED"

if [[ "$MODE" == "install" ]]; then
  bash "$ROOT/scripts/hakim-install-exact-20333-via-adb.sh" "$SIGNED" "$SIGNED.evidence.json"
  echo "HAKIM_20333_PROMOTE=PASS stage=field_installed_and_same_artifact_verified apk=$SIGNED evidence=$SIGNED.evidence.json"
else
  echo "HAKIM_20333_PROMOTE=PASS stage=d1_signed_ready_not_installed apk=$SIGNED evidence=$SIGNED.evidence.json"
fi
