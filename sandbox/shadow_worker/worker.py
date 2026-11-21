"""Bounded synthetic-only worker: no network, shell, model or production adapter."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sqlite3
import time
import uuid

MAX_TASKS = 256
MAX_ATTEMPTS = 3
MAX_EVENTS = MAX_TASKS * 8
EVENTS = {"enqueued", "claimed", "completed_shadow", "lease_expired", "retry_exhausted"}
ID = re.compile(r"shadow-[a-z0-9-]{1,64}\Z")
OWNER = re.compile(r"[a-z0-9-]{1,64}\Z")


class Rejected(ValueError):
    pass


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def digest(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def validate(task):
    if not isinstance(task, dict) or set(task) != {"id", "kind", "numbers", "synthetic"}:
        raise Rejected("invalid_fields")
    if not isinstance(task["id"], str) or not ID.fullmatch(task["id"]):
        raise Rejected("invalid_id")
    if task["synthetic"] is not True:
        raise Rejected("synthetic_only")
    if task["kind"] not in ("sum", "sort"):
        raise Rejected("capability_denied")
    numbers = task["numbers"]
    if (not isinstance(numbers, list) or not 1 <= len(numbers) <= 100
            or any(type(n) is not int or abs(n) > 10000 for n in numbers)):
        raise Rejected("bounded_integers_required")
    return task


def compute(task):
    validate(task)
    if task["kind"] == "sum":
        value = sum(task["numbers"])
    else:
        value = sorted(task["numbers"])
    return {"mode": "shadow", "kind": task["kind"], "value": value}


def validate_row(row):
    if not isinstance(row["payload"], str) or len(row["payload"]) > 4096:
        raise Rejected("stored_payload_bounds")
    try:
        task = validate(json.loads(row["payload"]))
    except (json.JSONDecodeError, TypeError):
        raise Rejected("stored_payload_invalid") from None
    if row["id"] != task["id"] or row["fingerprint"] != digest(task):
        raise Rejected("payload_integrity")
    attempts = row["attempts"]
    if type(attempts) is not int or not 0 <= attempts <= MAX_ATTEMPTS:
        raise Rejected("attempt_integrity")
    def valid_time(value):
        return type(value) in (int, float) and math.isfinite(value) and value >= 0
    if not valid_time(row["created"]) or not valid_time(row["updated"]) or row["updated"] < row["created"]:
        raise Rejected("timestamp_integrity")
    state = row["state"]
    if state not in ("pending", "leased", "completed", "failed"):
        raise Rejected("state_integrity")
    if state == "leased":
        if (not isinstance(row["owner"], str) or not OWNER.fullmatch(row["owner"])
                or not isinstance(row["fence"], str) or not re.fullmatch(r"[a-f0-9]{32}", row["fence"])
                or not valid_time(row["expires"]) or attempts < 1
                or not 0.049 <= row["expires"] - row["updated"] <= 60.001):
            raise Rejected("lease_integrity")
    elif any(row[k] is not None for k in ("owner", "fence", "expires")):
        raise Rejected("lease_integrity")
    if state == "pending" and attempts >= MAX_ATTEMPTS:
        raise Rejected("attempt_integrity")
    if state == "failed" and attempts != MAX_ATTEMPTS:
        raise Rejected("attempt_integrity")
    if state == "completed":
        if attempts < 1 or not isinstance(row["result"], str) or len(row["result"]) > 4096:
            raise Rejected("result_integrity")
        try:
            result = json.loads(row["result"])
        except json.JSONDecodeError:
            raise Rejected("result_integrity") from None
        if canonical(result) != canonical(compute(task)) or row["result_hash"] != digest(result):
            raise Rejected("result_integrity")
    elif row["result"] is not None or row["result_hash"] is not None:
        raise Rejected("result_integrity")
    return task


class Queue:
    def __init__(self, root):
        self.root = Path(root)
        if self.root.is_symlink():
            raise Rejected("symlink_root")
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.root = self.root.resolve()
        os.chmod(self.root, 0o700)
        self.path = self.root / "queue.sqlite3"
        if self.path.is_symlink():
            raise Rejected("symlink_database")
        fd = os.open(self.path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        os.close(fd)
        os.chmod(self.path, 0o600)
        with self.connect() as con:
            con.executescript("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY, payload TEXT NOT NULL, fingerprint TEXT NOT NULL,
                    state TEXT NOT NULL CHECK(state IN ('pending','leased','completed','failed')),
                    owner TEXT, fence TEXT, expires REAL, attempts INTEGER NOT NULL DEFAULT 0,
                    result TEXT, result_hash TEXT, created REAL NOT NULL, updated REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS events (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL,
                    event TEXT NOT NULL, at REAL NOT NULL, attempt INTEGER NOT NULL);
            """)

    def connect(self):
        con = sqlite3.connect(self.path, timeout=2, isolation_level=None)
        con.row_factory = sqlite3.Row
        con.execute("PRAGMA journal_mode=WAL")
        con.execute("PRAGMA synchronous=FULL")
        con.execute("PRAGMA busy_timeout=2000")
        return con

    def event(self, con, task_id, event, now, attempt):
        con.execute("INSERT INTO events(task_id,event,at,attempt) VALUES (?,?,?,?)",
                    (task_id, event, now, attempt))

    def validate_store(self, con):
        if con.execute("SELECT count(*) FROM tasks").fetchone()[0] > MAX_TASKS:
            raise Rejected("stored_capacity")
        if con.execute("SELECT count(*) FROM events").fetchone()[0] > MAX_EVENTS:
            raise Rejected("stored_event_capacity")
        if con.execute("SELECT count(*) FROM tasks WHERE length(payload)>4096 OR length(result)>4096").fetchone()[0]:
            raise Rejected("stored_payload_bounds")
        if con.execute("SELECT count(*) FROM events WHERE length(event)>32 OR length(task_id)>71").fetchone()[0]:
            raise Rejected("stored_event_bounds")
        rows = con.execute("SELECT * FROM tasks ORDER BY created,id").fetchall()
        for row in rows:
            validate_row(row)
        task_ids = {row["id"] for row in rows}
        for event in con.execute("SELECT * FROM events"):
            if (event["event"] not in EVENTS or event["task_id"] not in task_ids
                    or type(event["attempt"]) is not int or not 0 <= event["attempt"] <= MAX_ATTEMPTS
                    or type(event["at"]) not in (int, float) or not math.isfinite(event["at"])
                    or event["at"] < 0):
                raise Rejected("event_integrity")
        return rows

    def enqueue(self, task):
        validate(task)
        payload = canonical(task)
        fingerprint = digest(task)
        with self.connect() as con:
            con.execute("BEGIN IMMEDIATE")
            self.validate_store(con)
            old = con.execute("SELECT * FROM tasks WHERE id=?", (task["id"],)).fetchone()
            if old:
                if old["payload"] != payload or old["fingerprint"] != fingerprint:
                    raise Rejected("id_collision")
                con.commit()
                return "existing"
            if con.execute("SELECT count(*) FROM tasks").fetchone()[0] >= MAX_TASKS:
                raise Rejected("capacity_no_eviction")
            now = time.time()
            con.execute("INSERT INTO tasks(id,payload,fingerprint,state,created,updated) "
                        "VALUES (?,?,?,'pending',?,?)", (task["id"], payload, fingerprint, now, now))
            self.event(con, task["id"], "enqueued", now, 0)
            con.commit()
            return "created"

    def claim(self, owner, lease_seconds=30):
        if not isinstance(owner, str) or not OWNER.fullmatch(owner):
            raise Rejected("invalid_owner")
        if type(lease_seconds) not in (int, float) or not 0.05 <= lease_seconds <= 60:
            raise Rejected("invalid_lease")
        with self.connect() as con:
            con.execute("BEGIN IMMEDIATE")
            self.validate_store(con)
            now = time.time()
            expired = con.execute("SELECT * FROM tasks WHERE state='leased' AND expires<=?",
                                  (now,)).fetchall()
            for row in expired:
                state = "failed" if row["attempts"] >= MAX_ATTEMPTS else "pending"
                con.execute("UPDATE tasks SET state=?,owner=NULL,fence=NULL,expires=NULL,updated=? "
                            "WHERE id=?", (state, now, row["id"]))
                self.event(con, row["id"], "retry_exhausted" if state == "failed" else "lease_expired",
                           now, row["attempts"])
            row = con.execute("SELECT * FROM tasks WHERE state='pending' ORDER BY created,id LIMIT 1").fetchone()
            if row is None:
                con.commit()
                return None
            task = validate(json.loads(row["payload"]))
            if row["fingerprint"] != digest(task):
                raise Rejected("payload_integrity")
            fence = uuid.uuid4().hex
            attempt = row["attempts"] + 1
            con.execute("UPDATE tasks SET state='leased',owner=?,fence=?,expires=?,attempts=?,updated=? "
                        "WHERE id=?", (owner, fence, now + lease_seconds, attempt, now, row["id"]))
            self.event(con, row["id"], "claimed", now, attempt)
            con.commit()
            return {"task": task, "owner": owner, "fence": fence, "attempt": attempt}

    def complete(self, lease, result):
        with self.connect() as con:
            con.execute("BEGIN IMMEDIATE")
            self.validate_store(con)
            now = time.time()
            row = con.execute("SELECT * FROM tasks WHERE id=?", (lease["task"]["id"],)).fetchone()
            if (row is None or row["state"] != "leased" or row["owner"] != lease["owner"]
                    or row["fence"] != lease["fence"] or row["expires"] <= now):
                raise Rejected("lease_not_current")
            task = validate(json.loads(row["payload"]))
            if row["fingerprint"] != digest(task) or task != lease["task"]:
                raise Rejected("payload_integrity")
            if canonical(result) != canonical(compute(task)):
                raise Rejected("result_rejected")
            con.execute("UPDATE tasks SET state='completed',result=?,result_hash=?,owner=NULL,"
                        "fence=NULL,expires=NULL,updated=? WHERE id=?",
                        (canonical(result), digest(result), now, row["id"]))
            self.event(con, row["id"], "completed_shadow", now, row["attempts"])
            con.commit()

    def snapshot(self):
        with self.connect() as con:
            if con.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise Rejected("database_integrity")
            con.execute("BEGIN")
            tasks = [dict(r) for r in self.validate_store(con)]
            events = [dict(r) for r in con.execute("SELECT * FROM events ORDER BY seq")]
            for row in tasks:
                task = validate(json.loads(row["payload"]))
                if row["fingerprint"] != digest(task):
                    raise Rejected("payload_integrity")
                if row["state"] == "completed":
                    value = json.loads(row["result"])
                    if row["result_hash"] != digest(value) or value != compute(task):
                        raise Rejected("result_integrity")
            con.commit()
            return {"mode": "shadow", "tasks": tasks, "events": events}


