#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_HEAD="ed475233746388e1b8b94324874adc743957a1ef"
EXPECTED_CI_RUN="1485"
EXPECTED_UNSIGNED_SHA256="182b06dea9ea0e94fa6240d2d2663aa5e5af265283510c69e2a8050e0feb7cf7"
EXPECTED_PACKAGE="ps.hakim.stable"
EXPECTED_VERSION_CODE="20332"

INPUT="${1:-}"
OUTPUT="${2:-$PWD/hakim-field-20332-ed475233.apk}"
MANIFEST="${OUTPUT}.evidence.json"
TMP="${OUTPUT}.tmp"

fail() {
  printf 'HAKIM_D1_EXACT_SIGN=FAIL reason=%s\n' "$1" >&2
  exit 2
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    python3 - "$1" <<'PY'
import hashlib,sys
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for chunk in iter(lambda:f.read(1024*1024), b''):
        h.update(chunk)
print(h.hexdigest())
PY
  fi
}

find_build_tool() {
  local name="$1"
  local direct=""
  direct="$(command -v "$name" 2>/dev/null || true)"
  if [[ -n "$direct" ]]; then
    printf '%s\n' "$direct"
    return 0
  fi
  local home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$home" && -d "$home/build-tools" ]]; then
    local latest
    latest="$(find "$home/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
    if [[ -x "$latest/$name" ]]; then
      printf '%s\n' "$latest/$name"
      return 0
    fi
  fi
  return 1
}

[[ -n "$INPUT" && -f "$INPUT" ]] || fail "unsigned_apk_missing"
[[ -n "${HAKIM_FIELD_KEYSTORE_PATH:-}" && -f "$HAKIM_FIELD_KEYSTORE_PATH" ]] || fail "field_keystore_path_missing"
[[ -n "${HAKIM_FIELD_KEYSTORE_PASSWORD:-}" ]] || fail "field_keystore_password_missing"
[[ -n "${HAKIM_FIELD_KEY_ALIAS:-}" ]] || fail "field_key_alias_missing"
[[ -n "${HAKIM_FIELD_KEY_PASSWORD:-}" ]] || fail "field_key_password_missing"

ACTUAL_UNSIGNED_SHA="$(sha256_file "$INPUT")"
[[ "$ACTUAL_UNSIGNED_SHA" == "$EXPECTED_UNSIGNED_SHA256" ]] || fail "unsigned_sha256_mismatch"

APKSIGNER_BIN="${APKSIGNER:-$(find_build_tool apksigner || true)}"
ZIPALIGN_BIN="${ZIPALIGN:-$(find_build_tool zipalign || true)}"
AAPT_BIN="${AAPT:-$(find_build_tool aapt || true)}"
[[ -n "$APKSIGNER_BIN" && -x "$APKSIGNER_BIN" ]] || fail "apksigner_missing"
[[ -n "$ZIPALIGN_BIN" && -x "$ZIPALIGN_BIN" ]] || fail "zipalign_missing"
[[ -n "$AAPT_BIN" && -x "$AAPT_BIN" ]] || fail "aapt_missing"

"$ZIPALIGN_BIN" -c -P 16 -v 4 "$INPUT" >/dev/null || fail "unsigned_16kb_alignment_failed"

rm -f "$TMP"
trap 'rm -f "$TMP"' EXIT
"$APKSIGNER_BIN" sign \
  --ks "$HAKIM_FIELD_KEYSTORE_PATH" \
  --ks-key-alias "$HAKIM_FIELD_KEY_ALIAS" \
  --ks-pass env:HAKIM_FIELD_KEYSTORE_PASSWORD \
  --key-pass env:HAKIM_FIELD_KEY_PASSWORD \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$TMP" \
  "$INPUT"

APKSIGNER="$APKSIGNER_BIN" bash "$ROOT/scripts/verify-field-signer.sh" "$TMP" >/dev/null
python3 "$ROOT/tools/verify_hakim_d1_v2_structure.py" "$TMP" >/dev/null
"$ZIPALIGN_BIN" -c -P 16 -v 4 "$TMP" >/dev/null || fail "signed_16kb_alignment_failed"

VERIFY_OUTPUT="$(LC_ALL=C "$APKSIGNER_BIN" verify --verbose --print-certs "$TMP" 2>&1)" || fail "signed_apk_verify_failed"
grep -Eq 'Verified using v2 scheme .*: true' <<<"$VERIFY_OUTPUT" || fail "v2_not_verified"
grep -Eq 'Verified using v3 scheme .*: true' <<<"$VERIFY_OUTPUT" || fail "v3_not_verified"

BADGING="$(LC_ALL=C "$AAPT_BIN" dump badging "$TMP" 2>/dev/null | head -n1)"
grep -Fq "package: name='$EXPECTED_PACKAGE'" <<<"$BADGING" || fail "package_mismatch"
grep -Fq "versionCode='$EXPECTED_VERSION_CODE'" <<<"$BADGING" || fail "version_code_mismatch"

mv -f "$TMP" "$OUTPUT"
trap - EXIT
SIGNED_SHA="$(sha256_file "$OUTPUT")"

python3 - "$MANIFEST" "$SIGNED_SHA" <<PY
import json,sys
path,signed=sys.argv[1:3]
data={
  "schema_version":1,
  "source_head":"$EXPECTED_HEAD",
  "ci_run_number":int("$EXPECTED_CI_RUN"),
  "package":"$EXPECTED_PACKAGE",
  "version_code":int("$EXPECTED_VERSION_CODE"),
  "unsigned_sha256":"$EXPECTED_UNSIGNED_SHA256",
  "signed_sha256":signed,
  "d1_verified":True,
  "v2_verified":True,
  "v3_verified":True,
  "alignment_16kb_verified":True,
  "same_unsigned_ci_artifact_verified":True
}
with open(path,"w",encoding="utf-8") as f:
    json.dump(data,f,ensure_ascii=False,indent=2)
    f.write("\n")
PY

printf 'HAKIM_D1_EXACT_SIGN=PASS source_head=%s ci_run=%s unsigned_sha256=%s signed_sha256=%s output=%s evidence=%s\n' \
  "$EXPECTED_HEAD" "$EXPECTED_CI_RUN" "$EXPECTED_UNSIGNED_SHA256" "$SIGNED_SHA" "$OUTPUT" "$MANIFEST"
