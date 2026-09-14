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
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * جسر استدلال ويب متعدد المزودات داخل حكيم، بلا API مدفوع وبلا Accessibility.
 * يدعم مزودات معروفة عبر جلسة WebView مشتركة، ويعيد فقط خطة HakimReasoningProtocol لتُفحص وتُنفذ محليًا.
 * لا يظهر واجهة المزود فوق حكيم إلا عند تسجيل دخول لازم ومسموح.
 */
object HakimWebReasoningBridge {
    private const val MAX_READY_ATTEMPTS = 15
    private const val MAX_LOGIN_WAIT_ATTEMPTS = 150
    private const val MAX_SEND_ATTEMPTS = 12
    private const val MAX_RESPONSE_ATTEMPTS = 95

    data class Result(
        val available: Boolean,
        val plan: HakimReasoningProtocol.Plan?,
        val responseText: String,
        val reason: String,
        val providerId: String
    )

    fun ask(
        activity: Activity,
        basePrompt: String,
        providerId: String,
        interactiveLogin: Boolean,
        onProgress: (String) -> Unit = {},
        onComplete: (Result) -> Unit
    ) {
        val spec = HakimReasoningProviderRegistry.webProvider(providerId)
        if (spec == null) {
            onComplete(Result(false, null, "", "مزود الاستدلال غير معروف", providerId))
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete(Result(false, null, "", "انتهت واجهة حكيم", providerId))
            return
        }

        val startedAt = System.currentTimeMillis()
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
            val success = result.plan != null
            HakimReasoningProviderRegistry.recordWebResult(
                activity,
                providerId,
                success,
                System.currentTimeMillis() - startedAt
            )
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(
                    Intent(activity, HakimAgentsChatActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            Handler(Looper.getMainLooper()).postDelayed({ onComplete(result) }, 100L)
        }

        fun trustedOrigin(web: WebView): Boolean {
            val uri = runCatching { Uri.parse(web.url.orEmpty()) }.getOrNull() ?: return false
            return uri.scheme == "https" && spec.trustedHosts.contains(uri.host.orEmpty().lowercase())
        }

        fun decodeJsString(raw: String?): String {
            val value = raw.orEmpty()
            if (value == "null" || value.isBlank()) return ""
            return runCatching { JSONArray("[$value]").optString(0) }.getOrDefault("")
        }

        fun evaluateText(web: WebView, callback: (String) -> Unit) {
            if (!trustedOrigin(web)) {
                callback("")
                return
            }
            web.evaluateJavascript(
                "(() => (document.body && document.body.innerText ? document.body.innerText.slice(-60000) : ''))()"
            ) { raw -> callback(decodeJsString(raw)) }
        }

        lateinit var injectAndSend: (WebView) -> Unit
        lateinit var waitForLogin: () -> Unit

        fun launchLoginOnce() {
            if (finished || loginLaunched) return
            if (!interactiveLogin) {
                finish(Result(false, null, "", "NEEDS_LOGIN:${spec.id}", providerId))
                return
            }
            loginLaunched = true
            onProgress("يحتاج ${spec.title} تسجيل دخول مرة واحدة. أفتح صفحة الدخول داخل متصفح حكيم ثم أعود تلقائيًا.")
            activity.getSharedPreferences("hakim", Activity.MODE_PRIVATE).edit()
                .putString("last_url", spec.startUrl)
                .apply()
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            handler.postDelayed({ waitForLogin() }, 850L)
        }

        var loginAttempt = 0
        waitForLogin = loginLoop@ {
            if (finished) return@loginLoop
            if (activity.isFinishing || activity.isDestroyed) {
                finish(Result(false, null, "", "انتهت واجهة حكيم أثناء تسجيل الدخول", providerId))
                return@loginLoop
            }
            loginAttempt += 1
            val visible = HakimRuntime.visibleWebView()
            if (visible != null && trustedOrigin(visible)) {
                visible.evaluateJavascript(composerProbeScript()) { raw ->
                    when (decodeJsString(raw)) {
                        "READY" -> {
                            onProgress("اكتمل تسجيل الدخول إلى ${spec.title}. أعود للاستدلال داخل حكيم.")
                            injectAndSend(visible)
                        }
                        else -> {
                            if (loginAttempt >= MAX_LOGIN_WAIT_ATTEMPTS) {
                                finish(Result(true, null, "", "انتهت مهلة تسجيل الدخول إلى ${spec.title}؛ المهمة محفوظة", providerId))
                            } else handler.postDelayed({ waitForLogin() }, 850L)
                        }
                    }
                }
            } else if (loginAttempt >= MAX_LOGIN_WAIT_ATTEMPTS) {
                finish(Result(true, null, "", "لم يكتمل تسجيل الدخول إلى ${spec.title}؛ المهمة محفوظة", providerId))
            } else {
                handler.postDelayed({ waitForLogin() }, 850L)
            }
        }

        injectAndSend = inject@ { web ->
            if (finished) return@inject
            if (!trustedOrigin(web)) {
                finish(Result(false, null, "", "رفض حكيم تمرير الاستدلال لأن الأصل لا يطابق ${spec.vendor}", providerId))
                return@inject
            }

            evaluateText(web) { baseline ->
                val promptLiteral = JSONObject.quote(request.prompt)
                val insertScript = """
                    (() => {
                      const candidates = [
                        document.querySelector('#prompt-textarea'),
                        document.querySelector('rich-textarea [contenteditable="true"]'),
                        document.querySelector('[contenteditable="true"][role="textbox"]'),
                        document.querySelector('[contenteditable="true"][data-testid*="composer"]'),
                        document.querySelector('textarea'),
                        document.querySelector('input[type="text"]'),
                        document.querySelector('[contenteditable="true"]')
                      ].filter(Boolean);
                      const el = candidates.find(x => !x.disabled && x.offsetParent !== null) || candidates[0];
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
                        try {
                          el.innerHTML = '';
                          const sel = window.getSelection();
                          const range = document.createRange();
                          range.selectNodeContents(el);
                          range.collapse(true);
                          sel.removeAllRanges();
                          sel.addRange(range);
                          document.execCommand('insertText', false, prompt);
                        } catch (_) {
                          el.textContent = prompt;
                        }
                        el.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:prompt}));
                      }
                      return 'INSERTED';
                    })()
                """.trimIndent()

                web.evaluateJavascript(insertScript) { insertRaw ->
                    when (decodeJsString(insertRaw)) {
                        "INSERTED" -> {
                            onProgress("أرسلت المهمة المحكومة إلى ${spec.title} داخل حكيم دون كشف أسرار محلية.")
                            var sendAttempt = 0
                            lateinit var sendTry: () -> Unit
                            sendTry = sendLoop@ {
                                if (finished) return@sendLoop
                                sendAttempt += 1
                                web.evaluateJavascript(sendButtonScript()) { sendRaw ->
                                    when (decodeJsString(sendRaw)) {
                                        "SENT" -> {
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
                                                        finish(Result(true, plan, visible.takeLast(10000), "وصلت خطة حكيم المقيدة عبر ${spec.title}", providerId))
                                                    } else {
                                                        if (visible.isNotBlank() && visible == lastText) stable += 1 else stable = 0
                                                        lastText = visible
                                                        val changed = visible.isNotBlank() && visible != baseline
                                                        if (changed && responseAttempt >= 14 && stable >= 7) {
                                                            finish(Result(true, null, visible.takeLast(10000), "عاد الرد من ${spec.title} لكن لم تظهر خطة بروتوكول موثوقة", providerId))
                                                        } else if (responseAttempt >= MAX_RESPONSE_ATTEMPTS) {
                                                            finish(Result(true, null, visible.takeLast(10000), "انتهت مهلة ${spec.title} دون خطة قابلة للتنفيذ", providerId))
                                                        } else handler.postDelayed({ poll() }, 850L)
                                                    }
                                                }
                                            }
                                            handler.postDelayed({ poll() }, 850L)
                                        }
                                        else -> {
                                            if (sendAttempt >= MAX_SEND_ATTEMPTS) {
                                                finish(Result(false, null, "", "تعذر العثور على زر إرسال موثوق في ${spec.title}", providerId))
                                            } else handler.postDelayed({ sendTry() }, 420L)
                                        }
                                    }
                                }
                            }
                            handler.postDelayed({ sendTry() }, 420L)
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
            override fun shouldOverrideUrlLoading(view: WebView?, requestUrl: WebResourceRequest?): Boolean {
                val uri = requestUrl?.url ?: return true
                return uri.scheme != "https" || !spec.trustedHosts.contains(uri.host.orEmpty().lowercase())
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
            }
        }

        activity.addContentView(hidden, FrameLayout.LayoutParams(2, 2, Gravity.BOTTOM or Gravity.START))
        onProgress("حكيم يفكر عبر ${spec.title} في الخلفية…")
        hidden.loadUrl(spec.startUrl)

        var readyAttempt = 0
        lateinit var probe: () -> Unit
        probe = probeLoop@ {
            if (finished) return@probeLoop
            readyAttempt += 1
            if (!trustedOrigin(hidden)) {
                if (readyAttempt >= MAX_READY_ATTEMPTS) launchLoginOnce() else handler.postDelayed({ probe() }, 550L)
            } else {
                hidden.evaluateJavascript(composerProbeScript()) { raw ->
                    when (decodeJsString(raw)) {
                        "READY" -> injectAndSend(hidden)
                        else -> {
                            if (readyAttempt >= MAX_READY_ATTEMPTS) launchLoginOnce()
                            else handler.postDelayed({ probe() }, 550L)
                        }
                    }
                }
            }
        }
        handler.postDelayed({ probe() }, 700L)
    }

    private fun composerProbeScript(): String = """
        (() => {
          const candidates = [
            document.querySelector('#prompt-textarea'),
            document.querySelector('rich-textarea [contenteditable="true"]'),
            document.querySelector('[contenteditable="true"][role="textbox"]'),
            document.querySelector('[contenteditable="true"][data-testid*="composer"]'),
            document.querySelector('textarea'),
            document.querySelector('input[type="text"]'),
            document.querySelector('[contenteditable="true"]')
          ].filter(Boolean);
          return candidates.some(x => !x.disabled) ? 'READY' : 'WAIT';
        })()
    """.trimIndent()

    private fun sendButtonScript(): String = """
        (() => {
          const buttons = Array.from(document.querySelectorAll('button'));
          const direct = document.querySelector('button[data-testid="send-button"]') ||
                         document.querySelector('button[data-testid*="send"]') ||
                         document.querySelector('button.send-button') ||
                         document.querySelector('[role="button"][aria-label*="Send"]') ||
                         document.querySelector('[role="button"][aria-label*="إرسال"]');
          const btn = direct || buttons.find(b => {
            const a = (b.getAttribute('aria-label') || '').trim().toLowerCase();
            const t = (b.innerText || '').trim().toLowerCase();
            return a.includes('send') || a.includes('submit') || a.includes('إرسال') ||
                   t === 'send' || t === 'submit' || t === 'إرسال';
          });
          if (btn && !btn.disabled) { btn.click(); return 'SENT'; }
          return 'WAIT';
        })()
    """.trimIndent()
}
