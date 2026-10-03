#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077

EXPECTED_PACKAGE="ps.hakim.stable"
EXPECTED_VERSION_CODE="20333"
EXPECTED_MIN_CURRENT_VERSION_CODE="20317"
EXPECTED_SOURCE_HEAD="9ef17d8c9cd176296795bdf7db35778e8eaceabf"
EXPECTED_CI_RUN="1509"
EXPECTED_UNSIGNED_SHA256="944c08b4b57d8c23504206d3a61e06c183ab8a3bbe7fde0c48c9c644ad8f445a"

APK="${1:-}"
EVIDENCE="${2:-${APK}.evidence.json}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail(){ printf 'HAKIM_FIELD_INSTALL=FAIL reason=%s\n' "$1" >&2; exit 2; }

sha256_file(){
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    python3 - "$1" <<'PY'
import hashlib,sys
h=hashlib.sha256()
with open(sys.argv[1],'rb') as f:
    for b in iter(lambda:f.read(1024*1024),b''):
        h.update(b)
print(h.hexdigest())
PY
  fi
}

find_tool(){
  local n="$1" d=""
  d="$(command -v "$n" 2>/dev/null || true)"
  [[ -n "$d" ]] && { printf '%s\n' "$d"; return 0; }
  local home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$home" && -d "$home/build-tools" ]]; then
    d="$(find "$home/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
    [[ -x "$d/$n" ]] && { printf '%s\n' "$d/$n"; return 0; }
  fi
  return 1
}

[[ -n "$APK" && -f "$APK" ]] || fail "signed_apk_missing"
[[ -f "$EVIDENCE" ]] || fail "evidence_missing"
command -v adb >/dev/null 2>&1 || fail "adb_missing"
command -v python3 >/dev/null 2>&1 || fail "python_missing"

readarray -t EV < <(python3 - "$EVIDENCE" <<'PY'
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
for k in (
    'source_head','ci_run_number','unsigned_sha256','signed_sha256',
    'package','version_code','d1_verified','v2_verified','v3_verified',
    'alignment_16kb_verified','same_unsigned_ci_artifact_verified'
):
    print(str(d.get(k,'')))
PY
)
[[ "${EV[0]}" == "$EXPECTED_SOURCE_HEAD" ]] || fail "evidence_source_head_mismatch"
[[ "${EV[1]}" == "$EXPECTED_CI_RUN" ]] || fail "evidence_ci_run_mismatch"
[[ "${EV[2]}" == "$EXPECTED_UNSIGNED_SHA256" ]] || fail "evidence_unsigned_sha_mismatch"
SIGNED_SHA="$(sha256_file "$APK")"
[[ "${EV[3]}" == "$SIGNED_SHA" ]] || fail "signed_sha_mismatch"
[[ "${EV[4]}" == "$EXPECTED_PACKAGE" && "${EV[5]}" == "$EXPECTED_VERSION_CODE" ]] || fail "evidence_identity_mismatch"
for i in 6 7 8 9 10; do
  [[ "${EV[$i]}" == "True" || "${EV[$i]}" == "true" ]] || fail "evidence_gate_false"
done

APKSIGNER_BIN="${APKSIGNER:-$(find_tool apksigner || true)}"
AAPT_BIN="${AAPT:-$(find_tool aapt || true)}"
[[ -n "$APKSIGNER_BIN" && -x "$APKSIGNER_BIN" ]] || fail "apksigner_missing"
[[ -n "$AAPT_BIN" && -x "$AAPT_BIN" ]] || fail "aapt_missing"

APKSIGNER="$APKSIGNER_BIN" bash "$ROOT/scripts/verify-field-signer.sh" "$APK" >/dev/null
BADGING="$(LC_ALL=C "$AAPT_BIN" dump badging "$APK" 2>/dev/null | head -n1)"
grep -Fq "package: name='$EXPECTED_PACKAGE'" <<<"$BADGING" || fail "package_mismatch"
grep -Fq "versionCode='$EXPECTED_VERSION_CODE'" <<<"$BADGING" || fail "version_mismatch"

if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{ok=1} END{exit !ok}'; then
  if [[ -x "${PREFIX:-}/bin/hakim-adb" ]]; then
    "${PREFIX}/bin/hakim-adb" connect >/dev/null 2>&1 || true
  fi
