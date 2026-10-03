#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_SHA256="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"
DEFAULT_NAME="Hakim-20334-CI1521-UNSIGNED-FROZEN.apk"
MODE="sign"; INPUT=""
for arg in "$@"; do case "$arg" in --install) MODE="install";; --sign-only) MODE="sign";; *) [[ -z "$INPUT" ]]&&INPUT="$arg"||{ echo "HAKIM_20334_PROMOTE=FAIL reason=unexpected_argument" >&2; exit 2; };; esac; done
sha256_file(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"|awk '{print $1}'; else python3 - "$1" <<'PY'
import hashlib,sys
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
print(h.hexdigest())
PY
fi; }
if [[ -z "$INPUT" ]]; then for c in "$PWD/$DEFAULT_NAME" "$HOME/storage/downloads/$DEFAULT_NAME" "$HOME/Downloads/$DEFAULT_NAME" "$HOME/.hakim/candidates/$DEFAULT_NAME"; do [[ -f "$c" && "$(sha256_file "$c")" == "$EXPECTED_SHA256" ]]&&{ INPUT="$c"; break; }; done; fi
[[ -n "$INPUT" && -f "$INPUT" ]]||{ echo "HAKIM_20334_PROMOTE=BLOCKED reason=frozen_unsigned_apk_not_found expected_sha256=$EXPECTED_SHA256" >&2; exit 3; }
[[ "$(sha256_file "$INPUT")" == "$EXPECTED_SHA256" ]]||{ echo "HAKIM_20334_PROMOTE=FAIL reason=frozen_unsigned_sha_mismatch" >&2; exit 4; }
OUT="$HOME/.hakim/releases"; mkdir -p "$OUT"; chmod 700 "$HOME/.hakim" "$OUT" 2>/dev/null||true
SIGNED="$OUT/hakim-field-20334-a5fd08ce.apk"
bash "$ROOT/scripts/hakim-sign-exact-20334-ci1521.sh" "$INPUT" "$SIGNED"
if [[ "$MODE" == install ]]; then bash "$ROOT/scripts/hakim-install-exact-20334-via-adb.sh" "$SIGNED" "$SIGNED.evidence.json"; echo "HAKIM_20334_PROMOTE=PASS stage=field_installed_and_same_artifact_verified apk=$SIGNED evidence=$SIGNED.evidence.json"; else echo "HAKIM_20334_PROMOTE=PASS stage=d1_signed_ready_not_installed apk=$SIGNED evidence=$SIGNED.evidence.json"; fi
