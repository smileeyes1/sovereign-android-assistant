package ps.hakim.phoneagent

import android.net.Uri
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** تنفيذ واجهة الويب داخل WebView حكيم بأقل صلاحية، دون قراءة قيم حقول الإدخال. */
object HakimWebAutomation {
    private const val PACKAGE = "ps.hakim.stable"

    fun isUsable(web: WebView?): Boolean {
        val uri = runCatching { Uri.parse(web?.url.orEmpty()) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    fun snapshot(web: WebView, callback: (JSONArray) -> Unit) {
        if (!isUsable(web)) {
            callback(JSONArray())
            return
        }
        val script = """
            (() => {
              const clean = v => String(v ?? '').replace(/\s+/g, ' ').trim();
              const sensitiveRx = /(password|passcode|otp|pin|cvv|cvc|security.?code|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)/i;
              const items = [];
              items.push({package:'$PACKAGE', id:'document', text:clean(document.body?.innerText || '').slice(0,12000), desc:clean(location.host).slice(0,180), sensitive:false, clickable:false, editable:false});
              const nodes = Array.from(document.querySelectorAll('button,a,input,textarea,select,[role="button"],[contenteditable="true"],summary')).slice(0,140);
              for (const el of nodes) {
                const tag = String(el.tagName || '').toLowerCase();
                const type = clean(el.getAttribute('type')).toLowerCase();
                const role = clean(el.getAttribute('role')).toLowerCase();
                const aria = clean(el.getAttribute('aria-label'));
                const placeholder = clean(el.getAttribute('placeholder'));
                const name = clean(el.getAttribute('name'));
                const id = clean(el.id || name || aria || placeholder).slice(0,180);
                const title = clean(el.getAttribute('title'));
                const meta = [type,aria,placeholder,name,id,title].join(' ');
                const sensitive = type === 'password' || sensitiveRx.test(meta);
                const editable = tag === 'input' || tag === 'textarea' || el.getAttribute('contenteditable') === 'true';
                const clickable = tag === 'button' || tag === 'a' || tag === 'summary' || role === 'button' || (tag === 'input' && (type === 'button' || type === 'submit'));
                const buttonValue = clickable ? clean(el.getAttribute('value')) : '';
                const label = sensitive ? (aria || placeholder || name || id || type) : clean(el.innerText || aria || placeholder || buttonValue || title || name || id);
                items.push({
                  package:'$PACKAGE', id:id, text:label.slice(0,220),
                  desc:[aria,placeholder,title,name,id,type].filter(Boolean).join(' ').slice(0,260),
                  sensitive:sensitive, clickable:clickable, editable:editable
                });
              }
              return JSON.stringify(items);
            })()
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            val decoded = decodeJsString(raw)
            callback(runCatching { JSONArray(decoded) }.getOrDefault(JSONArray()))
        }
    }

    fun clickText(web: WebView, target: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || target.isBlank()) {
            callback(false)
            return
        }
        val targetLiteral = JSONObject.quote(target.take(500))
        val script = """
            (() => {
              const clean = v => String(v ?? '').replace(/\s+/g, ' ').trim().toLowerCase();
              const wanted = clean($targetLiteral);
              if (!wanted) return 'NO';
              const visible = el => { const r=el.getBoundingClientRect(); const s=getComputedStyle(el); return r.width>0 && r.height>0 && s.visibility!=='hidden' && s.display!=='none'; };
              const label = el => clean(el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || el.getAttribute('value') || el.textContent);
              const nodes = Array.from(document.querySelectorAll('button,a,[role="button"],input[type="button"],input[type="submit"],summary,[onclick]')).filter(el => visible(el) && !el.disabled);
              let hit = nodes.find(el => label(el) === wanted);
              if (!hit && wanted.length >= 2) hit = nodes.find(el => label(el).includes(wanted));
              if (!hit) return 'NO';
              hit.scrollIntoView({block:'center',inline:'nearest'});
              hit.click();
              return 'OK';
            })()
        """.trimIndent()
        web.evaluateJavascript(script) { raw -> callback(decodeJsString(raw) == "OK") }
    }

    fun setText(web: WebView, target: String, value: String, callback: (Boolean) -> Unit) {
        if (!isUsable(web) || target.isBlank()) {
            callback(false)
            return
        }
        val targetLiteral = JSONObject.quote(target.take(500))
        val valueLiteral = JSONObject.quote(value.take(6000))
        val script = """
            (() => {
              const clean = v => String(v ?? '').replace(/\s+/g, ' ').trim().toLowerCase();
              const wanted = clean($targetLiteral);
              const value = $valueLiteral;
              const forbidden = /(password|passcode|otp|pin|cvv|cvc|security.?code|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)/i;
              const nodes = Array.from(document.querySelectorAll('input,textarea,[contenteditable="true"]'));
              const descriptor = el => {
                const id = el.id || '';
                const explicit = id ? document.querySelector('label[for="' + CSS.escape(id) + '"]')?.innerText || '' : '';
                const parent = el.closest('label')?.innerText || '';
                return clean([explicit,parent,el.getAttribute('aria-label'),el.getAttribute('placeholder'),el.getAttribute('name'),id,el.getAttribute('title')].filter(Boolean).join(' '));
              };
              const allowed = el => clean(el.getAttribute('type')) !== 'password' && !forbidden.test(descriptor(el));
              let hit = nodes.find(el => allowed(el) && descriptor(el) === wanted);
              if (!hit && wanted.length >= 2) hit = nodes.find(el => allowed(el) && descriptor(el).includes(wanted));
              if (!hit) return 'NO';
              hit.focus();
              if (hit.tagName === 'INPUT' || hit.tagName === 'TEXTAREA') {
                const proto = hit.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(proto,'value')?.set;
                if (setter) setter.call(hit,value); else hit.value=value;
                hit.dispatchEvent(new Event('input',{bubbles:true}));
                hit.dispatchEvent(new Event('change',{bubbles:true}));
              } else {
                hit.textContent=value;
                hit.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:value}));
                hit.dispatchEvent(new Event('change',{bubbles:true}));
              }
              return 'OK';
            })()
        """.trimIndent()
        web.evaluateJavascript(script) { raw -> callback(decodeJsString(raw) == "OK") }
    }

    fun goBack(web: WebView): Boolean {
        if (!isUsable(web) || !web.canGoBack()) return false
        web.goBack()
        return true
    }

    private fun decodeJsString(raw: String?): String {
        val value = raw.orEmpty()
        if (value == "null" || value.isBlank()) return ""
        return runCatching { JSONArray("[$value]").optString(0) }.getOrDefault("")
    }
}
