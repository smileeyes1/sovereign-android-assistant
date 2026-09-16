package ps.hakim.phoneagent

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference

/** يربط طبقات ٢٠٠٤٠ بالـWebView القائم دون استبدال MainActivity أو كسر file chooser/WebChromeClient. */
object HakimSovereignBrowserRuntime {
    const val VERSION = "SOVEREIGN-BROWSER-RUNTIME-2026-09-16-v1"
    private const val BAR_TAG = "hakim_sovereign_browser_bar"
    private val handler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<Activity>? = null
    private var webRef: WeakReference<WebView>? = null
    private var ticker: Runnable? = null

    fun attach(activity: Activity, web: WebView) {
        activityRef = WeakReference(activity); webRef = WeakReference(web)
        HakimBrowserTabs.ensure(activity, web)
        HakimSovereignBrowserAgent.applyNetworkPolicy(activity, web)
        HakimSovereignBrowserAgent.resumePendingNavigation(activity)
        installEnhancedDownload(activity, web)
        injectToolbar(activity, web)
        startTicker(activity, web)
    }

    fun detach(web: WebView) {
        if (webRef?.get() !== web) return
        ticker?.let(handler::removeCallbacks)
        ticker = null
        webRef = null; activityRef = null
    }

    private fun startTicker(activity: Activity, web: WebView) {
        ticker?.let(handler::removeCallbacks)
        val task = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed || webRef?.get() !== web) return
                HakimBrowserTabs.updateCurrent(activity, web)
                HakimSovereignBrowserAgent.applyNetworkPolicy(activity, web)
                updateToolbar(activity, web)
                handler.postDelayed(this, 900L)
            }
        }
        ticker = task; handler.post(task)
    }

    private fun injectToolbar(activity: Activity, web: WebView) {
        val parent = web.parent as? LinearLayout ?: return
        if (parent.findViewWithTag<View>(BAR_TAG) != null) return
        val palette = HakimChatUi.palette(activity)
        val bar = LinearLayout(activity).apply {
            tag = BAR_TAG
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 3, 8, 3)
            background = HakimChatUi.rounded(palette.surface, 12f, activity, palette.border)
        }
        val state = TextView(activity).apply {
            tag = "hakim_browser_live_state"
            textSize = 12.5f
            setTextColor(palette.text)
            gravity = Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            maxLines = 2
        }
        val tabs = Button(activity).apply {
            tag = "hakim_browser_tabs_button"
            text = "تبويبات"
            textSize = 12f
            setOnClickListener { showTabs(activity, web, this) }
        }
        val tools = Button(activity).apply {
            text = "أدوات"
            textSize = 12f
            setOnClickListener { showTools(activity, this) }
        }
        bar.addView(state, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(tools)
        bar.addView(tabs)
        val index = parent.indexOfChild(web).coerceAtLeast(0)
        parent.addView(bar, index, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        updateToolbar(activity, web)
    }

    private fun updateToolbar(activity: Activity, web: WebView) {
        val parent = web.parent as? LinearLayout ?: return
        val bar = parent.findViewWithTag<View>(BAR_TAG) as? ViewGroup ?: return
        val state = bar.findViewWithTag<TextView>("hakim_browser_live_state") ?: return
        val tabsButton = bar.findViewWithTag<Button>("hakim_browser_tabs_button")
        val p = activity.getSharedPreferences("hakim_browser_agent", Context.MODE_PRIVATE)
        val live = p.getString("live_state", "IDLE").orEmpty()
        val action = p.getString("live_action", "").orEmpty()
        val layer = p.getString("live_layer", "").orEmpty()
        state.text = when (live) {
            "RUNNING" -> "يعمل الآن: ${action.ifBlank { "متصفح حكيم" }}${if(layer.isBlank())"" else " • $layer"}"
            "SUCCESS" -> "تم: ${p.getString("live_evidence", "").orEmpty().take(150)}"
            "WAITING" -> "بانتظارك: ${p.getString("live_evidence", "").orEmpty().take(150)}"
            "FAILED" -> "غيّر المسار: ${p.getString("live_evidence", "").orEmpty().take(150)}"
            else -> "متصفح حكيم السيادي • محلي أولًا"
        }
        tabsButton?.text = "تبويبات ${HakimBrowserTabs.list(activity).size}"
    }

    private fun showTabs(activity: Activity, web: WebView, anchor: View) {
        val popup = PopupMenu(activity, anchor)
        val tabs = HakimBrowserTabs.list(activity)
        tabs.forEachIndexed { i, tab -> popup.menu.add(0, 1000+i, i, (if (HakimBrowserTabs.active(activity)?.id==tab.id) "✓ " else "") + tab.title.take(30)) }
        popup.menu.add(0, 1900, 100, "+ تبويب جديد")
        popup.menu.add(0, 1901, 101, "إغلاق التبويب الحالي")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1900 -> { HakimBrowserTabs.newTab(activity, web); true }
                1901 -> { HakimBrowserTabs.active(activity)?.let { HakimBrowserTabs.close(activity, web, it.id) }; true }
                else -> tabs.getOrNull(item.itemId-1000)?.let { HakimBrowserTabs.switchTo(activity, web, it.id) } ?: false
            }
        }
        popup.show()
    }

    private fun showTools(activity: Activity, anchor: View) {
        val tools = HakimSovereignToolRegistry.all(activity)
        val popup = PopupMenu(activity, anchor)
        tools.take(18).forEachIndexed { i, tool -> popup.menu.add(0, 3000+i, i, tool.title + if(tool.available) " ✓" else " —") }
        popup.setOnMenuItemClickListener { item ->
            val tool = tools.getOrNull(item.itemId-3000) ?: return@setOnMenuItemClickListener false
            Toast.makeText(activity, "${tool.title}: ${tool.successCondition}", Toast.LENGTH_LONG).show(); true
        }
        popup.show()
    }

    private fun installEnhancedDownload(activity: Activity, web: WebView) {
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val safeUrl = url.orEmpty()
            if (!safeUrl.startsWith("https://") && !safeUrl.startsWith("http://")) return@setDownloadListener
            runCatching {
                val fileName = URLUtil.guessFileName(safeUrl, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(safeUrl)).apply {
                    if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(safeUrl)?.let { addRequestHeader("Cookie", it) }
                    if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                    setTitle(fileName); setDescription("تنزيل بواسطة حكيم")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = activity.getSystemService(DownloadManager::class.java)
                val id = dm.enqueue(request)
                HakimBrowserDownloadLedger.recordStart(activity, id, safeUrl, fileName)
                activity.getSharedPreferences("hakim_browser_agent", Context.MODE_PRIVATE).edit()
                    .putString("live_state", "RUNNING").putString("live_action", "تنزيل $fileName").putString("live_layer", "ANDROID_DOWNLOAD_MANAGER").apply()
                Toast.makeText(activity, "بدأ التنزيل: $fileName", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(activity, "تعذر بدء التنزيل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun status(context: Context): org.json.JSONObject = org.json.JSONObject()
        .put("version", VERSION)
        .put("toolbar_injected", webRef?.get() != null)
        .put("tabs", HakimBrowserTabs.status(context))
        .put("agent", HakimSovereignBrowserAgent.status(context))
        .put("downloads", HakimBrowserDownloadLedger.status(context))
}