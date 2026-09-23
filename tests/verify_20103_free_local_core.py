from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
MANAGER = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimLocalModelManager.kt").read_text(encoding="utf-8")
LOCAL = (ROOT / "app/src/main/java/ps/hakim/phoneagent/LiteRtLocalEngine.kt").read_text(encoding="utf-8")
REGISTRY = (ROOT / "app/src/main/java/ps/hakim/phoneagent/HakimEngineRegistry.kt").read_text(encoding="utf-8")
HOME = (ROOT / "app/src/main/java/ps/hakim/phoneagent/UnifiedHomeActivity.kt").read_text(encoding="utf-8")
VALUES = (ROOT / "governance/QURANIC_VALUES_GOVERNANCE.md").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("FREE_LOCAL_20103=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) == 20103, "version")
req("versionName '2.1.03-free-local-core'" in BUILD, "version_name")
req("com.google.ai.edge.litertlm:litertlm-android:0.17.1" in BUILD, "litertlm_dependency")

for needed in [
    "setAllowedOverMetered(false)",
    "setAllowedOverRoaming(false)",
    "MODEL_SHA256",
    "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    "gemma-4-E2B-it.litertlm",
]:
    req(needed in MANAGER, "manager:" + needed)

for needed in [
    "GENERAL_CHAT",
    "EngineConfig",
    "Backend.CPU()",
    "network_required=false",
    "api_key_required=false",
]:
    req(needed in LOCAL, "local_engine:" + needed)

local_index = REGISTRY.find("LiteRtLocalEngine(context)")
gemini_index = REGISTRY.find("GeminiDirectEngine(context)")
req(local_index >= 0, "local_not_registered")
req(gemini_index < 0 or local_index < gemini_index, "local_not_prioritized")
req("تنزيل الذكاء المحلي المجاني" in HOME, "local_setup_ui_missing")

for forbidden_claim in [
    "يزيد دقة نموذج",
    "مفاتيح أو أوزان أو أرقام سرية",
    "أثر غيبي مزعوم",
]:
    req(forbidden_claim in VALUES, "religious_boundary:" + forbidden_claim)

req("التبيّن" in VALUES and "العدل" in VALUES and "الأمانة" in VALUES, "quranic_values_missing")

print("FREE_LOCAL_20103=PASS zero_api=true local_first=true quranic_values=governance_only")
