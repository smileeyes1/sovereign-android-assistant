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
import java.util.UUID
import java.util.concurrent.TimeUnit

class HakimService : Service() {
    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "ps.hakim.phoneagent.STOP"
        private const val CHANNEL_ID = "hakim_background"
        private const val NOTIFICATION_ID = 17
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
    private lateinit var webView: WebView
    private val recentRequests = LinkedHashSet<String>()

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        createBrowser()
        connectRemote()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (socket == null && isPaired()) connectRemote()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        socket?.cancel()
        socket = null
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "حكيم يعمل في الخلفية", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, HakimService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("حكيم")
            .setContentText("جاهز لتنفيذ أوامر المتصفح")
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopPending)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun createBrowser() {
        webView = WebView(applicationContext)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
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
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    prefs.edit().putString("last_web_error", error?.description?.toString().orEmpty()).apply()
                }
            }
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }
        val last = prefs.getString("last_url", "https://www.google.com").orEmpty().ifBlank { "https://www.google.com" }
        webView.loadUrl(last)
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

    private fun connectRemote() {
        socket?.cancel()
        socket = null
        if (!isPaired()) return
        val topic = prefs.getString("command_topic", "").orEmpty()
        val req = Request.Builder().url("wss://ntfy.sh/$topic/ws").build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 1500L
                prefs.edit().putLong("last_connected_at", System.currentTimeMillis()).apply()
                sendResult(JSONObject().put("request_id", "system").put("status", "online").put("message", "حكيم جاهز في الخلفية"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    if (envelope.optString("event") != "message") return
                    val payload = envelope.optString("message")
                    if (payload.isNotBlank()) webView.post { executeCommand(JSONObject(payload)) }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                reconnectLater()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                reconnectLater()
            }
        })
    }

    private fun reconnectLater() {
        val delay = reconnectDelay.coerceAtMost(30000L)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
        if (::webView.isInitialized) webView.postDelayed({ if (isPaired() && socket == null) connectRemote() }, delay)
    }

    @Synchronized
    private fun firstTime(requestId: String): Boolean {
        if (recentRequests.contains(requestId)) return false
        recentRequests.add(requestId)
        while (recentRequests.size > 120) {
            val first = recentRequests.firstOrNull() ?: break
            recentRequests.remove(first)
        }
        return true
    }

    private fun executeCommand(cmd: JSONObject) {
        val requestId = cmd.optString("request_id", UUID.randomUUID().toString()).trim().take(160)
        if (requestId.isBlank()) return
        if (!firstTime(requestId)) {
            sendResult(JSONObject().put("request_id", requestId).put("status", "duplicate").put("message", "تم تجاهل أمر مكرر"))
            return
        }
        when (cmd.optString("type")) {
            "ping", "snapshot" -> sendSnapshot(requestId, "ok")
            "open_url" -> {
                navigate(cmd.optString("url").take(5000))
                webView.postDelayed({ sendSnapshot(requestId, "ok") }, cmd.optLong("after_ms", 1500L).coerceIn(300L, 8000L))
            }
            "back" -> {
                if (webView.canGoBack()) webView.goBack()
                webView.postDelayed({ sendSnapshot(requestId, "ok") }, 500)
            }
            "forward" -> {
                if (webView.canGoForward()) webView.goForward()
                webView.postDelayed({ sendSnapshot(requestId, "ok") }, 500)
            }
            "reload" -> {
                webView.reload()
                webView.postDelayed({ sendSnapshot(requestId, "ok") }, 900)
            }
            "wait" -> webView.postDelayed({ sendSnapshot(requestId, "ok") }, cmd.optLong("ms", 1000L).coerceIn(100L, 8000L))
            "scroll" -> runJsAction(requestId, scrollScript(cmd.optString("direction", "down"), cmd.optInt("amount", 0)))
            "tap_text" -> runJsAction(requestId, tapTextScript(cmd.optString("text").take(500), cmd.optBoolean("exact", false)))
            "tap_index" -> runJsAction(requestId, tapIndexScript(cmd.optInt("index", -1)))
            "set_text" -> runJsAction(requestId, setTextScript(cmd.optString("target").take(300), cmd.optString("value").take(6000)))
            "set_text_index" -> runJsAction(requestId, setTextIndexScript(cmd.optInt("index", -1), cmd.optString("value").take(6000)))
            "press_enter" -> runJsAction(requestId, pressEnterScript())
            else -> sendResult(JSONObject().put("request_id", requestId).put("status", "failed").put("message", "أمر غير معروف"))
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

    private fun runJsAction(requestId: String, script: String) {
        webView.evaluateJavascript(script) { raw ->
            val ok = raw == "true" || raw == "\"true\""
            webView.postDelayed({ sendSnapshot(requestId, if (ok) "ok" else "failed") }, 450)
        }
    }

    private fun sendSnapshot(requestId: String, actionStatus: String) {
        val script = """
            (function(){
              try {
                const items=[];
                const all=[...document.querySelectorAll('a,button,input,textarea,select,[role=button],[onclick],[contenteditable=true],summary,label')];
                for(const e of all){
                  const r=e.getBoundingClientRect();
                  const st=getComputedStyle(e);
                  if(r.width<1||r.height<1||st.visibility==='hidden'||st.display==='none') continue;
                  items.push({
                    index:items.length,
                    tag:e.tagName,
                    text:(e.innerText||e.value||e.getAttribute('aria-label')||e.getAttribute('placeholder')||'').trim().slice(0,180),
                    id:(e.id||'').slice(0,100),
                    name:(e.getAttribute('name')||'').slice(0,100),
                    type:(e.getAttribute('type')||'').slice(0,50),
                    href:(e.href||'').slice(0,500),
                    placeholder:(e.getAttribute('placeholder')||'').slice(0,160),
                    aria:(e.getAttribute('aria-label')||'').slice(0,160),
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
        webView.evaluateJavascript(script) { raw ->
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
          const q=${js(text)}.trim().toLowerCase();
          if(!q) return false;
          const els=${visibleElementsJs()};
          for(const e of els){
            const s=(e.innerText||e.value||e.getAttribute('aria-label')||e.getAttribute('title')||e.getAttribute('placeholder')||'').trim().toLowerCase();
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

    private fun setTextScript(target: String, value: String): String = """
        (function(){
          const q=${js(target)}.trim().toLowerCase();
          let els=[...document.querySelectorAll('input,textarea,[contenteditable=true]')];
          let e=els.find(x=>!q || (x.id||'').toLowerCase().includes(q) || (x.name||'').toLowerCase().includes(q) || (x.getAttribute('placeholder')||'').toLowerCase().includes(q) || (x.getAttribute('aria-label')||'').toLowerCase().includes(q));
          if(!e) e=document.activeElement && document.activeElement.matches('input,textarea,[contenteditable=true]') ? document.activeElement : els[0];
          if(!e||e.disabled||e.readOnly) return false;
          e.focus();
          if(e.isContentEditable) e.innerText=${js(value)}; else e.value=${js(value)};
          e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));
          return true;
        })();
    """.trimIndent()

    private fun setTextIndexScript(index: Int, value: String): String = """
        (function(){
          const i=$index;
          const els=${visibleElementsJs()};
          if(i<0||i>=els.length) return false;
          const e=els[i];
          if(!e.matches('input,textarea,[contenteditable=true]')||e.disabled||e.readOnly) return false;
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
          const e=document.activeElement;
          if(!e) return false;
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
        val chunkSize = 1800
        val total = ((raw.length + chunkSize - 1) / chunkSize).coerceAtLeast(1)
        for (i in 0 until total) {
            val part = if (raw.isEmpty()) "" else raw.substring(i * chunkSize, minOf(raw.length, (i + 1) * chunkSize))
            val body = JSONObject().put("request_id", requestId).put("chunk", i + 1).put("total", total).put("data", part).toString()
            sendChunk(topic, body, 0)
        }
    }

    private fun sendChunk(topic: String, body: String, attempt: Int) {
        val req = Request.Builder().url("https://ntfy.sh/$topic")
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt < 2 && ::webView.isInitialized) webView.postDelayed({ sendChunk(topic, body, attempt + 1) }, 700L * (attempt + 1))
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                if (!ok && attempt < 2 && ::webView.isInitialized) webView.postDelayed({ sendChunk(topic, body, attempt + 1) }, 700L * (attempt + 1))
            }
        })
    }
}
