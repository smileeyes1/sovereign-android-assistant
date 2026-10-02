from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DIRECT=(ROOT/"chatgpt-app/src/direct-relay.ts").read_text(encoding="utf-8")
CONT=(ROOT/"chatgpt-app/src/continuity-store.ts").read_text(encoding="utf-8")
BACKEND=(ROOT/"chatgpt-app/src/state-backend.ts").read_text(encoding="utf-8")

def need(text,needle,reason):
    if needle not in text:
        raise SystemExit("CLOUD_STATE_BACKEND_GATE=FAIL reason="+reason)

def forbid(text,needle,reason):
    if needle in text:
        raise SystemExit("CLOUD_STATE_BACKEND_GATE=FAIL reason="+reason)

need(DIRECT,'stateBackend.writeTextAtomic',"relay_atomic_backend")
need(DIRECT,'stateBackend.writeTextExclusive',"relay_exclusive_backend")
need(CONT,'stateBackend.writeTextAtomic',"continuity_atomic_backend")
need(BACKEND,'HAKIM_STATE_BACKEND',"backend_selector")
need(BACKEND,'unsupported_hakim_state_backend',"unknown_backend_fail_closed")
need(BACKEND,'if(requested==="file")',"file_baseline_default")

for text,name in [(DIRECT,"relay"),(CONT,"continuity")]:
    forbid(text,'from "node:fs/promises"',name+"_direct_fs_import")
    forbid(text,"fs.readFile",name+"_direct_read")
    forbid(text,"fs.writeFile",name+"_direct_write")
    forbid(text,"fs.rename",name+"_direct_rename")
    forbid(text,"fs.unlink",name+"_direct_unlink")
    forbid(text,"fs.readdir",name+"_direct_list")

# Known-failure sentinel.
probe=DIRECT+'\nimport fs from "node:fs/promises";\n'
try:
    forbid(probe,'from "node:fs/promises"',"known_failure_sentinel")
except SystemExit:
    pass
else:
    raise SystemExit("CLOUD_STATE_BACKEND_GATE=FAIL reason=sentinel_not_detected")

print("CLOUD_STATE_BACKEND_GATE=PASS default=file remote_claim=false")
