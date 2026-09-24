from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app/src/main/java/ps/hakim/phoneagent"
CENTER=(APP/"CommandCenterActivity.kt").read_text(encoding="utf-8")
HOME=(APP/"UnifiedHomeActivity.kt").read_text(encoding="utf-8")

def req(v,reason):
    if not v: raise SystemExit("PRODUCT_COPY_GATE=FAIL reason="+reason)

# Settings must be customer-facing. Inspect string literals, not internal class names.
home_strings="\n".join(re.findall(r'"(?:\\.|[^"\\])*"', HOME))
for forbidden in ["OpenRouter","Gemini API","ADB","متصفح حكيم","مفتاح API","openrouter/free"]:
    req(forbidden not in home_strings,"settings_jargon:"+forbidden)

# Primary user messages must not expose provider/transport jargon.
for line in CENTER.splitlines():
    if "appendConversation" in line or "status.text" in line:
        for forbidden in ["OpenRouter","OAuth","WebView","ADB","topic","JSON"]:
            req(forbidden not in line,"primary_copy_jargon:"+forbidden)

req('actionButton("المتصفح")' not in CENTER,"browser_button")
req('actionButton("إدارة")' not in CENTER,"developer_manage_button")
req('actionButton("الإعدادات")' in CENTER,"settings_button")
req(
    ('تم إنشاء الملف وحفظه محليًا:' in CENTER and 'تم إنشاء الملفات وحفظها محليًا:' in CENTER)
    or 'HakimProductUx.completionMessage("pdf", created.savedAt)' in CENTER,
    "artifact_product_copy"
)

print("PRODUCT_COPY_GATE=PASS provider_jargon=false developer_controls=false")
