package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/** تنفيذ محلي محدود للأوامر الطبيعية الواضحة؛ المهام المركبة تعود لمنسق الوكلاء. */
object HakimNaturalActionEngine {
    data class Result(val handled: Boolean, val success: Boolean, val message: String)

    fun execute(activity: Activity, raw: String): Result {
        val text = raw.trim()
        val s = text.lowercase()
        if (text.isBlank()) return Result(true, false, "اكتب ما تريد فعله.")

        if (s == "ارجع" || s.contains("ارجع للخلف") || s.contains("الصفحة السابقة")) {
            val ok = HakimAccessibilityService.instance?.action(JSONObject().put("action", "back")) ?: false
            return Result(true, ok, if (ok) "تم الرجوع." else "تعذر الرجوع من الواجهة الحالية.")
        }

        parseClick(text)?.let { target ->
            val ok = HakimAccessibilityService.instance?.action(
                JSONObject().put("action", "click_text").put("text", target)
            ) ?: false
            return Result(true, ok, if (ok) "تم الضغط على «$target»." else "لم أجد زرًا آمنًا مطابقًا لـ «$target».")
        }

        parseSetText(text)?.let { (field, value) ->
            if (looksSensitive(field) || looksSensitive(value)) {
                return Result(true, false, "هذا الحقل يبدو حساسًا؛ لن أمرر السر كنص. استخدم مدير اعتماد أندرويد أو أدخل السر مباشرة في الحقل الآمن.")
            }
            val ok = HakimAccessibilityService.instance?.action(
                JSONObject().put("action", "set_text").put("text", field).put("value", value)
            ) ?: false
            return Result(true, ok, if (ok) "تمت الكتابة في «$field»." else "لم أجد حقلًا آمنًا مطابقًا لـ «$field».")
        }

        parseOpen(text)?.let { query ->
            val url = toUrl(query)
            activity.getSharedPreferences("hakim", Activity.MODE_PRIVATE).edit().putString("last_url", url).apply()
            activity.startActivity(Intent(activity, MainActivity::class.java))
            return Result(true, true, "فتحت متصفح حكيم على المسار الأنسب للمطلوب.")
        }

        return Result(false, false, "المهمة تحتاج تخطيطًا من الوكيل القائد.")
    }

    private fun parseClick(text: String): String? {
        val m = Regex("^(?:اضغط|اكبس|انقر)(?:\\s+على)?\\s+(.+)$", RegexOption.IGNORE_CASE).find(text.trim()) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private fun parseSetText(text: String): Pair<String, String>? {
        val patterns = listOf(
            Regex("^(?:اكتب|ادخل|أدخل)\\s+(.+?)\\s+(?:في|داخل)\\s+(?:حقل\\s+)?(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:عبئ|عبّئ|املأ)\\s+(?:حقل\\s+)?(.+?)\\s+(?:ب|بـ)\\s*(.+)$", RegexOption.IGNORE_CASE)
        )
        for ((i, r) in patterns.withIndex()) {
            val m = r.find(text.trim()) ?: continue
            return if (i == 0) m.groupValues[2].trim() to m.groupValues[1].trim()
            else m.groupValues[1].trim() to m.groupValues[2].trim()
        }
        return null
    }

    private fun parseOpen(text: String): String? {
        val m = Regex("^(?:افتح|اذهب إلى|اذهب الى|ابحث عن|ابحث)\\s+(.+)$", RegexOption.IGNORE_CASE).find(text.trim()) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private fun toUrl(q: String): String = when {
        q.startsWith("https://") || q.startsWith("http://") -> q
        q.contains(".") && !q.contains(" ") -> "https://$q"
        q.contains("شات جي بي تي") || q.equals("chatgpt", true) -> "https://chatgpt.com/"
        q.contains("جيميني") || q.equals("gemini", true) -> "https://gemini.google.com/"
        else -> "https://www.google.com/search?q=" + Uri.encode(q)
    }

    private fun looksSensitive(v: String): Boolean = Regex(
        "(?i)(password|passcode|otp|pin|cvv|cvc|card|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة)"
    ).containsMatchIn(v)
}
