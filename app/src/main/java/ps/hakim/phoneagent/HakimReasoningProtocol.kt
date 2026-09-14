package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** بروتوكول محدود بين محرك الاستدلال ومنفذ حكيم؛ لا يقبل كودًا أو أوامر حرة. */
object HakimReasoningProtocol {
    data class Request(val prompt: String, val begin: String, val end: String)
    data class Action(val type: String, val args: JSONObject)
    data class Plan(val done: Boolean, val message: String, val actions: List<Action>)

    private val allowedTypes = setOf("open_url", "click_text", "set_text", "fill_profile", "back", "wait")
    private val allowedProfileFields = setOf(
        "full_name", "first_name", "last_name", "email", "phone", "address", "city", "country", "job_title", "organization"
    )

    fun wrap(basePrompt: String): Request {
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val begin = "HAKIM_${token}_BEGIN"
        val end = "HAKIM_${token}_END"
        val protocol = buildString {
            appendLine()
            appendLine("[بروتوكول التنفيذ المحلي لحكيم]")
            appendLine("إذا كانت المهمة تحتاج فعلًا على الهاتف/المتصفح، أضف في نهاية إجابتك خطة JSON محدودة.")
            appendLine("ابدأ الخطة حرفيًا بالسلسلة: $begin")
            appendLine("وانهِها حرفيًا بالسلسلة: $end")
            appendLine("بين السلسلتين ضع كائن JSON واحدًا فقط بالمفاتيح: done(boolean), message(string), actions(array).")
            appendLine("أنواع actions المسموحة فقط: open_url{url}، click_text{text}، set_text{target,value}، fill_profile{target,field_id}، back{}، wait{ms}.")
            appendLine("عند الحاجة لبيانات المستخدم استخدم fill_profile ولا تخمّن القيمة ولا تطلب كشفها. field_id المسموحة: ${allowedProfileFields.joinToString(",")}.")
            appendLine("لا تضع كلمة مرور/OTP/PIN/CVV/بطاقة/مفتاح سري في الخطة، ولا تقترح دفعًا أو حذفًا نهائيًا أو إرسالًا حساسًا كفعل تلقائي؛ حكيم يحكم ذلك محليًا.")
            appendLine("إذا لم يلزم أي فعل، اجعل done=true وactions=[]، ويمكنك وضع خلاصة قصيرة في message.")
        }
        return Request((basePrompt + protocol).take(24000), begin, end)
    }

    fun parse(visibleText: String, request: Request): Plan? {
        val start = visibleText.lastIndexOf(request.begin)
        if (start < 0) return null
        val jsonStart = start + request.begin.length
        val end = visibleText.indexOf(request.end, jsonStart)
        if (end <= jsonStart) return null
        val raw = visibleText.substring(jsonStart, end).trim()
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
        return Plan(
            done = obj.optBoolean("done", actions.isEmpty()),
            message = obj.optString("message").take(1200),
            actions = actions
        )
    }

    private fun containsSecret(v: String): Boolean = Regex(
        "(?i)(password|passcode|otp|pin|cvv|cvc|card.?number|security.?code|api.?key|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة|مفتاح.?سري)\\s*[:=]"
    ).containsMatchIn(v)
}
