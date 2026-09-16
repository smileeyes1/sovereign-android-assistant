package ps.hakim.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * جسر الاستدلال المتقدم متعدد المزودات.
 * التنفيذ المحلي يسبق هذه الطبقة؛ وعند الحاجة للاستدلال المتقدم تمر كل خطة عبر
 * ميزان الحكمة وناقد المداولة محليًا قبل أن تصل إلى منفذ الأفعال.
 *
 * قاعدة ثبات الواجهة: تجربة الجلسات الجاهزة تتم بصمت، أما فتح شاشة تسجيل دخول
 * أو نقل المستخدم من محادثة حكيم فلا يحدث تلقائيًا لمجرد فشل مزود صامت.
 */
object HakimReasoningBridge {
    private const val PACKAGE = "com.openai.chatgpt"
    private const val MAX_LAUNCH_ATTEMPTS = 8
    private const val MAX_POLL_ATTEMPTS = 45
    private const val PREFS = "hakim_reasoning_bridge"
    private const val ALLOW_INTERACTIVE_LOGIN = "allow_interactive_login"

    data class Result(
        val available: Boolean,
        val plan: HakimReasoningProtocol.Plan?,
        val responseText: String,
        val reason: String,
        val providerId: String = ""
    )

    /**
     * لا تُفعّل إلا من فعل مستخدم صريح داخل حكيم. الوضع الافتراضي يحفظ الواجهة
     * في مكانها ويطلب من المستخدم فتح المزود يدويًا إذا احتاج تسجيل دخول.
     */
    fun setInteractiveLoginAllowed(context: Context, allowed: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(ALLOW_INTERACTIVE_LOGIN, allowed)
            .apply()
    }

