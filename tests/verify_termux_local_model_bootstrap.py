from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
script = (ROOT / "scripts/hakim-local-model-termux.sh").read_text(encoding="utf-8")

def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)

require('HOST="127.0.0.1"' in script, "P0: خادم النموذج المحلي لا يثبت loopback")
require('--host "$HOST"' in script, "P0: llama-server لا يُجبر على loopback")
for forbidden in ['--host 0.0.0.0', '--host "::"', 'HAKIM_ALLOW_MODEL_DOWNLOAD:-YES']:
    require(forbidden not in script, f"P0: إعداد خطير في bootstrap المحلي: {forbidden}")

require('LLAMA_REF="v0.4.1"' in script and 'LLAMA_COMMIT="b29c606"' in script,
        "P0: llama.cpp غير مثبت على إصدار/commit معلوم")
require('MODEL_SHA256="d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"' in script,
        "P0: بصمة النموذج المحلي المرجعي مفقودة")
require('/ggml-org/Qwen3-1.7B-GGUF/resolve/daeb8e2d528a760970442092f6bf1e55c3b659eb/' in script,
        "P0: تنزيل النموذج غير مثبت على revision معلوم")
require('HAKIM_ALLOW_MODEL_DOWNLOAD:-NO' in script and 'MODEL_DOWNLOAD_NOT_AUTHORIZED' in script,
        "P0: تنزيل النموذج الثقيل لا يملك بوابة تفويض بيانات صريحة")
require('sha256sum "$part"' in script and 'MODEL_SHA256_MISMATCH' in script,
        "P0: تنزيل النموذج لا يفشل مغلقًا عند اختلاف البصمة")
require('mv "$part" "$MODEL"' in script,
        "P0: ملف النموذج قد يُعتمد قبل اكتمال التنزيل والتحقق")
require('/v1/models' in script and '/v1/chat/completions' in script,
        "P0: bootstrap لا يثبت واجهة OpenAI-compatible التي يحتاجها حكيم")
require('grep -Fq "$MODEL_ALIAS"' in script,
        "P0: readiness لا يثبت هوية النموذج المحمل")
require('temperature":0' in script,
        "P0: smoke test لا يثبت مسار temperature=0")
require('termux-wake-lock' in script,
        "P1: لا توجد محاولة best-effort لحماية runtime المحلي من النوم")
require('rm -f "$PIDFILE"' in script and 'kill -0 "$p"' in script,
        "P0: إدارة عملية النموذج المحلي بلا PID/تحقق حياة")
require('pkg install -y git cmake clang make curl libandroid-spawn' in script,
        "P0: متطلبات Termux الرسمية/العملية غير مثبتة")
require('HAKIM_LOCAL_CONTEXT:-4096' in script,
        "P1: السياق المحلي بلا حد افتراضي مناسب للهاتف")
require('HAKIM_LOCAL_THREADS:-4' in script,
        "P1: عدد threads المحلي بلا حد افتراضي مناسب للهاتف")

print("HAKIM_TERMUX_LOCAL_MODEL_BOOTSTRAP=PASS")
print("HAKIM_MODEL_DOWNLOAD_EXPLICIT_DATA_GATE=PASS")
print("HAKIM_LOCAL_MODEL_PINNED_INTEGRITY=PASS")
