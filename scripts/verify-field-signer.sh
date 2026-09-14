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
    print(json.load(f)['certificate_sha256'].replace(':','').upper())
PY
)"
FORBIDDEN="$(python3 - "$POLICY" <<'PY'
import json,sys
with open(sys.argv[1], encoding='utf-8') as f:
    print(json.load(f)['known_nonmatching_certificate_sha256'].replace(':','').upper())
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
    python3 -c 'import sys,re
text=sys.stdin.read()
seen=[]
patterns=[
 r"Signer\s*#?\d*\s*certificate\s*SHA-256\s*digest:\s*([0-9A-Fa-f:]{64,95})",
 r"certificate\s*SHA-256\s*digest:\s*([0-9A-Fa-f:]{64,95})",
]
for pat in patterns:
    for raw in re.findall(pat,text,re.I):
        d=re.sub(r"[^0-9A-Fa-f]","",raw).upper()
        if len(d)==64 and d not in seen:
            seen.append(d)
for d in seen:
    print(d)'
)

if [[ ${#ACTUAL_CERTS[@]} -ne 1 ]]; then
  echo "SIGNER_GUARD=FAIL reason=certificate_unreadable_or_multiple" >&2
  exit 3
fi
ACTUAL="${ACTUAL_CERTS[0]}"
if [[ "$ACTUAL" == "$FORBIDDEN" ]]; then
  echo "SIGNER_GUARD=FAIL reason=known_companion_signer_rejected" >&2
  exit 4
fi
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "SIGNER_GUARD=FAIL reason=field_signer_mismatch" >&2
  exit 5
fi

echo "SIGNER_GUARD=PASS"