fi
mapfile -t DEVICES < <(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1}')
[[ ${#DEVICES[@]} -eq 1 ]] || fail "authorized_device_count_not_one"
DEVICE="${DEVICES[0]}"

BEFORE="$(adb -s "$DEVICE" shell dumpsys package "$EXPECTED_PACKAGE" 2>/dev/null | tr -d '\r')"
grep -q "versionCode=" <<<"$BEFORE" || fail "installed_package_not_found"
BEFORE_CODE="$(sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' <<<"$BEFORE" | head -n1)"
BEFORE_UID="$(sed -n 's/^[[:space:]]*userId=\([0-9][0-9]*\).*/\1/p' <<<"$BEFORE" | head -n1)"
BEFORE_FIRST_INSTALL="$(sed -n 's/^[[:space:]]*firstInstallTime=//p' <<<"$BEFORE" | head -n1)"
[[ -n "$BEFORE_CODE" ]] || fail "installed_version_unreadable"
[[ -n "$BEFORE_UID" ]] || fail "installed_uid_unreadable"
[[ -n "$BEFORE_FIRST_INSTALL" ]] || fail "first_install_time_unreadable"
(( BEFORE_CODE >= EXPECTED_MIN_CURRENT_VERSION_CODE )) || fail "installed_version_below_verified_floor"
(( BEFORE_CODE < EXPECTED_VERSION_CODE )) || fail "not_a_forward_update"

CACHE_DIR="${TMPDIR:-$HOME/.cache}"
mkdir -p "$CACHE_DIR"
CURRENT_TMP="$CACHE_DIR/hakim-current-before-20333-$$.apk"
INSTALLED_TMP="$CACHE_DIR/hakim-installed-20333-$$.apk"
trap 'rm -f "$CURRENT_TMP" "$INSTALLED_TMP"' EXIT

CURRENT_REMOTE="$(adb -s "$DEVICE" shell pm path "$EXPECTED_PACKAGE" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep '/base.apk$' | head -n1)"
[[ -n "$CURRENT_REMOTE" ]] || fail "current_base_path_missing"
adb -s "$DEVICE" pull "$CURRENT_REMOTE" "$CURRENT_TMP" >/dev/null 2>&1 || fail "pull_current_apk_failed"
APKSIGNER="$APKSIGNER_BIN" bash "$ROOT/scripts/verify-field-signer.sh" "$CURRENT_TMP" >/dev/null || fail "current_field_signer_mismatch"
CURRENT_SHA="$(sha256_file "$CURRENT_TMP")"

INSTALL_OUT="$(adb -s "$DEVICE" install -r --no-streaming "$APK" 2>&1)" || {
  printf '%s\n' "$INSTALL_OUT" >&2
  fail "adb_install_failed"
}
grep -q "Success" <<<"$INSTALL_OUT" || fail "adb_install_not_confirmed"

AFTER="$(adb -s "$DEVICE" shell dumpsys package "$EXPECTED_PACKAGE" 2>/dev/null | tr -d '\r')"
grep -q "versionCode=$EXPECTED_VERSION_CODE" <<<"$AFTER" || fail "installed_version_mismatch"
AFTER_UID="$(sed -n 's/^[[:space:]]*userId=\([0-9][0-9]*\).*/\1/p' <<<"$AFTER" | head -n1)"
AFTER_FIRST_INSTALL="$(sed -n 's/^[[:space:]]*firstInstallTime=//p' <<<"$AFTER" | head -n1)"
[[ "$AFTER_UID" == "$BEFORE_UID" ]] || fail "package_uid_changed"
[[ "$AFTER_FIRST_INSTALL" == "$BEFORE_FIRST_INSTALL" ]] || fail "first_install_time_changed"

REMOTE_PATH="$(adb -s "$DEVICE" shell pm path "$EXPECTED_PACKAGE" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | grep '/base.apk$' | head -n1)"
[[ -n "$REMOTE_PATH" ]] || fail "installed_base_path_missing"
adb -s "$DEVICE" pull "$REMOTE_PATH" "$INSTALLED_TMP" >/dev/null 2>&1 || fail "pull_installed_apk_failed"
INSTALLED_SHA="$(sha256_file "$INSTALLED_TMP")"
[[ "$INSTALLED_SHA" == "$SIGNED_SHA" ]] || fail "installed_same_artifact_sha_mismatch"
APKSIGNER="$APKSIGNER_BIN" bash "$ROOT/scripts/verify-field-signer.sh" "$INSTALLED_TMP" >/dev/null || fail "installed_field_signer_mismatch"

adb -s "$DEVICE" shell monkey -p "$EXPECTED_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || fail "launch_failed"
sleep 2
adb -s "$DEVICE" shell pidof "$EXPECTED_PACKAGE" >/dev/null 2>&1 || fail "process_not_running_after_launch"

printf 'HAKIM_FIELD_INSTALL=PASS device=%s before=%s after=%s current_sha256=%s signed_sha256=%s current_signer_d1=true installed_signer_d1=true same_artifact=true uid_preserved=true first_install_time_preserved=true data_clear=false uninstall=false\n' \
  "$DEVICE" "$BEFORE_CODE" "$EXPECTED_VERSION_CODE" "$CURRENT_SHA" "$SIGNED_SHA"
