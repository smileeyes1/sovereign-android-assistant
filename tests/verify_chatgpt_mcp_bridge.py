from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(ok: bool, message: str) -> None:
    if not ok:
        raise SystemExit(message)

relay_android = text("app/src/main/java/ps/hakim/phoneagent/HakimUnifiedRelay.kt")
runtime = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignBrowserRuntime.kt")
legacy = text("app/src/main/java/ps/hakim/phoneagent/HakimService.kt")
gateway = text("mcp-server/src/relay.mjs")
server = text("mcp-server/src/server.mjs")
plugin = text("plugins/hakim-browser/.codex-plugin/plugin.json")
skill = text("plugins/hakim-browser/skills/hakim-browser-control/SKILL.md")
workflow = text(".github/workflows/chatgpt-mcp-bridge.yml")

require('setOf("status", "ui", "notifications", "screenshot", "browser_observe")' in relay_android,
        "P0: browser_observe ليس عملية قراءة محددة")
require('setOf("action", "launch", "browser_action")' in relay_android,
        "P0: browser_action ليس خلف مسار الموافقة")
for token in ["dispatchEnvelope", "remoteObserve", "remoteExecute", "remoteSensitivePath", "result_b64", "sent_at_ms", "pollSignature"]:
    require(token in relay_android or token in runtime, f"P0: عقد الهاتف يفتقد {token}")
require('if (key.isBlank())' in legacy and 'return null' in legacy,
        "P0: القناة القديمة لا تفشل مغلقة عند غياب المفتاح")
for token in ["aes-256-gcm", "createHmac", "timingSafeEqual", "verifyPhoneResult", "verifyPollRequest"]:
    require(token in gateway, f"P0: بوابة MCP تفتقد {token}")
require('Bearer realm="hakim-browser"' in server and 'readOnlyHint: true' in server and 'readOnlyHint: false' in server,
        "P0: المصادقة أو توصيف أدوات MCP غير مكتمل")
require("browser_observe" in server and "browser_action" in server and "CAPTCHA" in server and "isSafeHttpUrl" in server,
        "P0: أدوات المتصفح أو حدودها مفقودة")
require('"mcpServers": "./.mcp.json"' in plugin and "browser_observe" in skill,
        "P0: حزمة الإضافة لا تورث عقد الاستخدام")
require("npm test" in workflow and "verify_chatgpt_mcp_bridge.py" in workflow and "assembleDebug" in workflow,
        "P0: CI لا يختبر البوابة والتطبيق معًا")
require("HAKIM_RELAY_KEY:" not in gateway and "HAKIM_MCP_BEARER:" not in gateway,
        "P0: عُثر على قيمة سر ثابتة")

print("HAKIM_CHATGPT_MCP_PHONE_CONTRACT=PASS")
print("HAKIM_MCP_AUTH_AND_CRYPTO=PASS")
print("HAKIM_PLUGIN_PACKAGE=PASS")
print("HAKIM_FIELD_STATUS=PRE_FIELD")
