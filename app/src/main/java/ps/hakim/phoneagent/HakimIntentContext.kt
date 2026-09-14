package ps.hakim.phoneagent

import android.content.Context

/**
 * استنتاج المقصد من أقل إشارة ممكنة مع تقليل الأسئلة على المستخدم.
 * يعتمد على آخر غاية موثوقة + الشاشة الحالية بعد تنقيح الحقول الحساسة + آخر مسار ويب.
 * لا يحفظ محتوى الشاشة ولا الأسرار، ولا يعتبر واجهات حكيم الإدارية دليلًا على حالة مهمة الويب.
 */
object HakimIntentContext {
    data class Inference(
        val cue: String,
        val resolvedRequest: String,
        val confidence: String,
        val source: String,
        val canAutoContinue: Boolean
    )

    private const val PREFS = "hakim_intent_context"

    fun infer(context: Context, raw: String): Inference {
        val cue = raw.trim()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastGoal = prefs.getString("last_resolved_goal", "").orEmpty().trim()
        val lastUrl = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getString("last_url", "").orEmpty().trim()
        val screen = screenSummary()
        val minimal = isMinimalCue(cue)

        val resolved: String
        val confidence: String
        val source: String

        when {
            !minimal -> {
                resolved = cue
                confidence = "HIGH"
                source = "explicit_user_intent"
                rememberGoalIfSafe(context, cue)
            }
            lastGoal.isNotBlank() && screen.isNotBlank() -> {
                resolved = buildString {
                    append(lastGoal)
                    append("\nاستأنف المهمة من الحالة الحالية. إشارة المستخدم الآن: ")
                    append(cue.ifBlank { "أكمل" })
                    append(". لا تعِد ما تم؛ استنتج الخطوة التالية من الشاشة الحالية ونفّذ الآمن تلقائيًا.")
                }
                confidence = "HIGH"
                source = "last_goal+screen"
            }
            lastGoal.isNotBlank() && lastUrl.isNotBlank() -> {
                resolved = "$lastGoal\nاستأنف من مسار الويب الحالي $lastUrl. إشارة المستخدم: ${cue.ifBlank { "أكمل" }}. لا تكرر المنجز."
                confidence = "HIGH"
                source = "last_goal+last_url"
            }
            lastGoal.isNotBlank() -> {
                resolved = "$lastGoal\nإشارة المستخدم الآن: ${cue.ifBlank { "أكمل" }}. أكمل من آخر حالة معروفة دون إعادة الخطوات المنجزة."
                confidence = "MEDIUM"
                source = "last_goal"
            }
            screen.isNotBlank() -> {
                resolved = "استنتج المقصد الأكثر ترجيحًا من الشاشة الحالية وإشارة المستخدم «${cue.ifBlank { "أكمل" }}»، ثم نفّذ فقط الخطوات الآمنة القابلة للتراجع حتى يتضح المقصد أو يتحقق."
                confidence = "MEDIUM"
                source = "screen"
            }
            lastUrl.isNotBlank() -> {
                resolved = "استأنف العمل على المسار الحالي $lastUrl وفق إشارة المستخدم «${cue.ifBlank { "أكمل" }}»، واستنتج الخطوة التالية الآمنة."
                confidence = "MEDIUM"
                source = "last_url"
            }
            else -> {
                resolved = cue.ifBlank { "أكمل المهمة الحالية بأفضل مسار آمن ممكن، واستنتج المقصد من أي سياق متاح دون اختلاق حقائق." }
                confidence = "LOW"
                source = "minimal_only"
            }
        }

        prefs.edit()
            .putString("last_cue", cue.take(300))
            .putString("last_inference_source", source)
            .putString("last_confidence", confidence)
            .putLong("last_inferred_at", System.currentTimeMillis())
            .apply()

        return Inference(
            cue = cue,
            resolvedRequest = resolved,
            confidence = confidence,
            source = source,
            canAutoContinue = confidence != "LOW" || screen.isNotBlank() || lastUrl.isNotBlank()
        )
    }

