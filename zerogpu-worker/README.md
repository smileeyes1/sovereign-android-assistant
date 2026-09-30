---
title: Hakim Cinematic Worker
emoji: 🎬
colorFrom: indigo
colorTo: gray
sdk: gradio
sdk_version: 6.1.0
app_file: app.py
pinned: false
license: apache-2.0
python_version: 3.12
short_description: ZeroGPU cinematic render worker for Hakim
---

# Hakim Cinematic Worker

A provider-neutral ZeroGPU render worker for Hakim's cinematic video factory.

## Runtime contract

- Gradio Space with ZeroGPU hardware selected in Space settings.
- Default model: `Wan-AI/Wan2.2-TI2V-5B-Diffusers`.
- Named Gradio API: `render_video`.
- Named Gradio API: `health`.
- Planning is performed by Hakim; this worker only renders an approved shot/plan.
- No payment path exists in this worker.
- A generated file is **not** declared cinematically approved by this worker. It returns technical artifact evidence only; Hakim's higher-level QA must approve it.

## Golden render

Use `golden_render.json` first. It is synthetic, contains no personal media, avoids generated text, and is intentionally short to conserve ZeroGPU quota.

## ZeroGPU

Hugging Face currently requires ZeroGPU to run as a Gradio Space. Select ZeroGPU in the Space hardware settings after creating/duplicating the Space. Free-account hosting eligibility and daily quota are controlled by Hugging Face and can change.

## Privacy

Do not send private faces, voices, credentials, school records, or other sensitive media through a public Space. The first golden render is deliberately synthetic.
