"""Separate acceptance process; verifies saved DB rather than worker success strings."""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import sqlite3


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(root):
    here = Path(__file__).resolve().parent
    tree = ast.parse((here / "worker.py").read_text())
    imported = {n.name for node in ast.walk(tree) if isinstance(node, ast.Import) for n in node.names}
    imported |= {node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)}
    assert imported <= {"argparse", "hashlib", "json", "math", "os", "pathlib", "re", "sqlite3", "time", "uuid"}
    # Independent read-only connection. The worker cannot declare itself accepted.
    con = sqlite3.connect((root / "queue.sqlite3").resolve().as_uri() + "?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    assert con.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    rows = list(con.execute("SELECT * FROM tasks ORDER BY id"))
    assert {r["id"] for r in rows} == {"shadow-cycle-1", "shadow-cycle-2", "shadow-cycle-3"}
    expected = {"shadow-cycle-1": 7, "shadow-cycle-2": 15, "shadow-cycle-3": [-1, 2, 3]}
    for row in rows:
        assert row["state"] == "completed" and all(row[k] is None for k in ("owner", "fence", "expires"))
        task = json.loads(row["payload"])
        result = json.loads(row["result"])
        assert set(task) == {"id", "kind", "numbers", "synthetic"} and task["synthetic"] is True
        assert task["id"] == row["id"] and type(row["attempts"]) is int and 1 <= row["attempts"] <= 3
        assert result["mode"] == "shadow" and result["value"] == expected[row["id"]]
        for text, stored in ((row["payload"], row["fingerprint"]), (row["result"], row["result_hash"])):
            assert hashlib.sha256(text.encode()).hexdigest() == stored
        count = con.execute("SELECT count(*) FROM events WHERE task_id=? AND event='completed_shadow'", (row["id"],)).fetchone()[0]
        assert count == 1
    assert {r["id"]: r["attempts"] for r in rows} == {"shadow-cycle-1": 1, "shadow-cycle-2": 2, "shadow-cycle-3": 1}
    assert con.execute("SELECT count(*) FROM events WHERE event='lease_expired'").fetchone()[0] == 1
    con.close()
    manifest = json.loads((root / "cycles.json").read_text())
    assert len(manifest["cycles"]) == 3
    assert manifest["crash_exit"] == 23 and manifest["stop_detected"] is True
    assert manifest["idle_after_replay"] is True
    assert manifest["worker_sha256"] == sha(here / "worker.py")
    assert all(c["exit"] == 0 for c in manifest["cycles"])
    return {"acceptance": "PASS_SHADOW_ONLY", "three_cycles": True, "resume_without_duplicate": True,
            "worker_sha256": sha(here / "worker.py"), "checker_sha256": sha(here / "acceptance.py"),
            "source_sha": manifest["source_sha"], "external_github_actions": manifest["external_github_actions"],
            "production": "NO_GO", "recurring": "NO_GO", "inference_independence": "NOT_PROVEN"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(check(args.root), sort_keys=True))
