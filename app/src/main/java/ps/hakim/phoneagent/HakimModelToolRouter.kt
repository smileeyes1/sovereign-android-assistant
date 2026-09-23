package ps.hakim.phoneagent

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Policy-first router. It chooses a channel, not a vendor promise.
 *
 * Rules:
 * - no paid API is assumed;
 * - no account/session secret is copied between providers;
 * - current information goes to the browser/search path;
 * - attachments prefer an Android share-capable channel so URI grants stay scoped;
 * - provider web sessions are fallbacks and never treated as verified until the user account/session is actually usable;
 * - the router records observed success locally and may prefer a previously successful compatible route.
 */
object HakimModelToolRouter {

    enum class Channel {
        LOCAL_RESPONSE,
        LOCAL_BROWSER,
        PROVIDER_APP,
        SYSTEM_SHARE,
        PROVIDER_WEB
    }

    data class Provider(
        val id: String,
        val label: String,
        val webUrl: String,
        val packageName: String? = null
    )

    data class Decision(
        val channel: Channel,
        val provider: Provider?,
        val fallbacks: List<Provider>,
        val reason: String,
        val requiresUserChoice: Boolean = false
    )

    val providers = listOf(
        Provider(
            id = "chatgpt",
            label = "شات جي بي تي",
            webUrl = "https://chatgpt.com/",
            packageName = "com.openai.chatgpt"
        ),
        Provider(
            id = "gemini",
            label = "جيميني",
            webUrl = "https://gemini.google.com/app",
            packageName = "com.google.android.apps.bard"
        ),
        Provider(
            id = "claude",
            label = "كلود",
            webUrl = "https://claude.ai/new",
            packageName = "com.anthropic.claude"
        ),
        Provider(
            id = "deepseek",
            label = "ديب سيك",
            webUrl = "https://chat.deepseek.com/",
            packageName = "com.deepseek.chat"
        )
    )

    fun decide(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>
    ): Decision {
        val q = prompt.trim()

        if (attachments.isEmpty() && localReply(q) != null) {
            return Decision(
                channel = Channel.LOCAL_RESPONSE,
                provider = null,
                fallbacks = emptyList(),
                reason = "يمكن تحقيق هذا المقصد محليًا دون إرسال أي بيانات إلى مزود خارجي."
            )
        }

        if (isDirectUrl(q)) {
            return Decision(
                channel = Channel.LOCAL_BROWSER,
                provider = null,
                fallbacks = emptyList(),
                reason = "رابط مباشر؛ المتصفح هو الأداة الحاسمة الأقل كلفة."
            )
        }

        if (needsFreshWeb(q)) {
            return Decision(
                channel = Channel.LOCAL_BROWSER,
                provider = null,
                fallbacks = providers,
                reason = "المهمة تعتمد على معلومات حديثة؛ يبدأ حكيم بالويب بدل ذاكرة نموذج."
            )
        }

        val installed = providers.filter { p ->
            p.packageName?.let { isInstalled(context, it) } == true
        }

        if (attachments.isNotEmpty()) {
            val preferred = bestObservedProvider(context, installed)
            if (preferred != null) {
                return Decision(
                    channel = Channel.PROVIDER_APP,
                    provider = preferred,
                    fallbacks = providers.filterNot { it.id == preferred.id },
                    reason = "هناك مرفقات، وتوجد قناة تطبيق مثبتة تستطيع استلام URI بصلاحية قراءة محدودة."
                )
            }
            return Decision(
                channel = Channel.SYSTEM_SHARE,
                provider = null,
                fallbacks = providers,
                reason = "هناك مرفقات ولا توجد قناة مزود مثبتة موثقة؛ يستخدم حكيم مشاركة أندرويد الآمنة لتسليمها لتطبيق متوافق.",
                requiresUserChoice = true
            )
        }

        val preferredInstalled = bestObservedProvider(context, installed)
        if (preferredInstalled != null) {
            return Decision(
                channel = Channel.PROVIDER_APP,
                provider = preferredInstalled,
                fallbacks = providers.filterNot { it.id == preferredInstalled.id },
                reason = "قناة نموذج مثبتة ومتوافقة متاحة، ولا توجد حاجة لأداة أخرى."
            )
        }

        val lastWeb = bestObservedProvider(context, providers)
        if (lastWeb != null) {
            return Decision(
                channel = Channel.PROVIDER_WEB,
                provider = lastWeb,
                fallbacks = providers.filterNot { it.id == lastWeb.id },
                reason = "لا يوجد تطبيق مزود مثبت؛ يستخدم حكيم جلسة الويب الرسمية ذات النجاح المحلي الأعلى."
            )
        }

        return Decision(
            channel = Channel.PROVIDER_WEB,
            provider = providers.first(),
            fallbacks = providers.drop(1),
            reason = "لا توجد نتيجة نجاح محلية بعد؛ يبدأ حكيم بقناة رسمية ثم يتعلم من النتيجة."
        )
    }

