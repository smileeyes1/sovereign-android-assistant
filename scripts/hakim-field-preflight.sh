#!/usr/bin/env bash
set -euo pipefail

PKG="ps.hakim.stable"
F4="F42D71B0308A543E253099C02301BFDBFEFA45F8755B12AB22A3C903305E442E"
D1="D13E7AA8271CB6D32AEC2157CC5BA4FAFD226957EB0C731E9CEBA827BF78B0D3"

ADB_BIN="${ADB:-$(command -v adb || true)}"
if [[ -z "$ADB_BIN" ]]; then
  echo "PREFLIGHT=BLOCKED reason=adb_missing"
  exit 2
fi

SERIAL="${1:-${HAKIM_ADB_SERIAL:-}}"
if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB_BIN" devices | awk 'NR>1 && $2=="device"{print $1}')
  if [[ ${#DEVICES[@]} -ne 1 ]]; then
    echo "PREFLIGHT=BLOCKED reason=expected_exactly_one_authorized_device count=${#DEVICES[@]}"
    exit 3
  fi
  SERIAL="${DEVICES[0]}"
fi
ADB_CMD=("$ADB_BIN" -s "$SERIAL")

STATE="$("${ADB_CMD[@]}" get-state 2>/dev/null || true)"
if [[ "$STATE" != "device" ]]; then
  echo "PREFLIGHT=BLOCKED reason=device_not_ready serial=$SERIAL"
  exit 4
fi

PM_PATHS="$("${ADB_CMD[@]}" shell pm path "$PKG" 2>/dev/null | tr -d '\r')"
BASE_APK="$(printf '%s\n' "$PM_PATHS" | sed -n 's/^package://p' | head -n1)"
if [[ -z "$BASE_APK" ]]; then
  echo "PREFLIGHT=BLOCKED reason=package_not_installed package=$PKG serial=$SERIAL"
  exit 5
fi

DUMP="$("${ADB_CMD[@]}" shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r')"
VERSION_CODE="$(printf '%s\n' "$DUMP" | sed -nE 's/.*versionCode=([0-9]+).*/\1/p' | head -n1)"
VERSION_NAME="$(printf '%s\n' "$DUMP" | sed -nE 's/^[[:space:]]*versionName=(.*)$/\1/p' | head -n1)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
LOCAL_APK="$TMP/hakim-installed.apk"
"${ADB_CMD[@]}" pull "$BASE_APK" "$LOCAL_APK" >/dev/null
APK_SHA256="$(sha256sum "$LOCAL_APK" | awk '{print $1}')"
APK_SIZE="$(stat -c%s "$LOCAL_APK" 2>/dev/null || wc -c < "$LOCAL_APK")"

APKSIGNER_BIN="${APKSIGNER:-$(command -v apksigner || true)}"
if [[ -z "$APKSIGNER_BIN" && -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/build-tools" ]]; then
  APKSIGNER_BIN="$(find "${ANDROID_HOME}/build-tools" -type f -name apksigner -perm -111 2>/dev/null | sort -V | tail -n1)"
fi
if [[ -z "$APKSIGNER_BIN" ]]; then
  printf 'PREFLIGHT=PARTIAL reason=apksigner_missing serial=%s package=%s versionCode=%s versionName=%s apk_sha256=%s apk_size=%s\n' \
    "$SERIAL" "$PKG" "${VERSION_CODE:-unknown}" "${VERSION_NAME:-unknown}" "$APK_SHA256" "$APK_SIZE"
  exit 0
fi

CERT_OUTPUT="$(LC_ALL=C "$APKSIGNER_BIN" verify --verbose --print-certs "$LOCAL_APK" 2>/dev/null || true)"
SIGNER="$(printf '%s\n' "$CERT_OUTPUT" | sed -nE 's/.*certificate SHA-256 digest:[[:space:]]*([0-9A-Fa-f:]+).*/\1/p' | head -n1 | tr -d ':' | tr '[:lower:]' '[:upper:]')"

case "$SIGNER" in
  "$F4") RESULT="F4_MATCH" ;;
  "$D1") RESULT="D1_ALTERNATE_NOT_INPLACE_F4" ;;
  "") RESULT="BLOCKED_SIGNER_UNREADABLE" ;;
  *) RESULT="BLOCKED_UNKNOWN_SIGNER" ;;
esac

printf 'PREFLIGHT=%s serial=%s package=%s versionCode=%s versionName=%s signer_sha256=%s apk_sha256=%s apk_size=%s\n' \
  "$RESULT" "$SERIAL" "$PKG" "${VERSION_CODE:-unknown}" "${VERSION_NAME:-unknown}" "${SIGNER:-unknown}" "$APK_SHA256" "$APK_SIZE"
