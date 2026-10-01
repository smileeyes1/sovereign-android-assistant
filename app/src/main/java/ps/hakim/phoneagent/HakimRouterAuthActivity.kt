package ps.hakim.phoneagent

import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.json.JSONObject

/**
 * بوابة مصادقة وحماية DNS محلية محصورة للراوتر المثبت.
 *
 * R11:
 * - لا تقرأ اسم المستخدم أو كلمة المرور ولا تضيف JavascriptInterface.
 * - لا ترسل Cookies أو DOM أو بيانات اعتماد خارج الهاتف.
 * - لا تسمح بالملاحة خارج https://192.168.1.1.
 * - بعد تسجيل الدخول تستخدم واجهة DHCP الأصلية داخل WebView نفسه.
 * - تحفظ DNS الحالي قبل التغيير، وتتحقق بالقراءة، وتعمل rollback عند الفشل.
 */
class HakimRouterAuthActivity : ComponentActivity() {
    companion object {
        private const val ROUTER_ORIGIN = "https://192.168.1.1"
        private const val ROUTER_HOST = "192.168.1.1"
        private const val MODERN_VIEW_PATH = "/?_type=menuView&_tag=lanMgrIpv4&Menu3Location=0"
        private const val MODERN_VIEW_URL = ROUTER_ORIGIN + MODERN_VIEW_PATH
        private const val FAMILY_DNS_1 = "185.228.168.168"
        private const val FAMILY_DNS_2 = "185.228.169.168"
        private const val MAX_PROBE_RETRIES = 12
    }

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var mode = "AUTH"
    private var probeRetries = 0
    private var navigationIssued = false
    private var actionInFlight = false
    private var baselineDns1 = ""
    private var baselineDns2 = ""
    private var baselineSource = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        status = TextView(this).apply {
            text = "واجهة الراوتر المحلية — بيانات الدخول تبقى على الهاتف فقط."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(16, 14, 16, 14)
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        webView = WebView(this).apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)

