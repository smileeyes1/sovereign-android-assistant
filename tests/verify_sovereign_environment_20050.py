from pathlib import Path
import json, os, re, sqlite3, subprocess, sys, tempfile, time, urllib.error, urllib.request

ROOT=Path(__file__).resolve().parents[1]

def text(p): return (ROOT/p).read_text(encoding="utf-8")
def req(ok,msg):
    if not ok: raise SystemExit(msg)

runtime=text("scripts/hakim-sovereign-runtime.py")
installer=text("scripts/install-hakim-sovereign-runtime-termux.sh")
bridge=text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignEnvironmentBridge.kt")
kernel=text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignOneKernel.kt")
fabric=text("app/src/main/java/ps/hakim/phoneagent/HakimIntegrationFabric.kt")
health=text("app/src/main/java/ps/hakim/phoneagent/HakimHealthBeacon.kt")
app=text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
build=text("app/build.gradle")
policy=json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

for token in [
    'DEFAULT_HOST="127.0.0.1"',
    '"external_provider_required":False',
    '"owner":"human_user"',
    '"role":"durable_local_substrate_not_normative_governor"',
    'X-Hakim-Token',
    'hmac.compare_digest',
    'sqlite3.connect',
    'PRAGMA journal_mode=WAL',
    'CREATE TABLE IF NOT EXISTS commands',
    'CREATE TABLE IF NOT EXISTS events',
]:
    req(token in runtime, f"P0 runtime contract missing: {token}")

req("subprocess" not in runtime and "os.system" not in runtime and "shell=True" not in runtime,
    "P0 runtime must not execute arbitrary shell commands")
req("127.0.0.1:8765" in bridge and "http://127.0.0.1:8765" in bridge,
    "P0 Android bridge must use fixed loopback endpoint")
req("external_providers_are_optional_adapters" in bridge and
    "durable_substrate_not_normative_governor" in bridge and
    "one_sovereign_kernel_remains_governor" in bridge,
    "P0 bridge ownership/governance boundary missing")
req("HakimSovereignEnvironmentBridge.status(context)" in kernel,
    "P0 one kernel does not observe sovereign environment")
req("durable_state_substrate_is_local" in kernel and "durable_substrate_is_not_normative_governor" in kernel,
    "P0 kernel does not constrain environment to substrate role")
req("sovereign_environment_integrated" in fabric and "sovereign_environment→mission_ledger" in fabric,
    "P0 integration fabric does not include sovereign environment")
req('put("sovereign_environment", HakimSovereignEnvironmentBridge.status(app))' in health,
    "P0 health beacon does not expose sovereign environment")
req("HakimSovereignEnvironmentBridge.probe(app)" in app,
    "P0 app startup does not discover sovereign environment")
req("hakim-sovereign-runtime.py" in installer and "127.0.0.1" in installer,
    "P0 Termux installer does not pin local runtime")

m=re.search(r"versionCode\s+(\d+)",build)
req(m and int(m.group(1))==20050,"P0 version must be 20050")
req(policy["current_candidate_version"]==20050,"P0 signing policy candidate must be 20050")

with tempfile.TemporaryDirectory() as td:
    state=Path(td)/"state"
    port=18765
    proc=subprocess.Popen(
        [sys.executable, str(ROOT/"scripts/hakim-sovereign-runtime.py"),
         "--state-dir",str(state),"--host","127.0.0.1","--port",str(port)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        base=f"http://127.0.0.1:{port}"
        health_obj=None
        for _ in range(50):
            try:
                with urllib.request.urlopen(base+"/health",timeout=0.5) as r:
                    health_obj=json.load(r)
                break
            except Exception:
                time.sleep(0.1)
        req(health_obj and health_obj.get("ok") is True,"P0 runtime health not ready")
        req(health_obj.get("loopback_only") is True,"P0 runtime health not loopback-only")
        req(health_obj.get("external_provider_required") is False,"P0 runtime depends on external provider")

        try:
            urllib.request.urlopen(base+"/v1/status",timeout=1)
            raise SystemExit("P0 authenticated status accepted without token")
        except urllib.error.HTTPError as e:
            req(e.code==401,"P0 unauthenticated status did not return 401")

        token=(state/"token").read_text().strip()
        req(len(token)>=32,"P0 runtime token too short")
        headers={"X-Hakim-Token":token,"Content-Type":"application/json"}
        rq=urllib.request.Request(base+"/v1/status",headers=headers)
        with urllib.request.urlopen(rq,timeout=1) as r:
            status=json.load(r)
        req(status.get("single_durable_substrate") is True,"P0 runtime substrate flag missing")
        req(status.get("normative_governor")=="hakim_one_sovereign_kernel","P0 runtime claims wrong governor")
        req(status.get("external_providers")=="optional_adapters","P0 external providers not optional")

        body=json.dumps({"intent":"اختبار بيئة حكيم","params":{"x":1},"idempotency_key":"same"}).encode()
        ids=[]
        for _ in range(2):
            rq=urllib.request.Request(base+"/v1/commands",data=body,headers=headers,method="POST")
            with urllib.request.urlopen(rq,timeout=1) as r:
                ids.append(json.load(r)["command_id"])
        req(ids[0]==ids[1],"P0 idempotency produced duplicate command ids")

        db=sqlite3.connect(state/"state.sqlite3")
        count=db.execute("SELECT COUNT(*) FROM commands").fetchone()[0]
        db.close()
        req(count==1,"P0 durable queue duplicated idempotent command")
    finally:
        proc.terminate()
        try: proc.wait(timeout=3)
        except subprocess.TimeoutExpired: proc.kill()

print("HAKIM_SOVEREIGN_ENV_RUNTIME=PASS")
print("HAKIM_USER_OWNED_DURABLE_STATE=PASS")
print("HAKIM_EXTERNAL_PROVIDERS_OPTIONAL=PASS")
print("HAKIM_SINGLE_GOVERNOR_PRESERVED=PASS")
