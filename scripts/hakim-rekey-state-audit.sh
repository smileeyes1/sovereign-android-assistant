#!/usr/bin/env bash
set -euo pipefail

# تدقيق قراءة فقط قبل أي إعادة تأسيس لهوية توقيع حكيم.
# لا ينسخ محتوى خاصًا، لا يعرض أسرارًا، ولا يحذف/يمسح/يثبت أي تطبيق.
PKG="ps.hakim.stable"
EXPECTED_SIGNER="F42D71B0308A543E253099C02301BFDBFEFA45F8755B12AB22A3C903305E442E"
ADB_BIN="${ADB:-$(command -v adb || true)}"

block() { printf 'REKEY_STATE_AUDIT=BLOCKED reason=%s\n' "$1"; exit 2; }

[[ -n "$ADB_BIN" ]] || block "adb_missing"
SERIAL="${1:-${HAKIM_ADB_SERIAL:-}}"
if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB_BIN" devices | awk 'NR>1 && $2=="device"{print $1}')
  [[ ${#DEVICES[@]} -eq 1 ]] || block "expected_exactly_one_authorized_device"
  SERIAL="${DEVICES[0]}"
fi
ADB_CMD=("$ADB_BIN" -s "$SERIAL")
[[ "$("${ADB_CMD[@]}" get-state 2>/dev/null || true)" == "device" ]] || block "device_not_ready"

DUMP="$("${ADB_CMD[@]}" shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r')"
VERSION_CODE="$(printf '%s\n' "$DUMP" | sed -nE 's/.*versionCode=([0-9]+).*/\1/p' | head -n1)"
VERSION_NAME="$(printf '%s\n' "$DUMP" | sed -nE 's/^[[:space:]]*versionName=(.*)$/\1/p' | head -n1)"
[[ -n "$VERSION_CODE" ]] || block "package_not_installed"

# استدعاء run-as هنا للقراءة الوصفية فقط؛ لا نطبع محتوى الملفات.
RUN_AS="NO"
PRIVATE_META=""
if "${ADB_CMD[@]}" shell run-as "$PKG" sh -c 'test -d . && printf READY' 2>/dev/null | grep -q READY; then
  RUN_AS="YES"
  PRIVATE_META="$("${ADB_CMD[@]}" shell run-as "$PKG" sh -c '
    ledger=files/hakim-rule-ledger.enc
    if [ -f "$ledger" ]; then
      size=$(stat -c%s "$ledger" 2>/dev/null || wc -c < "$ledger")
      printf "ledger_exists=yes ledger_bytes=%s " "$size"
    else
      printf "ledger_exists=no ledger_bytes=0 "
    fi
    prefs_count=$(find shared_prefs -maxdepth 1 -type f -name "*.xml" 2>/dev/null | wc -l | tr -d " ")
    printf "shared_prefs_files=%s" "$prefs_count"
  ' 2>/dev/null || true)"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREFLIGHT="$SCRIPT_DIR/hakim-field-preflight.sh"
[[ -x "$PREFLIGHT" ]] || block "field_preflight_missing"
PREFLIGHT_OUT="$($PREFLIGHT "$SERIAL" 2>/dev/null || true)"
SIGNER="$(printf '%s\n' "$PREFLIGHT_OUT" | sed -nE 's/.*signer_sha256=([^ ]+).*/\1/p' | head -n1 | tr -d ':' | tr '[:lower:]' '[:upper:]')"
[[ "$SIGNER" == "$EXPECTED_SIGNER" ]] || block "installed_signer_not_proven_f4"

if [[ "$RUN_AS" != "YES" ]]; then
  printf 'REKEY_STATE_AUDIT=BLOCKED reason=private_state_not_readable_without_original_signer_or_app_export versionCode=%s versionName=%s signer=F4 run_as=no\n' \
    "$VERSION_CODE" "${VERSION_NAME:-unknown}"
  exit 3
fi

printf 'REKEY_STATE_AUDIT=PARTIAL versionCode=%s versionName=%s signer=F4 run_as=yes %s\n' \
  "$VERSION_CODE" "${VERSION_NAME:-unknown}" "$PRIVATE_META"
printf '%s\n' 'NEXT_GATE=IN_APP_PLAINTEXT_RULE_EXPORT_OR_PROVEN_EQUIVALENT_REQUIRED_BEFORE_UNINSTALL'
exit 0
