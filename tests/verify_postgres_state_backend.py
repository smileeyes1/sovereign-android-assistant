from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/"chatgpt-app/src/state-backend.ts").read_text()
need=["class PostgresStateBackend","HAKIM_POSTGRES_URL",'requested==="postgres"',"ON CONFLICT(state_key) DO NOTHING","RETURNING state_key","ON CONFLICT(state_key) DO UPDATE"]
missing=[x for x in need if x not in s]
if missing: raise SystemExit("POSTGRES_STATE_GATE=FAIL missing="+",".join(missing))
if 'requested==="file"' not in s: raise SystemExit("POSTGRES_STATE_GATE=FAIL baseline_file_missing")
print("POSTGRES_STATE_GATE=PASS field=false")
