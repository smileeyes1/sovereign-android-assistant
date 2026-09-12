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

CERT_OUTPUT="$(LC_ALL=C keytool -printcert -jarfile "$APK" 2>&1 || true)"
ACTUAL="$(printf '%s\n' "$CERT_OUTPUT" | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -n1 | tr '[:lower:]' '[:upper:]')"

if [[ -z "$ACTUAL" ]]; then
  echo "SIGNER_GUARD=FAIL reason=certificate_unreadable_or_missing" >&2
  exit 3
fi
if [[ "$ACTUAL" == "$FORBIDDEN" ]]; then
  echo "SIGNER_GUARD=FAIL reason=known_companion_signer_rejected" >&2
  exit 4
fi
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "SIGNER_GUARD=FAIL reason=field_signer_mismatch" >&2
  exit 5
fi

echo "SIGNER_GUARD=PASS"
