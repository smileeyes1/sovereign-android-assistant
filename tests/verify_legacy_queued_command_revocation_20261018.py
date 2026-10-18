#!/usr/bin/env python3
"""Fail-closed source contract for queued legacy WebSocket commands.
Static and synthetic tests only; not Android runtime or mid-JavaScript cancellation.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimService.kt").read_text(encoding="utf-8")


def block(source: str, start: str, end: str) -> str:
    assert source.count(start) == 1, "Ambiguous block " + start
    chunk = source.split(start, 1)[1]
    assert end in chunk, "Missing endpoint " + end
    return chunk.split(end, 1)[0]


def valid_legacy_session_guards(source: str) -> bool:
    paired = block(source, "private fun isPaired(): Boolean", "private fun authKey(): String")
    remote = block(source, "private fun connectRemote()", "private fun decodeCommandMessage(")
    queued = block(source, "private fun dispatchCommand(", "private fun decodeCommandMessage(")
    execute = block(source, "private fun executeCommand(", "private fun navigate(")
    return (
        'prefs.getBoolean("pairing_disabled_by_user", false)' in paired
        and "prefs.getString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, null)" in paired
        and "isPaired() && pairingSession() == expectedSession" in paired
        and "val connectionSession = pairingSession()" in remote
        and "if (!activePairingSession(connectionSession)) return" in remote
        and "dispatchCommand(cmd, connectionSession)" in remote
        and "if (!activePairingSession(expectedSession)) return" in queued
        and queued.count("if (activePairingSession(expectedSession)) executeCommand(") == 2
        and "if (!activePairingSession(expectedSession)) return" in execute
    )


def test_guarded_entry_and_queue() -> None:
    assert valid_legacy_session_guards(SOURCE)


def test_previous_channel_cannot_replay_after_repair() -> None:
    def allowed(paired, session_at_connection, current_session):
        return paired and session_at_connection == current_session
    assert allowed(True, "a", "a")
    assert not allowed(False, "a", "a")
    assert not allowed(True, "a", "b")
    assert allowed(True, None, None)  # legacy installations before session initialization
    assert not allowed(True, None, "b")
    print("SYNTHETIC_LEGACY_REVOKE=PASS")


def test_negative_mutations_rejected() -> None:
    mutants = {
        "old_onmessage": SOURCE.replace(
            "if (!activePairingSession(connectionSession)) return",
            "if (false) return", 1),
        "old_execute": SOURCE.replace(
            "if (!activePairingSession(expectedSession)) return\n        val requestId",
            "if (false) return\n        val requestId", 1),
        "old_queued_dispatch": SOURCE.replace(
            "if (activePairingSession(expectedSession)) executeCommand(visible, cmd, expectedSession)",
            "executeCommand(visible, cmd, expectedSession)", 1),
    }
    for name, source in mutants.items():
        assert source != SOURCE, name
        assert not valid_legacy_session_guards(source), name
        print("NEGATIVE_REJECTED", name)


if __name__ == "__main__":
    for name in ("test_guarded_entry_and_queue", "test_previous_channel_cannot_replay_after_repair", "test_negative_mutations_rejected"):
        globals()[name]()
        print("PASS", name)
    print("HAKIM_LEGACY_QUEUE_REVOCATION_GATE=PASS")
