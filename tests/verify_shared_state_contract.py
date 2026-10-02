from pathlib import Path
p=Path(__file__).resolve().parents[1]/"governance/HAKIM_SHARED_STATE_CONTRACT.md"
s=p.read_text(encoding="utf-8")
required=[
"قراءة متسقة","كتابة ذرية","إنشاء شرطي حصري","لا fallback صامت",
"أسرار أقل","TLS وهوية","حدود معلومة","قابلية النقل","اختبار مضيفين",
"اختبار takeover","FIELD_TAKEOVER_VERIFIED"
]
missing=[x for x in required if x not in s]
if missing:
    raise SystemExit("SHARED_STATE_CONTRACT_GATE=FAIL missing="+",".join(missing))
backend=(Path(__file__).resolve().parents[1]/"chatgpt-app/src/state-backend.ts").read_text(encoding="utf-8")
if 'if(requested==="file") return new FileStateBackend();' not in backend:
    raise SystemExit("SHARED_STATE_CONTRACT_GATE=FAIL reason=file_baseline_changed")
if 'unsupported_hakim_state_backend:' not in backend:
    raise SystemExit("SHARED_STATE_CONTRACT_GATE=FAIL reason=unknown_backend_not_fail_closed")
print("SHARED_STATE_CONTRACT_GATE=PASS field_takeover=false")
