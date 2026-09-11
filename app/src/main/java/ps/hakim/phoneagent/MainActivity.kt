package ps.hakim.phoneagent

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        configureBrowser()
        HakimRuntime.attach(webView)
        refreshPairingUi()
        if (isPaired()) startHakimService()
        if (savedInstanceState == null) {
            val last = prefs.getString("last_url", "https://www.google.com").orEmpty().ifBlank { "https://www.google.com" }
            webView.loadUrl(last)
        }
    }

    override fun onResume() {
        super.onResume()
        HakimRuntime.attach(webView)
        refreshPairingUi()
        val last = prefs.getString("last_url", "").orEmpty()
        if (last.startsWith("http") && webView.url != last) webView.loadUrl(last)
    }

    override fun onDestroy() {
        HakimRuntime.detach(webView)
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        webView.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback != null) {
                val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                callback.onReceiveValue(result)
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
            javaScriptCanOpenWindowsAutomatically = true
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
                webView.postDelayed({ refreshPairingUi() }, 500)
            }
        })
        connectionRow.addView(Button(this).apply {
            text = "إيقاف حكيم"
            setOnClickListener {
                stopService(Intent(this@MainActivity, HakimService::class.java).setAction(HakimService.ACTION_STOP))
                webView.postDelayed({ refreshPairingUi() }, 500)
            }
        })
        connectionRow.addView(Button(this).apply {
            text = "فصل الاقتران"
            setOnClickListener {
                stopService(Intent(this@MainActivity, HakimService::class.java).setAction(HakimService.ACTION_STOP))
                prefs.edit().remove("command_topic").remove("result_topic").apply()
                refreshPairingUi()
                Toast.makeText(this@MainActivity, "تم فصل الاقتران", Toast.LENGTH_SHORT).show()
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
        val row = pairField.tag as LinearLayout
        val paired = isPaired()
        row.visibility = if (paired) View.GONE else View.VISIBLE
        connectionRow.visibility = if (paired) View.VISIBLE else View.GONE
        status.text = when {
            !paired -> "الحالة: يحتاج رمز الاقتران"
            HakimService.running -> "الحالة: متصل — حكيم جاهز"
            else -> "الحالة: مقترن — الاتصال متوقف"
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
        Toast.makeText(this, "تم اقتران حكيم", Toast.LENGTH_SHORT).show()
    }

    private fun startHakimService() {
        if (!isPaired()) return
        val intent = Intent(this, HakimService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }
}
