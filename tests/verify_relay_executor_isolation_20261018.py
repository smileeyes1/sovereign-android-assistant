#!/usr/bin/env python3
"""Static non-starvation contract for the unified secure relay.
This validates source wiring plus negative mutations; it does NOT simulate Android runtime.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt").read_text(encoding="utf-8")


def section(source: str, begin: str, end: str) -> str:
    assert source.count(begin) == 1, f"Ambiguous block: {begin}"
    part = source.split(begin, 1)[1]
    assert end in part, f"Missing endpoint: {end}"
    return part.split(end, 1)[0]


def safe_executor_wiring(source: str) -> bool:
    start = section(source, "fun start(context: Context)", "private fun loop(context: Context)")
    approve = section(source, "fun handleApproval(context:", "private fun decodePayload(")
    acknowledgement = section(source, "fun sendPairingAckAsync(context:", "private fun encryptResult(")
    return (
        source.count("Executors.newSingleThreadExecutor()") == 2
        and source.count("private val pollingExecutor = Executors.newSingleThreadExecutor()") == 1
        and source.count("private val commandExecutor = Executors.newSingleThreadExecutor()") == 1
        and "pollingExecutor.execute { loop(app) }" in start
        and "commandExecutor.execute {" not in start
        and "commandExecutor.execute {" in approve
        and "pollingExecutor.execute {" not in approve
        and "commandExecutor.execute {" in acknowledgement
        and "pollingExecutor.execute {" not in acknowledgement
    )


assert safe_executor_wiring(SOURCE), "FAIL: blocking relay loop and commands share executor"

mutations = {
    "approval_starved": SOURCE.replace(
        "        commandExecutor.execute {\n            val result = executeEnvelope(",
        "        pollingExecutor.execute {\n            val result = executeEnvelope(",
        1,
    ),
    "ack_starved": SOURCE.replace(
        "        commandExecutor.execute {\n            if (resultTopic.isBlank()) return@execute",
        "        pollingExecutor.execute {\n            if (resultTopic.isBlank()) return@execute",
        1,
    ),
    "poll_occupies_command_lane": SOURCE.replace(
        "pollingExecutor.execute { loop(app) }",
        "commandExecutor.execute { loop(app) }",
        1,
    ),
}
for name, mutant in mutations.items():
    assert mutant != SOURCE, f"FAIL: negative mutation missing ({name})"
    assert not safe_executor_wiring(mutant), f"FAIL: unsafe executor layout accepted ({name})"
    print("NEGATIVE_REJECTED", name)

print("HAKIM_RELAY_EXECUTOR_GATE=PASS")