def run_once(queue, owner):
    lease = queue.claim(owner)
    if lease is None:
        return {"state": "idle", "mode": "shadow"}
    queue.complete(lease, compute(lease["task"]))
    return {"state": "completed_shadow", "task_id": lease["task"]["id"], "attempt": lease["attempt"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("snapshot")
    sub.add_parser("stop-check")
    enqueue = sub.add_parser("enqueue")
    enqueue.add_argument("--task-json", required=True)
    run = sub.add_parser("run-once")
    run.add_argument("--owner", required=True)
    run.add_argument("--simulate-crash", action="store_true")
    args = parser.parse_args()
    queue = Queue(args.root)
    if args.command == "enqueue":
        # Never evaluate input as instructions. Only a small closed typed schema is admitted.
        if len(args.task_json) > 4096:
            raise Rejected("input_too_large")
        result = {"state": queue.enqueue(json.loads(args.task_json))}
    elif args.command == "snapshot":
        result = queue.snapshot()
    elif args.command == "stop-check":
        snap = queue.snapshot()
        leased = [r for r in snap["tasks"] if r["state"] == "leased"]
        result = {"mode": "shadow", "outstanding_leases": len(leased),
                  "expired_leases": sum(r["expires"] <= time.time() for r in leased)}
    elif args.simulate_crash:
        lease = queue.claim(args.owner, 0.15)
        if lease is None:
            raise Rejected("crash_fixture_missing")
        print(canonical({"state": "injected_crash", "task_id": lease["task"]["id"]}), flush=True)
        os._exit(23)
    else:
        result = run_once(queue, args.owner)
    print(canonical(result))


if __name__ == "__main__":
    try:
        main()
    except (Rejected, sqlite3.DatabaseError, json.JSONDecodeError) as error:
        # Error codes are bounded; raw payloads and database content are not printed.
        code = str(error) if isinstance(error, Rejected) else type(error).__name__
        print(canonical({"state": "rejected", "error": code}))
        raise SystemExit(2)
