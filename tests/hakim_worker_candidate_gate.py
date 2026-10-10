#!/usr/bin/env python3
"""Fail-closed candidate path gate. The AI cannot edit this gate through worker allowlists."""
import argparse
import re
import subprocess
import sys
import tempfile
from pathlib import Path

SOURCE = "app/src/main/java/ps/hakim/phoneagent/"
ALLOW = {
    "self_check_failed": (
        {SOURCE + name for name in (
            "HakimSelfCheck.kt", "HakimFaultContainment.kt",
            "HakimExecutionFabric.kt", "HakimDevelopmentControlPlane.kt"
        )},
        {"tests/verify_development_control_plane.py"},
    ),
    "repeated_runtime_failure": (
        {SOURCE + name for name in (
            "HakimLearning.kt", "HakimFaultContainment.kt",
            "HakimGoalExecutor.kt", "HakimExecutionFabric.kt"
        )},
        {"tests/verify_20310_resilience.py"},
    ),
    # Changes to release/update and field acceptance code require separate manual work.
    "field_candidate_regression": (set(), set()),
}
TEMPORARY = {
    "candidate.patch", "candidate-meta.json", "changed-files.txt",
    "development-lease.json", "development-request.json",
}
SUSPECT_SECRET = re.compile(
    r"(?:github_pat_[A-Za-z0-9_]{15,}|gh[pousr]_[A-Za-z0-9]{18,}"
    r"|AKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)",
    re.IGNORECASE,
)

class GateError(ValueError):
    pass

def git(root, *args):
    result = subprocess.run(
        ["git", *args], cwd=root, capture_output=True, check=True
    )
    return result.stdout

def allowed_changes(root, trigger):
    if trigger not in ALLOW:
        raise GateError("unknown_trigger")
    sources, tests = ALLOW[trigger]
    if not sources or not tests:
        raise GateError("sensitive_trigger_manual_review_only")

    raw = git(root, "diff", "--no-renames", "--name-status", "-z", "HEAD")
    cells = raw.split(b"\0")
    if cells and cells[-1] == b"":
        cells.pop()
    if len(cells) % 2:
        raise GateError("invalid_diff")
    changes = {}
    for idx in range(0, len(cells), 2):
        status = cells[idx].decode("utf-8", "strict")
        path = cells[idx + 1].decode("utf-8", "strict")
        if status != "M":
            raise GateError("non_modification:" + path)
        if path in changes or path not in sources | tests:
            raise GateError("unexpected_path:" + path)
        if (root / path).is_symlink():
            raise GateError("symlink:" + path)
        changes[path] = status
    if not changes or not (changes.keys() & sources) or not (changes.keys() & tests):
        raise GateError("source_and_regression_test_required")
    if len(changes) > 4:
        raise GateError("too_many_files")
    if git(root, "diff", "--summary", "HEAD").strip():
        raise GateError("file_mode_or_type_change")
    stats = git(root, "diff", "--numstat", "HEAD").decode("utf-8", "strict")
    total = 0
    for row in stats.splitlines():
        added, removed, _ = row.split("\t", 2)
        if not added.isdecimal() or not removed.isdecimal():
            raise GateError("binary_patch")
        total += int(added) + int(removed)
    if total > 250:
        raise GateError("patch_too_large")
    extras = set(
        v.decode("utf-8", "strict")
        for v in git(root, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0")
        if v
    ) - TEMPORARY
    if extras:
        raise GateError("unexpected_untracked:" + sorted(extras)[0])
    patch = git(root, "diff", "--unified=0", "HEAD").decode("utf-8", "replace")
    additions = "\n".join(
        line[1:] for line in patch.splitlines()
        if line.startswith("+") and not line.startswith("+++")
    )
    if SUSPECT_SECRET.search(additions):
        raise GateError("suspected_secret")
    return sorted(changes)

def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        def run(*args):
            subprocess.run(["git", *args], cwd=root, check=True, capture_output=True)
        run("init", "-q")
        run("config", "user.name", "Hakim Gate Test")
        run("config", "user.email", "tests@example.invalid")
        s = SOURCE + "HakimLearning.kt"
        t = "tests/verify_20310_resilience.py"
        for file in (s, t):
            path = root / file
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("baseline\n", encoding="utf-8")
        run("add", "--", s, t)
        run("commit", "-qm", "baseline")
        def put(path, value):
            (root / path).write_text(value, encoding="utf-8")
        def fails(reason):
            try:
                allowed_changes(root, "repeated_runtime_failure")
            except GateError as exc:
                if reason not in str(exc):
                    raise AssertionError("wrong_rejection:" + str(exc)) from exc
                return
            raise AssertionError("false_accept:" + reason)
        put(s, "baseline\nfix\n")
        fails("source_and_regression_test_required")
        put(t, "baseline\nassert regression\n")
        assert allowed_changes(root, "repeated_runtime_failure") == sorted([s, t])
        put("candidate.patch", "temporary artifact\n")
        assert allowed_changes(root, "repeated_runtime_failure") == sorted([s, t])
        put(".env", "SHOULD_NOT_STAGE=true\n")
        fails("unexpected_untracked")
        (root / ".env").unlink()
        put(s, "baseline\nghp_" + "x" * 25 + "\n")
        fails("suspected_secret")
        put(s, "baseline\nfix\n")
        put("tests/verify_20310_resilience.py", "baseline\nassert regression\n")
        path = root / "unexpected.txt"
        put("unexpected.txt", "extra")
        fails("unexpected_untracked")
        path.unlink()
        put("tests/verify_20310_resilience.py", "baseline\n")
        fails("source_and_regression_test_required")
        put(t, "baseline\nassert regression\n")
        try:
            allowed_changes(root, "field_candidate_regression")
        except GateError as exc:
            assert "manual_review_only" in str(exc)
        else:
            raise AssertionError("sensitive_trigger_accepted")
        (root / s).unlink()
        fails("non_modification")
    print("HAKIM_WORKER_CANDIDATE_GATE_SELFTEST=PASS")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--trigger")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.trigger:
        parser.error("--trigger is required")
    try:
        for path in allowed_changes(Path.cwd(), args.trigger):
            print(path)
    except (GateError, subprocess.CalledProcessError) as exc:
        print("HAKIM_WORKER_CANDIDATE_GATE=FAIL reason=" + str(exc), file=sys.stderr)
        raise SystemExit(1)

if __name__ == "__main__":
    main()
