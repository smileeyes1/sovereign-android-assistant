package ps.hakim.phoneagent

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
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
    companion object {
        private const val FILE_CHOOSER_REQUEST = 7001
    }

    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var pairField: EditText
    private lateinit var pairButton: Button
    private lateinit var connectionRow: LinearLayout
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val prefs by lazy { getSharedPreferences("hakim", MODE_PRIVATE) }
    private val uiHandler = Handler(Looper.getMainLooper())
    private var resumed = false

    private val statusTicker = object : Runnable {
        override fun run() {
            if (!resumed) return
            refreshPairingUi()
            uiHandler.postDelayed(this, 1500L)
        }
    }

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
        resumed = true
        HakimRuntime.attach(webView)
        val last = prefs.getString("last_url", "").orEmpty()
        if (last.startsWith("http") && webView.url != last) webView.loadUrl(last)
        refreshPairingUi()
        uiHandler.removeCallbacks(statusTicker)
        uiHandler.post(statusTicker)
    }

    override fun onPause() {
        val current = webView.url.orEmpty()
        if (current.startsWith("http")) {
            prefs.edit().putString("last_url", current).apply()
            CookieManager.getInstance().flush()
            syncBackgroundUrl(current)
        }
        HakimRuntime.detach(webView)
        resumed = false
        uiHandler.removeCallbacks(statusTicker)
        super.onPause()
    }

    override fun onDestroy() {
        HakimRuntime.detach(webView)
        uiHandler.removeCallbacksAndMessages(null)
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback != null) {
                callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
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
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }, FILE_CHOOSER_REQUEST)
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    false
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val u = url.orEmpty()
                address.setText(u)
                if (u.startsWith("http")) prefs.edit().putString("last_url", u).apply()
                CookieManager.getInstance().flush()
                refreshPairingUi()
            }
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun enqueueDownload(url: String?, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val safeUrl = url.orEmpty()
        if (!safeUrl.startsWith("https://") && !safeUrl.startsWith("http://")) return
        try {
            val fileName = URLUtil.guessFileName(safeUrl, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(safeUrl)).apply {
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(safeUrl)?.let { addRequestHeader("Cookie", it) }
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                setTitle(fileName)
                setDescription("تنزيل بواسطة حكيم")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            getSystemService(DownloadManager::class.java).enqueue(request)
            Toast.makeText(this, "بدأ التنزيل", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر بدء التنزيل", Toast.LENGTH_SHORT).show()
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
            hint = "العنوان أو البحث"
            setSingleLine(true)
            textSize = 15f
            setOnEditorActionListener { _, _, _ ->
                navigate(text.toString())
                true
            }
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

        connectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        connectionRow.addView(Button(this).apply {
            text = "تشغيل حكيم"
            setOnClickListener {
                startHakimService()
                refreshPairingUi()
            }
        })
        connectionRow.addView(Button(this).apply {
            text = "إيقاف حكيم"
            setOnClickListener {
                stopService(Intent(this@MainActivity, HakimService::class.java).setAction(HakimService.ACTION_STOP))
                uiHandler.postDelayed({ refreshPairingUi() }, 300)
            }
        })
        connectionRow.addView(Button(this).apply {
            text = "فصل الاقتران"
            setOnClickListener {
                stopService(Intent(this@MainActivity, HakimService::class.java).setAction(HakimService.ACTION_STOP))
                prefs.edit()
                    .remove("command_topic")
                    .remove("result_topic")
                    .remove("auth_key")
                    .putBoolean("pairing_disabled_by_user", true)
                    .apply()
                refreshPairingUi()
                Toast.makeText(this@MainActivity, "تم فصل الاقتران ولن يُعاد تلقائيًا", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(connectionRow)

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
            else -> "https://www.google.com/search?q=" + Uri.encode(q)
        }
        webView.loadUrl(url)
    }

    private fun isPaired(): Boolean =
        prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
        prefs.getString("result_topic", "").orEmpty().isNotBlank()

    private fun refreshPairingUi() {
        if (!::pairField.isInitialized || !::status.isInitialized) return
        val row = pairField.tag as LinearLayout
        val paired = isPaired()
        row.visibility = if (paired) View.GONE else View.VISIBLE
        connectionRow.visibility = if (paired) View.VISIBLE else View.GONE
        status.text = when {
            !paired && prefs.getBoolean("pairing_disabled_by_user", false) -> "الحالة: الاقتران مفصول بقرارك"
            !paired -> "الحالة: يحتاج رمز الاقتران"
            HakimService.connected -> "الحالة: متصل فعليًا — حكيم جاهز"
            HakimService.running -> "الحالة: حكيم يعمل — جارٍ الاتصال"
            else -> "الحالة: مقترن — الخدمة متوقفة"
        }
    }

    private fun savePairing() {
        val parts = pairField.text.toString().trim().split("|")
        if (parts.size !in 2..3 || parts.take(2).any { it.isBlank() }) {
            Toast.makeText(this, "رمز الاقتران غير صحيح", Toast.LENGTH_SHORT).show()
            return
        }
        val edit = prefs.edit()
            .putString("command_topic", parts[0])
            .putString("result_topic", parts[1])
            .putBoolean("pairing_disabled_by_user", false)
        if (parts.size == 3 && parts[2].isNotBlank()) edit.putString("auth_key", parts[2])
        edit.apply()
        pairField.setText("")
        startHakimService()
        refreshPairingUi()
        Toast.makeText(this, "تم اقتران حكيم", Toast.LENGTH_SHORT).show()
    }

    private fun startHakimService() {
        if (!isPaired()) return
        val intent = Intent(this, HakimService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر تشغيل خدمة حكيم", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncBackgroundUrl(url: String) {
        if (!HakimService.running || !url.startsWith("http")) return
        try {
            startService(
                Intent(this, HakimService::class.java)
                    .setAction(HakimService.ACTION_SYNC_URL)
                    .putExtra(HakimService.EXTRA_URL, url)
            )
        } catch (_: Exception) {
            // الخدمة تعمل أصلًا؛ فشل المزامنة اللحظية لا يمنع حفظ last_url في SharedPreferences.
        }
    }
}
