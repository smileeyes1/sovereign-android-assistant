#!/usr/bin/env python3
"""Source contract: encrypted result must stay bound to its pairing epoch and topic.
Negative mutants plus actual Robolectric rejection tests; cannot roll back a POST started before disconnect.
"""
from pathlib import Path
S = (Path(__file__).resolve().parents[1] /
     "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")


def part(src, left, right):
    assert src.count(left) == 1, left
    return src.split(left, 1)[1].split(right, 1)[0]


def protected(src):
    try:
        check = part(src, "private fun isResultSessionCurrent(", "private fun sendResult(")
        result = part(src, "private fun sendResult(", "private fun ensureApprovalChannel(")
        carrier = part(src, "private fun handleCarrier(", "private fun decryptCarrier(")
        approval = part(src, "fun handleApproval(", "private fun decodePayload(")
        beacon = part(src, "fun sendHealthBeacon(", "private fun encryptResult(")
    except AssertionError:
        return False
    return all((
        'p.getString(KEY_APPROVAL_SESSION, null) == expectedSession' in check,
        'p.getString(KEY_RESULT_TOPIC, null) == resultTopic' in check,
        'p.getBoolean("pairing_disabled_by_user", false)' in check,
        'p.getBoolean("secure_relay_configured", false)' in check,
        "expectedSession: String" in result,
        result.count("if (!isResultSessionCurrent(context, resultTopic, expectedSession)) return false") >= 3,
        "executeEnvelope(context, envelope, approvalSession)" in carrier,
        carrier.count("approvalSession)") >= 4,
        "executeEnvelope(context.applicationContext, envelope, expectedSession)" in approval,
        'put("error", "rejected_by_user"), expectedSession)' in approval,
        'put("error", "request_expired"), expectedSession)' in approval,
        'if (result.optBoolean("ok", false)) "ok" else "error", result, expectedSession)' in approval,
        'val expectedSession = currentApprovalSession(context) ?: return false' in beacon,
        'val expectedSession = currentApprovalSession(app) ?: return' in beacon,
        'sendResult(app, resultTopic, requestId, "paired", result, expectedSession)' in beacon,
    ))


def test_secure_result_epoch_guard():
    assert protected(S)


def test_negative_mutations():
    variants = {
        "remove_epoch_match": S.replace(
            'p.getString(KEY_APPROVAL_SESSION, null) == expectedSession',
            "true", 1),
        "remove_topic_match": S.replace(
            'p.getString(KEY_RESULT_TOPIC, null) == resultTopic',
            "true", 1),
        "remove_result_check": S.replace(
            "if (!isResultSessionCurrent(context, resultTopic, expectedSession)) return false",
            "if (false) return false", 1),
        "unbound_approved_result": S.replace(
            'if (result.optBoolean("ok", false)) "ok" else "error", result, expectedSession)',
            'if (result.optBoolean("ok", false)) "ok" else "error", result)', 1),
    }
    for name, mutant in variants.items():
        assert mutant != S, "mutation missing: " + name
        assert not protected(mutant), "unsafe result accepted: " + name
        print("NEGATIVE_REJECTED", name)


if __name__ == "__main__":
    test_secure_result_epoch_guard()
    print("PASS test_secure_result_epoch_guard")
    test_negative_mutations()
    print("PASS test_negative_mutations")
    print("SECURE_RESULT_EPOCH_GATE=PASS")