    fun promptContext(context: Context, raw: String): String {
        val inference = infer(context, raw)
        val screen = screenSummary()
        val lastUrl = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getString("last_url", "").orEmpty().take(500)
        return buildString {
            appendLine("[فهم المقصد بأقل إشارة]")
            appendLine("درجة الاستنتاج: ${inference.confidence} • المصدر: ${inference.source}")
            appendLine("المقصد المستعاد/المستنتج: ${inference.resolvedRequest}")
            if (lastUrl.isNotBlank()) appendLine("المسار الحالي/الأخير: $lastUrl")
            if (screen.isNotBlank()) {
                appendLine("ملخص الشاشة الحالية المنقّح:")
                appendLine(screen)
            }
            appendLine("قاعدة: لا تطلب إعادة شرح ما يمكن استنتاجه بثقة من السياق. عند غموض منخفض الأثر اختر أفضل افتراض قابل للتراجع ونفّذ ثم تحقق. اسأل فقط إذا كان الغموض جوهريًا ويغيّر النتيجة أو يسبق فعلًا عالي الأثر.")
        }.take(5000)
    }

    fun isMinimalCue(raw: String): Boolean {
        val s = raw.trim().lowercase()
        if (s.isBlank()) return true
        if (s.length <= 2) return true
        val cues = setOf(
            "كمل", "كمّل", "اكمل", "أكمل", "تابع", "نفذ", "نفّذ", "اعملها", "سويها", "دبرها", "دبّرها",
            "هاي", "هذي", "هذا", "هون", "هنا", "هيك", "تمام", "يلا", "هيا", "هَيّا", "خلصها", "رتبها", "اضبطها"
        )
        if (s in cues) return true
        return s.split(Regex("\\s+")).size <= 2 && cues.any { s.contains(it) }
    }

    private fun rememberGoalIfSafe(context: Context, text: String) {
        if (text.isBlank() || containsSensitive(text)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_resolved_goal", text.take(2500))
            .putLong("last_goal_at", System.currentTimeMillis())
            .apply()
    }

    private fun screenSummary(): String {
        val snapshot = HakimAccessibilityService.instance?.uiSnapshot(80) ?: return ""
        // لا نستخدم شاشة المحادثة/الإعدادات نفسها كدليل على حالة المهمة الخارجية.
        for (i in 0 until snapshot.length()) {
            val obj = snapshot.optJSONObject(i) ?: continue
            val t = (obj.optString("text") + " " + obj.optString("desc")).trim()
            if (t.contains("حكيم — محادثة الوكلاء") || t.contains("النظام الحاكم والبيانات — حكيم")) return ""
        }
        val lines = linkedSetOf<String>()
        for (i in 0 until snapshot.length()) {
            val obj = snapshot.optJSONObject(i) ?: continue
            if (obj.optBoolean("sensitive", false)) continue
            val text = obj.optString("text").trim()
            val desc = obj.optString("desc").trim()
            val label = when {
                text.isNotBlank() && text != "[مخفي]" -> text
                desc.isNotBlank() && desc != "[مخفي]" -> desc
                else -> ""
            }.replace(Regex("\\s+"), " ").take(140)
            if (label.isBlank()) continue
            val role = when {
                obj.optBoolean("editable", false) -> "حقل"
                obj.optBoolean("clickable", false) -> "زر/عنصر"
                else -> "نص"
            }
            lines += "$role: $label"
            if (lines.size >= 18) break
        }
        return lines.joinToString("\n").take(2600)
    }

    private fun containsSensitive(text: String): Boolean = Regex(
        "(?i)(password|passcode|otp|pin|cvv|cvc|card\\s*number|كلمة\\s*المرور|رمز\\s*التحقق|رمز\\s*الأمان|رقم\\s*البطاقة)"
    ).containsMatchIn(text)
}
