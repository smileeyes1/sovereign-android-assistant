package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri

/**
 * استنتاج المقصد من أقل إشارة ممكنة مع تقليل الأسئلة على المستخدم.
 * يعتمد على آخر غاية موثوقة + الشاشة الحالية بعد تنقيح الحقول الحساسة + آخر مسار ويب.
 * يدعم الصمت/الرمز/الحرف/الكلمة القصيرة، لكن الإشارة الدقيقة لا تمنح سلطة عالية الأثر.
 * لا يحفظ محتوى الشاشة على القرص ولا الأسرار، ولا يعتبر واجهات حكيم الإدارية دليلًا على حالة مهمة الويب.
 */
object HakimIntentContext {
    data class Inference(
        val cue: String,
        val resolvedRequest: String,
        val confidence: String,
        val source: String,
        val canAutoContinue: Boolean,
        val cueKind: String = "EXPLICIT",
        val fastPathEligible: Boolean = false,
        val screenContext: String = "",
        val lastUrlContext: String = ""
    )

    private const val PREFS = "hakim_intent_context"
    private const val MICRO_CACHE_MS = 300L
    @Volatile private var cachedAt = 0L
    @Volatile private var cachedRaw = ""
    @Volatile private var cachedPackage = ""
    @Volatile private var cachedInference: Inference? = null

