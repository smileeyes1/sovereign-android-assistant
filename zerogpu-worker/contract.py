from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
from typing import Any

WORKER_VERSION = "HAKIM_ZEROGPU_WORKER_V1_2026-09-30"
MODEL_ID = os.getenv("HAKIM_VIDEO_MODEL_ID", "Wan-AI/Wan2.2-TI2V-5B-Diffusers")

DEFAULT_NEGATIVE = (
    "overexposed, underexposed, low quality, blurred details, subtitles, watermark, "
    "text, logo, extra limbs, extra fingers, malformed hands, deformed objects, "
    "fused geometry, duplicated objects, unstable background, temporal flicker, "
    "warping, morphing, random camera shake, sudden lighting changes, broken physics"
)


def _text(value: Any, limit: int, fallback: str = "") -> str:
    if not isinstance(value, str):
        return fallback
    value = value.strip()
    return (value or fallback)[:limit]


def parse_plan(plan_json: str) -> dict[str, Any]:
    if not isinstance(plan_json, str) or not plan_json.strip():
        raise ValueError("plan_required")
    try:
        plan = json.loads(plan_json)
    except json.JSONDecodeError as exc:
        raise ValueError("invalid_plan_json") from exc
    if not isinstance(plan, dict):
        raise ValueError("invalid_plan_object")
    goal = _text(plan.get("goal"), 1200)
    if not goal:
        raise ValueError("goal_required")
    return plan


def compile_prompt(plan: dict[str, Any]) -> str:
    goal = _text(plan.get("goal"), 1200)
    style = _text(plan.get("style"), 240, "cinematic photorealism")
    look = plan.get("master_look") if isinstance(plan.get("master_look"), dict) else {}
    shots = plan.get("shots") if isinstance(plan.get("shots"), list) else []
    shot = shots[0] if shots and isinstance(shots[0], dict) else {}

    framing = _text(shot.get("framing"), 80, "medium")
    move = _text(shot.get("camera_move"), 80, "locked")
    lens = shot.get("lens_mm", 50)
    lighting = _text(look.get("lighting"), 180, "motivated natural lighting")
    color = _text(look.get("color"), 180, "restrained cinematic color")
    camera_rule = _text(look.get("camera_rule"), 180, "motivated camera movement only")

    return (
        f"{goal} "
        f"Visual style: {style}. "
        f"Shot: {framing}, {lens}mm lens, camera movement {move}. "
        f"Lighting: {lighting}. Color: {color}. "
        f"Camera rule: {camera_rule}. "
        "Physically plausible motion, coherent object permanence, stable geometry, "
        "consistent exposure and white balance, natural motion blur, premium cinematic finish."
    )[:2400]


def clamp_steps(value: int) -> int:
    try:
        value = int(value)
    except Exception:
        value = 12
    return max(4, min(30, value))


def frame_count(duration_seconds: float, fps: int = 24) -> int:
    try:
        seconds = float(duration_seconds)
    except Exception:
        seconds = 2.0
    seconds = max(0.75, min(5.0, seconds))
    raw = max(17, min(121, round(seconds * fps)))
    # Wan temporal VAE works best with frame counts in 4k+1 form.
    lower = max(17, ((raw - 1) // 4) * 4 + 1)
    upper = min(121, lower + 4)
    return lower if abs(raw - lower) <= abs(upper - raw) else upper


def resolution_dims(resolution: str, aspect: str) -> tuple[int, int]:
    resolution = _text(resolution, 20, "480p").lower()
    aspect = _text(aspect, 20, "16:9")
    if resolution == "720p":
        if aspect == "9:16":
            return 704, 1280
        return 1280, 704
    if aspect == "9:16":
        return 480, 832
    return 832, 480


def artifact_evidence(path: str | Path, fps: int, frames: int) -> dict[str, Any]:
    p = Path(path)
    if not p.exists() or not p.is_file():
        raise ValueError("artifact_missing")
    size = p.stat().st_size
    if size < 1024:
        raise ValueError("artifact_too_small")
    digest = hashlib.sha256(p.read_bytes()).hexdigest()
    return {
        "worker_version": WORKER_VERSION,
        "model_id": MODEL_ID,
        "mime": "video/mp4",
        "sha256": digest,
        "size_bytes": size,
        "fps": fps,
        "frame_count": frames,
        "duration_sec": round(frames / fps, 3),
        "playback_probe": "pending_hakim_verification",
        "technical_gates_passed": True,
        "quality_gates_passed": False,
        "cinematic_approval": False,
        "rule": "rendered_is_not_cinematically_approved"
    }
