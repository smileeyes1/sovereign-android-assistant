from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(ok: bool, msg: str) -> None:
    if not ok:
        raise SystemExit(msg)


build = text("app/build.gradle")
bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimLocalReasoningBridge.kt")
registry = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningProviderRegistry.kt")
mesh = text("app/src/main/java/ps/hakim/phoneagent/HakimCapabilityMesh.kt")
app = text("app/src/main/java/ps/hakim/phoneagent/HakimApp.kt")
independence = text("app/src/main/java/ps/hakim/phoneagent/HakimSovereignIndependence.kt")
selfcheck = text("app/src/main/java/ps/hakim/phoneagent/HakimSelfCheck.kt")
reasoning_bridge = text("app/src/main/java/ps/hakim/phoneagent/HakimReasoningBridge.kt")
netsec = text("app/src/main/res/xml/network_security_config.xml")
policy = json.loads(text("governance/HAKIM_FIELD_SIGNING_IDENTITY.json"))

m = re.search(r"versionCode\s+(\d+)", build)
require(m and int(m.group(1)) >= 20045, "P0: مرشح الاستدلال المحلي يجب أن يكون ٢٠٠٤٥ أو أحدث")
require(policy["current_candidate_version"] == int(m.group(1)), "P0: سياسة التوقيع لا تطابق مرشح ٢٠٠٤٥")
field = int(policy["current_field_version"])
require(field >= 20049, "P0: سياسة الميدان أقدم من أحدث دليل مباشر مثبت")
require(policy.get("field_evidence", {}).get("version_code") == field,
        "P0: دليل الميدان لا يطابق current_field_version")
require(policy.get("field_evidence", {}).get("signer_sha256") == policy["certificate_sha256"],
        "P0: دليل الميدان لا يطابق موقّع D1 الحاكم")
require(int(m.group(1)) > field, "P0: المرشح يجب أن يزيد versionCode عن خط الميدان")

# جسر النموذج المحلي يجب أن يكون loopback فقط ولا يملك مسار API خارجي.
for token in [
    '"127.0.0.1"',
    '"localhost"',
    '"::1"',
    '"NON_LOOPBACK_ENDPOINT_REJECTED"',
    '.put("external_network_forbidden", true)',
    '.put("api_key_required", false)',
    '.put("authorization_header_used", false)',
    '.put("model_output_probabilistic", true)',
    '.put("deterministic_governance_outside_model", true)',
]:
    require(token in bridge, f"P0: قيد سيادة الاستدلال المحلي مفقود: {token}")

require('header("Authorization"' not in bridge, "P0: الجسر المحلي لا يجوز أن يستخدم Authorization أو مفتاح API")
require('https://api.' not in bridge and 'openai.com' not in bridge and 'generativelanguage.googleapis.com' not in bridge,
        "P0: جسر الاستدلال المحلي يحتوي مسار مزود خارجي")
require('"temperature", 0' in bridge, "P0: الاستدلال المحلي لا يقلل التباين بـ temperature=0")
require('scheme == "http" || scheme == "https"' in bridge, "P0: فحص مخطط عنوان loopback مفقود")
require('"/v1/models"' in bridge and 'LOCAL_MODEL_NOT_READY' in bridge,
        "P0: الاكتشاف المحلي قد يعلن الجاهزية دون إثبات نموذج فعلي")
require('host == "127.0.0.1" || host == "localhost" || host == "::1"' in bridge,
        "P0: قيد مضيف loopback غير صريح")

# الشبكة العامة تمنع cleartext، والاستثناء الوحيد الواضح هو loopback.
require('<base-config cleartextTrafficPermitted="false"' in netsec, "P0: cleartext الخارجي غير مغلق افتراضيًا")
require('>localhost<' in netsec and '>127.0.0.1<' in netsec, "P0: loopback المحلي غير مصرح له في إعداد الشبكة")

# النموذج المحلي يجب أن يكون مزودًا أصيلًا قبل السحابة، لا ملفًا معزولًا.
require('LOCAL_ADVANCED = "hakim_local_advanced"' in registry, "P0: معرف المزود المحلي المتقدم مفقود")
require('Class.LOCAL_ADVANCED' in registry, "P0: فئة الاستدلال المحلي المتقدم مفقودة")
require('HakimLocalReasoningBridge.readyNow(context)' in registry, "P0: الموجّه لا يقرأ جاهزية النموذج المحلي")
require('AUTO -> localReady || hasNetwork(context)' in registry, "P0: AUTO لا يفضّل الجاهزية المحلية قبل الاعتماد الخارجي")
require('preferred == LOCAL_ONLY || preferred == LOCAL_ADVANCED' in registry,
        "P0: اختيار المحلي الصريح ما زال يسمح بمزودات ويب تلقائيًا")

require('Node("local_advanced_reasoning"' in mesh, "P0: الاستدلال المحلي غير موجود في شبكة القدرات")
require('requiresNetwork' in mesh, "P0: شبكة القدرات لا تمثل اعتماد الشبكة")
require('HakimLocalReasoningBridge.probe(app)' in app, "P0: لا يوجد اكتشاف ذاتي للنموذج المحلي عند الصيانة")
require('externalDependency = !localReasoning.optBoolean("ready_now")' in independence,
        "P0: تقييم السيادة لا يخفض الاعتماد الخارجي عند جاهزية النموذج المحلي")
require('localReasoning.optBoolean("loopback_only")' in independence,
        "P0: تقييم السيادة لا يشترط loopback")
require('جسر الاستدلال المحلي محصور بالـ loopback' in selfcheck,
        "P0: الفحص الذاتي لا يحرس سيادة عنوان النموذج المحلي")
require('الحتمية للحاكمية لا لخرج النموذج الاحتمالي' in selfcheck,
        "P0: الفحص الذاتي يخلط حتمية السياسة باحتمالية النموذج")
require('HakimLocalReasoningBridge.complete' in reasoning_bridge,
        "P0: النموذج المحلي مسجل لكنه لا يحمل طلب الاستدلال الفعلي")
require('HakimReasoningProtocol.wrap(governedPrompt)' in reasoning_bridge and
        'HakimReasoningProtocol.parse(local.text, request)' in reasoning_bridge,
        "P0: خرج النموذج المحلي يتجاوز بروتوكول حكيم المقيد")
require('HakimDeliberationQuality.audit(it, goalForAudit)' in reasoning_bridge,
        "P0: الخطة المحلية لا تمر بالتدقيق المهني قبل التنفيذ")
require('Thread {' in reasoning_bridge and 'Handler(Looper.getMainLooper())' in reasoning_bridge,
        "P0: استدعاء النموذج المحلي قد يحجب واجهة الهاتف")
require(reasoning_bridge.index('HakimLocalReasoningBridge.complete') < reasoning_bridge.index('private fun askWeb'),
        "P0: المسار الخارجي يسبق محاولة الاستدلال المحلي")
require('preference == HakimReasoningProviderRegistry.LOCAL_ADVANCED' in reasoning_bridge and
        'لم تُرسل المهمة إلى السحابة' in reasoning_bridge and
        'لم يحدث fallback خارجي' in reasoning_bridge,
        "P0: وضع «محلي متقدم فقط» قد يسقط إلى مزود خارجي")

print("HAKIM_LOCAL_ADVANCED_REASONING_SOVEREIGNTY=PASS")
print("HAKIM_LOOPBACK_ONLY_MODEL_GATE=PASS")
print("HAKIM_DETERMINISTIC_GOVERNANCE_PROBABILISTIC_MODEL_BOUNDARY=PASS")
