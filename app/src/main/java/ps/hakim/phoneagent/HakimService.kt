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
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import java.util.concurrent.TimeUnit
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
        running = true
        connected = false
        recentRequests.addAll(prefs.getStringSet("seen_request_ids", emptySet()) ?: emptySet())
        trimRecentRequests()
        startAsForeground()
        HakimUnifiedRelay.start(applicationContext)
        HakimLocalPairing.reconnectAsync(applicationContext)
        createBrowser()
        connectRemote()
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
                if (url.startsWith("http") && ::webView.isInitialized) {
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
        if (socket == null && isPaired()) connectRemote()
        return START_STICKY
    }

    override fun onDestroy() {
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
                if (u.startsWith("http")) {
                    prefs.edit().putString("last_url", u).remove("last_web_error").apply()
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
            .putString(taskId + "_query", query.take(4000))
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
                    const title=(document.title||'').slice(0,300);
                    const text=(document.body&&document.body.innerText?document.body.innerText:'')
                      .replace(/\s+/g,' ').trim().slice(0,12000);
                    const links=[...document.querySelectorAll('a[href]')].slice(0,40).map(a=>({
                      text:(a.innerText||a.getAttribute('aria-label')||'').trim().slice(0,180),
                      href:(a.href||'').slice(0,700)
                    }));
                    return JSON.stringify({url:location.href,title:title,text:text,links:links});
                  }catch(e){
                    return JSON.stringify({url:location.href,error:String(e)});
                  }
                })()
            """.trimIndent()
            target.evaluateJavascript(script) { raw ->
                val decoded = decodeJsString(raw)
                val page = try { JSONObject(decoded) } catch (_: Exception) { JSONObject().put("raw", decoded) }
                page.put("captured_at", System.currentTimeMillis())
                browserTaskPrefs.edit()
                    .putString(taskId + "_state", "COMPLETE")
                    .putString(taskId + "_result", page.toString())
                    .putString(taskId + "_url", url)
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
          return /(otp|passcode|pin|cvv|cvc|card.?number|security.?code|رمز.?التحقق|كلمة.?المرور|رقم.?البطاقة)/i.test(all);
        }
    """.trimIndent()

    private fun sendSnapshot(target: WebView, requestId: String, actionStatus: String) {
        val script = """
            (function(){
              try {
                ${sensitiveJs()}
                const items=[];
                const all=[...document.querySelectorAll('a,button,input,textarea,select,[role=button],[onclick],[contenteditable=true],summary,label')];
                for(const e of all){
                  const r=e.getBoundingClientRect();
                  const st=getComputedStyle(e);
                  if(r.width<1||r.height<1||st.visibility==='hidden'||st.display==='none') continue;
                  const sensitive=__hakimSensitive(e);
                  const raw=(e.innerText||e.value||e.getAttribute('aria-label')||e.getAttribute('placeholder')||'').trim();
                  items.push({
                    index:items.length,
                    tag:e.tagName,
                    text:sensitive?'[حقل حساس مخفي]':raw.slice(0,180),
                    id:(e.id||'').slice(0,100),
                    name:(e.getAttribute('name')||'').slice(0,100),
                    type:(e.getAttribute('type')||'').slice(0,50),
                    href:(e.href||'').slice(0,500),
                    placeholder:sensitive?'[مخفي]':(e.getAttribute('placeholder')||'').slice(0,160),
                    aria:sensitive?'[مخفي]':(e.getAttribute('aria-label')||'').slice(0,160),
                    sensitive:sensitive,
                    disabled:!!e.disabled,
                    x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)
                  });
                  if(items.length>=60) break;
                }
                return JSON.stringify({
                  title:document.title,
                  url:location.href,
                  ready:document.readyState,
                  scrollY:Math.round(window.scrollY),
                  innerHeight:Math.round(window.innerHeight),
                  text:(document.body&&document.body.innerText||'').slice(0,7500),
                  interactive:items
                });
              } catch(e){ return JSON.stringify({title:'',url:location.href,text:'',interactive:[],error:String(e)}); }
            })();
        """.trimIndent()
        target.evaluateJavascript(script) { raw ->
            val decoded = decodeJsString(raw)
            val page = try { JSONObject(decoded) } catch (_: Exception) { JSONObject().put("raw", decoded) }
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
