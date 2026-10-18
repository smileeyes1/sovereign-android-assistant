#!/usr/bin/env python3
"""Static fail-closed regression contract for Hakim's legacy and secure pairing.

This is a source guard only, not an Android runtime or field-security test.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/ps/hakim/phoneagent"
main = (BASE / "MainActivity.kt").read_text(encoding="utf-8")
relay = (BASE / "HakimUnifiedRelay.kt").read_text(encoding="utf-8")
boot = (BASE / "BootReceiver.kt").read_text(encoding="utf-8")
app = (BASE / "HakimApp.kt").read_text(encoding="utf-8")
pair = (BASE / "HakimPairingActivity.kt").read_text(encoding="utf-8")


def section(source: str, start: str, end: str) -> str:
    assert source.count(start) == 1, f"Ambiguous or missing block: {start}"
    part = source.split(start, 1)[1]
    assert end in part, f"Missing endpoint for {start}"
    return part.split(end, 1)[0]


def test_dual_pairing() -> None:
    p = section(main, "private fun isPaired(): Boolean", "private fun refreshPairingUi()")
    assert 'prefs.getBoolean("pairing_disabled_by_user", false)' in p
    assert "legacyPaired || HakimUnifiedRelay.isConfigured(this)" in p
    assert "private fun startHakimService()" in main
    assert "if (!isPaired()) return" in main


def test_explicit_disconnect_revokes_both_transports() -> None:
    p = section(main, 'text = "فصل الاقتران"', 'text = "حفظ"')
    for key in ("command_topic", "result_topic", "auth_key", "pair_token"):
        assert f'.remove("{key}")' in p, key
    for key in ("KEY_TOPIC", "KEY_RESULT_TOPIC", "KEY_RELAY_KEY", "KEY_BRIDGE_BASE"):
        assert f".remove(HakimUnifiedRelay.{key})" in p, key
    assert '.putBoolean("pairing_disabled_by_user", true)' in p


def test_secure_config_respects_user_disconnect() -> None:
    p = section(relay, "fun isConfigured(context: Context): Boolean", "fun start(context: Context)")
    assert 'getBoolean("pairing_disabled_by_user", false)' in p
    for k in ("KEY_TOPIC", "KEY_RESULT_TOPIC", "KEY_RELAY_KEY"):
        assert k in p


def test_disable_blocks_poll_before_reading_topic() -> None:
    p = section(relay, "private fun loop(context: Context)", "private fun directPollOnce(")
    assert p.index('getBoolean("pairing_disabled_by_user", false)') < p.index("getString(KEY_TOPIC")


def test_disable_blocks_carrier_before_decryption() -> None:
    p = section(relay, "private fun handleCarrier(", "private fun decryptCarrier(")
    assert p.index('getBoolean("pairing_disabled_by_user", false)') < p.index("decryptCarrier(")


def test_disable_blocks_queued_approval() -> None:
    p = section(relay, "fun handleApproval(", "private fun decodePayload(")
    assert 'getBoolean("pairing_disabled_by_user", false)' in p
    assert p.index('getBoolean("pairing_disabled_by_user", false)') < p.index("if (!approved)")


def test_disable_blocks_async_execution() -> None:
    p = section(relay, "private fun executeEnvelope(", "private fun status(")
    assert 'getBoolean("pairing_disabled_by_user", false)' in p
    assert '"pairing_disabled"' in p
    assert p.index('getBoolean("pairing_disabled_by_user", false)') < p.index("decodePayload(")


def test_boot_recovers_secure_only_without_overriding_disconnect() -> None:
    p = section(boot, 'val disabled = prefs.getBoolean(', "try {")
    assert "HakimUnifiedRelay.isConfigured(context)" in p
    assert "disabled || (!legacyPaired && !securePaired)" in p
    assert 'if (disabled || (!legacyPaired && !securePaired)) return' in p


def test_user_repair_is_explicit() -> None:
    assert 'getBoolean("pairing_disabled_by_user", false)' in app
    confirm = section(pair, "private fun confirmPairing(", "\n}")
    commit = section(relay, "fun configure(", "private fun normalizeBridgeBase(")
    assert '.setPositiveButton("أوافق على الاقتران")' in pair
    assert 'HakimUnifiedRelay.configure(' in confirm
    assert 'this, topic, resultTopic, relayKey, bridgeBase, token' in confirm
    assert '.putBoolean("pairing_disabled_by_user", false)' in commit
    assert 'if (committed) revokePendingApprovals(context)' in commit
    assert '.putBoolean("pairing_disabled_by_user", false)' in main


if __name__ == "__main__":
    tests = sorted((name, fn) for name, fn in globals().items()
                   if name.startswith("test_") and callable(fn))
    for name, fn in tests:
        fn()
        print("PASS", name)
    print(f"STATIC_CONTRACT_PASS={len(tests)}")
