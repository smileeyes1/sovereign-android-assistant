#!/usr/bin/env python3
from __future__ import annotations
import base64, hashlib, struct, sys, zipfile
from pathlib import Path

EXPECTED_UNSIGNED_SHA256 = "9fa73a168adb211ceeea056885c42e89944345010883473aea00b22e3d346913"
EXPECTED_SIGNED_SHA256 = "383f356a66ac171caabc105a68fe7c89a035ddad55da7310aae014b119e6faa8"
EXPECTED_SIGNED_SIZE = 14153226
EXPECTED_BASE_BLOCK_SHA256 = "0ccd1ade4c7b7e40579a368176d3e6639600b874caa647798adf126436c3b6e5"
EXPECTED_BLOCK_SHA256 = "1c9bdfc0e114087564611f2b32e7515e9aff40a6cdae01a7a85f2f6d2f5c221d"
EXPECTED_BLOCK_SIZE = 4096
EXPECTED_ENTRIES = 101

PATCH_FILES = (
    (48, "release-control/20316-patch-0048.hex"),
    (1534, "release-control/20316-patch-1534.hex"),
    (1849, "release-control/20316-patch-1849.hex"),
    (1997, "release-control/20316-patch-1997.hex"),
)

def fail(reason: str) -> None:
    raise SystemExit("HAKIM_20316_RECONSTRUCT=FAIL reason=" + reason)

def main(src_path: str, baseline_block_path: str, out_path: str) -> None:
    src = Path(src_path)
    baseline_file = Path(baseline_block_path)
    out = Path(out_path)

    unsigned = src.read_bytes()
    if hashlib.sha256(unsigned).hexdigest() != EXPECTED_UNSIGNED_SHA256:
        fail("unsigned_sha_mismatch")

    try:
        block = bytearray(base64.b64decode(
            baseline_file.read_text(encoding="ascii").strip(),
            validate=True,
        ))
    except Exception:
        fail("baseline_block_decode")

    if len(block) != EXPECTED_BLOCK_SIZE:
        fail("baseline_block_size")
    if hashlib.sha256(block).hexdigest() != EXPECTED_BASE_BLOCK_SHA256:
        fail("baseline_block_sha_mismatch")

    for offset, patch_path in PATCH_FILES:
        try:
            replacement = bytes.fromhex(Path(patch_path).read_text(encoding="ascii").strip())
        except Exception:
            fail("patch_decode_" + str(offset))
        end = offset + len(replacement)
        if offset < 0 or end > len(block):
            fail("patch_bounds_" + str(offset))
        block[offset:end] = replacement

    if hashlib.sha256(block).hexdigest() != EXPECTED_BLOCK_SHA256:
        fail("derived_signing_block_sha_mismatch")
    if block[-16:] != b"APK Sig Block 42":
        fail("signing_block_magic")

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

    print("HAKIM_20316_RECONSTRUCT=PASS sha256=" + digest)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: reconstruct_20316_autoupdate_rootfix.py "
            "unsigned.apk baseline-20315-signing-block.b64 output.apk"
        )
    main(sys.argv[1], sys.argv[2], sys.argv[3])
