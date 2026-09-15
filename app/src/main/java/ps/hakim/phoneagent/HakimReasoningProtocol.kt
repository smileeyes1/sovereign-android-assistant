package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** بروتوكول محدود بين محرك الاستدلال ومنفذ حكيم؛ لا يقبل كودًا أو أوامر حرة. */
object HakimReasoningProtocol {
    data class Request(val prompt: String, val begin: String, val end: String)
    data class Action(val type: String, val args: JSONObject)
    data class Plan(
        val done: Boolean,
        val message: String,
        val actions: List<Action>,
        val protocolVersion: Int = 1,
        val confidence: Int = 0,
        val alternativesConsidered: Int = 0,
        val assumptions: List<String> = emptyList(),
        val unknowns: List<String> = emptyList(),
        val evidenceNeeded: List<String> = emptyList(),
        val successCriteria: List<String> = emptyList(),
        val verification: List<String> = emptyList(),
        val risk: String = "unknown",
        val rollback: String = ""
    )

    private val allowedTypes = setOf("open_url", "click_text", "set_text", "fill_profile", "back", "wait")
    private val allowedProfileFields = setOf(
        "full_name", "first_name", "last_name", "email", "phone", "address", "city", "country", "job_title", "organization"
    )
    private val allowedRisk = setOf("low", "moderate", "high", "unknown")

    fun wrap(basePrompt: String): Request {
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val begin = "HAKIM_${token}_BEGIN"
        val end = "HAKIM_${token}_END"
        val protocol = buildString {
            appendLine()
            appendLine("[بروتوكول التنفيذ المحلي لحكيم — v2]")
            appendLine("إذا كانت المهمة تحتاج فعلًا على الهاتف/المتصفح، أضف في نهاية إجابتك خطة JSON محدودة وسجل قرار مهني موجز. لا تكشف سلسلة التفكير الداخلية.")
            appendLine("ابدأ الخطة حرفيًا بالسلسلة: $begin")
            appendLine("وانهِها حرفيًا بالسلسلة: $end")
            appendLine("بين السلسلتين ضع كائن JSON واحدًا فقط بالمفاتيح التالية:")
            appendLine("protocol_version=2، done(boolean)، message(string)، confidence(0..100)، alternatives_considered(0..4)، assumptions(array)، unknowns(array)، evidence_needed(array)، success_criteria(array)، verification(array)، risk(low|moderate|high)، rollback(string)، actions(array).")
            appendLine("سجل القرار موجز وقابل للمراجعة: لا reasoning مخفي ولا chain-of-thought؛ فقط حقائق/افتراضات/مجهولات ومعايير تحقق وتراجع.")
            appendLine("إذا كانت actions غير فارغة: افحص بديلين متمايزين على الأقل عندما يوجد اختيار حقيقي، وحدد معيار نجاح واحدًا على الأقل، وخطوة تحقق واحدة على الأقل، وخطة تراجع/تعافٍ واضحة.")
            appendLine("لا تجعل confidence أعلى من قوة الدليل. إذا كانت هناك معلومة حاسمة غير متحققة ضعها في unknowns أو evidence_needed بدل التخمين.")
            appendLine("أنواع actions المسموحة فقط: open_url{url}، click_text{text}، set_text{target,value}، fill_profile{target,field_id}، back{}، wait{ms}.")
            appendLine("عند الحاجة لبيانات المستخدم استخدم fill_profile ولا تخمّن القيمة ولا تطلب كشفها. field_id المسموحة: ${allowedProfileFields.joinToString(",")}.")
            appendLine("لا تضع كلمة مرور/OTP/PIN/CVV/بطاقة/مفتاح سري في أي جزء من الخطة، ولا تقترح دفعًا أو حذفًا نهائيًا أو إرسالًا حساسًا كفعل تلقائي؛ حكيم يحكم ذلك محليًا.")
            appendLine("إذا كانت actions غير فارغة فلا تعتبر الجولة مكتملة؛ نفّذ الأفعال أولًا ثم تحقق في جولة لاحقة. done=true صالح فقط عندما actions=[] ولا يلزم فعل إضافي.")
            appendLine("إذا لم يلزم أي فعل، اجعل done=true وactions=[]، وضع خلاصة قصيرة ومضبوطة في message.")
        }
        return Request((basePrompt + protocol).take(28000), begin, end)
    }

    fun parse(visibleText: String, request: Request): Plan? {
        val start = visibleText.lastIndexOf(request.begin)
        if (start < 0) return null
        val jsonStart = start + request.begin.length
        val end = visibleText.indexOf(request.end, jsonStart)
        if (end <= jsonStart) return null
        val raw = visibleText.substring(jsonStart, end).trim()
        if (raw.length > 24000 || containsSecret(raw)) return null
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val arr = obj.optJSONArray("actions") ?: JSONArray()
        if (arr.length() > 8) return null
        val actions = mutableListOf<Action>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: return null
            val type = a.optString("type").trim()
            if (type !in allowedTypes) return null
            val args = a.optJSONObject("args") ?: JSONObject().apply {
                for (key in listOf("url", "text", "target", "value", "field_id", "ms")) {
                    if (a.has(key)) put(key, a.get(key))
                }
            }
            if (containsSecret(args.toString())) return null
            if (type == "fill_profile" && args.optString("field_id") !in allowedProfileFields) return null
            actions += Action(type, args)
        }

        val protocolVersion = obj.optInt("protocol_version", 1)
        if (protocolVersion !in 1..2) return null
        val confidence = obj.optInt("confidence", 0).coerceIn(0, 100)
        val alternatives = obj.optInt("alternatives_considered", 0).coerceIn(0, 4)
        val assumptions = readStringArray(obj, "assumptions") ?: return null
        val unknowns = readStringArray(obj, "unknowns") ?: return null
        val evidenceNeeded = readStringArray(obj, "evidence_needed") ?: return null
        val successCriteria = readStringArray(obj, "success_criteria") ?: return null
        val verification = readStringArray(obj, "verification") ?: return null
        val risk = obj.optString("risk", "unknown").trim().lowercase().ifBlank { "unknown" }
        if (risk !in allowedRisk) return null
        val rollback = obj.optString("rollback", "").trim().take(900)

        val requestedDone = obj.optBoolean("done", actions.isEmpty())
        val verifiedDone = requestedDone && actions.isEmpty()
        return Plan(
            done = verifiedDone,
            message = obj.optString("message").take(1600),
            actions = actions,
            protocolVersion = protocolVersion,
            confidence = confidence,
            alternativesConsidered = alternatives,
            assumptions = assumptions,
            unknowns = unknowns,
            evidenceNeeded = evidenceNeeded,
            successCriteria = successCriteria,
            verification = verification,
            risk = risk,
            rollback = rollback
        )
    }

    private fun readStringArray(obj: JSONObject, key: String, maxItems: Int = 6, maxChars: Int = 320): List<String>? {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        if (arr.length() > maxItems) return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optString(i, "").trim()
            if (v.length > maxChars || containsSecret(v)) return null
            if (v.isNotBlank()) out += v
        }
        return out
    }

    private fun containsSecret(v: String): Boolean = Regex(
        "(?i)(password|passcode|otp|pin|cvv|cvc|card.?number|security.?code|api.?key|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة|مفتاح.?سري)\\s*[:=]"
    ).containsMatchIn(v)
}