    fun infer(context: Context, raw: String): Inference {
        val now = System.currentTimeMillis()
        val cached = cachedInference
        if (cached != null && raw == cachedRaw && context.packageName == cachedPackage && now - cachedAt in 0..MICRO_CACHE_MS) {
            return cached
        }

        // هذه الدالة تستقبل إشارة المستخدم من واجهة المحادثة قبل التنفيذ. إذا كان المستخدم
        // قد ألغى المهمة ثم قال صراحة «أكمل/تابع/استأنف»، فهذا طلب جديد يرفع CANCELLED إلى RECOVER.
        // المبادرة الخلفية لا تصل إلى المهمة الملغاة أصلًا، لذلك لا يتحول هذا إلى استئناف تلقائي.
        if (isExplicitContinueCue(raw) && HakimMissionLedger.isCancelled(context)) {
            HakimMissionLedger.resumeCancelledByUser(context, "استأنف المستخدم المهمة صراحة بإشارة استمرار")
        }

        val signal = HakimMicroCueEngine.classify(raw)
        val cue = signal.normalizedCue.trim()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastGoal = prefs.getString("last_resolved_goal", "").orEmpty().trim()
        val lastUrl = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getString("last_url", "").orEmpty().trim()
        // المسار الخام يبقى محليًا لاستعادة WebView فقط. لا يُعرض ولا يُرسل لمحرك الاستدلال.
        val lastRoute = safeRouteLabel(lastUrl)
        // لقطة واحدة لكل استنتاج لتقليل العمل والتناقض بين قراءتين متتاليتين.
        val screen = screenSummary()
        val minimal = signal.kind != HakimMicroCueEngine.Kind.EXPLICIT

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
                source = "last_goal+screen+micro_cue"
            }
            lastGoal.isNotBlank() && lastUrl.isNotBlank() -> {
                resolved = "$lastGoal\nاستأنف من $lastRoute. إشارة المستخدم: ${cue.ifBlank { "أكمل" }}. لا تكرر المنجز."
                confidence = "HIGH"
                source = "last_goal+last_url+micro_cue"
            }
            lastGoal.isNotBlank() -> {
                resolved = "$lastGoal\nإشارة المستخدم الآن: ${cue.ifBlank { "أكمل" }}. أكمل من آخر حالة معروفة دون إعادة الخطوات المنجزة."
                confidence = "MEDIUM"
                source = "last_goal+micro_cue"
            }
            screen.isNotBlank() -> {
                resolved = "استنتج المقصد الأكثر ترجيحًا من الشاشة الحالية وإشارة المستخدم «${cue.ifBlank { "أكمل" }}»، ثم نفّذ فقط الخطوات الآمنة القابلة للتراجع حتى يتضح المقصد أو يتحقق."
                confidence = "MEDIUM"
                source = "screen+micro_cue"
            }
            lastUrl.isNotBlank() -> {
                resolved = "استأنف العمل على $lastRoute وفق إشارة المستخدم «${cue.ifBlank { "أكمل" }}»، واستنتج الخطوة التالية الآمنة."
                confidence = "MEDIUM"
                source = "last_url+micro_cue"
            }
            else -> {
                resolved = "الإشارة «${cue.ifBlank { "…" }}» دقيقة ولا يوجد سياق موثوق كافٍ. لا تختلق غاية ولا تنفذ فعلًا عالي الأثر؛ استخدمها فقط كطلب استمرار إذا ظهر سياق موثوق لاحقًا."
                confidence = "LOW"
                source = "micro_cue_without_context"
            }
        }

        prefs.edit()
            .putString("last_cue", cue.take(300))
            .putString("last_cue_kind", signal.kind.name)
            .putString("last_inference_source", source)
            .putString("last_confidence", confidence)
            .putLong("last_inferred_at", now)
            .apply()

        val contextual = lastGoal.isNotBlank() || screen.isNotBlank() || lastUrl.isNotBlank()
        val result = Inference(
            cue = cue,
            resolvedRequest = resolved,
            confidence = confidence,
            source = source,
            canAutoContinue = !minimal || (contextual && confidence != "LOW"),
            cueKind = signal.kind.name,
            fastPathEligible = signal.mayFastContinue && contextual && confidence != "LOW",
            screenContext = screen,
            lastUrlContext = if (lastUrl.isBlank()) "" else lastRoute
        )
        cachedRaw = raw
        cachedPackage = context.packageName
        cachedAt = now
        cachedInference = result
        return result
    }

    fun promptContext(context: Context, raw: String): String {
        val inference = infer(context, raw)
        return buildString {
            append(HakimHumanFirstPolicy.promptContext())
            appendLine("[فهم المقصد بأقل إشارة]")
            appendLine("نوع الإشارة: ${inference.cueKind} • درجة الاستنتاج: ${inference.confidence} • المصدر: ${inference.source}")
            appendLine("المسار السريع المحلي=${inference.fastPathEligible}")
            appendLine("المقصد المستعاد/المستنتج: ${inference.resolvedRequest}")
            if (inference.lastUrlContext.isNotBlank()) appendLine("المسار الحالي/الأخير: ${inference.lastUrlContext}")
            if (inference.screenContext.isNotBlank()) {
                appendLine("ملخص الشاشة الحالية المنقّح:")
                appendLine(inference.screenContext)
            }
            appendLine("افتراض إنساني: لا تتطلب من المستخدم معرفة تقنية أو مصطلحات يمكن لحكيم استنتاجها أو تنفيذها بنفسه؛ اشرح ببساطة عند الحاجة وارفع العمق فقط إذا طلبه المستخدم أو أثبت خبرة.")
            appendLine("الإشارة قد تكون صمتًا أو رمزًا أو حرفًا أو كلمة قصيرة. لا تمنح الإشارة الدقيقة وحدها موافقة على ضرر/كلفة/كشف بيانات/صلاحية/فعل غير قابل للتراجع.")
            appendLine("افترض حسن المقصد لا السذاجة المطلقة: لا تطلب إعادة شرح ما يمكن استنتاجه بثقة من السياق، ولا تفسر الطيبة أو السكوت أو «كمل» كموافقة على فعل عالي الأثر.")
            appendLine("قاعدة: عند غموض منخفض الأثر اختر أفضل افتراض قابل للتراجع ونفّذ ثم تحقق. اسأل فقط إذا كان الغموض جوهريًا ويغيّر النتيجة أو يسبق فعلًا عالي الأثر.")
            appendLine("خصوصية المسار: رابط الاستعادة الخام ومعرفات الجلسة/المحادثة تبقى محلية داخل حكيم؛ لا تُعرض للمستخدم ولا تُمرر لمزود الاستدلال، ويُستخدم وصف المضيف فقط عند الحاجة.")
        }.take(9800)
    }

    fun isMinimalCue(raw: String): Boolean = HakimMicroCueEngine.classify(raw).kind != HakimMicroCueEngine.Kind.EXPLICIT

    fun isKnownMinimalCue(raw: String): Boolean {
        val s = raw.trim().lowercase()
        if (s.isBlank()) return true
        val cues = setOf(
            "كمل", "كمّل", "اكمل", "أكمل", "تابع", "نفذ", "نفّذ", "اعملها", "سويها", "دبرها", "دبّرها",
            "هاي", "هذي", "هذا", "هون", "هنا", "هيك", "تمام", "يلا", "هيا", "هَيّا", "خلصها", "رتبها", "اضبطها",
            "نعم", "لا", "اوكي", "أوكي", "ok", "go", "next"
        )
        if (s in cues) return true
        return s.split(Regex("\\s+")).size <= 2 && cues.any { s.contains(it) }
    }

    fun isExplicitContinueCue(raw: String): Boolean {
        val s = raw.trim().lowercase()
        if (s.isBlank()) return true
        return s in setOf("كمل", "كمّل", "اكمل", "أكمل", "تابع", "استأنف", "استمر", "continue", "resume", "go on")
    }

    private fun safeRouteLabel(raw: String): String {
        if (raw.isBlank()) return "مسار ويب محفوظ"
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val host = uri?.host.orEmpty().lowercase().trim().take(160)
        return if (host.isBlank()) "مسار ويب محفوظ" else "مسار ويب محفوظ على $host"
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
