#!/usr/bin/env python3
"""Fail-closed contract for legacy WebView asynchronous callback epoch checks.
Static inspection and intentional negative mutations; these do not stop JS already executed.
"""
from pathlib import Path
SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/ps/hakim/phoneagent/HakimService.kt").read_text(encoding="utf-8")


def section(text, start, end):
    assert text.count(start) == 1, "missing/ambiguous " + start
    return text.split(start, 1)[1].split(end, 1)[0]


def guarded(source):
    try:
        execute = section(source, "private fun executeCommand(", "private fun navigate(")
        js = section(source, "private fun runJsAction(", "private fun sensitiveJs()")
        snapshot = section(source, "private fun sendSnapshot(", "private fun decodeJsString(")
        result = section(source, "private fun sendResult(obj:", "private fun sendChunk(")
        chunks = section(source, "private fun sendChunk(", "private fun hmacHex(")
    except AssertionError:
        return False
    checks = (
        'sendSnapshot(target, requestId, "ok", expectedSession)' in execute,
        execute.count("runJsAction(target,") == 6,
        all("expectedSession" in line for line in execute.splitlines()
            if "runJsAction(target," in line or "sendSnapshot(target," in line),
        "expectedSession: String?" in js,
        "if (!activePairingSession(expectedSession)) return" in js,
        "if (!activePairingSession(expectedSession)) return@evaluateJavascript" in js,
        "if (activePairingSession(expectedSession))" in js,
        "sendSnapshot(target, requestId, if (ok) \"ok\" else \"failed\", expectedSession)" in js,
        "expectedSession: String?" in snapshot,
        "if (!activePairingSession(expectedSession)) return" in snapshot,
        "if (!activePairingSession(expectedSession)) return@evaluateJavascript" in snapshot,
        'put("page", page), expectedSession)' in snapshot,
        "expectedSession: String? = null" in result,
        "if (expectedSession != null && !activePairingSession(expectedSession)) return" in result,
        "sendChunk(topic, wrapper.toString(), 0, expectedSession)" in result,
        "if (expectedSession != null && !activePairingSession(expectedSession)) return" in chunks,
        chunks.count("sendChunk(topic, body, attempt + 1, expectedSession)") == 2,
        "sendChunk(topic, body, attempt + 1)" not in chunks,
    )
    return all(checks)


def test_all_async_paths_check_original_epoch():
    assert guarded(SOURCE)


def test_negative_mutations_fail():
    mutations = {
        "js_callback_unchecked": SOURCE.replace(
            'if (!activePairingSession(expectedSession)) return@evaluateJavascript\n'
            '            val ok = raw == "true"',
            'if (false) return@evaluateJavascript\n'
            '            val ok = raw == "true"', 1),
        "snapshot_callback_unchecked": SOURCE.replace(
            'if (!activePairingSession(expectedSession)) return@evaluateJavascript\n'
            '            val decoded = decodeJsString(raw)',
            'if (false) return@evaluateJavascript\n'
            '            val decoded = decodeJsString(raw)', 1),
        "result_can_cross_epoch": SOURCE.replace(
            'if (expectedSession != null && !activePairingSession(expectedSession)) return\n'
            '        if (!isPaired()) return',
            'if (false) return\n        if (!isPaired()) return', 1),
        "retry_after_disconnect": SOURCE.replace(
            'sendChunk(topic, body, attempt + 1, expectedSession)',
            'sendChunk(topic, body, attempt + 1)', 1),
    }
    for name, mutant in mutations.items():
        assert mutant != SOURCE, "missing mutation: " + name
        assert not guarded(mutant), "unsafe variant accepted: " + name
        print("NEGATIVE_REJECTED", name)


def test_reference_epoch_cases():
    def allowed(paired, at_dispatch, current):
        return paired and at_dispatch == current
    assert allowed(True, "A", "A")
    assert not allowed(False, "A", "A")
    assert not allowed(True, "A", "B")  # re-paired after a delayed callback
    assert not allowed(True, None, "B")
    print("REFERENCE_EPOCH_CASES=PASS")


if __name__ == "__main__":
    cases = sorted((name, fn) for name, fn in globals().items()
                   if name.startswith("test_") and callable(fn))
    for name, fn in cases:
        fn()
        print("PASS", name)
    print("LEGACY_ASYNC_EPOCH_GATE=PASS", len(cases))
