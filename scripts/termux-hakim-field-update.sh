#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE="ps.hakim.stable"
readonly EXPECTED_D1="D13E7AA8271CB6D32AEC2157CC5BA4FAFD226957EB0C731E9CEBA827BF78B0D3"
readonly PROPERTIES_NAME="HAKIM-CANDIDATE.properties"

die() {
  printf 'HAKIM_FIELD_UPDATE=BLOCKED reason=%s\n' "$1" >&2
  exit "${2:-2}"
}

need_runtime() {
  local missing=()
  command -v java >/dev/null 2>&1 || missing+=(openjdk-17)
  command -v keytool >/dev/null 2>&1 || missing+=(openjdk-17)
  command -v adb >/dev/null 2>&1 || missing+=(android-tools)
  command -v sha256sum >/dev/null 2>&1 || missing+=(coreutils)
  if (( ${#missing[@]} > 0 )); then
    command -v pkg >/dev/null 2>&1 || die "missing_runtime:${missing[*]}"
    pkg install -y "${missing[@]}"
  fi
}

newest_file() {
  local pattern="$1" candidate newest="" newest_time=0 stamp
  shift
  for candidate in "$@"; do
    [[ -d "$candidate" ]] || continue
    while IFS= read -r -d '' file; do
      stamp="$(stat -c %Y "$file" 2>/dev/null || printf '0')"
      if (( stamp > newest_time )); then newest="$file"; newest_time="$stamp"; fi
    done < <(find "$candidate" -maxdepth 5 -type f -name "$pattern" -print0 2>/dev/null)
  done
  printf '%s' "$newest"
}

property() {
  local key="$1" file="$2"
  sed -n "s/^${key}=//p" "$file" | head -n1
}

normalize_sha256() {
  tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]'
}

apk_signer_sha256() {
  local apk="$1" output
  output="$(LC_ALL=C java -jar "$APKSIGNER_JAR" verify --verbose --print-certs "$apk" 2>/dev/null || true)"
  printf '%s\n' "$output" | sed -nE 's/.*certificate SHA-256 digest:[[:space:]]*([0-9A-Fa-f:]+).*/\1/p' | head -n1 | normalize_sha256
}

single_device() {
  local devices
  if command -v hakim-adb >/dev/null 2>&1; then hakim-adb connect >/dev/null 2>&1 || true; fi
  mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  (( ${#devices[@]} == 1 )) || die "expected_one_authorized_adb_device:${#devices[@]}"
  printf '%s' "${devices[0]}"
}

kit_hint="${1:-$PWD}"
search_roots=("$kit_hint" "$PWD" "$HOME/storage/downloads" "$HOME/storage/shared/Download")
PROPERTIES_FILE="$(newest_file "$PROPERTIES_NAME" "${search_roots[@]}")"
[[ -n "$PROPERTIES_FILE" ]] || die "signing_kit_not_found"
KIT_DIR="$(dirname "$PROPERTIES_FILE")"
UNSIGNED_APK="$KIT_DIR/app-release-unsigned.apk"
APKSIGNER_JAR="$KIT_DIR/apksigner.jar"
[[ -f "$UNSIGNED_APK" && -f "$APKSIGNER_JAR" ]] || die "signing_kit_incomplete"

expected_package="$(property package "$PROPERTIES_FILE")"
version_code="$(property version_code "$PROPERTIES_FILE")"
version_name="$(property version_name "$PROPERTIES_FILE")"
source_sha="$(property source_sha "$PROPERTIES_FILE")"
expected_apk_sha="$(property apk_sha256 "$PROPERTIES_FILE")"
[[ "$expected_package" == "$PACKAGE" ]] || die "candidate_package_mismatch"
[[ "$version_code" =~ ^[0-9]+$ && "$version_code" -ge 20041 ]] || die "candidate_version_invalid"
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || die "candidate_source_invalid"
[[ "$expected_apk_sha" =~ ^[0-9a-f]{64}$ ]] || die "candidate_digest_invalid"

need_runtime

actual_apk_sha="$(sha256sum "$UNSIGNED_APK" | awk '{print $1}')"
[[ "$actual_apk_sha" == "$expected_apk_sha" ]] || die "candidate_digest_mismatch"

keystore="${HAKIM_FIELD_KEYSTORE_PATH:-}"
if [[ -z "$keystore" ]]; then
  mapfile -d '' -t keystores < <(
    find "$HOME/.hakim" "$HOME/storage/downloads" "$HOME/storage/shared/Download" \
      -maxdepth 4 -type f \( -iname '*hakim*.jks' -o -iname '*hakim*.keystore' -o -iname '*d1*.jks' -o -iname '*d1*.keystore' \) \
      -print0 2>/dev/null
  )
  if (( ${#keystores[@]} == 1 )); then
    keystore="${keystores[0]}"
  else
    printf 'أدخل مسار ملف توقيع حكيم D1 على الهاتف (لن يُرفع): ' >&2
    IFS= read -r keystore
  fi
fi
[[ -f "$keystore" ]] || die "keystore_not_found"

printf 'أدخل كلمة مرور مخزن توقيع حكيم محليًا: ' >&2
IFS= read -r -s HAKIM_TMP_STORE_PASS
printf '\n' >&2
export HAKIM_TMP_STORE_PASS
mapfile -t aliases < <(
  LC_ALL=C keytool -list -v -keystore "$keystore" -storepass:env HAKIM_TMP_STORE_PASS 2>/dev/null \
    | sed -nE 's/^Alias name:[[:space:]]*(.*)$/\1/p'
)
(( ${#aliases[@]} > 0 )) || die "keystore_unreadable_or_password_invalid"
alias_name="${HAKIM_FIELD_KEY_ALIAS:-}"
if [[ -z "$alias_name" && ${#aliases[@]} -eq 1 ]]; then alias_name="${aliases[0]}"; fi
[[ -n "$alias_name" ]] || die "multiple_aliases_set_HAKIM_FIELD_KEY_ALIAS"
key_info="$(LC_ALL=C keytool -list -v -alias "$alias_name" -keystore "$keystore" -storepass:env HAKIM_TMP_STORE_PASS 2>/dev/null)"
keystore_signer="$(printf '%s\n' "$key_info" | sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*([0-9A-Fa-f:]+).*/\1/p' | head -n1 | normalize_sha256)"
[[ "$keystore_signer" == "$EXPECTED_D1" ]] || die "keystore_signer_not_D1"

printf 'أدخل كلمة مرور مفتاح D1، أو اضغط Enter إذا كانت نفسها: ' >&2
IFS= read -r -s HAKIM_TMP_KEY_PASS
printf '\n' >&2
if [[ -z "$HAKIM_TMP_KEY_PASS" ]]; then HAKIM_TMP_KEY_PASS="$HAKIM_TMP_STORE_PASS"; fi
export HAKIM_TMP_KEY_PASS

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
SIGNED_APK="$KIT_DIR/hakim-field-${version_code}-D1-${timestamp}.apk"
java -jar "$APKSIGNER_JAR" sign \
  --ks "$keystore" \
  --ks-key-alias "$alias_name" \
  --ks-pass env:HAKIM_TMP_STORE_PASS \
  --key-pass env:HAKIM_TMP_KEY_PASS \
  --out "$SIGNED_APK" \
  "$UNSIGNED_APK"
unset HAKIM_TMP_KEY_PASS HAKIM_TMP_STORE_PASS

signed_signer="$(apk_signer_sha256 "$SIGNED_APK")"
[[ "$signed_signer" == "$EXPECTED_D1" ]] || die "signed_candidate_not_D1"
signed_sha="$(sha256sum "$SIGNED_APK" | awk '{print $1}')"

serial="$(single_device)"
installed_path="$(adb -s "$serial" shell pm path "$PACKAGE" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
[[ -n "$installed_path" ]] || die "installed_hakim_not_found"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
adb -s "$serial" pull "$installed_path" "$tmp_dir/installed-before.apk" >/dev/null
installed_before_signer="$(apk_signer_sha256 "$tmp_dir/installed-before.apk")"
[[ "$installed_before_signer" == "$EXPECTED_D1" ]] || die "installed_signer_not_D1"
installed_before_version="$(adb -s "$serial" shell dumpsys package "$PACKAGE" | tr -d '\r' | sed -nE 's/.*versionCode=([0-9]+).*/\1/p' | head -n1)"
[[ "$installed_before_version" =~ ^[0-9]+$ ]] || die "installed_version_unreadable"
(( version_code >= installed_before_version )) || die "candidate_downgrade_blocked"

install_output="$(adb -s "$serial" install -r "$SIGNED_APK" 2>&1)" || {
  printf '%s\n' "$install_output" >&2
  die "adb_install_failed"
}
grep -q 'Success' <<<"$install_output" || die "adb_install_not_confirmed"

installed_after_version="$(adb -s "$serial" shell dumpsys package "$PACKAGE" | tr -d '\r' | sed -nE 's/.*versionCode=([0-9]+).*/\1/p' | head -n1)"
[[ "$installed_after_version" == "$version_code" ]] || die "installed_version_mismatch"
installed_after_path="$(adb -s "$serial" shell pm path "$PACKAGE" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
adb -s "$serial" pull "$installed_after_path" "$tmp_dir/installed-after.apk" >/dev/null
installed_after_signer="$(apk_signer_sha256 "$tmp_dir/installed-after.apk")"
[[ "$installed_after_signer" == "$EXPECTED_D1" ]] || die "installed_signer_after_update_not_D1"
installed_sha="$(sha256sum "$tmp_dir/installed-after.apk" | awk '{print $1}')"

evidence="$KIT_DIR/HAKIM-${version_code}-FIELD-EVIDENCE-${timestamp}.txt"
{
  printf 'status=PASS\n'
  printf 'package=%s\n' "$PACKAGE"
  printf 'version_code=%s\n' "$version_code"
  printf 'version_name=%s\n' "$version_name"
  printf 'source_sha=%s\n' "$source_sha"
  printf 'candidate_sha256=%s\n' "$signed_sha"
  printf 'installed_sha256=%s\n' "$installed_sha"
  printf 'installed_signer_sha256=%s\n' "$installed_after_signer"
  printf 'verified_at_utc=%s\n' "$timestamp"
} > "$evidence"

printf 'HAKIM_FIELD_UPDATE=PASS package=%s versionCode=%s signer=%s evidence=%s\n' \
  "$PACKAGE" "$version_code" "$installed_after_signer" "$evidence"
