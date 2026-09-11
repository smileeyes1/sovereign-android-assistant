package ps.hakim.phoneagent

import android.app.Activity
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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var pairField: EditText
    private lateinit var pairButton: Button
    private val prefs by lazy { getSharedPreferences("hakim", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var socket: WebSocket? = null
    private var reconnectDelay = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        configureBrowser()
        refreshPairingUi()
        connectRemote()
        if (savedInstanceState == null) webView.loadUrl("https://www.google.com")
    }

    override fun onDestroy() {
        socket?.cancel()
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
                address.setText(url.orEmpty())
                status.text = if (isPaired()) "الحالة: جاهز — ${view?.title.orEmpty()}" else "الحالة: يحتاج رمز الاقتران"
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "حكيم"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(16, 18, 16, 8)
        }
        root.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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
        status.text = if (isPaired()) "الحالة: تم الاقتران — جارٍ الاتصال" else "الحالة: يحتاج رمز الاقتران"
    }

    private fun savePairing() {
        val parts = pairField.text.toString().trim().split("|")
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            Toast.makeText(this, "رمز الاقتران غير صحيح", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("command_topic", parts[0]).putString("result_topic", parts[1]).apply()
        pairField.setText("")
        refreshPairingUi()
        connectRemote()
        Toast.makeText(this, "تم اقتران حكيم", Toast.LENGTH_SHORT).show()
    }

    private fun connectRemote() {
        socket?.cancel()
        if (!isPaired()) return
        val topic = prefs.getString("command_topic", "").orEmpty()
        val req = Request.Builder().url("wss://ntfy.sh/$topic/ws").build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelay = 1500L
                runOnUiThread { status.text = "الحالة: متصل وجاهز" }
                sendResult(JSONObject().put("request_id", "system").put("status", "online").put("message", "حكيم جاهز"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    if (envelope.optString("event") != "message") return
                    val payload = envelope.optString("message")
                    if (payload.isNotBlank()) runOnUiThread { executeCommand(JSONObject(payload)) }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = reconnectLater()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reconnectLater()
        })
    }

    private fun reconnectLater() {
        runOnUiThread { status.text = "الحالة: إعادة الاتصال…" }
        val delay = reconnectDelay.coerceAtMost(30000L)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
        webView.postDelayed({ connectRemote() }, delay)
    }

    private fun executeCommand(cmd: JSONObject) {
        val requestId = cmd.optString("request_id", UUID.randomUUID().toString())
        when (cmd.optString("type")) {
            "ping", "snapshot" -> sendSnapshot(requestId, "ok")
            "open_url" -> {
                navigate(cmd.optString("url"))
                webView.postDelayed({ sendSnapshot(requestId, "ok") }, cmd.optLong("after_ms", 1200L).coerceIn(300L, 5000L))
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
            sendResult(JSONObject()
                .put("request_id", requestId)
                .put("status", actionStatus)
                .put("page", page))
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
