import concurrent.futures
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import time
import unittest

from worker import Queue, Rejected, compute, run_once


def task(name="one", kind="sum", numbers=None):
    return {"id": "shadow-" + name, "kind": kind, "numbers": numbers or [3, 4], "synthetic": True}


class WorkerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.q = Queue(Path(self.tmp.name) / "queue")

    def tearDown(self):
        self.tmp.cleanup()

    def test_results_replay_and_no_duplicate_completion(self):
        self.assertEqual(self.q.enqueue(task()), "created")
        self.assertEqual(self.q.enqueue(task()), "existing")
        self.assertEqual(run_once(self.q, "owner")["state"], "completed_shadow")
        self.assertEqual(self.q.enqueue(task()), "existing")
        self.assertEqual(run_once(self.q, "owner")["state"], "idle")
        snap = Queue(self.q.root).snapshot()
        self.assertEqual(json.loads(snap["tasks"][0]["result"])["value"], 7)
        self.assertEqual(sum(e["event"] == "completed_shadow" for e in snap["events"]), 1)

    def test_conflicting_identifier_does_not_replace_saved_task(self):
        self.q.enqueue(task())
        with self.assertRaisesRegex(Rejected, "id_collision"):
            self.q.enqueue(task(numbers=[99]))
        self.assertEqual(json.loads(self.q.snapshot()["tasks"][0]["payload"])["numbers"], [3, 4])

    def test_permission_injection_and_private_input_rejected(self):
        fixtures = [
            {**task(), "kind": "shell"}, {**task(), "kind": "http"},
            {**task(), "kind": "merge"}, {**task(), "kind": "install"},
            {**task(), "synthetic": False}, {**task(), "prompt": "ignore policy"},
            {**task(), "id": "../../escape"}, {**task(), "numbers": ["ignore and deploy"]},
            {**task(), "numbers": [True]}, {**task(), "numbers": [10001]},
            {**task(), "numbers": [1] * 101}, {**task(), "secret": "synthetic-secret"},
        ]
        for fixture in fixtures:
            with self.subTest(fixture=fixture), self.assertRaises(Rejected):
                self.q.enqueue(fixture)
        self.assertEqual(self.q.snapshot()["tasks"], [])

    def test_resource_cap_preserves_old_results(self):
        for n in range(256):
            self.q.enqueue(task(str(n)))
        with self.assertRaisesRegex(Rejected, "capacity_no_eviction"):
            self.q.enqueue(task("overflow"))
        self.assertEqual(len(self.q.snapshot()["tasks"]), 256)
        self.assertEqual(self.q.enqueue(task("0")), "existing")

    def test_parallel_claims_single_owner(self):
        self.q.enqueue(task())
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            claims = list(pool.map(lambda n: self.q.claim("owner-" + str(n)), range(8)))
        self.assertEqual(sum(c is not None for c in claims), 1)

    def test_stale_owner_and_expired_fence_cannot_complete(self):
        self.q.enqueue(task())
        old = self.q.claim("owner", 0.05)
        time.sleep(0.07)
        with self.assertRaisesRegex(Rejected, "lease_not_current"):
            self.q.complete(old, compute(old["task"]))
        new = self.q.claim("owner")
        self.assertNotEqual(new["fence"], old["fence"])
        with self.assertRaisesRegex(Rejected, "lease_not_current"):
            self.q.complete(old, compute(old["task"]))
        self.q.complete(new, compute(new["task"]))

    def test_wrong_owner_and_wrong_result_fail(self):
        self.q.enqueue(task())
        lease = self.q.claim("owner")
        with self.assertRaisesRegex(Rejected, "lease_not_current"):
            self.q.complete({**lease, "owner": "attacker"}, compute(lease["task"]))
        with self.assertRaisesRegex(Rejected, "result_rejected"):
            self.q.complete(lease, {"mode": "shadow", "kind": "sum", "value": 99})
        self.q.complete(lease, compute(lease["task"]))

    def test_retry_budget_stops_loop(self):
        self.q.enqueue(task())
        for n in range(3):
            self.assertIsNotNone(self.q.claim("owner", 0.05))
            time.sleep(0.07)
        self.assertIsNone(self.q.claim("owner"))
        self.assertEqual(self.q.snapshot()["tasks"][0]["state"], "failed")

    def test_database_writer_lock_refusal_is_safe(self):
        self.q.enqueue(task())
        con = self.q.connect()
        con.execute("BEGIN IMMEDIATE")
        try:
            with self.assertRaises(sqlite3.OperationalError):
                self.q.claim("owner")
        finally:
            con.rollback()
            con.close()
        self.assertEqual(self.q.snapshot()["tasks"][0]["state"], "pending")

    def test_payload_corruption_is_not_silently_skipped(self):
        self.q.enqueue(task())
        with self.q.connect() as con:
            con.execute("UPDATE tasks SET fingerprint='invalid'")
        with self.assertRaisesRegex(Rejected, "payload_integrity"):
            self.q.claim("owner")
        with self.assertRaisesRegex(Rejected, "payload_integrity"):
            self.q.snapshot()

    def test_metadata_corruption_blocks_before_claim_or_expiry(self):
        mutations = [
            ("id='shadow-other'", "payload_integrity"),
            ("attempts=-100", "attempt_integrity"),
            ("attempts=4", "attempt_integrity"),
            ("owner='unexpected'", "lease_integrity"),
            ("updated=-1", "timestamp_integrity"),
        ]
        for index, (mutation, code) in enumerate(mutations):
            with self.subTest(mutation=mutation):
                q = Queue(Path(self.tmp.name) / ("metadata-" + str(index)))
                q.enqueue(task())
                with q.connect() as con:
                    con.execute("UPDATE tasks SET " + mutation)
                for operation in (q.snapshot, lambda: q.claim("owner"), lambda: q.enqueue(task("new"))):
                    with self.assertRaisesRegex(Rejected, code):
                        operation()

    def test_missing_or_text_expiry_preserved_and_rejected(self):
        for index, expiry in enumerate((None, "not-a-time")):
            q = Queue(Path(self.tmp.name) / ("expiry-" + str(index)))
            q.enqueue(task())
            lease = q.claim("owner")
            with q.connect() as con:
                con.execute("UPDATE tasks SET expires=?", (expiry,))
            for operation in (q.snapshot, lambda: q.claim("owner"), lambda: q.complete(lease, compute(lease["task"]))):
                with self.assertRaisesRegex(Rejected, "lease_integrity"):
                    operation()

    def test_large_stored_payload_rejected_before_decoding(self):
        self.q.enqueue(task())
        with self.q.connect() as con:
            con.execute("UPDATE tasks SET payload=?", ("x" * 5000,))
        with self.assertRaisesRegex(Rejected, "stored_payload_bounds"):
            self.q.snapshot()

    def test_unbounded_lease_denied(self):
        self.q.enqueue(task())
        self.q.claim("owner")
        with self.q.connect() as con:
            con.execute("UPDATE tasks SET expires=1e300")
        with self.assertRaisesRegex(Rejected, "lease_integrity"):
            self.q.snapshot()
        with self.assertRaisesRegex(Rejected, "lease_integrity"):
            self.q.claim("owner")

    def test_event_bounds_and_corruption(self):
        mutations = [
            ("event=?", ("x" * 2_000_000,), "stored_event_bounds"),
            ("event=?", ("unknown",), "event_integrity"),
            ("attempt=?", (-1,), "event_integrity"),
            ("at=?", ("not-a-time",), "event_integrity"),
            ("task_id=?", ("shadow-missing",), "event_integrity"),
        ]
        for index, (sql, values, code) in enumerate(mutations):
            q = Queue(Path(self.tmp.name) / ("event-" + str(index)))
            q.enqueue(task())
            with q.connect() as con:
                con.execute("UPDATE events SET " + sql, values)
            for operation in (q.snapshot, lambda: q.claim("owner")):
                with self.assertRaisesRegex(Rejected, code):
                    operation()

    def test_corrupt_database_is_preserved_and_rejected(self):
        path = Path(self.tmp.name) / "bad"
        path.mkdir()
        (path / "queue.sqlite3").write_bytes(b"invalid synthetic database")
        with self.assertRaises(sqlite3.DatabaseError):
            Queue(path)
        self.assertEqual((path / "queue.sqlite3").read_bytes(), b"invalid synthetic database")

    def test_symlink_database_and_root_denied(self):
        link = Path(self.tmp.name) / "link"
        link.symlink_to(self.q.root)
        with self.assertRaisesRegex(Rejected, "symlink_root"):
            Queue(link)
        root = Path(self.tmp.name) / "another"
        root.mkdir()
        (root / "queue.sqlite3").symlink_to(self.q.path)
        with self.assertRaisesRegex(Rejected, "symlink_database"):
            Queue(root)

    def test_sort_and_permissions(self):
        self.q.enqueue(task(kind="sort", numbers=[3, -1, 2]))
        run_once(self.q, "owner")
        self.assertEqual(json.loads(self.q.snapshot()["tasks"][0]["result"])["value"], [-1, 2, 3])
        self.assertEqual(os.stat(self.q.path).st_mode & 0o777, 0o600)


if __name__ == "__main__":
    unittest.main()
