package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.webkit.CookieManager
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
        private const val CHANNEL_ID = "hakim_background"
        private const val NOTIFICATION_ID = 17
    }

    private val prefs by lazy { getSharedPreferences("hakim", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var socket: WebSocket? = null
    private var reconnectDelay = 1500L
    private lateinit var webView: WebView

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        createBrowser()
        connectRemote()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (socket == null && isPaired()) connectRemote()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        socket?.cancel()
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
        val pending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("حكيم")
            .setContentText("جاهز لتنفيذ أوامر المتصفح")
            .setOngoing(true)
            .setContentIntent(pending)
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
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val u = url.orEmpty()
                if (u.isNotBlank()) prefs.edit().putString("last_url", u).apply()
            }
        }
        val last = prefs.getString("last_url", "https://www.google.com").orEmpty().ifBlank { "https://www.google.com" }
        webView.loadUrl(last)
    }

    private fun isPaired(): Boolean =
        prefs.getString("command_topic", "").orEmpty().isNotBlank() &&
        prefs.getString("result_topic", "").orEmpty().isNotBlank()

    private fun connectRemote() {
        socket?.cancel()
        if (!isPaired()) return
        val topic = prefs.getString("command_topic", "").orEmpty()
        val req = Request.Builder().url("wss://ntfy.sh/$topic/ws").build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 1500L
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

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = reconnectLater()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reconnectLater()
        })
    }

    private fun reconnectLater() {
        val delay = reconnectDelay.coerceAtMost(30000L)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
        if (::webView.isInitialized) webView.postDelayed({ if (isPaired()) connectRemote() }, delay)
    }

    private fun executeCommand(cmd: JSONObject) {
        val requestId = cmd.optString("request_id", UUID.randomUUID().toString())
        when (cmd.optString("type")) {
            "ping", "snapshot" -> sendSnapshot(requestId, "ok")
            "open_url" -> {
                navigate(cmd.optString("url"))
                webView.postDelayed({ sendSnapshot(requestId, "ok") }, cmd.optLong("after_ms", 1500L).coerceIn(300L, 6000L))
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
            "scroll" -> runJsAction(requestId, scrollScript(cmd.optString("direction", "down")))
            "tap_text" -> runJsAction(requestId, tapTextScript(cmd.optString("text"), cmd.optBoolean("exact", false)))
            "set_text" -> runJsAction(requestId, setTextScript(cmd.optString("target"), cmd.optString("value")))
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
            else -> "https://www.google.com/search?q=" + android.net.Uri.encode(q)
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
                const all=[...document.querySelectorAll('a,button,input,textarea,select,[role=button],[onclick]')].slice(0,80);
                for(const e of all){
                  const r=e.getBoundingClientRect();
                  if(r.width<1||r.height<1) continue;
                  items.push({tag:e.tagName,text:(e.innerText||e.value||e.getAttribute('aria-label')||e.getAttribute('placeholder')||'').trim().slice(0,160),id:(e.id||'').slice(0,100),name:(e.getAttribute('name')||'').slice(0,100),type:(e.getAttribute('type')||'').slice(0,50)});
                  if(items.length>=45) break;
                }
                return JSON.stringify({title:document.title,url:location.href,text:(document.body&&document.body.innerText||'').slice(0,7000),interactive:items});
              } catch(e){ return JSON.stringify({title:'',url:location.href,text:'',interactive:[],error:String(e)}); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            val decoded = decodeJsString(raw)
            val page = try { JSONObject(decoded) } catch (_: Exception) { JSONObject().put("raw", decoded) }
            sendResult(JSONObject().put("request_id", requestId).put("status", actionStatus).put("page", page))
        }
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return "{}"
        return try { JSONArray("[$raw]").getString(0) } catch (_: Exception) { raw }
    }

    private fun js(value: String): String = JSONObject.quote(value)

    private fun tapTextScript(text: String, exact: Boolean): String = """
        (function(){
          const q=${js(text)}.trim().toLowerCase();
          const els=[...document.querySelectorAll('a,button,input,[role=button],[onclick],label,summary')];
          for(const e of els){
            const s=(e.innerText||e.value||e.getAttribute('aria-label')||e.getAttribute('title')||'').trim().toLowerCase();
            if(${if (exact) "s===q" else "s.includes(q)"}){ e.scrollIntoView({block:'center'}); e.click(); return true; }
          }
          return false;
        })();
    """.trimIndent()

    private fun setTextScript(target: String, value: String): String = """
        (function(){
          const q=${js(target)}.trim().toLowerCase();
          let els=[...document.querySelectorAll('input,textarea,[contenteditable=true]')];
          let e=els.find(x=>!q || (x.id||'').toLowerCase().includes(q) || (x.name||'').toLowerCase().includes(q) || (x.getAttribute('placeholder')||'').toLowerCase().includes(q) || (x.getAttribute('aria-label')||'').toLowerCase().includes(q));
          if(!e) e=document.activeElement && (document.activeElement.matches('input,textarea,[contenteditable=true]')) ? document.activeElement : els[0];
          if(!e) return false;
          e.focus();
          if(e.isContentEditable) e.innerText=${js(value)}; else e.value=${js(value)};
          e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));
          return true;
        })();
    """.trimIndent()

    private fun scrollScript(direction: String): String {
        val y = if (direction.equals("up", true)) "-Math.max(350,window.innerHeight*0.75)" else "Math.max(350,window.innerHeight*0.75)"
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
            val req = Request.Builder().url("https://ntfy.sh/$topic")
                .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        }
    }
}
