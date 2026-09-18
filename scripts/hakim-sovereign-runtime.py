#!/usr/bin/env python3
import argparse, hashlib, hmac, json, os, secrets, signal, sqlite3, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.request import urlopen, Request

VERSION="HAKIM-SOVEREIGN-ENV-20050-v1"
DEFAULT_HOST="127.0.0.1"
DEFAULT_PORT=8765
MAX_BODY=262144
MAX_TEXT=24000

def now_ms():
    return int(time.time()*1000)

def ensure_dir(p: Path):
    p.mkdir(parents=True, exist_ok=True)
    os.chmod(p, 0o700)

class Store:
    def __init__(self, root: Path):
        self.root=root
        ensure_dir(root)
        self.db=root/"state.sqlite3"
        self.key_file=root/"access.key"
        self.manifest=root/"manifest.json"
        self.access_key=self._load_or_create_key()
        self._init_db()
        self._manifest()

    def _load_or_create_key(self):
        if self.key_file.exists():
            k=self.key_file.read_text(encoding="utf-8").strip()
            if len(k)>=32:
                return k
        k=secrets.token_urlsafe(48)
        self.key_file.write_text(k+"\n",encoding="utf-8")
        os.chmod(self.key_file,0o600)
        return k

    def conn(self):
        c=sqlite3.connect(self.db,timeout=10)
        c.execute("PRAGMA journal_mode=WAL")
        c.execute("PRAGMA synchronous=FULL")
        return c

    def _init_db(self):
        with self.conn() as c:
            c.executescript("""
            CREATE TABLE IF NOT EXISTS kv(
              k TEXT PRIMARY KEY, v TEXT NOT NULL, updated_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              ts INTEGER NOT NULL, kind TEXT NOT NULL, source TEXT NOT NULL,
              value TEXT NOT NULL, evidence_sha256 TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS commands(
              id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
              intent TEXT NOT NULL, params_json TEXT NOT NULL, status TEXT NOT NULL,
              result_json TEXT NOT NULL DEFAULT '{}');
            """)

    def _manifest(self):
        obj={
          "version":VERSION,
          "owner":"human_user",
          "role":"durable_local_substrate_not_normative_governor",
          "bind":f"{DEFAULT_HOST}:{DEFAULT_PORT}",
          "external_provider_required":False,
          "external_models_are_optional":True,
          "local_state_authoritative_for_runtime_continuity":True,
          "normative_authority":"quran_and_authentic_sunnah_via_hakim_kernel",
          "created_at": now_ms()
        }
        if not self.manifest.exists():
            self.manifest.write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding="utf-8")
            os.chmod(self.manifest,0o600)

    def event(self,kind,source,value):
        clean=str(value)[:MAX_TEXT]
        evhash=hashlib.sha256(clean.encode()).hexdigest()
        with self.conn() as c:
            c.execute(
                "INSERT INTO events(ts,kind,source,value,evidence_sha256) VALUES(?,?,?,?,?)",
                (now_ms(),str(kind)[:80],str(source)[:120],clean,evhash)
            )
        return evhash

    def put_command(self,intent,params,idempotency_key):
        intent=str(intent).strip()[:MAX_TEXT]
        if not intent:
            raise ValueError("EMPTY_INTENT")
        params=params if isinstance(params,dict) else {}
        canonical=json.dumps(
            {"intent":intent,"params":params,"idempotency_key":str(idempotency_key)},
            sort_keys=True,separators=(",",":"),ensure_ascii=False
        )
        cid=hashlib.sha256(canonical.encode()).hexdigest()
        t=now_ms()
        with self.conn() as c:
            c.execute(
                """INSERT OR IGNORE INTO commands(id,created_at,updated_at,intent,params_json,status)
                   VALUES(?,?,?,?,?,?)""",
                (cid,t,t,intent,json.dumps(params,ensure_ascii=False,sort_keys=True),"queued")
            )
        self.event("MISSION","api",f"queued:{cid}")
        return cid

    def get_command(self,cid):
        with self.conn() as c:
            r=c.execute(
                "SELECT id,created_at,updated_at,intent,params_json,status,result_json FROM commands WHERE id=?",
                (cid,)
            ).fetchone()
        if not r:
            return None
        return {
            "id":r[0],
            "created_at":r[1],
            "updated_at":r[2],
            "intent":r[3],
            "params":json.loads(r[4]),
            "status":r[5],
            "result":json.loads(r[6])
        }

    def counts(self):
        with self.conn() as c:
            e=c.execute("SELECT COUNT(*) FROM events").fetchone()[0]
            q=c.execute("SELECT COUNT(*) FROM commands WHERE status='queued'").fetchone()[0]
        return e,q

