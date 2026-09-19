#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/app/build/generated/quranAssets/quran"
JSON="$OUT_DIR/hafsData_v2-0.json"
PROVENANCE="$OUT_DIR/provenance.json"
LICENSE="$OUT_DIR/LICENSE-quran-meta.txt"

SOURCE_COMMIT="a5dd4a46dc6f7830a4303e89c3b4b3a15a213ac9"
SOURCE_URL="https://raw.githubusercontent.com/quran-center/quran-meta/$SOURCE_COMMIT/examples/data-check/data/hafsData_v2-0.json"
SOURCE_SHA256="d2960b3217962e7e4252abdcece67bea3d6b48271e4cd3af45bbbb2dd5c872ca"
CANONICAL_SHA256="c1a2d34f901cfb233cbff8c57c770b76b810cbe51eccab69629529318ea82186"

mkdir -p "$OUT_DIR"

if [[ ! -f "$JSON" ]] || [[ "$(sha256sum "$JSON" | awk '{print $1}')" != "$SOURCE_SHA256" ]]; then
  rm -f "$JSON"
  curl -fL --retry 5 --retry-delay 2 --connect-timeout 20 --max-time 180     "$SOURCE_URL" -o "$JSON"
fi

test "$(sha256sum "$JSON" | awk '{print $1}')" = "$SOURCE_SHA256"

python3 - "$JSON" "$CANONICAL_SHA256" <<'PY'
import hashlib, json, sys
path, expected = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8-sig") as f:
    obj=json.load(f)
if isinstance(obj, dict):
    for key in ("data","ayat","aya","verses","rows"):
        if isinstance(obj.get(key), list):
            obj=obj[key]
            break
if not isinstance(obj, list) or not obj:
    raise SystemExit("QURAN_ASSET_INVALID_SHAPE")
rows=[]
for raw in obj:
    r={str(k).strip().lower():v for k,v in raw.items()}
    s=int(str(r["sura_no"]).strip())
    a=int(str(r["aya_no"]).strip())
    name=str(r.get("sura_name_ar","")).strip()
    text=str(r.get("aya_text","")).strip()
    imlaey=str(r.get("aya_text_emlaey","")).strip()
    if not text:
        raise SystemExit(f"QURAN_ASSET_EMPTY_TEXT:{s}:{a}")
    rows.append((s,a,name,text,imlaey))
if len(rows)!=6236:
    raise SystemExit(f"QURAN_ASSET_AYAH_COUNT:{len(rows)}")
keys={(s,a) for s,a,_,_,_ in rows}
if len(keys)!=6236:
    raise SystemExit(f"QURAN_ASSET_DUP_KEYS:{len(keys)}")
surahs=sorted({s for s,_,_,_,_ in rows})
if surahs != list(range(1,115)):
    raise SystemExit("QURAN_ASSET_SURAH_RANGE")
for s in range(1,115):
    got=sorted(a for ss,a,_,_,_ in rows if ss==s)
    if got != list(range(1,len(got)+1)):
        raise SystemExit(f"QURAN_ASSET_SEQUENCE:{s}")
if sum(1 for s,_,_,_,_ in rows if s==87) != 19:
    raise SystemExit("QURAN_ASSET_AL_ALA_COUNT")
h=hashlib.sha256()
for s,a,name,text,imlaey in sorted(rows):
    h.update(f"{s}\t{a}\t{name}\t{text}\t{imlaey}\n".encode("utf-8"))
if h.hexdigest()!=expected:
    raise SystemExit("QURAN_ASSET_CANONICAL_HASH")
print("HAKIM_BUNDLED_QURAN_114_6236=PASS")
PY

cat > "$PROVENANCE" <<EOF
{
  "source_repository": "quran-center/quran-meta",
  "source_commit": "$SOURCE_COMMIT",
  "source_file": "examples/data-check/data/hafsData_v2-0.json",
  "source_sha256": "$SOURCE_SHA256",
  "canonical_corpus_sha256": "$CANONICAL_SHA256",
  "declared_upstream": "King Fahd Glorious Quran Printing Complex",
  "trust_class": "PINNED_KFQC_DERIVED_MIRROR",
  "surah_count": 114,
  "ayah_count": 6236,
  "surah_al_ala_ayah_count": 19,
  "runtime_network_required": false
}
EOF

cat > "$LICENSE" <<'EOF'
MIT License

Copyright (c) 2020 Quran-Center

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
EOF

echo "HAKIM_BUNDLED_QURAN_ASSET=PASS"
echo "SHA256=$SOURCE_SHA256"
