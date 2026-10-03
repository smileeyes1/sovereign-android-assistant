#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_HEAD="a5fd08ce653e58e13fd49a5ff4f8b11a807fd0e1"
EXPECTED_CI_RUN="1521"
EXPECTED_UNSIGNED_SHA256="8d22639739247c0f5c3feff7aa4e1ab32e6ef3ee9a828e3c5b69fb0eabf298a6"
EXPECTED_PACKAGE="ps.hakim.stable"
EXPECTED_VERSION_CODE="20334"
INPUT="${1:-}"
OUTPUT="${2:-$PWD/hakim-field-20334-a5fd08ce.apk}"
MANIFEST="${OUTPUT}.evidence.json"
TMP="${OUTPUT}.tmp"
fail(){ printf 'HAKIM_D1_EXACT_SIGN=FAIL reason=%s\n' "$1" >&2; exit 2; }
sha256_file(){ if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"|awk '{print $1}'; else python3 - "$1" <<'PY'
import hashlib,sys
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
print(h.hexdigest())
PY
fi; }
find_tool(){ local n="$1" d=""; d="$(command -v "$n" 2>/dev/null||true)"; [[ -n "$d" ]]&&{ printf '%s\n' "$d"; return 0; }; local h="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"; [[ -n "$h" && -d "$h/build-tools" ]]||return 1; d="$(find "$h/build-tools" -mindepth 1 -maxdepth 1 -type d|sort -V|tail -n1)"; [[ -x "$d/$n" ]]&&printf '%s\n' "$d/$n"; }
[[ -n "$INPUT" && -f "$INPUT" ]]||fail unsigned_apk_missing
[[ "$(sha256_file "$INPUT")" == "$EXPECTED_UNSIGNED_SHA256" ]]||fail unsigned_sha256_mismatch
[[ -n "${HAKIM_FIELD_KEYSTORE_PATH:-}" && -f "$HAKIM_FIELD_KEYSTORE_PATH" ]]||fail field_keystore_path_missing
[[ -n "${HAKIM_FIELD_KEYSTORE_PASSWORD:-}" ]]||fail field_keystore_password_missing
[[ -n "${HAKIM_FIELD_KEY_ALIAS:-}" ]]||fail field_key_alias_missing
[[ -n "${HAKIM_FIELD_KEY_PASSWORD:-}" ]]||fail field_key_password_missing
APKSIGNER_BIN="${APKSIGNER:-$(find_tool apksigner||true)}"; ZIPALIGN_BIN="${ZIPALIGN:-$(find_tool zipalign||true)}"; AAPT_BIN="${AAPT:-$(find_tool aapt||true)}"
[[ -x "$APKSIGNER_BIN" && -x "$ZIPALIGN_BIN" && -x "$AAPT_BIN" ]]||fail android_build_tools_missing
"$ZIPALIGN_BIN" -c -P 16 -v 4 "$INPUT" >/dev/null||fail unsigned_16kb_alignment_failed
rm -f "$TMP"; trap 'rm -f "$TMP"' EXIT
"$APKSIGNER_BIN" sign --ks "$HAKIM_FIELD_KEYSTORE_PATH" --ks-key-alias "$HAKIM_FIELD_KEY_ALIAS" --ks-pass env:HAKIM_FIELD_KEYSTORE_PASSWORD --key-pass env:HAKIM_FIELD_KEY_PASSWORD --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --out "$TMP" "$INPUT"
APKSIGNER="$APKSIGNER_BIN" bash "$ROOT/scripts/verify-field-signer.sh" "$TMP" >/dev/null
python3 "$ROOT/tools/verify_hakim_d1_v2_structure.py" "$TMP" >/dev/null
"$ZIPALIGN_BIN" -c -P 16 -v 4 "$TMP" >/dev/null||fail signed_16kb_alignment_failed
V="$(LC_ALL=C "$APKSIGNER_BIN" verify --verbose --print-certs "$TMP" 2>&1)"||fail signed_apk_verify_failed
grep -Eq 'Verified using v2 scheme .*: true' <<<"$V"||fail v2_not_verified
grep -Eq 'Verified using v3 scheme .*: true' <<<"$V"||fail v3_not_verified
B="$(LC_ALL=C "$AAPT_BIN" dump badging "$TMP" 2>/dev/null|head -n1)"
grep -Fq "package: name='$EXPECTED_PACKAGE'" <<<"$B"||fail package_mismatch
grep -Fq "versionCode='$EXPECTED_VERSION_CODE'" <<<"$B"||fail version_code_mismatch
mv -f "$TMP" "$OUTPUT"; trap - EXIT
SIGNED_SHA="$(sha256_file "$OUTPUT")"
python3 - "$MANIFEST" "$SIGNED_SHA" <<PY
import json,sys
path,signed=sys.argv[1:3]
d={"schema_version":1,"source_head":"$EXPECTED_HEAD","ci_run_number":int("$EXPECTED_CI_RUN"),"package":"$EXPECTED_PACKAGE","version_code":int("$EXPECTED_VERSION_CODE"),"unsigned_sha256":"$EXPECTED_UNSIGNED_SHA256","signed_sha256":signed,"d1_verified":True,"v2_verified":True,"v3_verified":True,"alignment_16kb_verified":True,"same_unsigned_ci_artifact_verified":True}
open(path,"w",encoding="utf-8").write(json.dumps(d,ensure_ascii=False,indent=2)+"\n")
PY
printf 'HAKIM_D1_EXACT_SIGN=PASS source_head=%s ci_run=%s unsigned_sha256=%s signed_sha256=%s output=%s evidence=%s\n' "$EXPECTED_HEAD" "$EXPECTED_CI_RUN" "$EXPECTED_UNSIGNED_SHA256" "$SIGNED_SHA" "$OUTPUT" "$MANIFEST"
