#!/usr/bin/env python3
"""
Hakim Sovereign Video Runtime R31.

Portable, provider-neutral bridge between Hakim and a user-owned ComfyUI runtime.
- stdlib only
- loopback bind by default
- non-loopback bind requires an access token
- fixed workflow template supplied by the operator
- no model downloads, no shell execution, no payment path
"""
from __future__ import annotations

import hashlib
import json
import os
import secrets
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

PROTOCOL = "hakim-video-runtime-v1"
VERSION = "VIDEO-RUNTIME-BRIDGE-2026-10-08-r31"
BIND = os.environ.get("HAKIM_VIDEO_BIND", "127.0.0.1").strip()
PORT = int(os.environ.get("HAKIM_VIDEO_PORT", "8765"))
COMFY_BASE = os.environ.get("COMFY_BASE_URL", "http://127.0.0.1:8188").rstrip("/")
TOKEN = os.environ.get("HAKIM_VIDEO_RUNTIME_TOKEN", "").strip()
WORKFLOW_TEMPLATE = os.environ.get("HAKIM_VIDEO_WORKFLOW_TEMPLATE", "").strip()
MODEL_LABEL = os.environ.get("HAKIM_VIDEO_MODEL_LABEL", "operator-configured").strip()
COST_CLASS = os.environ.get("HAKIM_VIDEO_COST_CLASS", "self_hosted").strip().lower()
STATE_DIR = Path(os.environ.get("HAKIM_VIDEO_STATE_DIR", str(Path.home() / ".hakim-video-runtime")))
JOBS_FILE = STATE_DIR / "jobs.json"
MAX_BODY = 2 * 1024 * 1024
JOBS_LOCK = threading.Lock()

if BIND not in {"127.0.0.1", "::1", "localhost"} and not TOKEN:
    raise SystemExit("HAKIM_VIDEO_RUNTIME=FAIL reason=token_required_for_non_loopback_bind")
if COST_CLASS == "paid":
    raise SystemExit("HAKIM_VIDEO_RUNTIME=FAIL reason=paid_runtime_not_allowed_by_bridge_default")

STATE_DIR.mkdir(parents=True, exist_ok=True)
try:
    os.chmod(STATE_DIR, 0o700)
except OSError:
    pass


def read_jobs() -> dict[str, Any]:
    with JOBS_LOCK:
        if not JOBS_FILE.exists():
            return {}
        try:
            data = json.loads(JOBS_FILE.read_text(encoding="utf-8"))
            return data if isinstance(data, dict) else {}
        except Exception:
            return {}


def write_jobs(data: dict[str, Any]) -> None:
    with JOBS_LOCK:
        tmp = JOBS_FILE.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.chmod(tmp, 0o600)
        os.replace(tmp, JOBS_FILE)


def json_request(url: str, method: str = "GET", payload: dict | None = None, timeout: int = 20) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Accept", "application/json")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read(MAX_BODY)
            return resp.status, json.loads(raw.decode("utf-8")) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read(MAX_BODY)
        try:
            obj = json.loads(raw.decode("utf-8"))
        except Exception:
            obj = {"error": "upstream_http_" + str(e.code)}
        return e.code, obj


def comfy_available() -> tuple[bool, str]:
    for path in ("/system_stats", "/object_info"):
        try:
            code, _ = json_request(COMFY_BASE + path, timeout=5)
            if 200 <= code < 300:
                return True, path
        except Exception:
            pass
    return False, "unreachable"


def load_workflow() -> dict[str, Any]:
    if not WORKFLOW_TEMPLATE:
        raise ValueError("workflow_template_not_configured")
    p = Path(WORKFLOW_TEMPLATE).expanduser().resolve()
    if not p.is_file():
        raise ValueError("workflow_template_missing")
    if p.stat().st_size > MAX_BODY:
        raise ValueError("workflow_template_too_large")
    obj = json.loads(p.read_text(encoding="utf-8"))
    if not isinstance(obj, dict):
        raise ValueError("workflow_template_invalid")
    return obj


def replace_tokens(value: Any, mapping: dict[str, Any]) -> Any:
    if isinstance(value, dict):
        return {k: replace_tokens(v, mapping) for k, v in value.items()}
    if isinstance(value, list):
        return [replace_tokens(v, mapping) for v in value]
    if isinstance(value, str):
        if value in mapping:
            return mapping[value]
        out = value
        for k, v in mapping.items():
            if isinstance(v, (str, int, float)):
                out = out.replace(k, str(v))
        return out
    return value


