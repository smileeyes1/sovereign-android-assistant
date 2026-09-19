package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * وكيل متصفح سيادي: DOM -> JavaScript ثابت مأذون -> إحداثيات محلية -> Android UI مأذون.
 * لا يتجاوز مصادقة/CAPTCHA/دفع/حقوق موقع، ولا يوسع الصلاحيات ذاتيًا.
 */
object HakimSovereignBrowserAgent {
    const val VERSION = "SOVEREIGN-BROWSER-AGENT-2026-09-16-v1"
    private const val PREFS = "hakim_browser_agent"
    private const val MAX_EVENTS = 40
    private val sensitive = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)")
    private val governedBarrier = Regex("(?i)(captcha|recaptcha|hcaptcha|payment|checkout|pay now|purchase|شراء|دفع|تحقق أنك إنسان|أنا لست روبوت)")

    enum class Layer { DOM, AUTHORIZED_JS, COORDINATE, ANDROID_UI }
    enum class ActionType {
        NAVIGATE, CLICK, TYPE, SELECT, SCROLL, BACK, RELOAD, COPY_VISIBLE_TEXT, PASTE_EXPLICIT, VERIFY_TEXT;
        companion object {
            fun fromDsl(value: String): ActionType = when (value.lowercase()) {
                "navigate" -> NAVIGATE; "click" -> CLICK; "type" -> TYPE; "select" -> SELECT
                "scroll" -> SCROLL; "back" -> BACK; "reload" -> RELOAD; "verify_text" -> VERIFY_TEXT
                else -> VERIFY_TEXT
            }
        }
    }

    data class Action(
        val type: ActionType,
        val target: String = "",
        val value: String = "",
        val url: String = "",
        val dx: Int = 0,
        val dy: Int = 0
    )

    data class Result(
        val success: Boolean,
        val layer: Layer?,
        val evidence: String,
        val retryable: Boolean = false,
        val needsApproval: Boolean = false,
        val needsCredential: Boolean = false
    )

    fun execute(activity: Activity, action: Action, callback: (Result) -> Unit) {
        if (HakimMissionLedger.isCancelled(activity)) { callback(Result(false, null, "المهمة متوقفة بأمر المستخدم")); return }
        val web = HakimRuntime.visibleWebView()
        if (!HakimWebAutomation.isUsable(web) && action.type !in setOf(ActionType.NAVIGATE, ActionType.BACK, ActionType.RELOAD)) {
            callback(Result(false, null, "لا توجد صفحة ويب نشطة قابلة للتحكم", retryable = true)); return
        }
        if (action.type in setOf(ActionType.TYPE, ActionType.PASTE_EXPLICIT, ActionType.SELECT) && sensitive.containsMatchIn(action.target)) {
            callback(Result(false, null, "الحقل حساس؛ يترك الاعتماد لمدير كلمات المرور/الحقل الآمن", needsCredential = true)); return
        }
        if (governedBarrier.containsMatchIn(action.target)) {
            callback(Result(false, null, "وصلت إلى حاجز مصادقة/دفع/تحقق بشري؛ لا يتجاوزه حكيم تلقائيًا", needsApproval = true)); return
        }

        fun gated(snapshot: JSONArray) {
            val authority = HakimAuthorityEnvelope.classifyUiAction("${action.type.name} ${action.target}", snapshot)
            when (authority.gate) {
                HakimAuthorityEnvelope.Gate.BLOCK -> callback(Result(false, null, authority.reason))
                HakimAuthorityEnvelope.Gate.CREDENTIAL -> callback(Result(false, null, authority.reason, needsCredential = true))
                HakimAuthorityEnvelope.Gate.APPROVAL, HakimAuthorityEnvelope.Gate.SYSTEM_PERMISSION -> callback(Result(false, null, authority.reason, needsApproval = true))
                else -> executeGated(activity, web, action, callback)
            }
        }

        if (web != null && HakimWebAutomation.isUsable(web)) HakimWebAutomation.snapshot(web, ::gated) else gated(JSONArray())
    }

    private fun executeGated(activity: Activity, web: WebView?, action: Action, callback: (Result) -> Unit) {
        HakimMissionLedger.progress(activity, HakimMissionLedger.Phase.EXECUTE, "متصفح حكيم: ${humanAction(action)}", attempted = true)
        publish(activity, "RUNNING", humanAction(action), "", "")
        when (action.type) {
            ActionType.NAVIGATE -> navigate(activity, web, action.url.ifBlank { action.target }, callback)
            ActionType.CLICK -> requireWeb(web, callback) { clickLayered(activity, it, action.target, callback) }
            ActionType.TYPE, ActionType.PASTE_EXPLICIT -> requireWeb(web, callback) { typeLayered(activity, it, action.target, action.value, callback) }
            ActionType.SELECT -> requireWeb(web, callback) { selectLayered(activity, it, action.target, action.value, callback) }
            ActionType.SCROLL -> requireWeb(web, callback) { scrollLayered(activity, it, action.target, action.dx, action.dy.ifZero(900), callback) }
            ActionType.BACK -> {
                val ok = web?.let { HakimWebAutomation.goBack(it) } == true
                finish(activity, action, Result(ok, if (ok) Layer.DOM else null, if (ok) "رجع المتصفح إلى الصفحة السابقة" else "لا يوجد سجل رجوع", retryable = false), callback)
            }
            ActionType.RELOAD -> {
                if (web == null) finish(activity, action, Result(false, null, "لا توجد صفحة لإعادة تحميلها", true), callback)
                else { web.reload(); Handler(Looper.getMainLooper()).postDelayed({ finish(activity, action, Result(true, Layer.DOM, "أعيد تحميل الصفحة"), callback) }, 450L) }
            }
            ActionType.COPY_VISIBLE_TEXT -> requireWeb(web, callback) { w ->
                HakimWebAutomation.visibleText(w) { text ->
                    if (text.isBlank()) finish(activity, action, Result(false, Layer.DOM, "لم يظهر نص مرئي للنسخ", true), callback)
                    else {
                        val cm = activity.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("حكيم", text.take(12000)))
                        finish(activity, action, Result(cm != null, Layer.DOM, if (cm != null) "نُسخ النص المرئي محليًا" else "تعذر الوصول للحافظة"), callback)
                    }
                }
            }
            ActionType.VERIFY_TEXT -> requireWeb(web, callback) { w ->
                HakimWebAutomation.verifyText(w, action.target) { ok -> finish(activity, action, Result(ok, Layer.DOM, if (ok) "ظهر النص المتوقع في الصفحة" else "لم يظهر النص المتوقع", retryable = !ok), callback) }
            }
        }
    }

    private fun navigate(activity: Activity, web: WebView?, raw: String, callback: (Result) -> Unit) {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || sensitive.containsMatchIn(uri.query.orEmpty())) {
            finish(activity, Action(ActionType.NAVIGATE, url = raw), Result(false, null, "عنوان التنقل غير صالح أو يحتوي معاملًا حساسًا"), callback); return
        }
        if (!networkAvailable(activity)) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pending_safe_navigation", uri.toString()).apply()
            finish(activity, Action(ActionType.NAVIGATE, url = raw), Result(false, null, "الشبكة غير متاحة؛ حُفظ التنقل الآمن للاستئناف", retryable = true), callback); return
        }
        val w = web ?: HakimRuntime.visibleWebView()
        if (w == null) { callback(Result(false, null, "افتح متصفح حكيم أولًا", retryable = true)); return }
        w.loadUrl(uri.toString())
        Handler(Looper.getMainLooper()).postDelayed({
            val ok = w.url?.let { runCatching { Uri.parse(it).host == uri.host }.getOrDefault(false) } == true
            finish(activity, Action(ActionType.NAVIGATE, url = raw), Result(ok, Layer.DOM, if (ok) "فتح ${uri.host}" else "بدأ التنقل ولم يثبت الوصول بعد", retryable = !ok), callback)
        }, 800L)
    }

    private fun clickLayered(activity: Activity, web: WebView, target: String, callback: (Result) -> Unit) {
        resolveRef(web, target) { ref ->
            fun jsFallback() = HakimWebAutomation.clickText(web, target) { jsOk ->
                if (jsOk) verifyChanged(activity, web, Action(ActionType.CLICK, target), Layer.AUTHORIZED_JS, "نقر دلالي JavaScript", callback)
                else coordinateClick(activity, web, target, callback)
            }
            if (ref != null) HakimWebAutomation.clickRef(web, ref) { ok -> if (ok) verifyChanged(activity, web, Action(ActionType.CLICK, target), Layer.DOM, "نقر DOM مباشر", callback) else jsFallback() }
            else jsFallback()
        }
    }

    private fun typeLayered(activity: Activity, web: WebView, target: String, value: String, callback: (Result) -> Unit) {
        if (value.isBlank()) { finish(activity, Action(ActionType.TYPE, target), Result(false, null, "لا توجد قيمة عابرة للكتابة"), callback); return }
        resolveRef(web, target) { ref ->
            fun jsFallback() = HakimWebAutomation.setText(web, target, value) { jsOk ->
                if (jsOk) finish(activity, Action(ActionType.TYPE, target), Result(true, Layer.AUTHORIZED_JS, "كُتب النص في الحقل غير الحساس دون حفظ قيمته"), callback)
                else androidUiFallback(activity, Action(ActionType.TYPE, target, value), callback)
            }
            if (ref != null) HakimWebAutomation.setTextByRef(web, ref, value) { ok -> if (ok) finish(activity, Action(ActionType.TYPE, target), Result(true, Layer.DOM, "كُتب النص عبر مرجع DOM دون حفظ قيمته"), callback) else jsFallback() }
            else jsFallback()
        }
    }

    private fun selectLayered(activity: Activity, web: WebView, target: String, value: String, callback: (Result) -> Unit) {
        HakimWebAutomation.selectOption(web, target, value) { ok ->
            if (ok) finish(activity, Action(ActionType.SELECT, target), Result(true, Layer.DOM, "تم تحديد الخيار وتوليد input/change"), callback)
            else androidUiFallback(activity, Action(ActionType.SELECT, target, value), callback)
        }
    }

    private fun scrollLayered(activity: Activity, web: WebView, target: String, dx: Int, dy: Int, callback: (Result) -> Unit) {
        HakimWebAutomation.scroll(web, target.takeIf { it.isNotBlank() }, dx, dy) { ok ->
            if (ok) finish(activity, Action(ActionType.SCROLL, target, dx = dx, dy = dy), Result(true, Layer.DOM, "تمرير DOM/النافذة محليًا"), callback)
            else {
                web.post {
                    web.scrollBy(dx, dy)
                    finish(activity, Action(ActionType.SCROLL, target, dx = dx, dy = dy), Result(true, Layer.COORDINATE, "تمرير WebView أصلي كمسار بديل"), callback)
                }
            }
        }
    }

    private fun coordinateClick(activity: Activity, web: WebView, target: String, callback: (Result) -> Unit) {
        HakimWebAutomation.elementCenter(web, target) { center ->
            if (center == null) { androidUiFallback(activity, Action(ActionType.CLICK, target), callback); return@elementCenter }
            web.post {
                val scale = web.scale.coerceAtLeast(0.1f); val x = center.first * scale; val y = center.second * scale
                val down = android.os.SystemClock.uptimeMillis()
                web.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
                web.dispatchTouchEvent(MotionEvent.obtain(down, down + 70L, MotionEvent.ACTION_UP, x, y, 0))
                Handler(Looper.getMainLooper()).postDelayed({ verifyChanged(activity, web, Action(ActionType.CLICK, target), Layer.COORDINATE, "نقر إحداثي محلي على مركز العنصر", callback) }, 350L)
            }
        }
    }

    private fun androidUiFallback(activity: Activity, action: Action, callback: (Result) -> Unit) {
        val service = HakimAccessibilityService.instance
        if (service == null) {
            finish(activity, action, Result(false, null, "انتهت مسارات WebView؛ أتمتة Android غير مفعلة أو غير مأذونة", retryable = true), callback); return
        }
        val ok = when (action.type) {
            ActionType.CLICK -> service.action(JSONObject().put("action", "click_text").put("text", action.target))
            ActionType.TYPE, ActionType.PASTE_EXPLICIT -> service.action(JSONObject().put("action", "set_text").put("text", action.target).put("value", action.value))
            ActionType.SCROLL -> service.action(JSONObject().put("action", "swipe").put("x1", 500).put("y1", 1500).put("x2", 500).put("y2", 600).put("duration", 450))
            else -> false
        }
        finish(activity, action, Result(ok, Layer.ANDROID_UI, if (ok) "نجح مسار Android UI المأذون" else "فشل مسار Android UI المأذون", retryable = !ok), callback)
    }

    private fun resolveRef(web: WebView, target: String, callback: (String?) -> Unit) {
        HakimWebAutomation.snapshot(web) { arr ->
            val wanted = normalize(target)
            var partial: String? = null
            for (i in 1 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ref = o.optString("ref")
                if (ref == target) { callback(ref); return@snapshot }
                val labels = listOf(o.optString("text"), o.optString("desc"), o.optString("id")).map(::normalize)
                if (labels.any { it == wanted }) { callback(ref); return@snapshot }
                if (partial == null && wanted.length >= 2 && labels.any { it.contains(wanted) }) partial = ref
            }
            callback(partial)
        }
    }

    private fun verifyChanged(activity: Activity, web: WebView, action: Action, layer: Layer, evidence: String, callback: (Result) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            HakimWebAutomation.snapshot(web) { after ->
                val ok = after.length() > 0
                finish(activity, action, Result(ok, layer, if (ok) "$evidence؛ أُعيدت قراءة الصفحة بعد الفعل" else "$evidence لكن تعذر التحقق من الصفحة", retryable = !ok), callback)
            }
        }, 250L)
    }

    private fun finish(activity: Activity, action: Action, result: Result, callback: (Result) -> Unit) {
        if (HakimMissionLedger.isCancelled(activity)) { callback(Result(false, result.layer, "المهمة متوقفة بأمر المستخدم")); return }
        val evidence = result.evidence.take(500)
        if (result.success) HakimSovereignEngine.recordExecution(activity, evidence)
        else if (!result.needsApproval && !result.needsCredential) HakimSovereignEngine.recordVerification(activity, false, evidence)
        record(activity, action, result)
        publish(activity, if (result.success) "SUCCESS" else if (result.needsApproval || result.needsCredential) "WAITING" else "FAILED", humanAction(action), result.layer?.name.orEmpty(), evidence)
        callback(result)
    }

    private fun record(context: Context, action: Action, result: Result) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString("events", "[]")) }.getOrDefault(JSONArray())
        arr.put(JSONObject()
            .put("time", System.currentTimeMillis())
            .put("action", action.type.name)
            .put("target", redact(action.target).take(160))
            .put("layer", result.layer?.name ?: "NONE")
            .put("success", result.success)
            .put("evidence", redact(result.evidence).take(320))
            .put("value_persisted", false))
        val compact = JSONArray(); val start = (arr.length() - MAX_EVENTS).coerceAtLeast(0)
        for (i in start until arr.length()) compact.put(arr.optJSONObject(i))
        p.edit().putString("events", compact.toString()).apply()
    }

    private fun publish(context: Context, state: String, action: String, layer: String, evidence: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("live_state", state)
            .putString("live_action", redact(action).take(220))
            .putString("live_layer", layer)
            .putString("live_evidence", redact(evidence).take(320))
            .putLong("live_at", System.currentTimeMillis())
            .apply()
    }

    fun applyNetworkPolicy(context: Context, web: WebView) {
        web.settings.cacheMode = if (networkAvailable(context)) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
    }

    fun resumePendingNavigation(activity: Activity) {
        if (!networkAvailable(activity)) return
        val p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = p.getString("pending_safe_navigation", "").orEmpty()
        if (url.isBlank()) return
        p.edit().remove("pending_safe_navigation").apply()
        execute(activity, Action(ActionType.NAVIGATE, url = url)) { result ->
            if (!result.success && result.retryable) p.edit().putString("pending_safe_navigation", url).apply()
        }
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("version", VERSION)
            .put("control_order", JSONArray(listOf("DOM", "AUTHORIZED_JS", "COORDINATE", "ANDROID_UI_AUTHORIZED")))
            .put("local_first", true).put("free_first", true)
            .put("paid_api_required", false)
            .put("arbitrary_javascript", false)
            .put("captcha_bypass", false).put("authentication_bypass", false).put("payment_bypass", false)
            .put("automatic_permission_expansion", false)
            .put("offline_cache_mode", true).put("safe_navigation_queue", true)
            .put("live_state", p.getString("live_state", "IDLE"))
            .put("live_layer", p.getString("live_layer", ""))
            .put("tool_registry", HakimSovereignToolRegistry.status(context))
            .put("skill_factory", HakimSkillFactory.status(context))
    }

    private fun networkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun requireWeb(web: WebView?, callback: (Result) -> Unit, block: (WebView) -> Unit) {
        if (web == null || !HakimWebAutomation.isUsable(web)) callback(Result(false, null, "لا توجد صفحة ويب نشطة", true)) else block(web)
    }

    private fun humanAction(a: Action): String = when (a.type) {
        ActionType.NAVIGATE -> "فتح صفحة ويب"; ActionType.CLICK -> "النقر على ${redact(a.target)}"; ActionType.TYPE -> "الكتابة في ${redact(a.target)}"
        ActionType.SELECT -> "اختيار ${redact(a.target)}"; ActionType.SCROLL -> "تمرير الصفحة"; ActionType.BACK -> "الرجوع"; ActionType.RELOAD -> "إعادة تحميل"
        ActionType.COPY_VISIBLE_TEXT -> "نسخ النص المرئي"; ActionType.PASTE_EXPLICIT -> "لصق نص مصرح"; ActionType.VERIFY_TEXT -> "التحقق من ظهور ${redact(a.target)}"
    }.take(220)

    private fun normalize(v: String): String = v.lowercase().replace(Regex("\\s+"), " ").trim()
    private fun redact(v: String): String = sensitive.replace(v) { "[سري محجوب]" }
    private fun Int.ifZero(other: Int): Int = if (this == 0) other else this
}