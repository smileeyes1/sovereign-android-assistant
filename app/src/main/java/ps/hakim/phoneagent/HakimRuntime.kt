package ps.hakim.phoneagent

import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * نقطة الربط المحلية بين واجهة حكيم وخدمة التحكم.
 * تحتفظ بمرجع ضعيف فقط حتى لا تمنع إغلاق Activity أو تسبب تسريب ذاكرة.
 */
object HakimRuntime {
    @Volatile
    private var visibleWebViewRef: WeakReference<WebView>? = null

    @Synchronized
    fun attach(webView: WebView) {
        visibleWebViewRef = WeakReference(webView)
    }

    @Synchronized
    fun detach(webView: WebView) {
        if (visibleWebViewRef?.get() === webView) {
            visibleWebViewRef?.clear()
            visibleWebViewRef = null
        }
    }

    fun visibleWebView(): WebView? = visibleWebViewRef?.get()
}
