#!/usr/bin/env python3
"""Fail-closed structural verifier for Hakim D1 APK Signature Scheme v2.

This tool intentionally contains no private key material. It checks the framing
that Android/AOSP requires before a field APK may be presented for installation.
"""
from __future__ import annotations
import hashlib
import struct
import sys
from pathlib import Path

APK_SIG_MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A
EXPECTED_CERT_SHA256 = "d13e7aa8271cb6d32aec2157cc5ba4fafd226957eb0c731e9ceba827bf78b0d3"
EXPECTED_ALG = 0x0104  # RSA PKCS#1 v1.5 with SHA-512 for the pinned D1 lineage.

def u32(buf: bytes, off: int) -> int:
    return struct.unpack_from("<I", buf, off)[0]

def u64(buf: bytes, off: int) -> int:
    return struct.unpack_from("<Q", buf, off)[0]

def take_lp32(buf: bytes, off: int):
    if off + 4 > len(buf):
        raise ValueError("truncated length-prefixed field")
    n = u32(buf, off)
    start = off + 4
    end = start + n
    if end > len(buf):
        raise ValueError("length-prefixed field exceeds container")
    return buf[start:end], end

def fail(reason: str) -> None:
    raise SystemExit("HAKIM_D1_V2_STRUCTURE=FAIL reason=" + reason)

def main(path: str) -> None:
    data = Path(path).read_bytes()
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        fail("eocd_missing")
    cd = u32(data, eocd + 16)
    if cd < 24 or data[cd - 16:cd] != APK_SIG_MAGIC:
        fail("apk_signing_block_missing")
    size2 = u64(data, cd - 24)
    start = cd - size2 - 8
    if start < 0 or u64(data, start) != size2:
        fail("signing_block_size_mismatch")

    p = start + 8
    end = cd - 24
    v2 = None
    while p < end:
        pair_len = u64(data, p)
        if pair_len < 4 or p + 8 + pair_len > end:
            fail("invalid_id_value_pair")
        ident = u32(data, p + 8)
        value = data[p + 12:p + 8 + pair_len]
        if ident == V2_ID:
            v2 = value
        p += 8 + pair_len
    if p != end:
        fail("pair_boundary_mismatch")
    if v2 is None:
        fail("v2_block_missing")

    # AOSP: length-prefixed sequence of length-prefixed signers.
    signers, off = take_lp32(v2, 0)
    if off != len(v2):
        fail("missing_outer_signers_sequence_wrapper")
    signer, off = take_lp32(signers, 0)
    if off != len(signers):
        fail("unexpected_signer_count_or_trailing_data")

    off = 0
    signed_data, off = take_lp32(signer, off)
    signatures, off = take_lp32(signer, off)
    public_key, off = take_lp32(signer, off)
    if off != len(signer):
        fail("signer_trailing_data")

    off = 0
    digests, off = take_lp32(signed_data, off)
    certs, off = take_lp32(signed_data, off)
    attrs, off = take_lp32(signed_data, off)
    if off != len(signed_data):
        fail("signed_data_trailing_data")
    if attrs:
        fail("unexpected_d1_attributes")

    digest_record, off = take_lp32(digests, 0)
    if off != len(digests):
        fail("unexpected_digest_count")
    digest_alg = u32(digest_record, 0)
    digest, digest_end = take_lp32(digest_record, 4)
    if digest_end != len(digest_record):
        fail("digest_record_trailing_data")

    signature_record, off = take_lp32(signatures, 0)
    if off != len(signatures):
        fail("unexpected_signature_count")
    signature_alg = u32(signature_record, 0)
    signature, sig_end = take_lp32(signature_record, 4)
    if sig_end != len(signature_record):
        fail("signature_record_trailing_data")

    cert, off = take_lp32(certs, 0)
    if off != len(certs):
        fail("unexpected_certificate_count")

    cert_sha = hashlib.sha256(cert).hexdigest()
    if cert_sha != EXPECTED_CERT_SHA256:
        fail("d1_certificate_mismatch")
    if digest_alg != EXPECTED_ALG or signature_alg != EXPECTED_ALG:
        fail("d1_signature_algorithm_mismatch")
    if len(digest) != 64 or len(signature) != 512:
        fail("d1_digest_or_signature_size_mismatch")
    if not public_key:
        fail("public_key_missing")

    print(
        "HAKIM_D1_V2_STRUCTURE=PASS "
        f"cert={cert_sha} alg=0x{EXPECTED_ALG:04x} "
        f"block={cd-start} signer={len(signer)}"
    )

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_hakim_d1_v2_structure.py APK")
    main(sys.argv[1])