        @Suppress("SetJavaScriptEnabled")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return !isRouterUri(uri)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val target = view ?: return
                val uri = runCatching { Uri.parse(url.orEmpty()) }.getOrNull() ?: return
                if (!isRouterUri(uri)) return
                CookieManager.getInstance().flush()
                handler.postDelayed({ handleRouterPage(target) }, 650L)
            }

            override fun onReceivedSslError(view: WebView?, handlerSsl: SslErrorHandler?, error: SslError?) {
                val url = error?.url.orEmpty()
                val uri = runCatching { Uri.parse(url) }.getOrNull()
                if (uri != null && isRouterUri(uri)) {
                    handlerSsl?.proceed()
                } else {
                    handlerSsl?.cancel()
                }
            }
        }

        webView.loadUrl("$ROUTER_ORIGIN/")
    }

    private fun handleRouterPage(target: WebView) {
        if (actionInFlight) return
        target.evaluateJavascript(
            """
            (function(){
              const pw=document.querySelector('input[type="password"]');
              const login=!!pw || !!document.querySelector('#LoginId,[name="fLogin"],#Frm_Password,[name*="password" i]');
              return JSON.stringify({login:login,href:location.href});
            })()
            """.trimIndent()
        ) { raw ->
            val page = decodeObject(raw)
            if (page == null) {
                status.text = "جارٍ التحقق من جلسة الراوتر…"
                return@evaluateJavascript
            }
            if (page.optBoolean("login", false)) {
                mode = "AUTH"
                navigationIssued = false
                status.text = "سجّل الدخول إلى الراوتر هنا؛ بيانات الدخول لا تغادر الهاتف."
                return@evaluateJavascript
            }

            if (!navigationIssued && mode == "AUTH") {
                mode = "PROBE"
                navigationIssued = true
                probeRetries = 0
                status.text = "تم الدخول — جارٍ فتح إعدادات DNS المحلية والتحقق منها…"
                target.loadUrl(MODERN_VIEW_URL)
                return@evaluateJavascript
            }

            when (mode) {
                "PROBE", "VERIFY", "ROLLBACK_VERIFY" -> probeDhcpUi(target)
                else -> Unit
            }
        }
    }

    private fun probeDhcpUi(target: WebView) {
        if (actionInFlight) return
        actionInFlight = true
        val script = """
            (function(){
              try{
                if(document.querySelector('input[type="password"]')) return JSON.stringify({state:'AUTH'});
                const host=(document.querySelector('#IF_URL_HOST')||{}).value||'';
                const btn=document.querySelector('#Btn_apply_DHCPBasicCfg');
                const source0=document.querySelector('#DnsServerSource0');
                const source1=document.querySelector('#DnsServerSource1');
                const hidden1=document.querySelector('#DNSServer1');
                const hidden2=document.querySelector('#DNSServer2');
                const got=document.querySelector('#DataHasBeenGot');
                const seg=(p)=>[0,1,2,3].map(i=>{
                  const e=document.querySelector('#'+p+i);
                  return e?String(e.value||''):'';
                }).join('.');
                const dns1=(hidden1&&hidden1.value)||seg('sub_DNSServer1');
                const dns2=(hidden2&&hidden2.value)||seg('sub_DNSServer2');
                const source=source0&&source0.checked?'0':(source1&&source1.checked?'1':'');
                const marker=!!document.querySelector('#DHCPBasicCfg') &&
                  !!document.querySelector('#template_DHCPBasicCfg') &&
                  !!btn && !!source0 && !!source1;
                if(!marker) return JSON.stringify({state:'WAIT'});
                if(got && got.value==='0') return JSON.stringify({state:'WAIT'});
                return JSON.stringify({
                  state:'READY',
                  host:String(host),
                  dns1:String(dns1),
                  dns2:String(dns2),
                  source:String(source),
                  disabled:!!btn.disabled
                });
              }catch(_){return JSON.stringify({state:'MISS'});}
            })()
        """.trimIndent()

        target.evaluateJavascript(script) { raw ->
            actionInFlight = false
            val data = decodeObject(raw)
            if (data == null) {
                retryProbe(target)
                return@evaluateJavascript
            }
            when (data.optString("state")) {
                "AUTH" -> {
                    mode = "AUTH"
                    navigationIssued = false
                    status.text = "انتهت جلسة الراوتر؛ افتحها محليًا هنا لإكمال الحماية."
                }
                "WAIT" -> retryProbe(target)
                "READY" -> handleReadyDns(target, data)
                else -> {
                    status.text = "لم تثبت واجهة DNS بما يكفي؛ لم يُجر أي تعديل."
                    HakimHealthBeacon.sendAsync(this, "router_webview_dns_not_proven")
                }
            }
        }
    }

    private fun retryProbe(target: WebView) {
        probeRetries += 1
        if (probeRetries >= MAX_PROBE_RETRIES) {
            status.text = "واجهة DHCP لم تكتمل بعد؛ لم يُجر أي تعديل."
            HakimHealthBeacon.sendAsync(this, "router_webview_dns_probe_timeout")
            return
        }
        handler.postDelayed({ probeDhcpUi(target) }, 700L)
    }

    private fun handleReadyDns(target: WebView, data: JSONObject) {
        val host = data.optString("host")
        val dns1 = data.optString("dns1")
        val dns2 = data.optString("dns2")
        val source = data.optString("source")
        val disabled = data.optBoolean("disabled", true)

        if (host != ROUTER_HOST || !isIpv4(dns1) || !isIpv4(dns2) || source !in setOf("0", "1") || disabled) {
            status.text = "لم تثبت صلاحية صفحة DNS للتعديل؛ لم يُجر أي تغيير."
            HakimHealthBeacon.sendAsync(this, "router_webview_dns_gate_blocked")
            return
        }

        when (mode) {
            "PROBE" -> {
                baselineDns1 = dns1
                baselineDns2 = dns2
                baselineSource = source
                if (!HakimNetworkGuardian.recordLocalWebViewBaseline(this, dns1, dns2, source)) {
                    status.text = "تعذر حفظ خط الأساس؛ لم يُجر أي تغيير."
                    return
                }
                if (dns1 == FAMILY_DNS_1 && dns2 == FAMILY_DNS_2 && source == "0") {
                    val result = HakimNetworkGuardian.verifyLocalWebViewDns(this, dns1, dns2, source)
                    if (result.optString("state") == "FAMILY_DNS_CONFIGURED") {
                        completeSuccess()
                    } else {
                        status.text = "DNS مضبوط لكن التحقق من المرشح لم يثبت؛ لم أغيّر شيئًا."
                        HakimHealthBeacon.sendAsync(this, "router_webview_existing_dns_unverified")
                    }
                    return
                }
                status.text = "تم حفظ DNS الحالي — جارٍ تطبيق DNS العائلي فقط…"
                applyDns(target, FAMILY_DNS_1, FAMILY_DNS_2, "0", rollback = false)
            }
            "VERIFY" -> {
                val result = HakimNetworkGuardian.verifyLocalWebViewDns(this, dns1, dns2, source)
                if (result.optString("state") == "FAMILY_DNS_CONFIGURED") {
                    completeSuccess()
                } else {
                    status.text = "لم يثبت التغيير؛ جارٍ إعادة DNS الأصلي تلقائيًا…"
                    rollbackDns(target)
                }
            }
            "ROLLBACK_VERIFY" -> {
                val result = HakimNetworkGuardian.recordLocalWebViewRollback(this, dns1, dns2, source)
                status.text = if (result.optBoolean("web_dns_rollback_verified", false)) {
                    "تم التراجع والتحقق؛ لم تُعتمد حماية غير مثبتة."
                } else {
                    "تعذر إثبات التراجع؛ لم أعتبر الحماية ناجحة."
                }
                HakimHealthBeacon.sendAsync(this, "router_webview_dns_rollback")
            }
        }
    }

    private fun applyDns(target: WebView, dns1: String, dns2: String, source: String, rollback: Boolean) {
        if (!isIpv4(dns1) || !isIpv4(dns2) || source !in setOf("0", "1")) return
        val a = dns1.split(".")
        val b = dns2.split(".")
        val js = """
            (function(){
              try{
                const btn=document.querySelector('#Btn_apply_DHCPBasicCfg');
                const src0=document.querySelector('#DnsServerSource0');
                const src1=document.querySelector('#DnsServerSource1');
                if(!btn||btn.disabled||!src0||!src1) return JSON.stringify({ok:false});
                const setSeg=(p,v)=>{
                  for(let i=0;i<4;i++){
                    const e=document.querySelector('#'+p+i);
                    if(!e) return false;
                    e.value=String(v[i]);
                    e.dispatchEvent(new Event('input',{bubbles:true}));
                    e.dispatchEvent(new Event('change',{bubbles:true}));
                  }
                  return true;
                };
                if(!setSeg('sub_DNSServer1',[${a.joinToString(",")}])) return JSON.stringify({ok:false});
                if(!setSeg('sub_DNSServer2',[${b.joinToString(",")}])) return JSON.stringify({ok:false});
                src0.checked=${source == "0"};
                src1.checked=${source == "1"};
                src0.dispatchEvent(new Event('change',{bubbles:true}));
                src1.dispatchEvent(new Event('change',{bubbles:true}));
                btn.click();
                return JSON.stringify({ok:true});
              }catch(_){return JSON.stringify({ok:false});}
            })()
        """.trimIndent()

        actionInFlight = true
        target.evaluateJavascript(js) { raw ->
            actionInFlight = false
            val result = decodeObject(raw)
            val ok = result?.optBoolean("ok", false) == true
            if (!ok) {
                if (!rollback) {
                    status.text = "لم تبدأ عملية التغيير؛ لم يُمس DNS."
                    HakimHealthBeacon.sendAsync(this, "router_webview_dns_apply_not_started")
                } else {
                    status.text = "تعذر بدء التراجع؛ يلزم تحقق لاحق."
                    HakimNetworkGuardian.recordLocalWebViewRollback(this, "", "", "")
                    HakimHealthBeacon.sendAsync(this, "router_webview_dns_rollback_not_started")
                }
                return@evaluateJavascript
            }

            if (!rollback) HakimNetworkGuardian.markLocalWebViewApplyAttempt(this)
            mode = if (rollback) "ROLLBACK_VERIFY" else "VERIFY"
            probeRetries = 0
            handler.postDelayed({
                status.text = if (rollback) "جارٍ التحقق من استعادة DNS الأصلي…" else "جارٍ التحقق من DNS بعد التطبيق…"
                target.loadUrl(MODERN_VIEW_URL)
            }, 2600L)
        }
    }

    private fun rollbackDns(target: WebView) {
        val baseline = HakimNetworkGuardian.localWebViewBaseline(this)
        val dns1 = baseline.optString("dns1", baselineDns1)
        val dns2 = baseline.optString("dns2", baselineDns2)
        val source = baseline.optString("source", baselineSource)
        if (!baseline.optBoolean("saved", false) || !isIpv4(dns1) || !isIpv4(dns2) || source !in setOf("0", "1")) {
            status.text = "خط الأساس غير صالح؛ لم أنفذ تراجعًا أعمى."
            HakimHealthBeacon.sendAsync(this, "router_webview_dns_rollback_baseline_invalid")
            return
        }
        applyDns(target, dns1, dns2, source, rollback = true)
    }

    private fun completeSuccess() {
        status.text = "تم تفعيل DNS العائلي على الراوتر والتحقق منه."
        CookieManager.getInstance().flush()
        HakimHealthBeacon.sendAsync(this, "router_webview_dns_verified")
        HakimConnectionResilience.recover(this, "router_webview_dns_verified")
        handler.postDelayed({ finish() }, 1200L)
    }

    private fun decodeObject(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val wrapper = JSONObject("{\"v\":$raw}")
            JSONObject(wrapper.getString("v"))
        }.getOrNull()
    }

    private fun isIpv4(value: String): Boolean {
        val parts = value.split(".").mapNotNull { it.toIntOrNull() }
        return parts.size == 4 && parts.all { it in 0..255 }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun isRouterUri(uri: Uri): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host == ROUTER_HOST &&
            uri.port in setOf(-1, 443)
}
