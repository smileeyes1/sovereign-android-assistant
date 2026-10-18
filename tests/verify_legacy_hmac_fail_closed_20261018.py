#!/usr/bin/env python3
"""Fail-closed static acceptance tests for the legacy WebSocket authentication.
These tests inspect source and synthetic malformed inputs, not a live Android device.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/ps/hakim/phoneagent"
SERVICE = (BASE / "HakimService.kt").read_text(encoding="utf-8")
MAIN = (BASE / "MainActivity.kt").read_text(encoding="utf-8")
BOOT = (BASE / "BootReceiver.kt").read_text(encoding="utf-8")
APP = (BASE / "HakimApp.kt").read_text(encoding="utf-8")
DEFAULTS = (BASE / "PairingDefaults.kt").read_text(encoding="utf-8")


def section(source, start, end):
    assert source.count(start) == 1, "ambiguous section " + start
    remainder = source.split(start, 1)[1]
    assert end in remainder, "missing section end " + end
    return remainder.split(end, 1)[0]


def valid_gate(service):
    paired = section(service, "private fun isPaired(): Boolean", "private fun pairingSession():")
    decoder = section(service, "private fun decodeCommandMessage(", "private fun reconnectLater(")
    sender = section(service, "private fun sendResult(obj:", "private fun sendChunk(")
    return (
        'PairingDefaults.validLegacyAuthKey(prefs.getString("auth_key", null))' in paired
        and "if (!PairingDefaults.validLegacyAuthKey(key))" in decoder
        and "return null" in decoder.split("if (!PairingDefaults.validLegacyAuthKey(key))", 1)[1].split("}", 1)[0]
        and "return try { JSONObject(rawMessage)" not in decoder
        and "constantTimeEquals(expected, signature)" in decoder
        and "issuedAt <= 0L" in decoder
        and "if (!PairingDefaults.validLegacyAuthKey(key)) return" in sender
        and "wrapper.put(\"sig\", hmacHex(key," in sender
    )


def test_legacy_command_is_never_unauthenticated():
    assert valid_gate(SERVICE)


def test_key_policy_and_injected_defaults():
    assert 'Regex("^[A-Za-z0-9_-]{32,128}$")' in DEFAULTS
    assert "fun validLegacyAuthKey(value: String?)" in DEFAULTS
    assert "validLegacyAuthKey(AUTH_KEY)" in DEFAULTS


def test_pairing_ui_fails_closed():
    pairing = section(MAIN, "private fun savePairing()", "private fun startHakimService()")
    status = section(MAIN, "private fun isPaired(): Boolean", "private fun refreshPairingUi()")
    assert "parts.size != 3" in pairing
    assert "!PairingDefaults.validLegacyAuthKey(parts[2])" in pairing
    assert 'edit.putString("auth_key", parts[2].trim())' in pairing
    assert "PairingDefaults.validLegacyAuthKey(prefs.getString" in status
    assert "legacyPaired || HakimUnifiedRelay.isConfigured(this)" in status  # secure pairing stays available


def test_boot_and_app_require_key_for_legacy_only():
    for file in (BOOT, APP):
        assert "PairingDefaults.validLegacyAuthKey(prefs.getString" in file
        assert "securePaired" in file
        assert "pairing_disabled_by_user" in file


def test_synthetic_credentials():
    pattern = re.compile(r"^[A-Za-z0-9_-]{32,128}$")
    good = "A" * 32  # format only, not a recommended secret
    assert pattern.fullmatch(good)
    for value in ("", "x" * 12, "__HAKIM_AUTH_KEY__", "x " * 20, "x" * 129):
        assert not pattern.fullmatch(value)
    assert not pattern.fullmatch("password")
    print("SYNTHETIC_KEY_FORMAT_CASES=PASS")


def test_negative_mutations():
    mutations = {
        "accept_unsigned_when_key_blank": SERVICE.replace(
            "if (!PairingDefaults.validLegacyAuthKey(key)) {",
            "if (false) {", 1),
        "missing_service_key_guard": SERVICE.replace(
            'PairingDefaults.validLegacyAuthKey(prefs.getString("auth_key", null))',
            "true", 1),
        "unsigned_outbound_results": SERVICE.replace(
            "if (!PairingDefaults.validLegacyAuthKey(key)) return",
            "if (false) return", 1),
    }
    for name, source in mutations.items():
        assert source != SERVICE, name
        assert not valid_gate(source), "unsafe mutation accepted: " + name
        print("NEGATIVE_REJECTED", name)


if __name__ == "__main__":
    cases = sorted((n, fn) for n, fn in globals().items()
                   if n.startswith("test_") and callable(fn))
    for name, case in cases:
        case()
        print("PASS", name)
    print("HAKIM_LEGACY_HMAC_GATE=PASS", len(cases))
