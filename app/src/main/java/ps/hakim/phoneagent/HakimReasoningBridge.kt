package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * جسر الاستدلال لحكيم بلا API مدفوع.
 * المسار الافتراضي داخل تطبيق حكيم عبر WebView موثوق إلى chatgpt.com؛
 * مسار تطبيق ChatGPT الرسمي عبر Accessibility يبقى احتياطيًا لبيئات التطوير فقط عندما يكون متاحًا.
 * لا ينفذ نص النموذج مباشرة؛ يعيد فقط خطة HakimReasoningProtocol لتُفحص محليًا.
 */
object HakimReasoningBridge {
    private const val PACKAGE = "com.openai.chatgpt"
    private const val MAX_LAUNCH_ATTEMPTS = 8
    private const val MAX_POLL_ATTEMPTS = 45

    data class Result(
        val available: Boolean,
        val plan: HakimReasoningProtocol.Plan?,
        val responseText: String,
        val reason: String
    )

    fun ask(
        activity: Activity,
        basePrompt: String,
        onProgress: (String) -> Unit = {},
        onComplete: (Result) -> Unit
    ) {
        HakimWebReasoningBridge.ask(
            activity = activity,
            basePrompt = basePrompt,
            onProgress = onProgress,
            onComplete = webDone@ { web ->
                if (web.available && (web.plan != null || web.reason.contains("تسجيل الدخول") || web.reason.contains("عاد الرد") || web.reason.contains("مهلة"))) {
                    onComplete(Result(true, web.plan, web.responseText, web.reason))
                    return@webDone
                }

                val service = HakimAccessibilityService.instance
                if (service == null) {
                    // النسخة الميدانية Play-Protect-safe لا تعلن Accessibility. ابقَ داخل حكيم بدل القفز لتطبيق خارجي.
                    onComplete(
                        Result(
                            true,
                            web.plan,
                            web.responseText,
                            if (web.reason.isBlank()) "محرك الاستدلال الداخلي غير جاهز؛ المهمة محفوظة داخل حكيم" else web.reason
                        )
                    )
                    return@webDone
                }

                onProgress("تعذر مسار الويب الداخلي؛ أستخدم المسار الرسمي الاحتياطي المقيد دون توسيع السلطة.")
                askViaOfficialApp(activity, basePrompt, service, onProgress, onComplete)
            }
        )
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
            onComplete(Result(true, null, "", "تطبيق ChatGPT الرسمي غير متاح؛ بقيت المهمة داخل حكيم دون تنفيذ تخميني"))
            return
        }

        val request = HakimReasoningProtocol.wrap(basePrompt)
        val handler = Handler(Looper.getMainLooper())
        activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        onProgress("فتحت محرك الاستدلال الرسمي الاحتياطي وأجهز تمرير المهمة المحكومة.")

        fun fail(reason: String) = onComplete(Result(true, null, "", reason))

        fun pollResponse(baseline: String) {
            var attempts = 0
            var lastText = ""
            var stable = 0
            lateinit var poll: () -> Unit
            poll = {
                if (activity.isFinishing || activity.isDestroyed) {
                    fail("انتهت واجهة الطلب")
                } else if (service.foregroundPackage() != PACKAGE) {
                    fail("خرج محرك الاستدلال من الواجهة قبل وصول الخطة")
                } else {
                    attempts += 1
                    val visible = service.visibleTextForPackage(PACKAGE)
                    val plan = HakimReasoningProtocol.parse(visible, request)
                    if (plan != null) {
                        onComplete(Result(true, plan, visible.takeLast(8000), "وصلت خطة حكيم المقيدة"))
                    } else {
                        if (visible == lastText && visible.isNotBlank()) stable += 1 else stable = 0
                        lastText = visible
                        val changed = visible.isNotBlank() && visible != baseline
                        if (changed && attempts >= 8 && stable >= 4) {
                            onComplete(Result(true, null, visible.takeLast(8000), "عاد الاستدلال دون خطة قابلة للتنفيذ"))
                        } else if (attempts >= MAX_POLL_ATTEMPTS) {
                            onComplete(Result(true, null, visible.takeLast(8000), "لم تظهر خطة حكيم ضمن حد المراقبة"))
                        } else {
                            handler.postDelayed(poll, 800L)
                        }
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
                if (attempt >= MAX_LAUNCH_ATTEMPTS) {
                    fail("تعذر الوصول إلى واجهة ChatGPT الرسمية")
                } else {
                    handler.postDelayed({ inject(attempt + 1) }, 450L)
                }
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
                if (!sent) {
                    fail("تعذر إرسال الرسالة من واجهة ChatGPT بأمان")
                } else {
                    onProgress("أرسلت المهمة المحكومة وأراقب نتيجة الاستدلال دون قراءة الحقول الحساسة.")
                    pollResponse(baseline)
                }
            }, 350L)
        }

        handler.postDelayed({ inject(0) }, 900L)
    }
}
