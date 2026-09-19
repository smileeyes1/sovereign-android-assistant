package ps.hakim.phoneagent

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * تبويبات منطقية خفيفة: WebView واحد نشط لتقليل الذاكرة، مع حفظ URL/العنوان لكل تبويب.
 * الجلسات والكوكيز مشتركة محليًا عبر CookieManager ولا تُصدّر إلى أي مزود.
 */
object HakimBrowserTabs {
    const val VERSION = "BROWSER-TABS-2026-09-16-v1"
    private const val PREFS = "hakim_browser_tabs"
    private const val KEY_TABS = "tabs"
    private const val KEY_ACTIVE = "active_id"
    private const val MAX_TABS = 8

    data class Tab(val id: String, val title: String, val url: String, val updatedAt: Long)

    fun ensure(context: Context, web: WebView): Tab {
        val tabs = list(context).toMutableList()
        if (tabs.isEmpty()) {
            val tab = Tab(UUID.randomUUID().toString(), "تبويب ١", web.url.orEmpty(), System.currentTimeMillis())
            save(context, listOf(tab), tab.id)
            return tab
        }
        return active(context) ?: tabs.first()
    }

    fun newTab(context: Context, web: WebView, url: String = "about:blank"): Tab {
        updateCurrent(context, web)
        val tabs = list(context).toMutableList()
        if (tabs.size >= MAX_TABS) tabs.removeAt(0)
        val tab = Tab(UUID.randomUUID().toString(), "تبويب ${tabs.size + 1}", url, System.currentTimeMillis())
        tabs += tab
        save(context, tabs, tab.id)
        if (url == "about:blank") web.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
        else web.loadUrl(url)
        return tab
    }

    fun switchTo(context: Context, web: WebView, id: String): Boolean {
        updateCurrent(context, web)
        val tab = list(context).firstOrNull { it.id == id } ?: return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE, id).apply()
        if (tab.url.startsWith("http://") || tab.url.startsWith("https://")) web.loadUrl(tab.url)
        else web.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
        return true
    }

    fun close(context: Context, web: WebView, id: String): Boolean {
        updateCurrent(context, web)
        val tabs = list(context).toMutableList()
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false
        tabs.removeAt(index)
        if (tabs.isEmpty()) return newTab(context, web).let { true }
        val activeId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, "")
        val next = if (activeId == id) tabs[index.coerceAtMost(tabs.lastIndex)] else tabs.firstOrNull { it.id == activeId } ?: tabs.first()
        save(context, tabs, next.id)
        if (activeId == id) switchTo(context, web, next.id)
        return true
    }

    fun updateCurrent(context: Context, web: WebView, title: String = web.title.orEmpty(), url: String = web.url.orEmpty()) {
        if (url.isBlank()) return
        val tabs = list(context).toMutableList()
        if (tabs.isEmpty()) { ensure(context, web); return }
        val active = active(context) ?: tabs.first()
        val index = tabs.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
        tabs[index] = active.copy(
            title = title.trim().ifBlank { hostLabel(url) }.take(120),
            url = url.take(4000),
            updatedAt = System.currentTimeMillis()
        )
        save(context, tabs, active.id)
        CookieManager.getInstance().flush()
    }

    fun active(context: Context): Tab? {
        val tabs = list(context)
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, "").orEmpty()
        return tabs.firstOrNull { it.id == id } ?: tabs.firstOrNull()
    }

    fun list(context: Context): List<Tab> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TABS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = ArrayList<Tab>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            out += Tab(id, o.optString("title").take(120), o.optString("url").take(4000), o.optLong("updated_at"))
        }
        return out.take(MAX_TABS)
    }

    private fun save(context: Context, tabs: List<Tab>, activeId: String) {
        val arr = JSONArray()
        tabs.takeLast(MAX_TABS).forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("title", t.title).put("url", t.url).put("updated_at", t.updatedAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TABS, arr.toString())
            .putString(KEY_ACTIVE, activeId)
            .apply()
    }

    private fun hostLabel(url: String): String = runCatching { android.net.Uri.parse(url).host.orEmpty() }.getOrDefault("").ifBlank { "تبويب" }

    fun status(context: Context): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("logical_tabs", true)
        .put("active_tab_count", list(context).size)
        .put("max_tabs", MAX_TABS)
        .put("single_webview_memory_efficient", true)
        .put("cookies_local_shared_session", true)
        .put("exports_cookies_to_model", false)
}