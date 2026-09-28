#!/usr/bin/env python3
from __future__ import annotations
import base64, hashlib, struct, sys, zipfile
from pathlib import Path

EXPECTED_UNSIGNED_SHA256 = "56971ddfae3a6e35bf6104f50da9e41f7ce97c6aecbb17093b7fb9d41ad8cce1"
EXPECTED_SIGNED_SHA256 = "5ebef4b2163dedfeb0ec6e778b8dc2d26fa0997eaa540bf101af3a683bb85f19"
EXPECTED_SIGNED_SIZE = 14136782
EXPECTED_BLOCK_SHA256 = "b76a68c86fa56662f2581195efbd23908fb3026675988f25ac46ba83fdd89bbb"
EXPECTED_BLOCK_SIZE = 4096
EXPECTED_ENTRIES = 101

def fail(reason: str) -> None:
    raise SystemExit("HAKIM_20313_RECONSTRUCT=FAIL reason=" + reason)

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

    print("HAKIM_20313_RECONSTRUCT=PASS sha256=" + digest)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: reconstruct_20313_sovereign_governance.py unsigned.apk signing-block.b64 output.apk")
    main(sys.argv[1], sys.argv[2], sys.argv[3])
