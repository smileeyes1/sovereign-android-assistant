package ps.hakim.phoneagent

import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * تحكم DOM/JavaScript/إحداثيات داخل WebView حكيم بأقل صلاحية.
 * لا يعيد قيم input/textarea/select/contenteditable إلى حكيم، ولا ينفذ JavaScript حرًا من المستخدم أو النموذج.
 * clickText وsetText يحافظان على التوافق مع المنفذات القديمة مع توريث الطبقات الجديدة تلقائيًا.
 */
object HakimWebAutomation {
    private const val PACKAGE = "ps.hakim.stable"
    private val sensitiveRx = Regex("(?i)(password|passcode|otp|pin|cvv|cvc|security.?code|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)")

    fun isUsable(web: WebView?): Boolean {
        val uri = runCatching { Uri.parse(web?.url.orEmpty()) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    /** لقطة بنيوية فقط؛ قيم الحقول القابلة للتحرير لا تُقرأ. */
    fun snapshot(web: WebView, callback: (JSONArray) -> Unit) {
        if (!isUsable(web)) { callback(JSONArray()); return }
        val script = """
            (() => {
              const clean = v => String(v ?? '').replace(/\s+/g, ' ').trim();
              const sensitiveRx = /(password|passcode|otp|pin|cvv|cvc|security.?code|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)/i;
              const visible = el => { const r=el.getBoundingClientRect(); const s=getComputedStyle(el); return r.width>0 && r.height>0 && s.visibility!=='hidden' && s.display!=='none'; };
              const bodyClone = document.body ? document.body.cloneNode(true) : null;
              if (bodyClone) bodyClone.querySelectorAll('input,textarea,select,[contenteditable="true"]').forEach(el => { el.textContent=''; el.removeAttribute('value'); });
              const bodyText = clean(bodyClone?.innerText || '').slice(0,12000);
              const items = [{package:'$PACKAGE', ref:'document', id:'document', tag:'document', role:'document', text:bodyText, desc:clean(location.host).slice(0,180), sensitive:false, clickable:false, editable:false, selected:false, checked:false, scrollable:true, bounds:[0,0,innerWidth,innerHeight]}];
              const selector = 'button,a,input,textarea,select,[role="button"],[role="link"],[role="menuitem"],[role="option"],[role="checkbox"],[role="tab"],[role="dialog"],[contenteditable="true"],summary,label';
              const nodes = Array.from(document.querySelectorAll(selector)).slice(0,220);
              nodes.forEach((el, i) => {
                if (!el.dataset.hakimRef) el.dataset.hakimRef = 'h' + i.toString(36) + '-' + Math.abs((el.outerHTML||'').length).toString(36);
                const tag = String(el.tagName || '').toLowerCase();
                const type = clean(el.getAttribute('type')).toLowerCase();
                const role = clean(el.getAttribute('role')).toLowerCase();
                const aria = clean(el.getAttribute('aria-label'));
                const placeholder = clean(el.getAttribute('placeholder'));
                const name = clean(el.getAttribute('name'));
                const id = clean(el.id || name || aria || placeholder).slice(0,180);
                const title = clean(el.getAttribute('title'));
                const meta = [type,role,aria,placeholder,name,id,title].join(' ');
                const sensitive = type === 'password' || sensitiveRx.test(meta);
                const editable = tag === 'input' || tag === 'textarea' || tag === 'select' || el.getAttribute('contenteditable') === 'true';
                const clickable = tag === 'button' || tag === 'a' || tag === 'summary' || tag === 'label' || ['button','link','menuitem','option','checkbox','tab'].includes(role) || (tag === 'input' && ['button','submit','checkbox','radio'].includes(type));
                const labelText = sensitive ? (aria || placeholder || name || id || type) : clean(el.innerText || aria || placeholder || (clickable ? el.getAttribute('value') : '') || title || name || id);
                const r = el.getBoundingClientRect();
                const style = getComputedStyle(el);
                const scrollable = (el.scrollHeight > el.clientHeight || el.scrollWidth > el.clientWidth) && ['auto','scroll'].includes(style.overflowY || style.overflowX);
                items.push({
                  package:'$PACKAGE', ref:el.dataset.hakimRef, id:id, tag:tag, role:role,
                  text:labelText.slice(0,240), desc:[aria,placeholder,title,name,id,type].filter(Boolean).join(' ').slice(0,280),
                  sensitive:sensitive, clickable:clickable && visible(el), editable:editable && visible(el),
                  selected:Boolean(el.selected || el.getAttribute('aria-selected')==='true'), checked:Boolean(el.checked || el.getAttribute('aria-checked')==='true'),
                  scrollable:scrollable, bounds:[Math.round(r.left),Math.round(r.top),Math.round(r.right),Math.round(r.bottom)]
                });
              });
              return JSON.stringify(items);
            })()
        """.trimIndent()
        web.evaluateJavascript(script) { raw -> callback(runCatching { JSONArray(decodeJsString(raw)) }.getOrDefault(JSONArray())) }
    }

    /** DOM أولًا: مرجع transient من snapshot الحالي. */
    fun clickRef(web: WebView, ref: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || ref.isBlank()) { callback(false); return }
        val refLiteral = JSONObject.quote(ref.take(180))
        evalOk(web, """
            (() => {
              const el=document.querySelector('[data-hakim-ref=' + CSS.escape($refLiteral) + ']');
              if(!el || el.disabled) return 'NO';
              const r=el.getBoundingClientRect(); const s=getComputedStyle(el);
              if(r.width<=0||r.height<=0||s.display==='none'||s.visibility==='hidden') return 'NO';
              el.scrollIntoView({block:'center',inline:'nearest'}); el.click(); return 'OK';
            })()
        """.trimIndent(), callback)
    }

    /**
     * مسار توافق متعدد الطبقات للمنفذات القديمة:
     * DOM-ref -> JavaScript ثابت مأذون -> إحداثيات WebView محلية.
     * إذا فشلت الثلاث يعيد false ليبقى Android/UI المأذون هو fallback الأخير عند المنفذ.
     */
    fun clickText(web: WebView, target: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || target.isBlank()) { callback(false); return }
        resolveRef(web, target, clickableOnly = true) { ref ->
            fun semanticThenCoordinate() {
                semanticClick(web, target) { jsOk ->
                    if (jsOk) callback(true)
                    else coordinateTap(web, target, callback)
                }
            }
            if (ref != null) clickRef(web, ref) { domOk -> if (domOk) callback(true) else semanticThenCoordinate() }
            else semanticThenCoordinate()
        }
    }