def compile_workflow(plan: dict[str, Any]) -> dict[str, Any]:
    prompt = str(plan.get("prompt") or plan.get("goal") or "").strip()
    if not prompt or len(prompt) > 12000:
        raise ValueError("prompt_required_or_too_large")
    negative = str(plan.get("negative_prompt") or "").strip()[:8000]
    width = max(256, min(4096, int(plan.get("width", 1280))))
    height = max(256, min(4096, int(plan.get("height", 720))))
    fps = max(1, min(120, int(plan.get("fps", 24))))
    frames = max(1, min(10000, int(plan.get("frames", fps * max(1, int(plan.get("duration_sec", 5)))))))
    seed = int(plan.get("seed", secrets.randbits(31))) & 0x7FFFFFFF
    mapping = {
        "__HAKIM_PROMPT__": prompt,
        "__HAKIM_NEGATIVE_PROMPT__": negative,
        "__HAKIM_WIDTH__": width,
        "__HAKIM_HEIGHT__": height,
        "__HAKIM_FPS__": fps,
        "__HAKIM_FRAMES__": frames,
        "__HAKIM_SEED__": seed,
    }
    return replace_tokens(load_workflow(), mapping)


def collect_outputs(history: Any, prompt_id: str) -> list[dict[str, str]]:
    if not isinstance(history, dict):
        return []
    record = history.get(prompt_id, history)
    if not isinstance(record, dict):
        return []
    outputs = record.get("outputs", {})
    found: list[dict[str, str]] = []
    if not isinstance(outputs, dict):
        return found
    for node in outputs.values():
        if not isinstance(node, dict):
            continue
        for key in ("videos", "gifs", "images", "audio"):
            items = node.get(key, [])
            if not isinstance(items, list):
                continue
            for item in items:
                if not isinstance(item, dict) or not item.get("filename"):
                    continue
                found.append({
                    "kind": key.rstrip("s"),
                    "filename": str(item.get("filename"))[:512],
                    "subfolder": str(item.get("subfolder", ""))[:512],
                    "type": str(item.get("type", "output"))[:64],
                })
    return found


def safe_job_id(value: str) -> bool:
    return 8 <= len(value) <= 128 and all(c.isalnum() or c in "._:-" for c in value)