    fun recordOutcome(context: Context, providerId: String?, success: Boolean) {
        if (providerId.isNullOrBlank()) return
        val prefs = context.getSharedPreferences("hakim_router", Context.MODE_PRIVATE)
        val key = "provider_" + providerId + "_score"
        val old = prefs.getInt(key, 0)
        val next = (old + if (success) 2 else -3).coerceIn(-20, 40)
        prefs.edit()
            .putInt(key, next)
            .putString("last_provider", providerId)
            .putLong("last_provider_at", System.currentTimeMillis())
            .apply()
    }

    fun browserTarget(raw: String): String {
        val q = raw.trim()
        return when {
            isDirectUrl(q) -> q
            q.contains(".") && !q.contains(" ") -> "https://$q"
            else -> "https://www.google.com/search?q=" + Uri.encode(q)
        }
    }

    fun governedShareIntent(
        context: Context,
        prompt: String,
        attachments: List<HakimAttachmentGateway.Attachment>,
        packageName: String?
    ): Intent {
        val externalPrompt = HakimExecutiveLoop.providerInstruction(context, prompt)
        val out = HakimAttachmentGateway.buildShareIntent(context, externalPrompt, attachments)
        if (!packageName.isNullOrBlank()) out.setPackage(packageName)
        return out
    }

    fun localReply(prompt: String): String? {
        val q = prompt.trim()
            .replace(Regex("[!؟?،,.]+$"), "")
            .trim()
            .lowercase()
        if (isHakimKeyboardQuestion(q)) {
            return "لوحة المفاتيح تأخذ جزءًا من ارتفاع الشاشة عند الكتابة. في هذا الإصدار سيطلب حكيم من أندرويد تصغير مساحة المحادثة تلقائيًا بدل تغطية مكان الكتابة، ليبقى مربع الإدخال ظاهرًا."
        }
        if (isHakimUiQuestion(q)) {
            return "هذا سؤال عن واجهة حكيم نفسها، لذلك أجيبك هنا محليًا ولا أفتح نموذجًا خارجيًا. اذكر العنصر الذي يزعجك وسأتعامل معه كدليل ميداني."
        }
        return when (q) {
            "مرحبا", "مرحباً", "أهلا", "أهلاً", "السلام عليكم", "سلام", "هاي", "hello", "hi" ->
                "أهلًا بك. أنا حكيم، اكتب مقصدك وسأتولى أفضل مسار متاح."
            "شكرا", "شكراً", "شكرًا", "مشكور", "thanks", "thank you" ->
                "على الرحب والسعة."
            "من انت", "من أنت", "ما انت", "ما أنت" ->
                "أنا حكيم، واجهة تنفيذ موحدة تختار الأدوات والنماذج بحسب المقصد والصلاحيات المتاحة."
            else -> null
        }
    }

    private fun isHakimKeyboardQuestion(q: String): Boolean {
        val mentionsKeyboard = listOf("لوحة المفاتيح", "الكيبورد", "keyboard").any { q.contains(it) }
        val mentionsComposer = listOf("مكان الكتابة", "مربع الكتابة", "حقل الكتابة", "الإدخال", "يغطي", "تغطي").any { q.contains(it) }
        return mentionsKeyboard && mentionsComposer
    }

    private fun isHakimUiQuestion(q: String): Boolean {
        val mentionsHakim = q.contains("حكيم") || q.contains("التطبيق") || q.contains("الواجهة")
        val mentionsUi = listOf("الواجهة", "زر", "مكان الكتابة", "المحادثة", "الشاشة", "نافذة").any { q.contains(it) }
        return mentionsHakim && mentionsUi
    }

    private fun compactExternalPrompt(prompt: String): String {
        val q = prompt.trim()
        return buildString {
            appendLine("أجب عن طلب المستخدم مباشرة وبالعربية ما لم يطلب غير ذلك.")
            appendLine("لا تدّعِ تنفيذًا أو نجاحًا لم يحدث فعليًا.")
            appendLine("طلب المستخدم:")
            append(q)
        }.take(2_000)
    }

    private fun bestObservedProvider(context: Context, candidates: List<Provider>): Provider? {
        if (candidates.isEmpty()) return null
        val prefs = context.getSharedPreferences("hakim_router", Context.MODE_PRIVATE)
        val scored = candidates.map { it to prefs.getInt("provider_" + it.id + "_score", 0) }
        return scored.maxByOrNull { it.second }?.first
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isDirectUrl(text: String): Boolean {
        return text.startsWith("https://") || text.startsWith("http://")
    }

    private fun needsFreshWeb(text: String): Boolean {
        val q = text.lowercase()
        val markers = listOf(
            "اليوم", "الآن", "حالي", "أحدث", "آخر خبر", "ابحث", "تحقق من",
            "سعر", "طقس", "موعد", "متوفر", "فتح الآن", "latest", "today", "current"
        )
        return markers.any { q.contains(it) }
    }
}