    private fun semanticClick(web: WebView, target: String, callback: (Boolean) -> Unit) {
        val targetLiteral = JSONObject.quote(target.take(500))
        evalOk(web, """
            (() => {
              const clean=v=>String(v??'').replace(/\s+/g,' ').trim().toLowerCase(); const wanted=clean($targetLiteral); if(!wanted)return 'NO';
              const visible=el=>{const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';};
              const label=el=>clean(el.innerText||el.getAttribute('aria-label')||el.getAttribute('title')||el.getAttribute('value')||el.textContent);
              const nodes=Array.from(document.querySelectorAll('button,a,[role="button"],[role="link"],[role="menuitem"],[role="option"],[role="checkbox"],[role="tab"],input[type="button"],input[type="submit"],summary,label,[onclick]')).filter(el=>visible(el)&&!el.disabled);
              let hit=nodes.find(el=>label(el)===wanted); if(!hit&&wanted.length>=2)hit=nodes.find(el=>label(el).includes(wanted)); if(!hit)return 'NO';
              hit.scrollIntoView({block:'center',inline:'nearest'}); hit.click(); return 'OK';
            })()
        """.trimIndent(), callback)
    }

    private fun coordinateTap(web: WebView, target: String, callback: (Boolean) -> Unit) {
        elementCenter(web, target) { center ->
            if (center == null) { callback(false); return@elementCenter }
            web.post {
                val scale = web.scale.coerceAtLeast(0.1f)
                val x = center.first * scale
                val y = center.second * scale
                val down = SystemClock.uptimeMillis()
                val d = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
                val u = MotionEvent.obtain(down, down + 70L, MotionEvent.ACTION_UP, x, y, 0)
                val downOk = web.dispatchTouchEvent(d)
                val upOk = web.dispatchTouchEvent(u)
                d.recycle(); u.recycle()
                callback(downOk || upOk)
            }
        }
    }

