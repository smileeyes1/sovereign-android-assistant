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
        private const val MAX_PROBE_RETRIES = 20
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
                const docs=[];
                const seen=[];
                function addDoc(d,label){
                  if(!d || seen.indexOf(d)>=0) return;
                  seen.push(d); docs.push({d:d,label:label});
                  try{
                    const fs=d.querySelectorAll('iframe,frame');
                    for(let i=0;i<fs.length && docs.length<8;i++){
                      try{
                        const cd=fs[i].contentDocument;
                        if(cd) addDoc(cd,label+'.f'+i);
                      }catch(_){}
                    }
                  }catch(_){}
                }
                addDoc(document,'top');

                function byIdOrName(d,n){
                  return d.getElementById(n) || d.querySelector('[name="'+n+'"]');
                }
                function generic(d,kind){
                  const els=d.querySelectorAll('input,button,select,a');
                  for(let i=0;i<els.length && i<800;i++){
                    const e=els[i];
                    const key=String((e.id||'')+' '+(e.name||'')).toLowerCase();
                    if(kind==='apply' && key.indexOf('apply')>=0 && key.indexOf('dhcp')>=0) return e;
                    if(kind==='source0' && key.indexOf('dnsserversource0')>=0) return e;
                    if(kind==='source1' && key.indexOf('dnsserversource1')>=0) return e;
                    if(kind==='source' && key.indexOf('dnsserversource')>=0 && key.indexOf('0')<0 && key.indexOf('1')<0) return e;
                    if(kind==='dns1' && key==='dnsserver1') return e;
                    if(kind==='dns2' && key==='dnsserver2') return e;
                  }
                  return null;
                }
                function seg(d,p){
                  const vals=[];
                  for(let i=0;i<4;i++){
                    const e=byIdOrName(d,p+i);
                    if(!e) return null;
                    vals.push(String(e.value||''));
                  }
                  return vals.join('.');
                }
                function hasSegments(d,p){
                  for(let i=0;i<4;i++) if(!byIdOrName(d,p+i)) return false;
                  return true;
                }
                function login(d){
                  return !!d.querySelector('input[type="password"],#LoginId,[name="fLogin"],#Frm_Password,[name*="password" i]');
                }
                for(let x=0;x<docs.length;x++){
                  if(login(docs[x].d)) {
                    return JSON.stringify({state:'AUTH',variant:docs[x].label,frames:Math.max(0,docs.length-1)});
                  }
                }

                let globalApply=false,globalDns=false,globalSource=false,globalDhcp=false;
                for(let x=0;x<docs.length;x++){
                  const d=docs[x].d;
                  const btn=byIdOrName(d,'Btn_apply_DHCPBasicCfg') || generic(d,'apply');
                  const src0=byIdOrName(d,'DnsServerSource0') || generic(d,'source0');
                  const src1=byIdOrName(d,'DnsServerSource1') || generic(d,'source1');
                  const srcDirect=byIdOrName(d,'DnsServerSource') || generic(d,'source');
                  const hidden1=byIdOrName(d,'DNSServer1') || generic(d,'dns1');
                  const hidden2=byIdOrName(d,'DNSServer2') || generic(d,'dns2');
                  const seg1=hasSegments(d,'sub_DNSServer1');
                  const seg2=hasSegments(d,'sub_DNSServer2');
                  const dhcpMarker=!!d.querySelector('#DHCPBasicCfg,#template_DHCPBasicCfg,[id*="DHCPBasicCfg"],[name*="DHCPBasicCfg"]') || !!btn;

                  globalApply=globalApply||!!btn;
                  globalDns=globalDns||((!!hidden1||seg1) && (!!hidden2||seg2));
                  globalSource=globalSource||((!!src0&&!!src1)||!!srcDirect);
                  globalDhcp=globalDhcp||dhcpMarker;

                  if(!btn || (!hidden1 && !seg1) || (!hidden2 && !seg2) || ((!src0||!src1) && !srcDirect) || !dhcpMarker) continue;

                  const dns1=(hidden1 && String(hidden1.value||'')) || seg(d,'sub_DNSServer1') || '';
                  const dns2=(hidden2 && String(hidden2.value||'')) || seg(d,'sub_DNSServer2') || '';
                  let source='';
                  if(src0 && src1) source=src0.checked?'0':(src1.checked?'1':'');
                  else if(srcDirect) source=String(srcDirect.value||'');

                  return JSON.stringify({
                    state:'READY',
                    host:String(location.hostname||''),
                    dns1:String(dns1),
                    dns2:String(dns2),
                    source:String(source),
                    disabled:!!btn.disabled,
                    variant:docs[x].label,
                    frames:Math.max(0,docs.length-1),
                    hasApply:true,
                    hasDns:true,
                    hasSource:true,
                    hasDhcp:true
                  });
                }
                return JSON.stringify({
                  state:'WAIT',
                  variant:'none',
                  frames:Math.max(0,docs.length-1),
                  hasApply:globalApply,
                  hasDns:globalDns,
                  hasSource:globalSource,
                  hasDhcp:globalDhcp
                });
              }catch(_){
                return JSON.stringify({state:'MISS',variant:'error',frames:0,hasApply:false,hasDns:false,hasSource:false,hasDhcp:false});
              }
            })()
        """.trimIndent()

        target.evaluateJavascript(script) { raw ->
            actionInFlight = false
            val data = decodeObject(raw)
            if (data == null) {
                HakimNetworkGuardian.markLocalWebViewProbeState(this, "DECODE_MISS")
                retryProbe(target)
                return@evaluateJavascript
            }
            HakimNetworkGuardian.recordLocalWebViewProbe(
                this,
                state = data.optString("state").take(32),
                variant = data.optString("variant").take(32),
                frameCount = data.optInt("frames", 0).coerceIn(0, 8),
                hasApply = data.optBoolean("hasApply", false),
                hasDns = data.optBoolean("hasDns", false),
                hasSource = data.optBoolean("hasSource", false),
                hasDhcp = data.optBoolean("hasDhcp", false)
            )
            when (data.optString("state")) {
                "AUTH" -> {
                    mode = "AUTH"
                    navigationIssued = false
                    status.text = "انتهت جلسة الراوتر؛ افتحها محليًا هنا لإكمال الحماية."
                }
                "WAIT", "MISS" -> retryProbe(target)
                "READY" -> handleReadyDns(target, data)
                else -> retryProbe(target)
            }
        }
    }

    private fun retryProbe(target: WebView) {
        probeRetries += 1
        if (probeRetries >= MAX_PROBE_RETRIES) {
            HakimNetworkGuardian.markLocalWebViewProbeState(this, "TIMEOUT")
            status.text = "واجهة DHCP لم تثبت بعد؛ لم يُجر أي تعديل."
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

        val baselineValuesValid =
            source in setOf("0", "1") &&
            isIpv4OrBlank(dns1) &&
            isIpv4OrBlank(dns2) &&
            (source == "1" || dns1.isNotBlank())

        if (host != ROUTER_HOST || !baselineValuesValid || disabled) {
            HakimNetworkGuardian.markLocalWebViewProbeState(this, "GATE_BLOCKED")
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
                    HakimNetworkGuardian.markLocalWebViewProbeState(this, "BASELINE_REJECTED")
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
        val valueOk = if (rollback) {
            isIpv4OrBlank(dns1) && isIpv4OrBlank(dns2)
        } else {
            isIpv4(dns1) && isIpv4(dns2)
        }
        if (!valueOk || source !in setOf("0", "1")) return

        val a = splitIpv4OrBlank(dns1)
        val b = splitIpv4OrBlank(dns2)
        val js = """
            (function(){
              try{
                const docs=[]; const seen=[];
                function addDoc(d){
                  if(!d || seen.indexOf(d)>=0) return;
                  seen.push(d); docs.push(d);
                  try{
                    const fs=d.querySelectorAll('iframe,frame');
                    for(let i=0;i<fs.length && docs.length<8;i++){
                      try{ if(fs[i].contentDocument) addDoc(fs[i].contentDocument); }catch(_){}
                    }
                  }catch(_){}
                }
                addDoc(document);
                function byIdOrName(d,n){ return d.getElementById(n) || d.querySelector('[name="'+n+'"]'); }
                function generic(d,kind){
                  const els=d.querySelectorAll('input,button,select,a');
                  for(let i=0;i<els.length && i<800;i++){
                    const e=els[i];
                    const key=String((e.id||'')+' '+(e.name||'')).toLowerCase();
                    if(kind==='apply' && key.indexOf('apply')>=0 && key.indexOf('dhcp')>=0) return e;
                    if(kind==='source0' && key.indexOf('dnsserversource0')>=0) return e;
                    if(kind==='source1' && key.indexOf('dnsserversource1')>=0) return e;
                    if(kind==='source' && key.indexOf('dnsserversource')>=0 && key.indexOf('0')<0 && key.indexOf('1')<0) return e;
                    if(kind==='dns1' && key==='dnsserver1') return e;
                    if(kind==='dns2' && key==='dnsserver2') return e;
                  }
                  return null;
                }
                function emit(e){
                  if(!e) return;
                  try{e.dispatchEvent(new Event('input',{bubbles:true}));}catch(_){}
                  try{e.dispatchEvent(new Event('change',{bubbles:true}));}catch(_){}
                }
                function setSegments(d,p,v){
                  let found=true;
                  for(let i=0;i<4;i++){
                    const e=byIdOrName(d,p+i);
                    if(!e){found=false;break;}
                    e.value=String(v[i]||''); emit(e);
                  }
                  return found;
                }
                for(let x=0;x<docs.length;x++){
                  const d=docs[x];
                  const btn=byIdOrName(d,'Btn_apply_DHCPBasicCfg') || generic(d,'apply');
                  const src0=byIdOrName(d,'DnsServerSource0') || generic(d,'source0');
                  const src1=byIdOrName(d,'DnsServerSource1') || generic(d,'source1');
                  const srcDirect=byIdOrName(d,'DnsServerSource') || generic(d,'source');
                  const direct1=byIdOrName(d,'DNSServer1') || generic(d,'dns1');
                  const direct2=byIdOrName(d,'DNSServer2') || generic(d,'dns2');
                  if(!btn || btn.disabled || ((!src0||!src1) && !srcDirect)) continue;

                  let dns1ok=setSegments(d,'sub_DNSServer1',[${a.joinToString(",")}]);
                  let dns2ok=setSegments(d,'sub_DNSServer2',[${b.joinToString(",")}]);
                  if(direct1){ direct1.value='${jsSafe(dns1)}'; emit(direct1); dns1ok=true; }
                  if(direct2){ direct2.value='${jsSafe(dns2)}'; emit(direct2); dns2ok=true; }
                  if(!dns1ok || !dns2ok) continue;

                  if(src0 && src1){
                    src0.checked=${source == "0"};
                    src1.checked=${source == "1"};
                    emit(src0); emit(src1);
                  }else if(srcDirect){
                    srcDirect.value='${jsSafe(source)}'; emit(srcDirect);
                  }else continue;

                  btn.click();
                  return JSON.stringify({ok:true,variant:x===0?'top':'frame'});
                }
                return JSON.stringify({ok:false,variant:'none'});
              }catch(_){return JSON.stringify({ok:false,variant:'error'});}
            })()
        """.trimIndent()

        actionInFlight = true
        target.evaluateJavascript(js) { raw ->
            actionInFlight = false
            val result = decodeObject(raw)
            val ok = result?.optBoolean("ok", false) == true
            if (!ok) {
                HakimNetworkGuardian.markLocalWebViewProbeState(
                    this,
                    if (rollback) "ROLLBACK_NOT_STARTED" else "APPLY_NOT_STARTED"
                )
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
        if (!baseline.optBoolean("saved", false) ||
            !isIpv4OrBlank(dns1) ||
            !isIpv4OrBlank(dns2) ||
            source !in setOf("0", "1")
        ) {
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
        val parts = value.split(".")
        if (parts.size != 4) return false
        return parts.all { p ->
            val n = p.toIntOrNull()
            n != null && n in 0..255
        }
    }

    private fun isIpv4OrBlank(value: String): Boolean = value.isBlank() || isIpv4(value)

    private fun splitIpv4OrBlank(value: String): List<String> =
        if (value.isBlank()) listOf("", "", "", "") else value.split(".")

    private fun jsSafe(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'").take(64)

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
