package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var pairField: EditText
    private lateinit var pairButton: Button
    private val prefs by lazy { getSharedPreferences("hakim", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        configureBrowser()
        refreshPairingUi()
        if (isPaired()) startHakimService()
        if (savedInstanceState == null) {
            val last = prefs.getString("last_url", "https://www.google.com").orEmpty().ifBlank { "https://www.google.com" }
            webView.loadUrl(last)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPairingUi()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureBrowser() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val u = url.orEmpty()
                address.setText(u)
                if (u.isNotBlank()) prefs.edit().putString("last_url", u).apply()
                refreshPairingUi()
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "حكيم"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(16, 18, 16, 8)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(10, 4, 10, 4)
        }
        address = EditText(this).apply {
            hint = "العنوان أو الرابط"
            setSingleLine(true)
            textSize = 15f
        }
        val go = Button(this).apply {
            text = "اذهب"
            setOnClickListener { navigate(address.text.toString()) }
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addressRow.addView(go)
        root.addView(addressRow)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun navButton(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        nav.addView(navButton("رجوع") { if (webView.canGoBack()) webView.goBack() })
        nav.addView(navButton("تقدم") { if (webView.canGoForward()) webView.goForward() })
        nav.addView(navButton("تحديث") { webView.reload() })
        nav.addView(navButton("الرئيسية") { webView.loadUrl("https://www.google.com") })
        root.addView(nav)

        status = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 4)
        }
        root.addView(status)

        val pairRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(10, 0, 10, 8)
        }
        pairField = EditText(this).apply {
            hint = "رمز الاقتران"
            setSingleLine(true)
            textSize = 14f
        }
        pairButton = Button(this).apply {
            text = "حفظ"
            setOnClickListener { savePairing() }
        }
        pairRow.addView(pairField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pairRow.addView(pairButton)
        pairField.tag = pairRow
        root.addView(pairRow)

        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun navigate(raw: String) {
        val q = raw.trim()
        if (q.isBlank()) return
        val url = when {
            q.startsWith("https://") || q.startsWith("http://") -> q
            q.contains(".") && !q.contains(" ") -> "https://$q"
            else -> "https://www.google.com/search?q=" + android.net.Uri.encode(q)
        }
        webView.loadUrl(url)
    }

    private fun isPaired(): Boolean =
        prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
        prefs.getString("result_topic", "").orEmpty().isNotBlank()

    private fun refreshPairingUi() {
        val row = pairField.tag as LinearLayout
        row.visibility = if (isPaired()) View.GONE else View.VISIBLE
        status.text = when {
            !isPaired() -> "الحالة: يحتاج رمز الاقتران"
            HakimService.running -> "الحالة: متصل — حكيم يعمل في الخلفية"
            else -> "الحالة: مقترن — جارٍ تشغيل الخدمة الخلفية"
        }
    }

    private fun savePairing() {
        val parts = pairField.text.toString().trim().split("|")
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            Toast.makeText(this, "رمز الاقتران غير صحيح", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("command_topic", parts[0]).putString("result_topic", parts[1]).apply()
        pairField.setText("")
        startHakimService()
        refreshPairingUi()
        Toast.makeText(this, "تم اقتران حكيم وتشغيله في الخلفية", Toast.LENGTH_SHORT).show()
    }

    private fun startHakimService() {
        val intent = Intent(this, HakimService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        webView.postDelayed({ refreshPairingUi() }, 600)
    }
}
