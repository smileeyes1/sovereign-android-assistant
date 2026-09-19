#!/usr/bin/env python3
"""Read-only structural audit for a Quran DOCX candidate.

This tool NEVER authorizes a DOCX as canonical Quran text. It only reports
structure and quarantines the candidate until official digest verification or
full per-ayah comparison against an already verified official corpus occurs.
It never repairs Quran text from memory.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sys
import zipfile
from pathlib import Path

EXPECTED_COUNTS = [
    7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,
    112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,
    59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,
    52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,
    21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6,
]
EXPECTED_SURAH_COUNT = 114
EXPECTED_AYA_COUNT = 6236


def extract_paragraphs(path: Path) -> list[str]:
    if path.suffix.lower() != ".docx":
        raise ValueError("المدقق الحالي يقبل DOCX فقط")
    with zipfile.ZipFile(path) as archive:
        xml = archive.read("word/document.xml").decode("utf-8")
    paragraphs: list[str] = []
    for para in re.findall(r"<w:p\b.*?</w:p>", xml, flags=re.S):
        parts = re.findall(r"<w:t(?: [^>]*)?>(.*?)</w:t>", para, flags=re.S)
        if parts:
            paragraphs.append("".join(html.unescape(x) for x in parts))
    return paragraphs


def audit(path: Path) -> dict:
    paragraphs = extract_paragraphs(path)
    chunks: list[dict] = []
    current = None
    for paragraph in paragraphs:
        value = paragraph.strip()
        if value.startswith("سورة"):
            if current is not None:
                chunks.append(current)
            current = {"heading": value, "content": []}
        elif current is not None and value:
            current["content"].append(value)
    if current is not None:
        chunks.append(current)

    issues = []
    numbered_total = 0
    for index, chunk in enumerate(chunks, start=1):
        text = " ".join(chunk["content"])
        numbers = [int(x) for x in re.findall(r"\((\d{1,3})\)", text)]
        numbered_total += len(numbers)
        expected = EXPECTED_COUNTS[index - 1] if index <= len(EXPECTED_COUNTS) else None
        contiguous = numbers == list(range(1, len(numbers) + 1))
        if expected is None or len(numbers) != expected or not contiguous:
            issues.append({
                "surah_number": index,
                "heading": chunk["heading"],
                "expected_numbered_ayahs": expected,
                "numbered_ayah_markers_found": len(numbers),
                "last_numbered_marker": numbers[-1] if numbers else None,
                "numbering_contiguous_from_1": contiguous,
            })

    structural_ok = (
        len(chunks) == EXPECTED_SURAH_COUNT
        and numbered_total == EXPECTED_AYA_COUNT
        and not issues
    )
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {
        "tool": "HAKIM_QURAN_DOCX_CANDIDATE_AUDIT_V1",
        "path": path.name,
        "sha256": digest,
        "surah_headings_found": len(chunks),
        "numbered_ayah_markers_found": numbered_total,
        "expected_surah_count": EXPECTED_SURAH_COUNT,
        "expected_ayah_count": EXPECTED_AYA_COUNT,
        "structural_coverage_ok": structural_ok,
        "issues": issues,
        "canonical_quran_text_source": False,
        "classification": "QUARANTINED_CANDIDATE__NOT_AUTHORIZED_AS_MUSHAF_TEXT",
        "reason": (
            "الفحص البنيوي فشل؛ يبقى الملف محجورًا."
            if not structural_ok else
            "الفحص البنيوي نجح لكنه لا يثبت أصل النص؛ يلزم تطابق بصمة رسمية أو مطابقة كاملة آيةً بآية مع corpus رسمي متحقق."
        ),
        "structural_count_is_necessary_not_sufficient": True,
        "auto_repair_from_memory_forbidden": True,
        "original_file_mutated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("docx", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(args.docx)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    # Audit command succeeds if it could inspect the file. Admission remains false by design.
    return 0


if __name__ == "__main__":
    sys.exit(main())