    fun setTextByRef(web: WebView, ref: String, value: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || ref.isBlank()) { callback(false); return }
        val refLiteral = JSONObject.quote(ref.take(180)); val valueLiteral = JSONObject.quote(value.take(6000))
        evalOk(web, editScript("document.querySelector('[data-hakim-ref=' + CSS.escape($refLiteral) + ']')", valueLiteral), callback)
    }

    /** DOM-ref أولًا ثم JavaScript ثابت؛ يعيد false للـAndroid/UI fallback عند الحاجة. */
    fun setText(web: WebView, target: String, value: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || target.isBlank()) { callback(false); return }
        resolveRef(web, target, editableOnly = true) { ref ->
            fun semantic() = semanticSetText(web, target, value, callback)
            if (ref != null) setTextByRef(web, ref, value) { domOk -> if (domOk) callback(true) else semantic() }
            else semantic()
        }
    }

    private fun semanticSetText(web: WebView, target: String, value: String, callback: (Boolean) -> Unit) {
        val targetLiteral = JSONObject.quote(target.take(500)); val valueLiteral = JSONObject.quote(value.take(6000))
        val script = """
            (() => {
              const clean=v=>String(v??'').replace(/\s+/g,' ').trim().toLowerCase(); const wanted=clean($targetLiteral);
              const forbidden=/(password|passcode|otp|pin|cvv|cvc|security.?code|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)/i;
              const nodes=Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'));
              const descriptor=el=>{const id=el.id||'';const explicit=id?document.querySelector('label[for="'+CSS.escape(id)+'"]')?.innerText||'':'';const parent=el.closest('label')?.innerText||'';return clean([explicit,parent,el.getAttribute('aria-label'),el.getAttribute('placeholder'),el.getAttribute('name'),id,el.getAttribute('title')].filter(Boolean).join(' '));};
              const allowed=el=>clean(el.getAttribute('type'))!=='password'&&!forbidden.test(descriptor(el));
              let hit=nodes.find(el=>allowed(el)&&descriptor(el)===wanted); if(!hit&&wanted.length>=2)hit=nodes.find(el=>allowed(el)&&descriptor(el).includes(wanted)); if(!hit)return 'NO';
              ${editBody("hit", valueLiteral)}
            })()
        """.trimIndent()
        evalOk(web, script, callback)
    }

    fun selectOption(web: WebView, target: String, value: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || target.isBlank() || value.isBlank() || sensitiveRx.containsMatchIn(target)) { callback(false); return }
        val targetLiteral=JSONObject.quote(target.take(500)); val valueLiteral=JSONObject.quote(value.take(500))
        evalOk(web, """
            (() => {
              const clean=v=>String(v??'').replace(/\s+/g,' ').trim().toLowerCase(); const wanted=clean($targetLiteral); const desired=clean($valueLiteral);
              const descriptor=el=>clean([el.getAttribute('aria-label'),el.getAttribute('name'),el.id,el.getAttribute('title'),el.closest('label')?.innerText].filter(Boolean).join(' '));
              const nodes=Array.from(document.querySelectorAll('select'));
              let hit=nodes.find(el=>el.dataset.hakimRef===wanted||descriptor(el)===wanted); if(!hit)hit=nodes.find(el=>descriptor(el).includes(wanted)); if(!hit)return 'NO';
              const option=Array.from(hit.options).find(o=>clean(o.text)===desired||clean(o.value)===desired)||Array.from(hit.options).find(o=>clean(o.text).includes(desired)); if(!option)return 'NO';
              hit.value=option.value; hit.dispatchEvent(new Event('input',{bubbles:true})); hit.dispatchEvent(new Event('change',{bubbles:true})); return 'OK';
            })()
        """.trimIndent(), callback)
    }

    fun scroll(web: WebView, target: String? = null, dx: Int = 0, dy: Int, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || (dx == 0 && dy == 0)) { callback(false); return }
        val targetLiteral=JSONObject.quote(target.orEmpty().take(180))
        evalOk(web, """
            (() => {
              const ref=$targetLiteral; let el=null;
              if(ref) el=document.querySelector('[data-hakim-ref=' + CSS.escape(ref) + ']');
              if(el&&typeof el.scrollBy==='function') el.scrollBy({left:${dx.coerceIn(-5000,5000)},top:${dy.coerceIn(-5000,5000)},behavior:'auto'});
              else window.scrollBy({left:${dx.coerceIn(-5000,5000)},top:${dy.coerceIn(-5000,5000)},behavior:'auto'});
              return 'OK';
            })()
        """.trimIndent(), callback)
    }

    fun elementCenter(web: WebView, target: String, callback: (Pair<Float, Float>?) -> Unit) {
        if (!isUsable(web) || target.isBlank()) { callback(null); return }
        val literal=JSONObject.quote(target.take(500))
        val script="""
            (() => {
              const clean=v=>String(v??'').replace(/\s+/g,' ').trim().toLowerCase(); const wanted=clean($literal);
              const label=el=>clean(el.innerText||el.getAttribute('aria-label')||el.getAttribute('title')||el.getAttribute('placeholder')||el.getAttribute('name')||el.id);
              let el=document.querySelector('[data-hakim-ref=' + CSS.escape($literal) + ']');
              if(!el){const nodes=Array.from(document.querySelectorAll('button,a,input,textarea,select,[role],[contenteditable="true"],summary,label'));el=nodes.find(x=>label(x)===wanted)||nodes.find(x=>wanted.length>=2&&label(x).includes(wanted));}
              if(!el)return ''; const r=el.getBoundingClientRect(); if(r.width<=0||r.height<=0)return ''; return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2});
            })()
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            val s=decodeJsString(raw); val o=runCatching { JSONObject(s) }.getOrNull(); callback(if(o==null)null else o.optDouble("x").toFloat() to o.optDouble("y").toFloat())
        }
    }

    fun verifyText(web: WebView, expected: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || expected.isBlank()) { callback(false); return }
        val literal=JSONObject.quote(expected.take(1000))
        evalOk(web, """
            (()=>{const clean=v=>String(v??'').replace(/\s+/g,' ').trim().toLowerCase();const wanted=clean($literal);const clone=document.body?.cloneNode(true);if(clone)clone.querySelectorAll('input,textarea,select,[contenteditable="true"]').forEach(el=>{el.textContent='';el.removeAttribute('value');});return clean(clone?.innerText||'').includes(wanted)?'OK':'NO';})()
        """.trimIndent(), callback)
    }

    fun visibleText(web: WebView, callback: (String) -> Unit) {
        snapshot(web) { arr -> callback(arr.optJSONObject(0)?.optString("text").orEmpty().take(12000)) }
    }

    fun goBack(web: WebView): Boolean { if (!isUsable(web) || !web.canGoBack()) return false; web.goBack(); return true }

    private fun resolveRef(
        web: WebView,
        target: String,
        clickableOnly: Boolean = false,
        editableOnly: Boolean = false,
        callback: (String?) -> Unit
    ) {
        val wanted = normalize(target)
        snapshot(web) { arr ->
            var partial: String? = null
            for (i in 1 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optBoolean("sensitive", false)) continue
                if (clickableOnly && !o.optBoolean("clickable", false)) continue
                if (editableOnly && !o.optBoolean("editable", false)) continue
                val ref = o.optString("ref")
                val labels = listOf(o.optString("text"), o.optString("desc"), o.optString("id")).map(::normalize)
                if (ref == target || labels.any { it == wanted }) { callback(ref); return@snapshot }
                if (partial == null && wanted.length >= 2 && labels.any { it.contains(wanted) }) partial = ref
            }
            callback(partial)
        }
    }

    private fun editScript(hitExpression: String, valueLiteral: String): String = """
        (()=>{const forbidden=/(password|passcode|otp|pin|cvv|cvc|security.?code|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)/i;const hit=$hitExpression;if(!hit)return 'NO';const meta=[hit.getAttribute('type'),hit.getAttribute('aria-label'),hit.getAttribute('placeholder'),hit.getAttribute('name'),hit.id,hit.getAttribute('title')].filter(Boolean).join(' ');if(String(hit.getAttribute('type')||'').toLowerCase()==='password'||forbidden.test(meta))return 'NO';${editBody("hit", valueLiteral)}})()
    """.trimIndent()

    private fun editBody(hit: String, valueLiteral: String): String = """
        const value=$valueLiteral; $hit.focus();
        if($hit.tagName==='INPUT'||$hit.tagName==='TEXTAREA'){
          const proto=$hit.tagName==='TEXTAREA'?window.HTMLTextAreaElement.prototype:window.HTMLInputElement.prototype;const setter=Object.getOwnPropertyDescriptor(proto,'value')?.set;if(setter)setter.call($hit,value);else $hit.value=value;
          $hit.dispatchEvent(new Event('input',{bubbles:true}));$hit.dispatchEvent(new Event('change',{bubbles:true}));
        }else if($hit.getAttribute('contenteditable')==='true'){$hit.textContent=value;$hit.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:value}));$hit.dispatchEvent(new Event('change',{bubbles:true}));}
        else return 'NO'; return 'OK';
    """.trimIndent()

    private fun evalOk(web: WebView, script: String, callback: (Boolean) -> Unit) {
        web.evaluateJavascript(script) { raw -> callback(decodeJsString(raw) == "OK") }
    }

    private fun decodeJsString(raw: String?): String {
        val value=raw.orEmpty(); if(value=="null"||value.isBlank())return ""
        return runCatching { JSONArray("[$value]").optString(0) }.getOrDefault("")
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("\\s+"), " ").trim()
}