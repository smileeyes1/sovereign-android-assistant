"""Three fresh worker processes with a real controlled process interruption."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def main(root):
    root = root.resolve()
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    if (root / "queue.sqlite3").exists():
        raise SystemExit("Use a fresh synthetic proof directory; previous evidence is never erased.")
    here = Path(__file__).resolve().parent
    worker = here / "worker.py"
    prefix = [sys.executable, str(worker), "--root", str(root)]
    def execute(args, expected=0):
        started = time.time()
        p = subprocess.Popen(prefix + args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        out, err = p.communicate(timeout=10)
        if p.returncode != expected:
            raise AssertionError((p.returncode, out, err))
        return {"pid": p.pid, "started": started, "finished": time.time(), "exit": p.returncode,
                "observed": json.loads(out)}
    def enqueue(n, kind, values):
        value = {"id": "shadow-cycle-" + str(n), "kind": kind, "numbers": values, "synthetic": True}
        execute(["enqueue", "--task-json", json.dumps(value)])
        return value
    cycles = []
    first = enqueue(1, "sum", [3, 4])
    cycles.append(execute(["run-once", "--owner", "cycle-1"]))
    enqueue(2, "sum", [7, 8])
    crash = execute(["run-once", "--owner", "crashed-worker", "--simulate-crash"], 23)
    time.sleep(0.2)
    stop = execute(["stop-check"])["observed"]
    assert stop["outstanding_leases"] == 1 and stop["expired_leases"] == 1
    cycles.append(execute(["run-once", "--owner", "cycle-2-resumed"]))
    enqueue(3, "sort", [3, -1, 2])
    cycles.append(execute(["run-once", "--owner", "cycle-3"]))
    replay = execute(["enqueue", "--task-json", json.dumps(first)])
    assert replay["observed"]["state"] == "existing"
    idle = execute(["run-once", "--owner", "replay-check"])["observed"]
    manifest = {"mode": "shadow", "cycles": cycles, "crash_exit": crash["exit"],
                "crash": crash, "stop_detected": True, "stop_probe": stop,
                "idle_after_replay": idle["state"] == "idle",
                "worker_sha256": hashlib.sha256(worker.read_bytes()).hexdigest(),
                "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=here, text=True).strip(),
                "external_github_actions": os.environ.get("GITHUB_ACTIONS") == "true"}
    (root / "cycles.json").write_text(json.dumps(manifest, indent=2) + "\n")
    subprocess.run([sys.executable, str(here / "acceptance.py"), "--root", str(root)], check=True, timeout=10)
    print(json.dumps(manifest, sort_keys=True))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    main(parser.parse_args().root)
