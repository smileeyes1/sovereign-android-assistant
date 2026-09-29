#!/usr/bin/env python3
from __future__ import annotations
import base64, hashlib, struct, sys, zipfile
from pathlib import Path

EXPECTED_UNSIGNED_SHA256 = "e3c7822bfb1f4f33ba1b3ef8b2e0cb73c30b1abbecec6fd1220e7b44469f0c70"
EXPECTED_SIGNED_SHA256 = "43b26f82a9a5ab24c919ebafadfef986d791015f2e06497bb37bab9388d83491"
EXPECTED_SIGNED_SIZE = 14151714
EXPECTED_BLOCK_SHA256 = "1b0f719c990234abb2155cee5bd992c7fc3342a0de3a955101263d09db7ba4fa"
EXPECTED_BLOCK_SIZE = 2624
EXPECTED_ENTRIES = 101

def fail(reason: str) -> None:
    raise SystemExit("HAKIM_20315_FIELD_RECONSTRUCT=FAIL reason=" + reason)

def main(src_path: str, block_path: str, out_path: str) -> None:
    src = Path(src_path)
    block_file = Path(block_path)
    out = Path(out_path)
    unsigned = src.read_bytes()
    if hashlib.sha256(unsigned).hexdigest() != EXPECTED_UNSIGNED_SHA256:
        fail("unsigned_sha_mismatch")

    block = base64.b64decode(block_file.read_text(encoding="ascii").strip(), validate=True)
    if hashlib.sha256(block).hexdigest() != EXPECTED_BLOCK_SHA256:
        fail("signing_block_sha_mismatch")
    if len(block) != EXPECTED_BLOCK_SIZE or block[-16:] != b"APK Sig Block 42":
        fail("signing_block_shape")

    eocd = unsigned.rfind(b"PK\x05\x06")
    if eocd < 0:
        fail("eocd_missing")
    cd = struct.unpack_from("<I", unsigned, eocd + 16)[0]
    if not (0 < cd <= eocd):
        fail("central_directory_offset_invalid")

    rebuilt = bytearray(unsigned[:cd]) + block + bytearray(unsigned[cd:])
    new_eocd = eocd + len(block)
    struct.pack_into("<I", rebuilt, new_eocd + 16, cd + len(block))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(rebuilt)

    digest = hashlib.sha256(rebuilt).hexdigest()
    if len(rebuilt) != EXPECTED_SIGNED_SIZE:
        fail("signed_size_mismatch")
    if digest != EXPECTED_SIGNED_SHA256:
        fail("signed_sha_mismatch")

    with zipfile.ZipFile(src) as source_zip, zipfile.ZipFile(out) as signed_zip:
        source_entries = source_zip.infolist()
        signed_entries = signed_zip.infolist()
        if len(source_entries) != EXPECTED_ENTRIES or len(signed_entries) != EXPECTED_ENTRIES:
            fail("zip_entry_count")
        if [x.filename for x in source_entries] != [x.filename for x in signed_entries]:
            fail("zip_entry_names")
        if signed_zip.testzip() is not None:
            fail("zip_integrity")
        for before, after in zip(source_entries, signed_entries):
            if before.header_offset != after.header_offset:
                fail("entry_offset_changed_" + before.filename)
            if (before.CRC, before.file_size, before.compress_size, before.compress_type) != (
                after.CRC, after.file_size, after.compress_size, after.compress_type
            ):
                fail("entry_metadata_changed_" + before.filename)

    print("HAKIM_20315_FIELD_RECONSTRUCT=PASS sha256=" + digest)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: reconstruct_20315_field_verified.py unsigned.apk signing-block.b64 output.apk")
    main(sys.argv[1], sys.argv[2], sys.argv[3])
