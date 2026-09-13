#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY="$ROOT/governance/HAKIM_FIELD_SIGNING_IDENTITY.json"

if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "SIGNER_GUARD=FAIL reason=apk_missing" >&2
  exit 2
fi

EXPECTED="$(python3 - "$POLICY" <<'PY'
import json,sys
with open(sys.argv[1], encoding='utf-8') as f:
    print(json.load(f)['certificate_sha256'].upper())
PY
)"
FORBIDDEN="$(python3 - "$POLICY" <<'PY'
import json,sys
with open(sys.argv[1], encoding='utf-8') as f:
    print(json.load(f)['known_nonmatching_certificate_sha256'].upper())
PY
)"

APKSIGNER_BIN="${APKSIGNER:-$(command -v apksigner || true)}"
if [[ -z "$APKSIGNER_BIN" ]]; then
  echo "SIGNER_GUARD=FAIL reason=apksigner_missing" >&2
  exit 3
fi

if ! CERT_OUTPUT="$(LC_ALL=C "$APKSIGNER_BIN" verify --verbose --print-certs "$APK" 2>&1)"; then
  echo "SIGNER_GUARD=FAIL reason=apk_signature_invalid" >&2
  exit 3
fi

mapfile -t ACTUAL_CERTS < <(
  printf '%s\n' "$CERT_OUTPUT" |
    sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' |
    tr '[:lower:]' '[:upper:]'
)

EXPECTED="${EXPECTED//:/}"
FORBIDDEN="${FORBIDDEN//:/}"

if [[ ${#ACTUAL_CERTS[@]} -ne 1 ]]; then
  echo "SIGNER_GUARD=FAIL reason=certificate_unreadable_or_missing" >&2
  exit 3
fi
ACTUAL="${ACTUAL_CERTS[0]//:/}"
if [[ "$ACTUAL" == "$FORBIDDEN" ]]; then
  echo "SIGNER_GUARD=FAIL reason=known_companion_signer_rejected" >&2
  exit 4
fi
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "SIGNER_GUARD=FAIL reason=field_signer_mismatch" >&2
  exit 5
fi

echo "SIGNER_GUARD=PASS"
