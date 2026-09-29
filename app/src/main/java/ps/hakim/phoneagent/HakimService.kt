package ps.hakim.phoneagent

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

class HakimService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var connected = false
        const val ACTION_STOP = "ps.hakim.phoneagent.STOP"
        const val ACTION_SYNC_URL = "ps.hakim.phoneagent.SYNC_URL"
        const val ACTION_BROWSER_TASK = "ps.hakim.phoneagent.BROWSER_TASK"
        const val EXTRA_URL = "url"
        const val EXTRA_BROWSER_TASK_ID = "browser_task_id"
        const val EXTRA_BROWSER_QUERY = "browser_query"
        private const val CHANNEL_ID = "hakim_background"
        private const val NOTIFICATION_ID = 17
        private const val MAX_COMMAND_AGE_MS = 180_000L
        @Volatile private var activeService: HakimService? = null

        /** Read only; called from the authenticated relay worker, never from the UI thread. */
        fun readActiveBrowser(): JSONObject = activeService?.readBrowser()
            ?: JSONObject().put("ok", false).put("error", "browser_service_unavailable")

        /** Requires the Secure Relay approval and replay guard before entry. */
        fun backActiveBrowser(): JSONObject = activeService?.backBrowser()
            ?: JSONObject().put("ok", false).put("error", "browser_service_unavailable")

        /** Local-only acceptance probe. Uses an isolated WebView and never reads the user's active page. */
        fun fieldBrowserSelfTest(): JSONObject = activeService?.browserSelfTest()
            ?: JSONObject().put("ok", false).put("error", "browser_service_unavailable")

        /** قراءة ChatGPT محصورة في chatgpt.com ولا تتعامل مع حقول الدخول الحساسة. */
        fun chatGptRead(payload: JSONObject): JSONObject = activeService?.chatGptReadInternal(payload)
            ?: JSONObject().put("ok", false).put("error", "browser_service_unavailable")

        /** تنقّل قرائي داخل ChatGPT فقط؛ لا يرسل رسائل ولا يحذف شيئًا. */
        fun chatGptNavigate(payload: JSONObject): JSONObject = activeService?.chatGptNavigateInternal(payload)
            ?: JSONObject().put("ok", false).put("error", "browser_service_unavailable")

        /** أفعال تغيّر حالة حساب ChatGPT، ولا تصل هنا إلا بعد بوابة موافقة Secure Relay. */
        fun chatGptAction(payload: JSONObject): JSONObject = activeService?.chatGptActionInternal(payload)
            ?: JSONObject().put("ok", false).put("error", "browser_service_unavailable")
    }

    private val prefs by lazy { getSharedPreferences("hakim", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var socket: WebSocket? = null
    private var reconnectDelay = 1500L
    private var explicitStop = false
    private lateinit var webView: WebView
    private val recentRequests = LinkedHashSet<String>()
    private var activeBrowserTaskId: String? = null
    private var activeBrowserTaskStartedAt: Long = 0L
    private val browserTaskPrefs by lazy { getSharedPreferences("hakim_browser_tasks", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        running = true
        connected = false
        recentRequests.addAll(prefs.getStringSet("seen_request_ids", emptySet()) ?: emptySet())
        trimRecentRequests()
        startAsForeground()
        HakimUnifiedRelay.start(applicationContext)
        HakimLocalPairing.reconnectAsync(applicationContext)
        if (isPaired()) {
            createBrowser()
            connectRemote()
        } else {
            updateNotification("قناة حكيم المشفّرة تعمل — وضع خفيف بلا متصفح")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                explicitStop = true
                HakimUnifiedRelay.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SYNC_URL -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (url.startsWith("http")) {
                    if (!::webView.isInitialized) createBrowser()
                    webView.post {
                        if (webView.url != url) webView.loadUrl(url)
                    }
                }
                return START_STICKY
            }
            ACTION_BROWSER_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_BROWSER_TASK_ID).orEmpty().trim()
                val query = intent.getStringExtra(EXTRA_BROWSER_QUERY).orEmpty().trim()
                if (taskId.isNotBlank() && query.isNotBlank()) {
                    startBrowserTask(taskId, query)
                }
                return START_STICKY
            }
        }
        HakimUnifiedRelay.start(applicationContext)
        HakimLocalPairing.reconnectAsync(applicationContext)
        if (socket == null && isPaired()) {
            if (!::webView.isInitialized) createBrowser()
            connectRemote()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        running = false
        connected = false
        socket?.cancel()
        socket = null
        if (::webView.isInitialized) webView.destroy()
        if (!explicitStop) {
            HakimConnectionResilience.schedule(applicationContext)
            HakimConnectionResilience.scheduleSoon(applicationContext, "service_destroyed")
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        HakimConnectionResilience.schedule(applicationContext)
        HakimConnectionResilience.scheduleSoon(applicationContext, "task_removed")
        HakimExecutionFabric.recover(applicationContext, "task_removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "حكيم يعمل في الخلفية", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification("جارٍ الاتصال بقناة حكيم"))
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, HakimService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("حكيم")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopPending)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION")
    private fun createBrowser() {
        webView = WebView(applicationContext)
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
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val u = url.orEmpty()
                val address = HakimBrowserPrivacy.safeAddress(u)
                if (address.isNotBlank()) {
                    prefs.edit().putString("last_url", address).apply()
                    CookieManager.getInstance().flush()
                    view?.let { maybeCompleteBrowserTask(it, u) }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val message = error?.description?.toString().orEmpty()
                    prefs.edit().putString("last_web_error", message).apply()
                    activeBrowserTaskId?.let { failBrowserTask(it, message.ifBlank { "تعذر تحميل الصفحة" }) }
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    val reason = "تعذر تحميل الصفحة (HTTP ${errorResponse?.statusCode})"
                    prefs.edit().putString("last_web_error", reason).apply()
                    activeBrowserTaskId?.let { failBrowserTask(it, reason) }
                }
            }
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }
        val last = prefs.getString("last_url", "https://www.google.com").orEmpty().ifBlank { "https://www.google.com" }
        webView.loadUrl(last)
    }

    private fun startBrowserTask(taskId: String, query: String) {
        activeBrowserTaskId = taskId
        activeBrowserTaskStartedAt = System.currentTimeMillis()
        browserTaskPrefs.edit()
            .putString(taskId + "_state", "RUNNING")
            .remove(taskId + "_query")
            .putLong(taskId + "_started_at", activeBrowserTaskStartedAt)
            .remove(taskId + "_result")
            .remove(taskId + "_error")
            .apply()
        if (!::webView.isInitialized) createBrowser()
        webView.post {
            navigate(webView, query)
        }
    }

    private fun maybeCompleteBrowserTask(target: WebView, url: String) {
        val taskId = activeBrowserTaskId ?: return
        if (System.currentTimeMillis() - activeBrowserTaskStartedAt < 250L) return
        target.postDelayed({
            if (activeBrowserTaskId != taskId) return@postDelayed
            val script = """
                (function(){
                  try{
                    const secretKey=/(token|secret|password|code|state|signature|api.?key|relay.?key|session)/i;
                    const safeUrl=(raw)=>{
                      try{
                        const u=new URL(raw,location.href);
                        if(u.protocol!=='https:'&&u.protocol!=='http:') return '[رابط خاص مخفي]';
                        if(u.pathname.length>160||u.pathname.split('/').some(x=>x.length>48||secretKey.test(x))) return u.origin;
                        return u.origin+u.pathname;
                      }catch(_){return '[رابط غير صالح]';}
                    };
                    const title=(document.title||'').slice(0,300);
                    const text=(document.body&&document.body.innerText?document.body.innerText:'')
                      .replace(/\s+/g,' ').trim().slice(0,12000);
                    const protectedPage=[...document.querySelectorAll('a[href],input')].some(e=>{
                      const href=e.getAttribute('href')||'';
                      const type=(e.getAttribute('type')||'').toLowerCase();
                      const ac=(e.getAttribute('autocomplete')||'').toLowerCase();
                      return /^hakim:\/\/pair(?:\?|$)/i.test(href)||
                        /[?&](token|relay_key|client_secret|access_token|refresh_token|code)=/i.test(href)||
                        type==='password'||ac.includes('one-time-code')||ac.startsWith('cc-');
                    }) || /\b(?:api[_-]?key|(?:access|refresh|id|relay)?[_-]?token|client[_-]?secret|password|session(?:[_-]?(?:id|key|token))?|authorization)\s*[:=]\s*["']?\S+/i.test(title+'\n'+text)
                      || /\bbearer\s+[A-Za-z0-9_.-]{12,}/i.test(title+'\n'+text);
                    if(protectedPage) return JSON.stringify({url:safeUrl(location.href),privacy_gate:true});
                    const links=[...document.querySelectorAll('a[href]')].slice(0,40).map(a=>({
                      text:(a.innerText||a.getAttribute('aria-label')||'').trim().slice(0,180),
                      href:safeUrl(a.href||'')
                    }));
                    const blocked=/^(this page is blocked|access denied|تم حظر هذه الصفحة|الوصول مرفوض)\s*[.!؟]?$/i.test(title)
                      || /^(this page is blocked\b|your organization (doesn['’]t|does not) allow you to (view|visit) this site\b|تم حظر هذه الصفحة)/i.test(text.slice(0,250));
                    return JSON.stringify({url:safeUrl(location.href),title:title,text:text,links:links,blocked:blocked});
                  }catch(e){
                    return JSON.stringify({error:'page_read_failed'});
                  }
                })()
            """.trimIndent()
            target.evaluateJavascript(script) { raw ->
                if (activeBrowserTaskId != taskId) return@evaluateJavascript
                val decoded = decodeJsString(raw)
                val page = try { JSONObject(decoded) } catch (_: Exception) { JSONObject().put("raw", decoded) }
                if (page.optBoolean("privacy_gate")) {
                    failBrowserTask(taskId, "تتطلب هذه الصفحة إدخالًا أو موافقة محلية؛ لم تُقرأ أسرارها")
                    return@evaluateJavascript
                }
                if (page.optBoolean("blocked") || page.has("error")) {
                    failBrowserTask(taskId, "حُظر الوصول إلى الصفحة أو تعذر قراءتها")
                    return@evaluateJavascript
                }
                page.put("captured_at", System.currentTimeMillis())
                browserTaskPrefs.edit()
                    .putString(taskId + "_state", "COMPLETE")
                    .putString(taskId + "_result", page.toString())
                    .putString(taskId + "_url", HakimBrowserPrivacy.safeAddress(url))
                    .putLong(taskId + "_completed_at", System.currentTimeMillis())
                    .apply()
                activeBrowserTaskId = null
                updateNotification("حكيم جاهز — اكتمل جمع نتيجة الويب")
            }
        }, 900L)
    }

    private fun failBrowserTask(taskId: String, reason: String) {
        browserTaskPrefs.edit()
            .putString(taskId + "_state", "FAILED")
            .putString(taskId + "_error", reason.take(500))
            .putLong(taskId + "_completed_at", System.currentTimeMillis())
            .apply()
        if (activeBrowserTaskId == taskId) activeBrowserTaskId = null
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
            val id = getSystemService(DownloadManager::class.java).enqueue(request)
            prefs.edit().putLong("last_download_id", id).putString("last_download_name", fileName).apply()
        } catch (_: Exception) {}
    }

    private fun isPaired(): Boolean =
        prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
        prefs.getString("result_topic", "").orEmpty().isNotBlank()

    private fun hasSecurePairing(): Boolean =
        HakimUnifiedRelay.isConfigured(applicationContext)

    private fun authKey(): String = prefs.getString("auth_key", "").orEmpty().trim()

    private fun connectRemote() {
        socket?.cancel()
        socket = null
        connected = false
        if (!isPaired()) return
        updateNotification("جارٍ الاتصال بقناة حكيم")
        val topic = prefs.getString("command_topic", "").orEmpty()
        val req = Request.Builder().url("wss://ntfy.sh/$topic/ws").build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                reconnectDelay = 1500L
                prefs.edit().putLong("last_connected_at", System.currentTimeMillis()).remove("last_socket_error").apply()
                updateNotification("متصل — جاهز لتنفيذ أوامر المتصفح")
                sendResult(JSONObject().put("request_id", "system").put("status", "online").put("message", "حكيم متصل وجاهز"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    if (envelope.optString("event") != "message") return
                    val rawMessage = envelope.optString("message")
                    if (rawMessage.isBlank()) return
                    val cmd = decodeCommandMessage(rawMessage) ?: return
                    dispatchCommand(cmd)
                } catch (e: Exception) {
                    prefs.edit().putString("last_command_error", e.message.orEmpty().take(300)).apply()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                socket = null
                prefs.edit().putString("last_socket_error", t.message.orEmpty().take(300)).apply()
                updateNotification("انقطع الاتصال — إعادة المحاولة تلقائيًا")
                reconnectLater()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                socket = null
                updateNotification("انقطع الاتصال — إعادة المحاولة تلقائيًا")
                reconnectLater()
            }
        })
    }

    private fun dispatchCommand(cmd: JSONObject) {
        val visible = HakimRuntime.visibleWebView()
        if (visible != null) {
            visible.post { executeCommand(visible, cmd) }
        } else {
            webView.post { executeCommand(webView, cmd) }
        }
    }

    private fun decodeCommandMessage(rawMessage: String): JSONObject? {
        val key = authKey()
        if (key.isBlank()) {
            return try { JSONObject(rawMessage) } catch (_: Exception) { null }
        }
        return try {
            val envelope = JSONObject(rawMessage)
            val payload = envelope.optString("payload")
            val signature = envelope.optString("sig").lowercase()
            if (payload.isBlank() || signature.isBlank()) return null
            val expected = hmacHex(key, payload)
            if (!constantTimeEquals(expected, signature)) {
                prefs.edit().putString("last_auth_error", "توقيع أمر غير صالح").apply()
                return null
            }
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val cmd = JSONObject(String(decoded, StandardCharsets.UTF_8))
            val issuedAt = cmd.optLong("issued_at", 0L)
            if (issuedAt <= 0L || abs(System.currentTimeMillis() - issuedAt) > MAX_COMMAND_AGE_MS) {
                prefs.edit().putString("last_auth_error", "أمر قديم أو بلا توقيت موثوق").apply()
                return null
            }
            cmd
        } catch (_: Exception) {
            prefs.edit().putString("last_auth_error", "تعذر التحقق من الأمر").apply()
            null
        }
    }

    private fun reconnectLater() {
        val delay = reconnectDelay.coerceAtMost(30_000L)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30_000L)
        if (::webView.isInitialized) {
            webView.postDelayed({ if (isPaired() && socket == null) connectRemote() }, delay)
        }
    }

    @Synchronized
    private fun firstTime(requestId: String): Boolean {
        if (recentRequests.contains(requestId)) return false
        recentRequests.add(requestId)
        trimRecentRequests()
        prefs.edit().putStringSet("seen_request_ids", LinkedHashSet(recentRequests)).apply()
        return true
    }

    @Synchronized
    private fun trimRecentRequests() {
        while (recentRequests.size > 96) {
            val first = recentRequests.firstOrNull() ?: break
            recentRequests.remove(first)
        }
    }

    private fun executeCommand(target: WebView, cmd: JSONObject) {
        val requestId = cmd.optString("request_id", UUID.randomUUID().toString()).trim().take(160)
        if (requestId.isBlank()) return
        if (!firstTime(requestId)) {
            sendResult(JSONObject().put("request_id", requestId).put("status", "duplicate").put("message", "تم تجاهل أمر مكرر"))
            return
        }
        when (cmd.optString("type")) {
            "ping", "snapshot" -> sendSnapshot(target, requestId, "ok")
            "open_url" -> {
                navigate(target, cmd.optString("url").take(5000))
                target.postDelayed({ sendSnapshot(target, requestId, "ok") }, cmd.optLong("after_ms", 1500L).coerceIn(300L, 8000L))
            }
            "back" -> {
                if (target.canGoBack()) target.goBack()
                target.postDelayed({ sendSnapshot(target, requestId, "ok") }, 500)
            }
            "forward" -> {
                if (target.canGoForward()) target.goForward()
                target.postDelayed({ sendSnapshot(target, requestId, "ok") }, 500)
            }
            "reload" -> {
                target.reload()
                target.postDelayed({ sendSnapshot(target, requestId, "ok") }, 900)
            }
            "wait" -> target.postDelayed({ sendSnapshot(target, requestId, "ok") }, cmd.optLong("ms", 1000L).coerceIn(100L, 8000L))
            "scroll" -> runJsAction(target, requestId, scrollScript(cmd.optString("direction", "down"), cmd.optInt("amount", 0)))
            "tap_text" -> runJsAction(target, requestId, tapTextScript(cmd.optString("text").take(500), cmd.optBoolean("exact", false)))
            "tap_index" -> runJsAction(target, requestId, tapIndexScript(cmd.optInt("index", -1)))
            "set_text" -> runJsAction(target, requestId, setTextScript(cmd.optString("target").take(300), cmd.optString("value").take(6000)))
            "set_text_index" -> runJsAction(target, requestId, setTextIndexScript(cmd.optInt("index", -1), cmd.optString("value").take(6000)))
            "press_enter" -> runJsAction(target, requestId, pressEnterScript())
            else -> sendResult(JSONObject().put("request_id", requestId).put("status", "failed").put("message", "أمر غير معروف"))
        }
    }

    private fun navigate(target: WebView, raw: String) {
        val q = raw.trim()
        if (q.isBlank()) return
        val url = when {
            q.startsWith("https://") || q.startsWith("http://") -> q
            q.contains(".") && !q.contains(" ") -> "https://$q"
            else -> "https://www.google.com/search?q=" + Uri.encode(q)
        }
        target.loadUrl(url)
    }

    private fun runJsAction(target: WebView, requestId: String, script: String) {
        target.evaluateJavascript(script) { raw ->
            val ok = raw == "true" || raw == "\"true\""
            target.postDelayed({ sendSnapshot(target, requestId, if (ok) "ok" else "failed") }, 450)
        }
    }

    private fun sensitiveJs(): String = """
        function __hakimSensitive(e){
          const type=(e.getAttribute('type')||'').toLowerCase();
          const ac=(e.getAttribute('autocomplete')||'').toLowerCase();
          const id=(e.id||'').toLowerCase();
          const name=(e.getAttribute('name')||'').toLowerCase();
          const ph=(e.getAttribute('placeholder')||'').toLowerCase();
          const aria=(e.getAttribute('aria-label')||'').toLowerCase();
          const all=[type,ac,id,name,ph,aria].join(' ');
          if(type==='password') return true;
          if(ac.includes('password')||ac.includes('one-time-code')||ac.startsWith('cc-')) return true;
          return /(otp|passcode|pin|cvv|cvc|card.?number|security.?code|api.?key|token|secret|session|رمز.?التحقق|كلمة.?المرور|رقم.?البطاقة)/i.test(all);
        }
    """.trimIndent()

    private fun snapshotScript(): String = """
            (function(){
              try {
                ${sensitiveJs()}
                const secretKey=/(token|secret|password|code|state|signature|api.?key|relay.?key|session)/i;
                const safeUrl=(raw)=>{
                  try{
                    const u=new URL(raw,location.href);
                    if(u.protocol!=='https:'&&u.protocol!=='http:') return '[رابط خاص مخفي]';
                    if(u.pathname.length>160||u.pathname.split('/').some(x=>x.length>48||secretKey.test(x))) return u.origin;
                    return u.origin+u.pathname;
                  }catch(_){return '[رابط غير صالح]';}
                };
                const title=(document.title||'').slice(0,300);
                const text=(document.body&&document.body.innerText||'').slice(0,7500);
                const protectedPage=[...document.querySelectorAll('a[href],input')].some(e=>{
                  const href=e.getAttribute('href')||'';
                  const type=(e.getAttribute('type')||'').toLowerCase();
                  const ac=(e.getAttribute('autocomplete')||'').toLowerCase();
                  return /^hakim:\/\/pair(?:\?|$)/i.test(href)||
                    /[?&](token|relay_key|client_secret|access_token|refresh_token|code)=/i.test(href)||
                    type==='password'||ac.includes('one-time-code')||ac.startsWith('cc-');
                }) || /\b(?:api[_-]?key|(?:access|refresh|id|relay)?[_-]?token|client[_-]?secret|password|session(?:[_-]?(?:id|key|token))?|authorization)\s*[:=]\s*["']?\S+/i.test(title+'\n'+text)
                  || /\bbearer\s+[A-Za-z0-9_.-]{12,}/i.test(title+'\n'+text);
                if(protectedPage) return JSON.stringify({url:safeUrl(location.href),privacy_gate:true,interactive:[],text:''});
                const blockedText=text.replace(/\s+/g,' ').trim();
                const blocked=/^(this page is blocked|access denied|تم حظر هذه الصفحة|الوصول مرفوض)\s*[.!؟]?$/i.test(title)
                  || /^(this page is blocked\b|your organization (doesn['’]t|does not) allow you to (view|visit) this site\b|تم حظر هذه الصفحة)/i.test(blockedText.slice(0,250));
                const items=[];
                const all=[...document.querySelectorAll('a,button,input,textarea,select,[role=button],[onclick],[contenteditable=true],summary,label')];
                for(const e of all){
                  const r=e.getBoundingClientRect();
                  const st=getComputedStyle(e);
                  if(r.width<1||r.height<1||st.visibility==='hidden'||st.display==='none') continue;
                  const sensitive=__hakimSensitive(e);
                  const editable=e.tagName==='INPUT'||e.tagName==='TEXTAREA'||e.getAttribute('contenteditable')==='true';
                  const raw=(editable?'':(e.innerText||''))||e.getAttribute('aria-label')||e.getAttribute('placeholder')||'';
                  items.push({
                    index:items.length,
                    tag:e.tagName,
                    text:sensitive?'[حقل حساس مخفي]':raw.trim().slice(0,180),
                    id:(e.id||'').slice(0,100),
                    name:(e.getAttribute('name')||'').slice(0,100),
                    type:(e.getAttribute('type')||'').slice(0,50),
                    href:safeUrl(e.href||''),
                    placeholder:sensitive?'[مخفي]':(e.getAttribute('placeholder')||'').slice(0,160),
                    aria:sensitive?'[مخفي]':(e.getAttribute('aria-label')||'').slice(0,160),
                    sensitive:sensitive,
                    disabled:!!e.disabled,
                    x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)
                  });
                  if(items.length>=60) break;
                }
                return JSON.stringify({
                  title:title,
                  url:safeUrl(location.href),
                  blocked:blocked,
                  ready:document.readyState,
                  scrollY:Math.round(window.scrollY),
                  innerHeight:Math.round(window.innerHeight),
                  text:text,
                  interactive:items
                });
              } catch(e){ return JSON.stringify({text:'',interactive:[],error:'page_read_failed'}); }
            })();
        """.trimIndent()


    private fun isChatGptAddress(raw: String): Boolean = runCatching {
        val u = Uri.parse(raw)
        u.scheme.equals("https", true) &&
            (u.host.equals("chatgpt.com", true) || u.host.equals("www.chatgpt.com", true))
    }.getOrDefault(false)

    private fun looksLikeChatGptAuth(raw: String): Boolean = runCatching {
        val host = Uri.parse(raw).host.orEmpty().lowercase()
        host == "auth.openai.com" || host.endsWith(".openai.com") ||
            host == "accounts.google.com" || host.endsWith(".google.com")
    }.getOrDefault(false)

    private fun chatGptEvaluate(script: String, timeoutMs: Long = 7_000L): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper())
            return JSONObject().put("ok", false).put("error", "chatgpt_read_on_ui_thread")
        val done = CountDownLatch(1)
        val response = AtomicReference(
            JSONObject().put("ok", false).put("error", "chatgpt_browser_unavailable")
        )
        Handler(Looper.getMainLooper()).post {
            val target = HakimRuntime.visibleWebView() ?: if (::webView.isInitialized) webView else null
            if (target == null) {
                done.countDown()
                return@post
            }
            val address = target.url.orEmpty()
            if (!isChatGptAddress(address)) {
                response.set(
                    JSONObject()
                        .put("ok", false)
                        .put("error", if (looksLikeChatGptAuth(address)) "chatgpt_login_required" else "chatgpt_not_open")
                        .put("scope", "chatgpt.com")
                )
                done.countDown()
                return@post
            }
            runCatching {
                target.evaluateJavascript(script) { raw ->
                    val decoded = decodeJsString(raw)
                    val parsed = runCatching { JSONObject(decoded) }.getOrNull()
                        ?: JSONObject().put("ok", false).put("error", "chatgpt_script_invalid")
                    parsed.put("scope", "chatgpt.com")
                    response.set(parsed)
                    done.countDown()
                }
            }.onFailure {
                response.set(
                    JSONObject().put("ok", false).put("error", "chatgpt_script_failed").put("scope", "chatgpt.com")
                )
                done.countDown()
            }
        }
        return if (runCatching { done.await(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)) response.get()
        else JSONObject().put("ok", false).put("error", "chatgpt_timeout").put("scope", "chatgpt.com")
    }

    private fun navigateChatGpt(url: String): JSONObject {
        if (!isChatGptAddress(url))
            return JSONObject().put("ok", false).put("error", "chatgpt_scope_mismatch").put("scope", "chatgpt.com")
        if (Looper.myLooper() == Looper.getMainLooper())
            return JSONObject().put("ok", false).put("error", "chatgpt_navigation_on_ui_thread").put("scope", "chatgpt.com")
        val done = CountDownLatch(1)
        val response = AtomicReference(
            JSONObject().put("ok", false).put("error", "chatgpt_browser_unavailable").put("scope", "chatgpt.com")
        )
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val target = HakimRuntime.visibleWebView() ?: if (::webView.isInitialized) webView else run {
                if (hasSecurePairing()) {
                    createBrowser()
                    webView
                } else null
            }
            if (target == null) {
                done.countDown()
                return@post
            }
            target.loadUrl(url)
            val check = object : Runnable {
                var attempts = 0
                override fun run() {
                    val current = target.url.orEmpty()
                    when {
                        looksLikeChatGptAuth(current) -> {
                            response.set(
                                JSONObject().put("ok", false).put("error", "chatgpt_login_required").put("scope", "chatgpt.com")
                            )
                            done.countDown()
                        }
                        isChatGptAddress(current) && target.progress == 100 -> {
                            response.set(
                                JSONObject()
                                    .put("ok", true)
                                    .put("url", HakimBrowserPrivacy.safeAddress(current))
                                    .put("scope", "chatgpt.com")
                            )
                            done.countDown()
                        }
                        ++attempts >= 40 -> {
                            response.set(
                                JSONObject().put("ok", false).put("error", "chatgpt_navigation_unverified").put("scope", "chatgpt.com")
                            )
                            done.countDown()
                        }
                        else -> handler.postDelayed(this, 200L)
                    }
                }
            }
            handler.postDelayed(check, 200L)
        }
        return if (runCatching { done.await(9, TimeUnit.SECONDS) }.getOrDefault(false)) response.get()
        else JSONObject().put("ok", false).put("error", "chatgpt_navigation_timeout").put("scope", "chatgpt.com")
    }

    private fun chatGptSidebarReadScript(): String = """
        (function(){
          try{
            if(location.protocol!=='https:' || (location.hostname!=='chatgpt.com' && location.hostname!=='www.chatgpt.com'))
              return JSON.stringify({ok:false,error:'chatgpt_scope_mismatch'});
            if(document.querySelector('input[type=password],input[autocomplete*=one-time-code]'))
              return JSON.stringify({ok:false,error:'chatgpt_login_required'});
            const anchors=[...document.querySelectorAll('a[href]')];
            const seen=new Set(), items=[];
            for(const a of anchors){
              let u; try{u=new URL(a.href,location.href);}catch(_){continue;}
              if((u.hostname!=='chatgpt.com'&&u.hostname!=='www.chatgpt.com') || !/(^|\/)c\/[^/?#]+/.test(u.pathname)) continue;
              const href=u.origin+u.pathname;
              const title=((a.innerText||a.getAttribute('aria-label')||a.getAttribute('title')||'').replace(/\s+/g,' ').trim()).slice(0,180);
              if(!title||seen.has(href)) continue;
              seen.add(href); items.push({title:title,href:href});
              if(items.length>=120) break;
            }
            const candidates=[...document.querySelectorAll('nav,aside,div')].filter(e=>e.scrollHeight>e.clientHeight+20);
            let best=null,bestCount=-1;
            for(const c of candidates){
              const n=[...c.querySelectorAll('a[href]')].filter(a=>{try{return /(^|\/)c\/[^/?#]+/.test(new URL(a.href,location.href).pathname)}catch(_){return false}}).length;
              if(n>bestCount){best=c;bestCount=n;}
            }
            const side=best?{
              can_scroll:best.scrollHeight>best.clientHeight+20,
              top:Math.round(best.scrollTop),
              height:Math.round(best.scrollHeight),
              client:Math.round(best.clientHeight),
              at_bottom:(best.scrollTop+best.clientHeight)>=best.scrollHeight-8
            }:{can_scroll:false,top:0,height:0,client:0,at_bottom:true};
            const body=(document.body&&document.body.innerText||'').slice(0,1200);
            const loginRequired=items.length===0 && /(log in|sign up|تسجيل الدخول|إنشاء حساب)/i.test(body);
            if(loginRequired) return JSON.stringify({ok:false,error:'chatgpt_login_required'});
            return JSON.stringify({ok:true,items:items,sidebar:side,coverage:'visible_sidebar_slice'});
          }catch(_){return JSON.stringify({ok:false,error:'chatgpt_sidebar_read_failed'});}
        })();
    """.trimIndent()

    private fun chatGptSidebarScrollScript(toTop: Boolean): String =
        """
        (function(){
          try{
            if(location.protocol!=='https:' || (location.hostname!=='chatgpt.com' && location.hostname!=='www.chatgpt.com'))
              return JSON.stringify({ok:false,error:'chatgpt_scope_mismatch'});
            const candidates=[...document.querySelectorAll('nav,aside,div')].filter(e=>e.scrollHeight>e.clientHeight+20);
            let best=null,bestCount=-1;
            for(const c of candidates){
              const n=[...c.querySelectorAll('a[href]')].filter(a=>{try{return /(^|\/)c\/[^/?#]+/.test(new URL(a.href,location.href).pathname)}catch(_){return false}}).length;
              if(n>bestCount){best=c;bestCount=n;}
            }
            if(!best) return JSON.stringify({ok:true,moved:false,at_bottom:true});
            const before=best.scrollTop;
            if(__TO_TOP__) best.scrollTop=0;
            else best.scrollTop=Math.min(best.scrollHeight,best.scrollTop+Math.max(300,best.clientHeight*0.82));
            return JSON.stringify({ok:true,moved:Math.abs(best.scrollTop-before)>1,top:Math.round(best.scrollTop),at_bottom:(best.scrollTop+best.clientHeight)>=best.scrollHeight-8});
          }catch(_){return JSON.stringify({ok:false,error:'chatgpt_sidebar_scroll_failed'});}
        })();
        """.trimIndent().replace("__TO_TOP__", if (toTop) "true" else "false")

    private fun chatGptMessagesScript(): String = """
        (function(){
          try{
            if(location.protocol!=='https:' || (location.hostname!=='chatgpt.com' && location.hostname!=='www.chatgpt.com'))
              return JSON.stringify({ok:false,error:'chatgpt_scope_mismatch'});
            if(document.querySelector('input[type=password],input[autocomplete*=one-time-code]'))
              return JSON.stringify({ok:false,error:'chatgpt_login_required'});
            const redact=(s)=>String(s||'').replace(/((?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|otp|كلمة.?المرور|رمز.?التحقق)\s*[:=]\s*)\S+/gi,(_m,p)=>p+'[مخفي]');
            let nodes=[...document.querySelectorAll('[data-message-author-role]')];
            if(nodes.length===0) nodes=[...document.querySelectorAll('article')];
            const messages=[], seen=new Set();
            for(const n of nodes){
              const role=n.getAttribute('data-message-author-role')||'unknown';
              const text=redact((n.innerText||'').replace(/\s+/g,' ').trim()).slice(0,8000);
              if(!text) continue;
              const key=role+'\n'+text;
              if(seen.has(key)) continue;
              seen.add(key); messages.push({role:role,text:text});
              if(messages.length>=100) break;
            }
            return JSON.stringify({
              ok:true,
              title:(document.title||'').slice(0,220),
              url:location.origin+location.pathname,
              messages:messages,
              coverage:'currently_rendered_messages_only'
            });
          }catch(_){return JSON.stringify({ok:false,error:'chatgpt_message_read_failed'});}
        })();
    """.trimIndent()

    private fun chatGptScrollConversationScript(direction: String): String =
        """
        (function(){
          try{
            if(location.protocol!=='https:' || (location.hostname!=='chatgpt.com' && location.hostname!=='www.chatgpt.com'))
              return JSON.stringify({ok:false,error:'chatgpt_scope_mismatch'});
            const msgs=[...document.querySelectorAll('[data-message-author-role],article')];
            let best=null;
            for(const n of msgs){
              let p=n.parentElement;
              while(p&&p!==document.body){
                if(p.scrollHeight>p.clientHeight+30){best=p;break;}
                p=p.parentElement;
              }
              if(best) break;
            }
            const target=best||document.scrollingElement||document.documentElement;
            const before=target.scrollTop;
            const delta=Math.max(350,(target.clientHeight||window.innerHeight)*0.8)*(__UP__?-1:1);
            target.scrollTop=Math.max(0,Math.min(target.scrollHeight,target.scrollTop+delta));
            return JSON.stringify({ok:true,moved:Math.abs(target.scrollTop-before)>1,top:Math.round(target.scrollTop),at_top:target.scrollTop<=4,at_bottom:(target.scrollTop+target.clientHeight)>=target.scrollHeight-8});
          }catch(_){return JSON.stringify({ok:false,error:'chatgpt_conversation_scroll_failed'});}
        })();
        """.trimIndent().replace("__UP__", if (direction.equals("up", true)) "true" else "false")

    private fun chatGptSendScript(message: String): String =
        """
        (function(){
          try{
            if(location.protocol!=='https:' || (location.hostname!=='chatgpt.com' && location.hostname!=='www.chatgpt.com'))
              return JSON.stringify({ok:false,error:'chatgpt_scope_mismatch'});
            if(document.querySelector('input[type=password],input[autocomplete*=one-time-code]'))
              return JSON.stringify({ok:false,error:'chatgpt_login_required'});
            const msg=__MESSAGE__;
            let e=document.querySelector('#prompt-textarea')||document.querySelector('textarea')||document.querySelector('[contenteditable=true]');
            if(!e) return JSON.stringify({ok:false,error:'chatgpt_composer_not_found'});
            e.focus();
            if(e.tagName==='TEXTAREA'||e.tagName==='INPUT'){
              const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
              const setter=Object.getOwnPropertyDescriptor(proto,'value')&&Object.getOwnPropertyDescriptor(proto,'value').set;
              if(setter) setter.call(e,msg); else e.value=msg;
              e.dispatchEvent(new Event('input',{bubbles:true}));
            }else{
              e.textContent='';
              try{document.execCommand('insertText',false,msg);}catch(_){e.textContent=msg;}
              e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:msg}));
            }
            const send=document.querySelector('button[data-testid="send-button"]')||
              [...document.querySelectorAll('button')].find(b=>/(send|إرسال)/i.test((b.getAttribute('aria-label')||b.innerText||'').trim()));
            if(!send||send.disabled) return JSON.stringify({ok:false,error:'chatgpt_send_button_unavailable'});
            send.click();
            return JSON.stringify({ok:true,action:'send_message'});
          }catch(_){return JSON.stringify({ok:false,error:'chatgpt_send_failed'});}
        })();
        """.trimIndent().replace("__MESSAGE__", js(message))

    private fun chatGptStatusScript(): String = """
        (function(){
          const sensitive=!!document.querySelector('input[type=password],input[autocomplete*=one-time-code]');
          const composer=!!document.querySelector('#prompt-textarea,textarea,[contenteditable=true]');
          const chats=[...document.querySelectorAll('a[href]')].some(a=>{try{return /(^|\/)c\/[^/?#]+/.test(new URL(a.href,location.href).pathname)}catch(_){return false}});
          const body=(document.body&&document.body.innerText||'').slice(0,1200);
          const login=sensitive||(!composer&&!chats&&/(log in|sign up|تسجيل الدخول|إنشاء حساب)/i.test(body));
          return JSON.stringify({ok:true,host:location.hostname,logged_in:!login&&(composer||chats),login_required:login});
        })();
    """.trimIndent()

    private fun chatGptReadInternal(payload: JSONObject): JSONObject {
        return when (payload.optString("mode", "status")) {
            "status" -> chatGptEvaluate(chatGptStatusScript())
            "sync_index" -> {
                val maxSteps = payload.optInt("max_steps", 40).coerceIn(1, 80)
                if (payload.optBoolean("reset_to_top", true)) {
                    val top = chatGptEvaluate(chatGptSidebarScrollScript(true))
                    if (!top.optBoolean("ok", false)) return top
                    try { Thread.sleep(250) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
                var steps = 0
                var complete = false
                var stagnant = 0
                var previousCount = -1
                while (steps < maxSteps) {
                    val page = chatGptEvaluate(chatGptSidebarReadScript())
                    if (!page.optBoolean("ok", false)) return page
                    val items = page.optJSONArray("items") ?: JSONArray()
                    val merged = HakimChatGptIndex.merge(applicationContext, items)
                    val count = merged.optInt("count")
                    if (count == previousCount) stagnant++ else stagnant = 0
                    previousCount = count
                    val side = page.optJSONObject("sidebar")
                    if (side == null || side.optBoolean("at_bottom", true) || !side.optBoolean("can_scroll", false)) {
                        complete = true
                        break
                    }
                    val moved = chatGptEvaluate(chatGptSidebarScrollScript(false))
                    if (!moved.optBoolean("ok", false)) return moved
                    if (!moved.optBoolean("moved", false) && stagnant >= 1) {
                        complete = true
                        break
                    }
                    try { Thread.sleep(300) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    steps++
                }
                JSONObject()
                    .put("ok", true)
                    .put("count", HakimChatGptIndex.load(applicationContext).length())
                    .put("complete", complete)
                    .put("steps", steps)
                    .put("coverage", if (complete) "sidebar_scan_reached_bottom" else "partial_sidebar_scan")
                    .put("scope", "chatgpt.com")
            }
            "index" -> JSONObject()
                .put("ok", true)
                .put("items", HakimChatGptIndex.load(applicationContext))
                .put("scope", "chatgpt.com")
            "search_index" -> {
                val q = payload.optString("query").trim()
                if (q.isBlank()) JSONObject().put("ok", false).put("error", "query_required").put("scope", "chatgpt.com")
                else JSONObject()
                    .put("ok", true)
                    .put("matches", HakimChatGptIndex.search(applicationContext, q))
                    .put("scope", "chatgpt.com")
            }
            "current_messages" -> chatGptEvaluate(chatGptMessagesScript())
            else -> JSONObject().put("ok", false).put("error", "unsupported_chatgpt_read_mode").put("scope", "chatgpt.com")
        }
    }

    private fun chatGptNavigateInternal(payload: JSONObject): JSONObject {
        return when (payload.optString("mode")) {
            "open_home" -> navigateChatGpt("https://chatgpt.com/")
            "open_conversation" -> {
                val href = payload.optString("href").trim().ifBlank {
                    HakimChatGptIndex.resolveHref(applicationContext, payload.optString("title").trim()).orEmpty()
                }
                if (href.isBlank())
                    JSONObject().put("ok", false).put("error", "conversation_not_indexed").put("scope", "chatgpt.com")
                else navigateChatGpt(href)
            }
            "scroll_sidebar_down" -> chatGptEvaluate(chatGptSidebarScrollScript(false))
            "scroll_sidebar_top" -> chatGptEvaluate(chatGptSidebarScrollScript(true))
            "scroll_conversation_up" -> chatGptEvaluate(chatGptScrollConversationScript("up"))
            "scroll_conversation_down" -> chatGptEvaluate(chatGptScrollConversationScript("down"))
            else -> JSONObject().put("ok", false).put("error", "unsupported_chatgpt_navigation_mode").put("scope", "chatgpt.com")
        }
    }

    private fun chatGptActionInternal(payload: JSONObject): JSONObject {
        return when (payload.optString("mode")) {
            "new_chat" -> navigateChatGpt("https://chatgpt.com/")
            "send_message" -> {
                val message = payload.optString("message")
                if (message.isBlank() || message.length > 12000)
                    JSONObject().put("ok", false).put("error", "invalid_message").put("scope", "chatgpt.com")
                else chatGptEvaluate(chatGptSendScript(message))
            }
            else -> JSONObject().put("ok", false).put("error", "unsupported_chatgpt_action_mode").put("scope", "chatgpt.com")
        }
    }

    private fun browserSelfTest(): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return JSONObject().put("ok", false).put("error", "field_browser_self_test_on_ui_thread")
        }

        fun probe(html: String): JSONObject {
            val done = CountDownLatch(1)
            val response = AtomicReference(JSONObject().put("error", "probe_not_completed"))
            Handler(Looper.getMainLooper()).post {
                val testView = runCatching { WebView(this) }.getOrNull()
                if (testView == null) {
                    response.set(JSONObject().put("error", "webview_create_failed"))
                    done.countDown()
                    return@post
                }
                testView.settings.javaScriptEnabled = true
                testView.settings.domStorageEnabled = false
                testView.settings.allowFileAccess = false
                testView.settings.allowContentAccess = false
                testView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val target = view ?: return
                        target.evaluateJavascript(snapshotScript()) { raw ->
                            val page = runCatching { JSONObject(decodeJsString(raw)) }.getOrNull()
                            response.set(page ?: JSONObject().put("error", "snapshot_parse_failed"))
                            runCatching { target.destroy() }
                            done.countDown()
                        }
                    }
                }
                runCatching {
                    testView.loadDataWithBaseURL(
                        "https://selftest.hakim.invalid/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }.onFailure {
                    response.set(JSONObject().put("error", "probe_load_failed"))
                    runCatching { testView.destroy() }
                    done.countDown()
                }
            }
            return if (runCatching { done.await(6, TimeUnit.SECONDS) }.getOrDefault(false)) response.get()
            else JSONObject().put("error", "probe_timeout")
        }

        val safe = probe(
            "<html><head><title>اختبار حكيم الآمن</title></head>" +
                "<body><p>نص حكيم آمن للتحقق المحلي</p></body></html>"
        )
        val sensitive = probe(
            "<html><head><title>اختبار خصوصية حكيم</title></head>" +
                "<body><input type='password' value='سر-اختبار-لا-يخرج'/>" +
                "<p>صفحة حساسة</p></body></html>"
        )

        val safeReadable =
            !safe.has("error") &&
                !safe.optBoolean("privacy_gate") &&
                !safe.optBoolean("blocked") &&
                safe.optString("text").contains("نص حكيم آمن")
        val privacyGated =
            !sensitive.has("error") &&
                sensitive.optBoolean("privacy_gate") &&
                !sensitive.toString().contains("سر-اختبار-لا-يخرج")

        return JSONObject()
            .put("ok", safeReadable && privacyGated)
            .put("safe_readable", safeReadable)
            .put("privacy_gate", privacyGated)
            .put("safe_error", safe.optString("error", ""))
            .put("sensitive_error", sensitive.optString("error", ""))
    }

    private fun readBrowser(): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper())
            return JSONObject().put("ok", false).put("error", "browser_read_on_ui_thread")
        val done = CountDownLatch(1)
        val response = AtomicReference(JSONObject().put("ok", false).put("error", "browser_unavailable"))
        Handler(Looper.getMainLooper()).post {
            val target = HakimRuntime.visibleWebView()
                ?: if (::webView.isInitialized) webView
                else if (hasSecurePairing()) {
                    createBrowser()
                    webView
                } else null
            if (target == null) {
                done.countDown()
                return@post
            }
            runCatching {
                target.evaluateJavascript(snapshotScript()) { raw ->
                    val page = runCatching { JSONObject(decodeJsString(raw)) }.getOrNull()
                    val result = when {
                        page == null || page.has("error") -> JSONObject().put("ok", false).put("error", "browser_read_failed")
                        page.optBoolean("privacy_gate") -> JSONObject().put("ok", false).put("error", "local_approval_required")
                        page.optBoolean("blocked") -> JSONObject().put("ok", false).put("error", "browser_access_blocked")
                        else -> JSONObject().put("ok", true).put("page", page)
                    }
                    response.set(result)
                    done.countDown()
                }
            }.onFailure { done.countDown() }
        }
        return if (runCatching { done.await(6, TimeUnit.SECONDS) }.getOrDefault(false)) response.get()
        else JSONObject().put("ok", false).put("error", "browser_read_timeout")
    }

    private fun backBrowser(): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper())
            return JSONObject().put("ok", false).put("error", "browser_back_on_ui_thread")
        val done = CountDownLatch(1)
        val state = AtomicReference("browser_unavailable")
        val previousAddress = AtomicReference("")
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            runCatching {
                val target = HakimRuntime.visibleWebView() ?: if (::webView.isInitialized) webView else null
                when {
                    target == null -> done.countDown()
                    activeBrowserTaskId != null -> {
                        state.set("browser_busy")
                        done.countDown()
                    }
                    !target.canGoBack() -> {
                        state.set("no_browser_history")
                        done.countDown()
                    }
                    else -> {
                        previousAddress.set(HakimBrowserPrivacy.safeAddress(target.url.orEmpty()))
                        val previousIndex = target.copyBackForwardList().currentIndex
                        target.goBack()
                        val check = object : Runnable {
                            var attempts = 0
                            override fun run() {
                                val loaded = runCatching {
                                    target.copyBackForwardList().currentIndex < previousIndex && target.progress == 100
                                }.getOrDefault(false)
                                if (loaded) {
                                    state.set("ready")
                                    done.countDown()
                                } else if (++attempts >= 25) {
                                    state.set("browser_navigation_unverified")
                                    done.countDown()
                                } else handler.postDelayed(this, 200L)
                            }
                        }
                        handler.postDelayed(check, 200L)
                    }
                }
            }.onFailure {
                state.set("browser_navigation_unverified")
                done.countDown()
            }
        }
        if (!runCatching { done.await(6, TimeUnit.SECONDS) }.getOrDefault(false))
            return JSONObject().put("ok", false).put("error", "browser_navigation_unverified")
        if (state.get() != "ready")
            return JSONObject().put("ok", false).put("error", state.get())
        val observed = readBrowser()
        if (!observed.optBoolean("ok", false)) return observed
        val page = observed.optJSONObject("page") ?: return JSONObject().put("ok", false).put("error", "browser_navigation_unverified")
        val currentAddress = page.optString("url")
        if (currentAddress.isBlank() || currentAddress == previousAddress.get())
            return JSONObject().put("ok", false).put("error", "browser_navigation_unverified")
        return JSONObject().put("ok", true).put("page", page)
    }

    private fun sendSnapshot(target: WebView, requestId: String, actionStatus: String) {
        target.evaluateJavascript(snapshotScript()) { raw ->
            val decoded = decodeJsString(raw)
            val page = try { JSONObject(decoded) } catch (_: Exception) { JSONObject().put("raw", decoded) }
            if (page.optBoolean("privacy_gate")) {
                sendResult(JSONObject().put("request_id", requestId).put("status", "gated")
                    .put("message", "تتطلب هذه الصفحة موافقة أو إدخالًا محليًا")
                    .put("page", page))
                return@evaluateJavascript
            }
            if (page.optBoolean("blocked")) {
                sendResult(JSONObject().put("request_id", requestId).put("status", "failed")
                    .put("message", "حُظر الوصول إلى الصفحة"))
                return@evaluateJavascript
            }
            prefs.getString("last_web_error", "")?.takeIf { it.isNotBlank() }?.let { page.put("last_error", it) }
            prefs.getString("last_download_name", "")?.takeIf { it.isNotBlank() }?.let { page.put("last_download", it) }
            sendResult(JSONObject().put("request_id", requestId).put("status", actionStatus).put("page", page))
        }
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return "{}"
        return try { JSONArray("[$raw]").getString(0) } catch (_: Exception) { raw }
    }

    private fun js(value: String): String = JSONObject.quote(value)

    private fun visibleElementsJs(): String = """
        [...document.querySelectorAll('a,button,input,textarea,select,[role=button],[onclick],[contenteditable=true],summary,label')]
        .filter(e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';})
    """.trimIndent()

    private fun tapTextScript(text: String, exact: Boolean): String = """
        (function(){
          ${sensitiveJs()}
          const q=${js(text)}.trim().toLowerCase();
          if(!q) return false;
          const els=${visibleElementsJs()};
          for(const e of els){
            const s=(e.innerText||(__hakimSensitive(e)?'':e.value)||e.getAttribute('aria-label')||e.getAttribute('title')||e.getAttribute('placeholder')||'').trim().toLowerCase();
            if(${if (exact) "s===q" else "s.includes(q)"}){ e.scrollIntoView({block:'center'}); e.click(); return true; }
          }
          return false;
        })();
    """.trimIndent()

    private fun tapIndexScript(index: Int): String = """
        (function(){
          const i=$index;
          const els=${visibleElementsJs()};
          if(i<0||i>=els.length) return false;
          const e=els[i]; e.scrollIntoView({block:'center'}); e.click(); return true;
        })();
    """.trimIndent()

    private fun setTextScript(targetName: String, value: String): String = """
        (function(){
          ${sensitiveJs()}
          const q=${js(targetName)}.trim().toLowerCase();
          let els=[...document.querySelectorAll('input,textarea,[contenteditable=true]')];
          let e=els.find(x=>!q || (x.id||'').toLowerCase().includes(q) || (x.name||'').toLowerCase().includes(q) || (x.getAttribute('placeholder')||'').toLowerCase().includes(q) || (x.getAttribute('aria-label')||'').toLowerCase().includes(q));
          if(!e) e=document.activeElement && document.activeElement.matches('input,textarea,[contenteditable=true]') ? document.activeElement : els[0];
          if(!e||e.disabled||e.readOnly||__hakimSensitive(e)) return false;
          e.focus();
          if(e.isContentEditable) e.innerText=${js(value)}; else e.value=${js(value)};
          e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));
          return true;
        })();
    """.trimIndent()

    private fun setTextIndexScript(index: Int, value: String): String = """
        (function(){
          ${sensitiveJs()}
          const i=$index;
          const els=${visibleElementsJs()};
          if(i<0||i>=els.length) return false;
          const e=els[i];
          if(!e.matches('input,textarea,[contenteditable=true]')||e.disabled||e.readOnly||__hakimSensitive(e)) return false;
          e.focus();
          if(e.isContentEditable) e.innerText=${js(value)}; else e.value=${js(value)};
          e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));
          return true;
        })();
    """.trimIndent()

    private fun scrollScript(direction: String, amount: Int): String {
        val base = if (amount > 0) "${amount.coerceIn(100, 5000)}" else "Math.max(350,window.innerHeight*0.75)"
        val y = if (direction.equals("up", true)) "-($base)" else base
        return "(function(){window.scrollBy({top:$y,behavior:'smooth'});return true;})();"
    }

    private fun pressEnterScript(): String = """
        (function(){
          ${sensitiveJs()}
          const e=document.activeElement;
          if(!e||__hakimSensitive(e)) return false;
          if(e.form && [...e.form.querySelectorAll('input')].some(__hakimSensitive)) return false;
          e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
          if(e.form){ if(e.form.requestSubmit) e.form.requestSubmit(); else e.form.submit(); }
          return true;
        })();
    """.trimIndent()

    private fun sendResult(obj: JSONObject) {
        if (!isPaired()) return
        val topic = prefs.getString("result_topic", "").orEmpty()
        val raw = obj.toString()
        val requestId = obj.optString("request_id", "system")
        val chunkSize = 1700
        val total = ((raw.length + chunkSize - 1) / chunkSize).coerceAtLeast(1)
        val key = authKey()
        for (i in 0 until total) {
            val part = if (raw.isEmpty()) "" else raw.substring(i * chunkSize, minOf(raw.length, (i + 1) * chunkSize))
            val chunk = i + 1
            val wrapper = JSONObject()
                .put("request_id", requestId)
                .put("chunk", chunk)
                .put("total", total)
                .put("data", part)
            if (key.isNotBlank()) {
                wrapper.put("sig", hmacHex(key, "$requestId\n$chunk\n$total\n$part"))
            }
            sendChunk(topic, wrapper.toString(), 0)
        }
    }

    private fun sendChunk(topic: String, body: String, attempt: Int) {
        val req = Request.Builder().url("https://ntfy.sh/$topic")
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt < 2 && ::webView.isInitialized) {
                    webView.postDelayed({ sendChunk(topic, body, attempt + 1) }, 700L * (attempt + 1))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                if (!ok && attempt < 2 && ::webView.isInitialized) {
                    webView.postDelayed({ sendChunk(topic, body, attempt + 1) }, 700L * (attempt + 1))
                }
            }
        })
    }

    private fun hmacHex(keyText: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes(keyText), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun keyBytes(keyText: String): ByteArray {
        val hex = keyText.trim()
        return if (hex.length % 2 == 0 && hex.matches(Regex("^[0-9a-fA-F]+$"))) {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } else {
            hex.toByteArray(StandardCharsets.UTF_8)
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(
            a.lowercase().toByteArray(StandardCharsets.UTF_8),
            b.lowercase().toByteArray(StandardCharsets.UTF_8)
        )
}
