package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * جسر استدلال داخل حكيم بلا API مدفوع وبلا Accessibility.
 * يستخدم جلسة chatgpt.com داخل WebView من نفس تطبيق حكيم. بعد تسجيل الدخول مرة واحدة في متصفح حكيم،
 * تعمل الطلبات التالية عبر WebView مؤقت صغير ثم يُدمّر فور انتهاء الجولة لتقليل الذاكرة.
 * المحتوى المسترجع يبقى بيانات فقط، والخطة تمر دائمًا عبر HakimReasoningProtocol ثم المنفذ المحلي.
 */
object HakimWebReasoningBridge {
    private const val CHAT_URL = "https://chatgpt.com/"
    private const val MAX_READY_ATTEMPTS = 18
    private const val MAX_LOGIN_WAIT_ATTEMPTS = 150
    private const val MAX_SEND_ATTEMPTS = 10
    private const val MAX_RESPONSE_ATTEMPTS = 90

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
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete(Result(false, null, "", "انتهت واجهة حكيم"))
            return
        }

        val request = HakimReasoningProtocol.wrap(basePrompt)
        val handler = Handler(Looper.getMainLooper())
        val hidden = WebView(activity)
        var finished = false
        var loginLaunched = false

        fun cleanup() {
            handler.removeCallbacksAndMessages(null)
            runCatching {
                (hidden.parent as? ViewGroup)?.removeView(hidden)
                hidden.stopLoading()
                hidden.loadUrl("about:blank")
                hidden.clearHistory()
                hidden.removeAllViews()
                hidden.destroy()
            }
        }

        fun finish(result: Result) {
            if (finished) return
            finished = true
            cleanup()
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(
                    Intent(activity, HakimAgentsChatActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            Handler(Looper.getMainLooper()).postDelayed({ onComplete(result) }, 120L)
        }

        fun isTrustedChatOrigin(web: WebView): Boolean {
            val uri = runCatching { Uri.parse(web.url.orEmpty()) }.getOrNull() ?: return false
            return uri.scheme == "https" && uri.host.orEmpty().lowercase() == "chatgpt.com"
        }

        fun decodeJsString(raw: String?): String {
            val value = raw.orEmpty()
            if (value == "null" || value.isBlank()) return ""
            return runCatching { JSONArray("[$value]").optString(0) }.getOrDefault("")
        }

        fun evaluateText(web: WebView, callback: (String) -> Unit) {
            if (!isTrustedChatOrigin(web)) {
                callback("")
                return
            }
            web.evaluateJavascript(
                "(() => (document.body && document.body.innerText ? document.body.innerText.slice(-50000) : ''))()"
            ) { raw -> callback(decodeJsString(raw)) }
        }

        lateinit var injectAndSend: (WebView) -> Unit
        lateinit var waitForLogin: () -> Unit

        fun launchLoginOnce() {
            if (finished || loginLaunched) return
            loginLaunched = true
            onProgress("يحتاج محرك الذكاء داخل حكيم تسجيل الدخول مرة واحدة. فتحت لك ChatGPT داخل متصفح حكيم؛ بعد تسجيل الدخول سأكمل تلقائيًا.")
            activity.getSharedPreferences("hakim", Activity.MODE_PRIVATE).edit()
                .putString("last_url", CHAT_URL)
                .apply()
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            handler.postDelayed({ waitForLogin() }, 900L)
        }

        var loginAttempt = 0
        waitForLogin = loginLoop@ {
            if (finished) return@loginLoop
            if (activity.isFinishing || activity.isDestroyed) {
                finish(Result(false, null, "", "انتهت واجهة حكيم أثناء تسجيل الدخول"))
                return@loginLoop
            }
            loginAttempt += 1
            val visible = HakimRuntime.visibleWebView()
            if (visible != null && isTrustedChatOrigin(visible)) {
                visible.evaluateJavascript(composerProbeScript()) { raw ->
                    when (decodeJsString(raw)) {
                        "READY" -> {
                            onProgress("اكتمل تسجيل الدخول داخل حكيم؛ أرسل المهمة المحكومة الآن وأعيد النتيجة إلى واجهتك.")
                            injectAndSend(visible)
                        }
                        else -> {
                            if (loginAttempt >= MAX_LOGIN_WAIT_ATTEMPTS) {
                                finish(Result(true, null, "", "انتهت مهلة تسجيل الدخول داخل متصفح حكيم؛ المهمة محفوظة ولم تُرسل خارجيًا"))
                            } else handler.postDelayed({ waitForLogin() }, 900L)
                        }
                    }
                }
            } else if (loginAttempt >= MAX_LOGIN_WAIT_ATTEMPTS) {
                finish(Result(true, null, "", "لم يكتمل تسجيل الدخول داخل متصفح حكيم؛ المهمة محفوظة"))
            } else {
                handler.postDelayed({ waitForLogin() }, 900L)
            }
        }

        injectAndSend = inject@ { web ->
            if (finished) return@inject
            if (!isTrustedChatOrigin(web)) {
                finish(Result(false, null, "", "رفض حكيم تمرير الاستدلال لأن الأصل ليس chatgpt.com"))
                return@inject
            }

            val baselineHolder = arrayOf("")
            evaluateText(web) { baseline ->
                baselineHolder[0] = baseline
                val promptLiteral = JSONObject.quote(request.prompt)
                val insertScript = """
                    (() => {
                      const el = document.querySelector('#prompt-textarea') ||
                                 document.querySelector('textarea') ||
                                 document.querySelector('[contenteditable="true"][data-testid*="composer"]') ||
                                 document.querySelector('[contenteditable="true"]');
                      if (!el) return 'NO_COMPOSER';
                      const prompt = $promptLiteral;
                      el.focus();
                      if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                        const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                        const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
                        if (setter) setter.call(el, prompt); else el.value = prompt;
                        el.dispatchEvent(new Event('input', {bubbles:true}));
                        el.dispatchEvent(new Event('change', {bubbles:true}));
                      } else {
                        el.innerHTML = '';
                        const sel = window.getSelection();
                        const range = document.createRange();
                        range.selectNodeContents(el);
                        range.collapse(true);
                        sel.removeAllRanges();
                        sel.addRange(range);
                        document.execCommand('insertText', false, prompt);
                        el.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:prompt}));
                      }
                      return 'INSERTED';
                    })()
                """.trimIndent()

                web.evaluateJavascript(insertScript) { insertRaw ->
                    when (decodeJsString(insertRaw)) {
                        "INSERTED" -> {
                            onProgress("أدخلت المهمة داخل محرك الويب الموثوق في حكيم؛ أرسلها الآن دون كشف أسرار محلية.")
                            var sendAttempt = 0
                            lateinit var sendTry: () -> Unit
                            sendTry = sendLoop@ {
                                if (finished) return@sendLoop
                                sendAttempt += 1
                                web.evaluateJavascript(sendButtonScript()) { sendRaw ->
                                    when (decodeJsString(sendRaw)) {
                                        "SENT" -> {
                                            onProgress("أرسلت المهمة المحكومة وأراقب الخطة المقيدة داخل حكيم.")
                                            var responseAttempt = 0
                                            var lastText = ""
                                            var stable = 0
                                            lateinit var poll: () -> Unit
                                            poll = pollLoop@ {
                                                if (finished) return@pollLoop
                                                responseAttempt += 1
                                                evaluateText(web) { visible ->
                                                    val plan = HakimReasoningProtocol.parse(visible, request)
                                                    if (plan != null) {
                                                        finish(Result(true, plan, visible.takeLast(9000), "وصلت خطة حكيم المقيدة عبر محرك الويب الداخلي"))
                                                    } else {
                                                        if (visible.isNotBlank() && visible == lastText) stable += 1 else stable = 0
                                                        lastText = visible
                                                        val changed = visible.isNotBlank() && visible != baselineHolder[0]
                                                        if (changed && responseAttempt >= 14 && stable >= 7) {
                                                            finish(Result(true, null, visible.takeLast(9000), "عاد الرد داخل حكيم لكن لم تظهر خطة بروتوكول موثوقة"))
                                                        } else if (responseAttempt >= MAX_RESPONSE_ATTEMPTS) {
                                                            finish(Result(true, null, visible.takeLast(9000), "انتهت مهلة الاستدلال الداخلي دون خطة قابلة للتنفيذ"))
                                                        } else handler.postDelayed({ poll() }, 900L)
                                                    }
                                                }
                                            }
                                            handler.postDelayed({ poll() }, 900L)
                                        }
                                        else -> {
                                            if (sendAttempt >= MAX_SEND_ATTEMPTS) {
                                                finish(Result(false, null, "", "تعذر العثور على زر إرسال موثوق داخل محرك الويب"))
                                            } else handler.postDelayed({ sendTry() }, 450L)
                                        }
                                    }
                                }
                            }
                            handler.postDelayed({ sendTry() }, 450L)
                        }
                        else -> launchLoginOnce()
                    }
                }
            }
        }

        @Suppress("SetJavaScriptEnabled")
        hidden.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = false
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString.replace("; wv", "")
        }
        hidden.setBackgroundColor(Color.TRANSPARENT)
        hidden.alpha = 0.01f
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(hidden, true)
        }
        hidden.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, requestUrl: android.webkit.WebResourceRequest?): Boolean {
                val host = requestUrl?.url?.host.orEmpty().lowercase()
                return host != "chatgpt.com"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
            }
        }

        activity.addContentView(
            hidden,
            FrameLayout.LayoutParams(2, 2, Gravity.BOTTOM or Gravity.START)
        )
        onProgress("أفتح محرك الاستدلال داخل حكيم بصورة مؤقتة وخفيفة.")
        hidden.loadUrl(CHAT_URL)

        var readyAttempt = 0
        lateinit var probe: () -> Unit
        probe = probeLoop@ {
            if (finished) return@probeLoop
            readyAttempt += 1
            if (!isTrustedChatOrigin(hidden)) {
                if (readyAttempt >= MAX_READY_ATTEMPTS) launchLoginOnce() else handler.postDelayed({ probe() }, 600L)
            } else {
                hidden.evaluateJavascript(composerProbeScript()) { raw ->
                    when (decodeJsString(raw)) {
                        "READY" -> injectAndSend(hidden)
                        "LOGIN" -> launchLoginOnce()
                        else -> {
                            if (readyAttempt >= MAX_READY_ATTEMPTS) launchLoginOnce()
                            else handler.postDelayed({ probe() }, 600L)
                        }
                    }
                }
            }
        }
        handler.postDelayed({ probe() }, 800L)
    }

    private fun composerProbeScript(): String = """
        (() => {
          const composer = document.querySelector('#prompt-textarea') ||
                           document.querySelector('textarea') ||
                           document.querySelector('[contenteditable="true"][data-testid*="composer"]') ||
                           document.querySelector('[contenteditable="true"]');
          if (composer) return 'READY';
          const body = (document.body?.innerText || '').toLowerCase();
          if (body.includes('log in') || body.includes('sign up') || body.includes('تسجيل الدخول') || body.includes('إنشاء حساب')) return 'LOGIN';
          return 'WAIT';
        })()
    """.trimIndent()

    private fun sendButtonScript(): String = """
        (() => {
          const buttons = Array.from(document.querySelectorAll('button'));
          const btn = document.querySelector('button[data-testid="send-button"]') ||
                      document.querySelector('button[data-testid*="send"]') ||
                      buttons.find(b => {
                        const a = (b.getAttribute('aria-label') || '').toLowerCase();
                        const t = (b.innerText || '').trim().toLowerCase();
                        return a.includes('send') || a.includes('إرسال') || t === 'send' || t === 'إرسال';
                      });
          if (btn && !btn.disabled) { btn.click(); return 'SENT'; }
          return 'WAIT';
        })()
    """.trimIndent()
}
