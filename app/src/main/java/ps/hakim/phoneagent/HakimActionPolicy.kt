package ps.hakim.phoneagent

import org.json.JSONArray

/** قرار مركزي: ما الذي ينفذ تلقائيًا، وما الذي ينتظر موافقة، وما الذي يمنع. */
object HakimActionPolicy {
    enum class Level { AUTO, APPROVAL, BLOCK }

    data class Decision(val level: Level, val reason: String)

    fun classify(label: String, screen: JSONArray? = null): Decision {
        val text = normalize(label)
        if (sensitiveRegex.containsMatchIn(text)) {
            return Decision(Level.BLOCK, "يتعلق بسر أو اعتماد حساس")
        }
        val screenText = screen?.let { summarize(it) }.orEmpty()
        if (sensitiveRegex.containsMatchIn(screenText) && text.isBlank()) {
            return Decision(Level.BLOCK, "الشاشة تتطلب اعتمادًا حساسًا")
        }
        // وجود سياق دفع/حذف/إرسال لا يوقف التحضير وحده؛ التوقف عند الفعل النهائي نفسه.
        if (highImpactRegex.containsMatchIn(text) ||
            (highImpactRegex.containsMatchIn(screenText) && finalActionRegex.containsMatchIn(text))) {
            return Decision(Level.APPROVAL, "فعل جوهري أو غير قابل للتراجع")
        }
        return Decision(Level.AUTO, "منخفض الأثر وقابل للتراجع")
    }

    fun screenHasSensitiveInput(snapshot: JSONArray): Boolean {
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (node.optBoolean("sensitive", false)) return true
        }
        return false
    }

    fun screenHasHighImpactContext(snapshot: JSONArray): Boolean =
        highImpactRegex.containsMatchIn(summarize(snapshot))

    fun isSafeContinuation(label: String, snapshot: JSONArray): Boolean {
        val n = normalize(label)
        if (n.isBlank()) return false
        val continuation = listOf(
            "التالي", "متابعة", "استمرار", "اكمل", "أكمل", "تابع", "حسنا", "حسنًا",
            "next", "continue", "proceed", "done", "ok"
        ).any { n.contains(normalize(it)) }
        if (!continuation) return false
        return classify(label, snapshot).level == Level.AUTO
    }

    /** لا نعلن النجاح بكلمة عامة؛ نطلب عبارة حالة صريحة. */
    fun isSuccessState(snapshot: JSONArray): Boolean {
        val s = summarize(snapshot)
        return Regex(
            "(?i)(تم بنجاح|اكتمل بنجاح|تم الحفظ بنجاح|تم التسجيل بنجاح|تمت العملية بنجاح|successfully completed|completed successfully|saved successfully|submitted successfully|submission received|thank you for your submission|your request has been received)"
        ).containsMatchIn(s)
    }

    private fun summarize(snapshot: JSONArray): String = buildString {
        for (i in 0 until snapshot.length()) {
            val node = snapshot.optJSONObject(i) ?: continue
            if (node.optBoolean("sensitive", false)) continue
            append(' ')
            append(node.optString("text"))
            append(' ')
            append(node.optString("desc"))
        }
    }.take(12000).lowercase()

    private fun normalize(v: String): String = v.lowercase()
        .replace(Regex("[_\\-.:/]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val sensitiveRegex = Regex(
        "(?i)(password|passcode|otp|one.?time|pin|cvv|cvc|card.?number|security.?code|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة|مفتاح.?سري)"
    )

    private val highImpactRegex = Regex(
        "(?i)(pay|payment|purchase|buy|checkout|delete|remove account|send|submit|publish|transfer|confirm order|place order|sign contract|ادفع|دفع|شراء|اشتر|سلة|احذف|حذف الحساب|إرسال|ارسل|أرسل|نشر|تحويل|تأكيد الطلب|تأكيد الشراء|توقيع|عقد)"
    )

    private val finalActionRegex = Regex(
        "(?i)(confirm|submit|send|pay|buy|delete|publish|transfer|وافق|تأكيد|إرسال|ارسل|أرسل|ادفع|شراء|احذف|نشر|تحويل)"
    )
}
