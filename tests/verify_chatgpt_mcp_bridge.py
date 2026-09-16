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
build = text("app/build.gradle")
mission = text("governance/HAKIM_20041_MISSION_STATE.json")
local_update = text("scripts/termux-hakim-field-update.sh")

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
require("quranic_invariant_kernel" in relay_android and "quran_sunnah_method" in relay_android,
        "P0: الهاتف لا يُظهر إقرار الحاكمية الإسلامية للبوابة")
for token in ["hakim_governance", "signed_phone_status", "religious_claim_requires_exact_source",
              "public_religious_publication_requires_human_approval", "coercion_or_mass_unsolicited_publication_allowed"]:
    require(token in server, f"P0: أداة الحاكمية تفتقد {token}")
require('"mcpServers": "./.mcp.json"' in plugin and "browser_observe" in skill and "hakim_governance" in skill,
        "P0: حزمة الإضافة لا تورث عقد الاستخدام")
for token in ["مصدر الحديث ودرجته", "لا تُصدر فتوى", "النشر العلني", "الدليل العلمي المتخصص"]:
    require(token in skill, f"P0: مهارة حكيم تفتقد حد الحاكمية: {token}")
require("npm test" in workflow and "verify_chatgpt_mcp_bridge.py" in workflow and "assembleDebug assembleRelease" in workflow,
        "P0: CI لا يختبر البوابة والتطبيق معًا")
require("hakim-20041-local-signing-kit-PRE-FIELD" in workflow and "bash -n scripts/termux-hakim-field-update.sh" in workflow,
        "P0: عدة التوقيع المحلي الآمن غير محفوظة في CI")
require("HAKIM_RELAY_KEY:" not in gateway and "HAKIM_MCP_BEARER:" not in gateway,
        "P0: عُثر على قيمة سر ثابتة")
require("versionCode 20041" in build and "2.0.41-chatgpt-islamic-browser-bridge" in build,
        "P0: هوية إصدار ٢٠٠٤١ غير متسقة")
for token in ["PRE_FIELD", "b58035d9595da3170503c08a78c225f9e741d5a0", "LOCAL_DEVICE_ONLY", "NO_UNINSTALL"]:
    require(token in mission, f"P0: سجل المهمة يفتقد {token}")
for token in ["EXPECTED_D1", "apksigner.jar", "install -r", "HAKIM_FIELD_UPDATE=PASS",
              "HAKIM-CANDIDATE.properties", "candidate_sha256", "installed_signer_sha256"]:
    require(token in local_update, f"P0: مسار التحديث المحلي يفتقد {token}")
require("adb uninstall" not in local_update and "pm clear" not in local_update,
        "P0: مسار التحديث المحلي يتضمن حذف التطبيق أو بياناته")

print("HAKIM_CHATGPT_MCP_PHONE_CONTRACT=PASS")
print("HAKIM_MCP_AUTH_AND_CRYPTO=PASS")
print("HAKIM_PLUGIN_PACKAGE=PASS")
print("HAKIM_FIELD_STATUS=PRE_FIELD")
