"""Static wiring regression: the real language gate is separately exercised on JVM."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = (root / "app/src/main/java/ps/hakim/phoneagent/CommandCenterActivity.kt").read_text(encoding="utf-8")
def body(name, following):
    return src.split("private fun " + name + "(", 1)[1].split("private fun " + following + "(", 1)[0]
artifact = body("generateArtifactContent", "retryArtifactEngine")
direct = body("executeDirectModel", "retryDirectOrBlock")
for part in (artifact, direct):
    assert "HakimWisdomMatrix.rank(" in part
    assert "HakimEngineRegistry.directEngines(" not in part
    assert "success = result is HakimInferenceEngine.Result.Success" not in part
    assert part.index("if (currentDirectEngine !== engine)") < part.index("HakimEngineTelemetry.record(")
assert artifact.index("validateContent(request, result.text)") < artifact.index("validated.isSuccess, latency")
assert direct.index("HakimArabicOutputGate.validateText(") < direct.index("arabicGate.passed, latency")
assert direct.index("arabicGate.passed, latency") < direct.index("if (!arabicGate.passed)")
print("FOREGROUND_QUALITY_WIRING=PASS 11/11")