    fun interactiveLoginAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ALLOW_INTERACTIVE_LOGIN, false)

    fun ask(
        activity: Activity,
        basePrompt: String,
        onProgress: (String) -> Unit = {},
        onComplete: (Result) -> Unit
    ) {
        val goalForAudit = basePrompt.take(12000)
        val governedPrompt = buildString {
            appendLine(basePrompt.take(15000))
            append(HakimEliteWisdomEngine.promptContext(goalForAudit))
            append(HakimDeliberationQuality.promptContext(goalForAudit))
            append(HakimDecisionMatrix.promptContext(goalForAudit))
            append(HakimExcellenceOptimizer.promptContext())
        }.take(24000)

        val preference = HakimReasoningProviderRegistry.preferredProviderId(activity)
        if (preference == HakimReasoningProviderRegistry.LOCAL_ONLY) {
            onComplete(Result(true, null, "", "الوضع محلي فقط؛ لم تُرسل المهمة إلى مزود خارجي", "local_deterministic"))
            return
        }

        val candidates = HakimReasoningProviderRegistry.orderedWebProviders(activity)
        if (candidates.isEmpty()) {
            onComplete(Result(true, null, "", "لا يوجد مزود استدلال متقدم مختار؛ استمر حكيم محليًا فيما يمكن إثباته", "local_deterministic"))
            return
        }

        val failures = ArrayList<String>()

        fun interactiveFallback() {
            val first = candidates.firstOrNull()
            if (first == null) {
                onComplete(Result(true, null, "", "لا يوجد مزود متقدم متاح", ""))
                return
            }
            if (!interactiveLoginAllowed(activity)) {
                val detail = failures.takeLast(3).joinToString("؛ ").take(480)
                onComplete(
                    Result(
                        true,
                        null,
                        "",
                        buildString {
                            append("المزودات المتقدمة تحتاج جلسة دخول أو لم تثبت جاهزيتها؛ بقيت واجهة حكيم مفتوحة ولم أغيّر الشاشة تلقائيًا")
                            if (detail.isNotBlank()) append(". $detail")
                            append(". افتح المزود المطلوب من متصفح حكيم وسجّل الدخول عند الحاجة، ثم أعد المحاولة")
                        },
                        "local_deterministic"
                    )
                )
                return
            }
            onProgress("سمحتَ صراحةً بفتح جلسة الدخول؛ سأفتح مزودًا واحدًا فقط ثم أعود إلى حكيم.")
            HakimWebReasoningBridge.ask(
                activity = activity,
                basePrompt = governedPrompt,
                providerId = first.id,
                interactiveLogin = true,
                onProgress = onProgress,
                onComplete = { web ->
                    // التفويض التفاعلي للاستدعاء الحالي فقط؛ لا يتحول إلى موافقة دائمة.
                    setInteractiveLoginAllowed(activity, false)
                    val plan = web.plan
                    if (plan != null) {
                        val audit = HakimDeliberationQuality.audit(plan, goalForAudit)
                        if (audit.acceptable) {
                            onComplete(Result(true, plan, web.responseText, "${web.reason} • ${audit.reason}", web.providerId))
                        } else {
                            maybeOfficialAppFallback(
                                activity,
                                governedPrompt,
                                "رفض ناقد المداولة الخطة: ${audit.reason}",
                                onProgress,
                                onComplete
                            )
                        }
                    } else if (web.responseText.isNotBlank()) {
                        onComplete(Result(true, null, web.responseText, web.reason, web.providerId))
                    } else {
                        maybeOfficialAppFallback(activity, governedPrompt, web.reason, onProgress, onComplete)
                    }
                }
            )
        }

        fun tryProvider(index: Int) {
            if (index >= candidates.size) {
                interactiveFallback()
                return
            }
            val provider = candidates[index]
            onProgress(if (index == 0) "حكيم يفكر عبر أفضل مزود متاح ثم يدقق الخطة محليًا…" else "أبدّل تلقائيًا إلى مزود استدلال آخر دون فقد المهمة…")
            HakimWebReasoningBridge.ask(
                activity = activity,
                basePrompt = governedPrompt,
                providerId = provider.id,
                interactiveLogin = false,
                onProgress = onProgress,
                onComplete = { web ->
                    when {
                        web.plan != null -> {
                            val audit = HakimDeliberationQuality.audit(web.plan, goalForAudit)
                            if (audit.acceptable) {
                                onComplete(Result(true, web.plan, web.responseText, "${web.reason} • ${audit.reason}", web.providerId))
                            } else {
                                failures += "${provider.title}: خطة دون معيار مهني كافٍ (${audit.score}/100)"
                                tryProvider(index + 1)
                            }
                        }
                        web.responseText.isNotBlank() && !web.reason.startsWith("NEEDS_LOGIN") -> {
                            failures += "${provider.title}: عاد رد بلا خطة موثوقة"
                            tryProvider(index + 1)
                        }
                        else -> {
                            failures += "${provider.title}: ${web.reason.take(160)}"
                            tryProvider(index + 1)
                        }
                    }
                }
            )
        }

        tryProvider(0)
    }

    /**
     * احتياط قديم لا يعمل في ملف Play-Protect-safe عادةً لأن Accessibility غير معلن.
     * يبقى فقط لبيئات تطوير ثبتت فيها الخدمة، ولا يصبح المسار الافتراضي أو نقطة فشل واحدة.
     */
    private fun maybeOfficialAppFallback(
        activity: Activity,
        basePrompt: String,
        webReason: String,
        onProgress: (String) -> Unit,
        onComplete: (Result) -> Unit
    ) {
        val service = HakimAccessibilityService.instance
        if (service == null) {
            onComplete(Result(true, null, "", if (webReason.isBlank()) "لم يثبت مزود متقدم جاهز؛ المهمة محفوظة داخل حكيم" else webReason, ""))
            return
        }
        val launch = activity.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch == null) {
            onComplete(Result(true, null, "", "لم يثبت مزود متقدم جاهز؛ المهمة محفوظة داخل حكيم", ""))
            return
        }
        onProgress("أستخدم مسار ChatGPT الرسمي الاحتياطي المقيد في بيئة التطوير.")
        askViaOfficialApp(activity, basePrompt, service, onProgress, onComplete)
    }

    private fun askViaOfficialApp(
        activity: Activity,
        basePrompt: String,
        service: HakimAccessibilityService,
        onProgress: (String) -> Unit,
        onComplete: (Result) -> Unit
    ) {
        val launch = activity.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch == null) {
            onComplete(Result(true, null, "", "تطبيق ChatGPT الرسمي غير متاح", "chatgpt_official"))
            return
        }

        val request = HakimReasoningProtocol.wrap(basePrompt)
        val handler = Handler(Looper.getMainLooper())
        activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))

        fun fail(reason: String) = onComplete(Result(true, null, "", reason, "chatgpt_official"))

        fun pollResponse(baseline: String) {
            var attempts = 0
            var lastText = ""
            var stable = 0
            lateinit var poll: () -> Unit
            poll = {
                if (activity.isFinishing || activity.isDestroyed) {
                    fail("انتهت واجهة الطلب")
                } else if (service.foregroundPackage() != PACKAGE) {
                    fail("خرج مسار الاستدلال الاحتياطي قبل وصول الخطة")
                } else {
                    attempts += 1
                    val visible = service.visibleTextForPackage(PACKAGE)
                    val plan = HakimReasoningProtocol.parse(visible, request)
                    if (plan != null) {
                        val audit = HakimDeliberationQuality.audit(plan, basePrompt)
                        if (audit.acceptable) {
                            onComplete(Result(true, plan, visible.takeLast(8000), "وصلت خطة حكيم المقيدة واجتازت التدقيق المهني", "chatgpt_official"))
                        } else {
                            fail("وصلت خطة قابلة للتحليل لكنها لم تجتز جودة المداولة: ${audit.reason}")
                        }
                    } else {
                        if (visible == lastText && visible.isNotBlank()) stable += 1 else stable = 0
                        lastText = visible
                        val changed = visible.isNotBlank() && visible != baseline
                        if (changed && attempts >= 8 && stable >= 4) {
                            onComplete(Result(true, null, visible.takeLast(8000), "عاد الاستدلال دون خطة قابلة للتنفيذ", "chatgpt_official"))
                        } else if (attempts >= MAX_POLL_ATTEMPTS) {
                            fail("لم تظهر خطة حكيم ضمن حد المراقبة")
                        } else handler.postDelayed(poll, 800L)
                    }
                }
            }
            handler.postDelayed(poll, 900L)
        }

        fun inject(attempt: Int) {
            if (activity.isFinishing || activity.isDestroyed) {
                fail("انتهت واجهة الطلب")
                return
            }
            if (service.foregroundPackage() != PACKAGE) {
                if (attempt >= MAX_LAUNCH_ATTEMPTS) fail("تعذر الوصول إلى واجهة ChatGPT الرسمية")
                else handler.postDelayed({ inject(attempt + 1) }, 450L)
                return
            }
            val inserted = service.setFirstEditableForPackage(PACKAGE, request.prompt)
            if (!inserted) {
                if (attempt >= MAX_LAUNCH_ATTEMPTS) fail("تعذر العثور على حقل المحادثة بأمان")
                else handler.postDelayed({ inject(attempt + 1) }, 450L)
                return
            }
            val baseline = service.visibleTextForPackage(PACKAGE)
            handler.postDelayed({
                val labels = listOf("إرسال", "Send", "إرسال الرسالة", "send message")
                var sent = false
                for (label in labels) {
                    if (service.clickTextInPackage(PACKAGE, label)) {
                        sent = true
                        break
                    }
                }
                if (!sent) sent = service.performImeEnterForPackage(PACKAGE)
                if (!sent) fail("تعذر إرسال الرسالة من الواجهة الاحتياطية بأمان")
                else pollResponse(baseline)
            }, 350L)
        }
        handler.postDelayed({ inject(0) }, 900L)
    }
}