def model_status():
    try:
        req=Request("http://127.0.0.1:8080/v1/models",headers={"User-Agent":"HAKIM-SOVEREIGN-ENV/1"})
        with urlopen(req,timeout=1.5) as r:
            obj=json.load(r)
        mid=((obj.get("data") or [{}])[0].get("id") or "")[:160]
        return {"ready":bool(mid),"model":mid,"endpoint":"127.0.0.1:8080"}
    except Exception as e:
        return {
            "ready":False,
            "model":"",
            "endpoint":"127.0.0.1:8080",
            "error":e.__class__.__name__[:80]
        }

class Api(BaseHTTPRequestHandler):
    server_version="HakimSovereignEnv/1"

    def log_message(self,fmt,*args):
        pass

    def _json(self,code,obj):
        raw=json.dumps(obj,ensure_ascii=False,separators=(",",":")).encode()
        self.send_response(code)
        self.send_header("Content-Type","application/json; charset=utf-8")
        self.send_header("Content-Length",str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _auth(self):
        got=self.headers.get("X-Hakim-Token","")
        return hmac.compare_digest(got,self.server.store.access_key)

    def _body(self):
        n=int(self.headers.get("Content-Length","0") or 0)
        if n<0 or n>MAX_BODY:
            raise ValueError("BODY_TOO_LARGE")
        raw=self.rfile.read(n)
        return json.loads(raw.decode("utf-8")) if raw else {}

    def do_GET(self):
        if self.client_address[0] not in ("127.0.0.1","::1"):
            return self._json(403,{"ok":False,"error":"LOOPBACK_ONLY"})
        if self.path=="/health":
            e,q=self.server.store.counts()
            return self._json(200,{
                "ok":True,
                "version":VERSION,
                "owner":"human_user",
                "loopback_only":True,
                "external_provider_required":False,
                "event_count":e,
                "queued_commands":q
            })
        if not self._auth():
            return self._json(401,{"ok":False,"error":"AUTH_REQUIRED"})
        if self.path=="/v1/status":
            e,q=self.server.store.counts()
            return self._json(200,{
                "ok":True,
                "version":VERSION,
                "state_root":str(self.server.store.root),
                "event_count":e,
                "queued_commands":q,
                "model":model_status(),
                "single_durable_substrate":True,
                "normative_governor":"hakim_one_sovereign_kernel",
                "external_providers":"optional_adapters"
            })
        if self.path.startswith("/v1/commands/"):
            cid=self.path.rsplit("/",1)[-1]
            cmd=self.server.store.get_command(cid)
            return self._json(
                200 if cmd else 404,
                {"ok":True,"command":cmd} if cmd else {"ok":False,"error":"NOT_FOUND"}
            )
        return self._json(404,{"ok":False,"error":"NOT_FOUND"})

    def do_POST(self):
        if self.client_address[0] not in ("127.0.0.1","::1"):
            return self._json(403,{"ok":False,"error":"LOOPBACK_ONLY"})
        if not self._auth():
            return self._json(401,{"ok":False,"error":"AUTH_REQUIRED"})
        try:
            body=self._body()
        except Exception as e:
            return self._json(400,{"ok":False,"error":str(e)[:80]})
        if self.path=="/v1/events":
            evhash=self.server.store.event(
                body.get("kind","EVIDENCE"),
                body.get("source","client"),
                body.get("value","")
            )
            return self._json(200,{"ok":True,"evidence_sha256":evhash})
        if self.path=="/v1/commands":
            try:
                cid=self.server.store.put_command(
                    body.get("intent",""),
                    body.get("params",{}),
                    body.get("idempotency_key","")
                )
                return self._json(200,{"ok":True,"command_id":cid,"status":"queued"})
            except Exception as e:
                return self._json(400,{"ok":False,"error":str(e)[:80]})
        return self._json(404,{"ok":False,"error":"NOT_FOUND"})

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--state-dir",default=os.path.expanduser("~/.hakim-sovereign"))
    p.add_argument("--host",default=DEFAULT_HOST)
    p.add_argument("--port",type=int,default=DEFAULT_PORT)
    p.add_argument("--print-access-key",action="store_true")
    a=p.parse_args()
    if a.host not in ("127.0.0.1","::1","localhost"):
        raise SystemExit("LOOPBACK_ONLY")
    root=Path(a.state_dir).expanduser().resolve()
    s=Store(root)
    if a.print_access_key:
        print(s.access_key)
        return
    httpd=ThreadingHTTPServer(("127.0.0.1",a.port),Api)
    httpd.store=s
    def stop(*_):
        httpd.shutdown()
    signal.signal(signal.SIGTERM,stop)
    signal.signal(signal.SIGINT,stop)
    print(json.dumps({
        "ok":True,
        "version":VERSION,
        "bind":f"127.0.0.1:{a.port}",
        "state":str(root)
    },ensure_ascii=False),flush=True)
    httpd.serve_forever()

if __name__=="__main__":
    main()
