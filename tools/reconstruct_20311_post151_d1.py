#!/usr/bin/env python3
from __future__ import annotations
import base64, hashlib, struct, sys, zipfile
from pathlib import Path

EXPECTED_UNSIGNED_SHA256 = "8da6d86ae9222a402ae7718940724d5b5ac6336974eb5d01cbe8873ad862a8a4"
EXPECTED_SIGNED_SHA256 = "15904ce102af29ed818dd3b0d421a45ac6d7069cbc75c5336658ffb7a9e47e33"
EXPECTED_SIGNED_SIZE = 14102494
EXPECTED_BLOCK_SHA256 = "dac8c89738b6c0e757eb8df3fd31b463e26d934bc195d7cebc88ec876f36e662"

def fail(reason: str) -> None:
    raise SystemExit("HAKIM_20311_RECONSTRUCT=FAIL reason=" + reason)

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
    if len(block) != 2624 or block[-16:] != b"APK Sig Block 42":
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
    with zipfile.ZipFile(out) as z:
        bad = z.testzip()
        if bad:
            fail("zip_bad_" + bad)
        if len(z.infolist()) != 101:
            fail("zip_entry_count")
    print("HAKIM_20311_RECONSTRUCT=PASS sha256=" + digest)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: reconstruct_20311_post151.py unsigned.apk signing-block.b64 output.apk")
    main(sys.argv[1], sys.argv[2], sys.argv[3])
