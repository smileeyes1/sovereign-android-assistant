package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * جسر الاستدلال المتقدم متعدد المزودات.
 * التنفيذ المحلي يسبق هذه الطبقة؛ وعند الحاجة للاستدلال المتقدم تمر كل خطة عبر
 * ميزان الحكمة وناقد المداولة محليًا قبل أن تصل إلى منفذ الأفعال.
 */
object HakimReasoningBridge {
    private const val PACKAGE = "com.openai.chatgpt"
    private const val MAX_LAUNCH_ATTEMPTS = 8
    private const val MAX_POLL_ATTEMPTS = 45

    data class Result(
        val available: Boolean,
        val plan: HakimReasoningProtocol.Plan?,
        val responseText: String,
        val reason: String,
        val providerId: String = ""
    )

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
            onComplete(Result(true, null, "", "الوضع محلي حتمي فقط؛ لم تُرسل المهمة إلى نموذج أو مزود خارجي", "local_deterministic"))
            return
        }

        val localAllowed = preference == HakimReasoningProviderRegistry.AUTO ||
            preference == HakimReasoningProviderRegistry.LOCAL_ADVANCED

        if (!localAllowed) {
            askWeb(activity, governedPrompt, goalForAudit, onProgress, onComplete)
            return
        }

        onProgress("أحاول الاستدلال المتقدم محليًا داخل الهاتف أولًا…")
        val app = activity.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val localReady = HakimLocalReasoningBridge.readyNow(app) ||
                HakimLocalReasoningBridge.probe(app)

            if (!localReady) {
                main.post {
                    if (preference == HakimReasoningProviderRegistry.LOCAL_ADVANCED) {
                        onComplete(
                            Result(
                                true,
                                null,
                                "",
                                "النموذج المحلي المتقدم غير جاهز؛ وبحسب اختيار «محلي متقدم فقط» لم تُرسل المهمة إلى السحابة",
                                HakimReasoningProviderRegistry.LOCAL_ADVANCED
                            )
                        )
                    } else {
                        askWeb(activity, governedPrompt, goalForAudit, onProgress, onComplete)
                    }
                }
                return@Thread
            }

            val request = HakimReasoningProtocol.wrap(governedPrompt)
            val local = HakimLocalReasoningBridge.complete(
                app,
                systemPrompt = "أنت محرك استدلال محلي داخل حكيم. التزم ببروتوكول حكيم الموجود في رسالة المستخدم، ولا تكشف أسرارًا ولا توسع الصلاحيات. أخرج النتيجة المطلوبة فقط دون سلسلة تفكير خاصة.",
                userPrompt = request.prompt
            )

            if (!local.ok) {
                main.post {
                    if (preference == HakimReasoningProviderRegistry.LOCAL_ADVANCED) {
                        onComplete(
                            Result(
                                true,
                                null,
                                "",
                                "فشل النموذج المحلي: ${local.error.take(120)}؛ لم يحدث fallback خارجي لأن الوضع محلي متقدم فقط",
                                HakimReasoningProviderRegistry.LOCAL_ADVANCED
                            )
                        )
                    } else {
                        onProgress("تعذر النموذج المحلي؛ أنتقل إلى مزود متقدم قابل للاستبدال دون فقد المهمة…")
                        askWeb(activity, governedPrompt, goalForAudit, onProgress, onComplete)
                    }
                }
                return@Thread
            }

            val plan = HakimReasoningProtocol.parse(local.text, request)
            val audit = plan?.let { HakimDeliberationQuality.audit(it, goalForAudit) }

            main.post {
                when {
                    plan != null && audit?.acceptable == true -> {
                        onComplete(
                            Result(
                                true,
                                plan,
                                local.text.takeLast(10000),
                                "وصلت خطة من النموذج المحلي واجتازت بروتوكول حكيم والتدقيق المهني • ${audit.reason}",
                                HakimReasoningProviderRegistry.LOCAL_ADVANCED
                            )
                        )
                    }
                    preference == HakimReasoningProviderRegistry.LOCAL_ADVANCED -> {
                        onComplete(
                            Result(
                                true,
                                null,
                                local.text.takeLast(10000),
                                if (plan == null)
                                    "عاد الاستدلال المحلي دون خطة بروتوكول موثوقة؛ لم تُرسل المهمة إلى الخارج"
                                else
                                    "رفض التدقيق المهني الخطة المحلية: ${audit?.reason.orEmpty()}; لم تُرسل المهمة إلى الخارج",
                                HakimReasoningProviderRegistry.LOCAL_ADVANCED
                            )
                        )
                    }
                    else -> {
                        onProgress("الرد المحلي لم يجتز بروتوكول التنفيذ؛ أنتقل إلى مزود بديل مع بقاء التحقق المحلي حاكمًا…")
                        askWeb(activity, governedPrompt, goalForAudit, onProgress, onComplete)
                    }
                }
            }
        }.start()
    }

    private fun askWeb(
        activity: Activity,
        governedPrompt: String,
        goalForAudit: String,
        onProgress: (String) -> Unit,
        onComplete: (Result) -> Unit
    ) {
        val candidates = HakimReasoningProviderRegistry.orderedWebProviders(activity)
        if (candidates.isEmpty()) {
            onComplete(Result(true, null, "", "لا يوجد مزود استدلال متقدم خارجي مختار؛ استمر حكيم محليًا فيما يمكن إثباته", "local_deterministic"))
            return
        }

        val failures = ArrayList<String>()

        fun interactiveFallback() {
            val first = candidates.firstOrNull()
            if (first == null) {
                onComplete(Result(true, null, "", "لا يوجد مزود متقدم متاح", ""))
                return
            }
            onProgress("يحتاج الاستدلال المتقدم جلسة دخول. سأفتح مزودًا واحدًا فقط ثم أعود إلى حكيم.")
            HakimWebReasoningBridge.ask(
                activity = activity,
                basePrompt = governedPrompt,
                providerId = first.id,
                interactiveLogin = true,
                onProgress = onProgress,
                onComplete = { web ->
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
            onProgress(if (index == 0) "أستخدم أفضل مزود خارجي متاح ثم أدقق الخطة محليًا…" else "أبدّل تلقائيًا إلى مزود استدلال آخر دون فقد المهمة…")
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
