import json
import tempfile
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from contract import artifact_evidence, clamp_steps, compile_prompt, frame_count, parse_plan, resolution_dims


def test_golden_plan_compiles_to_bounded_cinematic_prompt():
    plan = json.loads((ROOT / "golden_render.json").read_text(encoding="utf-8"))
    parsed = parse_plan(json.dumps(plan))
    prompt = compile_prompt(parsed)
    assert "red toy car" in prompt
    assert "50mm" in prompt
    assert len(prompt) <= 2400


def test_frames_are_bounded_and_wan_aligned():
    for seconds in [0.1, 1, 2, 5, 20]:
        frames = frame_count(seconds)
        assert 17 <= frames <= 121
        assert (frames - 1) % 4 == 0


def test_resolution_is_landscape_or_portrait_and_bounded():
    assert resolution_dims("480p", "16:9") == (832, 480)
    assert resolution_dims("480p", "9:16") == (480, 832)
    assert resolution_dims("720p", "16:9") == (1280, 704)
    assert resolution_dims("720p", "9:16") == (704, 1280)


def test_steps_are_bounded():
    assert clamp_steps(1) == 4
    assert clamp_steps(12) == 12
    assert clamp_steps(100) == 30


def test_artifact_evidence_never_claims_cinematic_approval():
    with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
        tmp.write(b"x" * 4096)
        path = tmp.name
    try:
        evidence = artifact_evidence(path, 24, 49)
        assert len(evidence["sha256"]) == 64
        assert evidence["technical_gates_passed"] is True
        assert evidence["quality_gates_passed"] is False
        assert evidence["cinematic_approval"] is False
    finally:
        Path(path).unlink(missing_ok=True)
