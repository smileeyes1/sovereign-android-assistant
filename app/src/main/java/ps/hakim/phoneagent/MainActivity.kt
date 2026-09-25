package ps.hakim.phoneagent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
        private const val APP_PERMISSIONS_REQUEST = 7002
    }

    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var permissionStatus: TextView
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
            refreshPermissionStatus()
            uiHandler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        configureBrowser()
        refreshPairingUi()
        refreshPermissionStatus()
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
        refreshPermissionStatus()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == APP_PERMISSIONS_REQUEST) {
            refreshPermissionStatus()
            val denied = permissions.indices.any { i -> grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED }
            Toast.makeText(
                this,
                if (denied) "مُنحت الصلاحيات التي وافقت عليها — يمكنك إكمال الباقي من إعدادات التطبيق" else "تم منح الصلاحيات النافعة لحكيم",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION")
    private fun configureBrowser() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            safeBrowsingEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString.replace("; wv", "")
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = true
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

            override fun onPermissionRequest(request: PermissionRequest?) {
                val r = request ?: return
                runOnUiThread { handleWebMediaPermission(r) }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                val safeOrigin = origin.orEmpty()
                val cb = callback ?: return
                runOnUiThread { handleWebLocationPermission(safeOrigin, cb) }
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

    private fun handleWebMediaPermission(request: PermissionRequest) {
        val origin = request.origin?.toString().orEmpty()
        if (!origin.startsWith("https://")) {
            request.deny()
            Toast.makeText(this, "رُفض طلب الوسائط لأن الصفحة ليست آمنة", Toast.LENGTH_SHORT).show()
            return
        }

        val allowed = mutableListOf<String>()
        val labels = mutableListOf<String>()
        request.resources.forEach { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    if (hasPermission(Manifest.permission.CAMERA)) {
                        allowed += resource
                        labels += "الكاميرا"
                    }
                }
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        allowed += resource
                        labels += "الميكروفون"
                    }
                }
            }
        }

        if (allowed.isEmpty()) {
            request.deny()
            Toast.makeText(
                this,
                "الموقع لا يستطيع طلب صلاحية أندرويد نيابةً عنك. استخدم «منح الصلاحيات» واختر المطلوب ثم أعد المحاولة.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("صلاحية للموقع")
            .setMessage("السماح للموقع:\n$origin\nباستخدام: ${labels.joinToString("، ")}؟")
            .setPositiveButton("سماح") { _, _ -> request.grant(allowed.toTypedArray()) }
            .setNegativeButton("رفض") { _, _ -> request.deny() }
            .setOnCancelListener { request.deny() }
            .show()
    }

    private fun handleWebLocationPermission(origin: String, callback: GeolocationPermissions.Callback) {
        if (!origin.startsWith("https://")) {
            callback.invoke(origin, false, false)
            Toast.makeText(this, "رُفض طلب الموقع لأن الصفحة ليست آمنة", Toast.LENGTH_SHORT).show()
            return
        }
        val locationGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!locationGranted) {
            callback.invoke(origin, false, false)
            Toast.makeText(
                this,
                "الموقع لا يستطيع فتح صلاحية النظام تلقائيًا. استخدم «منح الصلاحيات» واختر الموقع ثم أعد المحاولة.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("صلاحية الموقع")
            .setMessage("السماح للموقع:\n$origin\nباستخدام موقع الجهاز؟")
            .setPositiveButton("سماح") { _, _ -> callback.invoke(origin, true, false) }
            .setNegativeButton("رفض") { _, _ -> callback.invoke(origin, false, false) }
            .setOnCancelListener { callback.invoke(origin, false, false) }
            .show()
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

        root.addView(TextView(this).apply {
            text = "على أندرويد: حكيم يعمل محليًا ويستخدم تطبيقات النماذج/المتصفح عند الحاجة. إضافة MCP الخاصة بـChatGPT ليست قناة أندرويد."
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(12, 2, 12, 6)
        })

        permissionStatus = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 4)
        }
        root.addView(permissionStatus)

        val permissionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        permissionRow.addView(Button(this).apply {
            text = "منح الصلاحيات"
            setOnClickListener { showPermissionChooser() }
        })
        permissionRow.addView(Button(this).apply {
            text = "إعدادات التطبيق"
            setOnClickListener { openAppSettings() }
        })
        root.addView(permissionRow)

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
            hint = "رمز اقتران الجسر التنفيذي"
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

    private fun usefulRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT <= 28) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        return list.distinct().toTypedArray()
    }

    private fun showPermissionChooser() {
        val choices = mutableListOf<Pair<String, List<String>>>()
        if (!hasPermission(Manifest.permission.CAMERA)) {
            choices += "الكاميرا" to listOf(Manifest.permission.CAMERA)
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            choices += "الميكروفون" to listOf(Manifest.permission.RECORD_AUDIO)
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            choices += "الموقع" to listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            choices += "الإشعارات" to listOf(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= 28 && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            choices += "حفظ التنزيلات" to listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (choices.isEmpty()) {
            refreshPermissionStatus()
            Toast.makeText(this, "لا توجد صلاحيات لازمة غير ممنوحة", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("اختر صلاحية واحدة")
            .setItems(choices.map { it.first }.toTypedArray()) { _, which ->
                requestSpecificPermissions(choices[which].second, explicitUserGrant = true)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun requestSpecificPermissions(
        requested: Collection<String>,
        explicitUserGrant: Boolean = false
    ) {
        val missing = requested.distinct().filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            refreshPermissionStatus()
            return
        }

        val target = missing.sorted().joinToString("|")
        if (explicitUserGrant) {
            HakimCapabilityKernel.grantOnce(this, "grant_permission", target)
        }
        val authorization = HakimCapabilityKernel.authorize(
            this,
            "grant_permission",
            target,
            "count=" + missing.size
        )
        if (authorization.optString("verdict") != "allow") {
            Toast.makeText(this, "لم تُطلب صلاحية جديدة دون اختيارك الصريح.", Toast.LENGTH_LONG).show()
            return
        }

        requestPermissions(missing.toTypedArray(), APP_PERMISSIONS_REQUEST)
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun refreshPermissionStatus() {
        if (!::permissionStatus.isInitialized) return
        val all = usefulRuntimePermissions()
        val granted = all.count { hasPermission(it) }
        permissionStatus.text = "الصلاحيات النافعة: $granted/${all.size} ممنوحة"
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح إعدادات التطبيق", Toast.LENGTH_SHORT).show()
        }
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
            isDesktopOnlyPluginPage() -> "تنبيه: إضافة ChatGPT هذه لسطح المكتب فقط؛ لا تعتمدها على أندرويد. اقتران حكيم بالجسر منفصل عنها."
            !paired && prefs.getBoolean("pairing_disabled_by_user", false) -> "قناة الجسر التنفيذي: الاقتران مفصول بقرارك"
            !paired -> "قناة الجسر التنفيذي: تحتاج رمز اقتران حكيم — وليس رمز إضافة ChatGPT"
            HakimService.connected -> "الحالة: متصل فعليًا — حكيم جاهز"
            HakimService.running -> "الحالة: حكيم يعمل — جارٍ الاتصال"
            else -> "الحالة: مقترن — الخدمة متوقفة"
        }
    }

    private fun isDesktopOnlyPluginPage(): Boolean {
        val u = if (::webView.isInitialized) webView.url.orEmpty() else ""
        return u.startsWith("https://chatgpt.com/plugins/") || u.contains("/codex/")
    }

    private fun savePairing() {
        val parts = pairField.text.toString().trim().split("|")
        if (parts.size !in 2..3 || parts.take(2).any { it.isBlank() }) {
            Toast.makeText(this, "رمز اقتران الجسر التنفيذي غير صحيح", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "تم اقتران حكيم بالجسر التنفيذي", Toast.LENGTH_SHORT).show()
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