class Handler(BaseHTTPRequestHandler):
    server_version = "HakimVideoRuntime/1"

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("[hakim-video] " + (fmt % args) + "\n")

    def auth_ok(self) -> bool:
        if not TOKEN:
            return True
        header = self.headers.get("Authorization", "")
        return secrets.compare_digest(header, "Bearer " + TOKEN)

    def send_json(self, code: int, obj: dict[str, Any]) -> None:
        raw = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_BODY:
            raise ValueError("invalid_body_size")
        raw = self.rfile.read(length)
        obj = json.loads(raw.decode("utf-8"))
        if not isinstance(obj, dict):
            raise ValueError("json_object_required")
        return obj

    def do_GET(self) -> None:
        if not self.auth_ok():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return

        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/hakim/video/v1/capabilities":
            available, probe = comfy_available()
            template_ok = bool(WORKFLOW_TEMPLATE and Path(WORKFLOW_TEMPLATE).expanduser().is_file())
            self.send_json(200, {
                "ok": True,
                "protocol": PROTOCOL,
                "version": VERSION,
                "available": bool(available and template_ok),
                "executor": "comfyui",
                "model_label": MODEL_LABEL,
                "cost_class": COST_CLASS,
                "workflow_template_configured": template_ok,
                "upstream_probe": probe,
                "model_download_automatic": False,
                "arbitrary_shell_exposed": False,
                "paid_execution_automatic": False,
                "artifact_proxy": True,
            })
            return

        prefix = "/hakim/video/v1/jobs/"
        if parsed.path.startswith(prefix):
            suffix = parsed.path[len(prefix):]
            if suffix.endswith("/artifact"):
                job_id = suffix[:-len("/artifact")].rstrip("/")
                self.serve_artifact(job_id, parsed.query)
                return
            job_id = suffix.strip("/")
            if not safe_job_id(job_id):
                self.send_json(400, {"ok": False, "error": "invalid_job_id"})
                return
            jobs = read_jobs()
            job = jobs.get(job_id)
            if not isinstance(job, dict):
                self.send_json(404, {"ok": False, "error": "job_not_found"})
                return
            prompt_id = str(job.get("prompt_id", ""))
            try:
                code, history = json_request(COMFY_BASE + "/history/" + urllib.parse.quote(prompt_id), timeout=10)
                if not (200 <= code < 300):
                    self.send_json(502, {"ok": False, "error": "history_unavailable"})
                    return
                outputs = collect_outputs(history, prompt_id)
                state = "completed" if outputs else "processing"
                self.send_json(200, {
                    "ok": True,
                    "protocol": PROTOCOL,
                    "job_id": job_id,
                    "prompt_id": prompt_id,
                    "state": state,
                    "outputs": outputs,
                    "artifact_count": len(outputs),
                })
            except Exception:
                self.send_json(502, {"ok": False, "error": "history_request_failed"})
            return

        self.send_json(404, {"ok": False, "error": "not_found"})

    def do_POST(self) -> None:
        if not self.auth_ok():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path != "/hakim/video/v1/jobs":
            self.send_json(404, {"ok": False, "error": "not_found"})
            return
        try:
            body = self.read_json()
        except Exception as e:
            self.send_json(400, {"ok": False, "error": str(e)})
            return
        if body.get("protocol") != PROTOCOL:
            self.send_json(400, {"ok": False, "error": "protocol_mismatch"})
            return
        key = str(body.get("idempotency_key", "")).strip()
        if not safe_job_id(key):
            self.send_json(400, {"ok": False, "error": "invalid_idempotency_key"})
            return
        plan = body.get("plan")
        if not isinstance(plan, dict):
            self.send_json(400, {"ok": False, "error": "plan_required"})
            return

        jobs = read_jobs()
        for existing_id, meta in jobs.items():
            if isinstance(meta, dict) and meta.get("idempotency_key") == key:
                self.send_json(200, {
                    "ok": True, "protocol": PROTOCOL, "job_id": existing_id,
                    "prompt_id": meta.get("prompt_id"), "idempotent_replay": True,
                })
                return

        try:
            workflow = compile_workflow(plan)
            code, upstream = json_request(
                COMFY_BASE + "/prompt",
                method="POST",
                payload={"prompt": workflow, "client_id": "hakim-video-runtime"},
                timeout=30,
            )
        except ValueError as e:
            self.send_json(400, {"ok": False, "error": str(e)})
            return
        except Exception:
            self.send_json(502, {"ok": False, "error": "comfy_submit_failed"})
            return
        if not (200 <= code < 300) or not isinstance(upstream, dict) or not upstream.get("prompt_id"):
            self.send_json(502, {"ok": False, "error": "comfy_rejected_job"})
            return

        job_id = "hv_" + secrets.token_hex(12)
        jobs[job_id] = {
            "prompt_id": str(upstream["prompt_id"]),
            "idempotency_key": key,
            "created_at": int(time.time()),
            "plan_sha256": hashlib.sha256(json.dumps(plan, sort_keys=True, separators=(",", ":")).encode()).hexdigest(),
        }
        write_jobs(jobs)
        self.send_json(202, {
            "ok": True,
            "protocol": PROTOCOL,
            "job_id": job_id,
            "prompt_id": str(upstream["prompt_id"]),
            "state": "queued",
            "idempotent_replay": False,
        })

    def serve_artifact(self, job_id: str, query: str) -> None:
        if not safe_job_id(job_id):
            self.send_json(400, {"ok": False, "error": "invalid_job_id"})
            return
        jobs = read_jobs()
        job = jobs.get(job_id)
        if not isinstance(job, dict):
            self.send_json(404, {"ok": False, "error": "job_not_found"})
            return
        prompt_id = str(job.get("prompt_id", ""))
        try:
            _, history = json_request(COMFY_BASE + "/history/" + urllib.parse.quote(prompt_id), timeout=10)
            outputs = collect_outputs(history, prompt_id)
            params = urllib.parse.parse_qs(query)
            index = int(params.get("index", ["0"])[0])
            if index < 0 or index >= len(outputs):
                self.send_json(404, {"ok": False, "error": "artifact_not_found"})
                return
            item = outputs[index]
            qs = urllib.parse.urlencode({
                "filename": item["filename"],
                "subfolder": item["subfolder"],
                "type": item["type"],
            })
            req = urllib.request.Request(COMFY_BASE + "/view?" + qs, method="GET")
            with urllib.request.urlopen(req, timeout=60) as resp:
                length = resp.headers.get("Content-Length")
                self.send_response(200)
                self.send_header("Content-Type", resp.headers.get("Content-Type", "application/octet-stream"))
                if length:
                    self.send_header("Content-Length", length)
                self.send_header("Content-Disposition", 'attachment; filename="hakim-video-artifact"')
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                while True:
                    chunk = resp.read(1024 * 1024)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        except Exception:
            if not self.wfile.closed:
                try:
                    self.send_json(502, {"ok": False, "error": "artifact_proxy_failed"})
                except Exception:
                    pass


def main() -> None:
    print(json.dumps({
        "event": "HAKIM_VIDEO_RUNTIME_READY",
        "protocol": PROTOCOL,
        "version": VERSION,
        "bind": BIND,
        "port": PORT,
        "comfy_base_sha256": hashlib.sha256(COMFY_BASE.encode()).hexdigest(),
        "workflow_configured": bool(WORKFLOW_TEMPLATE),
        "token_required": bool(TOKEN),
        "cost_class": COST_CLASS,
    }, separators=(",", ":")), flush=True)
    ThreadingHTTPServer((BIND, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
