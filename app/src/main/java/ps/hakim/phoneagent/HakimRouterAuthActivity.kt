package ps.hakim.phoneagent

import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * بوابة مصادقة محلية محصورة للراوتر المثبت.
 *
 * - لا تقرأ حقول الإدخال ولا تضيف JavascriptInterface.
 * - لا ترسل Cookies أو كلمات مرور أو DOM خارج التطبيق.
 * - لا تسمح بالملاحة خارج https://192.168.1.1.
 * - بعد تغيّر الجلسة المحلية تعيد تشغيل NetworkGuardian فقط.
 */
class HakimRouterAuthActivity : ComponentActivity() {
    companion object {
        private const val ROUTER_ORIGIN = "https://192.168.1.1"
        private const val ROUTER_HOST = "192.168.1.1"
    }

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val verifyRunnable = object : Runnable {
        override fun run() {
            val state = HakimNetworkGuardian.status(this@HakimRouterAuthActivity)
            when (state.optString("state")) {
                "FAMILY_DNS_CONFIGURED" -> {
                    status.text = "تمت مصادقة الراوتر واستؤنفت الحماية بنجاح."
                    handler.postDelayed({ finish() }, 900L)
                }
                "ROUTER_AUTH_REQUIRED" -> {
                    status.text = "سجّل الدخول إلى الراوتر هنا؛ بيانات الدخول تبقى محليًا على الهاتف."
                }
                else -> {
                    status.text = "جارٍ التحقق من جلسة الراوتر والحماية…"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        status = TextView(this).apply {
            text = "واجهة الراوتر المحلية — بيانات الدخول لا تُرسل إلى حكيم أو السحابة."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(16, 14, 16, 14)
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        webView = WebView(this).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)

        @Suppress("SetJavaScriptEnabled")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return !isRouterUri(uri)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isRouterUri(Uri.parse(url.orEmpty()))) return
                CookieManager.getInstance().flush()
                HakimNetworkGuardian.inspectAsync(
                    this@HakimRouterAuthActivity.applicationContext,
                    "router_local_auth_webview"
                )
                handler.removeCallbacks(verifyRunnable)
                handler.postDelayed(verifyRunnable, 1800L)
            }

            override fun onReceivedSslError(view: WebView?, handlerSsl: SslErrorHandler?, error: SslError?) {
                val url = error?.url.orEmpty()
                val uri = runCatching { Uri.parse(url) }.getOrNull()
                if (uri != null && isRouterUri(uri)) {
                    handlerSsl?.proceed()
                } else {
                    handlerSsl?.cancel()
                }
            }
        }

        webView.loadUrl("$ROUTER_ORIGIN/")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun isRouterUri(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host == ROUTER_HOST &&
            uri.port in setOf(-1, 443)
}
