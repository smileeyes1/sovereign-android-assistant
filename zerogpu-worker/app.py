# Import spaces before CUDA-related packages. This is required by ZeroGPU.
import spaces

import json
import os
import tempfile

import gradio as gr
import torch
from diffusers import AutoencoderKLWan, WanPipeline
from diffusers.utils import export_to_video

from contract import (
    DEFAULT_NEGATIVE,
    MODEL_ID,
    WORKER_VERSION,
    artifact_evidence,
    clamp_steps,
    compile_prompt,
    frame_count,
    parse_plan,
    resolution_dims,
)

FPS = 24

# ZeroGPU provides CUDA emulation at module initialization. Keep model placement
# at module level so the real GPU allocation can attach efficiently at call time.
vae = AutoencoderKLWan.from_pretrained(
    MODEL_ID,
    subfolder="vae",
    torch_dtype=torch.float32,
)
pipe = WanPipeline.from_pretrained(
    MODEL_ID,
    vae=vae,
    torch_dtype=torch.bfloat16,
)
pipe.to("cuda")


def _gpu_duration(plan_json, seed, steps, duration_seconds, resolution):
    del plan_json, seed, resolution
    step_count = clamp_steps(steps)
    try:
        seconds = max(0.75, min(5.0, float(duration_seconds)))
    except Exception:
        seconds = 2.0
    # Stay conservative but bounded. Shorter declared duration improves queue priority.
    return int(max(75, min(300, 55 + step_count * 6 + seconds * 8)))


@spaces.GPU(duration=_gpu_duration, size="large")
def render_video(plan_json, seed=42, steps=12, duration_seconds=2.0, resolution="480p"):
    plan = parse_plan(plan_json)
    prompt = compile_prompt(plan)
    step_count = clamp_steps(steps)
    frames = frame_count(duration_seconds, FPS)
    width, height = resolution_dims(resolution, str(plan.get("aspect", "16:9")))

    generator = torch.Generator(device="cuda").manual_seed(int(seed))
    output = pipe(
        prompt=prompt,
        negative_prompt=DEFAULT_NEGATIVE,
        height=height,
        width=width,
        num_frames=frames,
        guidance_scale=5.0,
        num_inference_steps=step_count,
        generator=generator,
    ).frames[0]

    with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
        video_path = tmp.name

    export_to_video(output, video_path, fps=FPS, quality=7.0)
    evidence = artifact_evidence(video_path, FPS, frames)
    evidence.update(
        {
            "seed": int(seed),
            "steps": step_count,
            "width": width,
            "height": height,
            "prompt_compiled": True,
            "provider": "huggingface_zerogpu",
        }
    )
    return video_path, json.dumps(evidence, ensure_ascii=False)


def health():
    return json.dumps(
        {
            "ok": True,
            "worker_version": WORKER_VERSION,
            "model_id": MODEL_ID,
            "provider": "huggingface_zerogpu",
            "gpu_requested_only_during_render": True,
            "paid_path": False,
            "artifact_success_claimed": False,
        },
        ensure_ascii=False,
    )


with gr.Blocks(title="Hakim Cinematic Worker") as demo:
    gr.Markdown(
        "# Hakim Cinematic Worker\n"
        "ZeroGPU render worker. Rendering is not cinematic approval; Hakim performs final QA."
    )
    plan_input = gr.Code(
        label="Hakim cinematic plan (JSON)",
        language="json",
        value=open(os.path.join(os.path.dirname(__file__), "golden_render.json"), encoding="utf-8").read(),
    )
    with gr.Row():
        seed_input = gr.Number(label="Seed", value=42, precision=0)
        steps_input = gr.Slider(4, 30, value=12, step=1, label="Inference steps")
        duration_input = gr.Slider(0.75, 5.0, value=2.0, step=0.25, label="Seconds")
        resolution_input = gr.Dropdown(["480p", "720p"], value="480p", label="Resolution")

    render_button = gr.Button("Render", variant="primary")
    video_output = gr.Video(label="Rendered MP4")
    evidence_output = gr.Code(label="Artifact evidence", language="json")

    render_button.click(
        fn=render_video,
        inputs=[plan_input, seed_input, steps_input, duration_input, resolution_input],
        outputs=[video_output, evidence_output],
        api_name="render_video",
    )

    health_output = gr.Code(label="Health", language="json", visible=False)
    health_button = gr.Button("Health", visible=False)
    health_button.click(fn=health, inputs=[], outputs=health_output, api_name="health")


if __name__ == "__main__":
    demo.queue(default_concurrency_limit=1).launch(mcp_server=True)
