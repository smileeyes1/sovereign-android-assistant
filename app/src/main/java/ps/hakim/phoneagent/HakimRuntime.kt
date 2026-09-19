package ps.hakim.phoneagent

import android.app.Activity
import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * نقطة الربط المحلية بين واجهة حكيم ومحركات التحكم.
 * تحتفظ بمرجع ضعيف فقط، وتربط متصفح ٢٠٠٤٠ عندما يكون WebView داخل Activity ظاهرة.
 */
object HakimRuntime {
    @Volatile
    private var visibleWebViewRef: WeakReference<WebView>? = null

    @Synchronized
    fun attach(webView: WebView) {
        visibleWebViewRef = WeakReference(webView)
        (webView.context as? Activity)?.let { activity ->
            runCatching { HakimSovereignBrowserRuntime.attach(activity, webView) }
                .onFailure { HakimCrashShield.recordNonFatal(activity, "sovereign_browser_runtime_attach", it) }
        }
    }

    @Synchronized
    fun detach(webView: WebView) {
        if (visibleWebViewRef?.get() === webView) {
            runCatching { HakimSovereignBrowserRuntime.detach(webView) }
            visibleWebViewRef?.clear()
            visibleWebViewRef = null
        }
    }

    fun visibleWebView(): WebView? = visibleWebViewRef?.get()
}