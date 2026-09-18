package ps.hakim.phoneagent

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** قارئ/بحث قرآن محلي؛ الاسترجاع اللفظي ليس تفسيراً ولا فتوى. */
class HakimQuranActivity : Activity() {
    private lateinit var query: EditText
    private lateinit var status: TextView
    private lateinit var adapter: ResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        ensureCorpusReady()
    }

    private fun buildUi() {
        val p = HakimChatUi.palette(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(p.background)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(TextView(this).apply {
            text = "القرآن المحلي"
            textSize = 24f
            setTextColor(p.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
        })
        root.addView(TextView(this).apply {
            text = "نص محلي متحقق • بلا شبكة • البحث اللفظي ليس تفسيراً ولا حكماً شرعياً"
            textSize = 13f
            setTextColor(p.muted)
            gravity = Gravity.START
            setPadding(0, dp(5), 0, dp(12))
        })
        query = EditText(this).apply {
            hint = "ابحث بكلمة أو اكتب مثل ٨٧:١"
            textSize = 17f
            setTextColor(p.text)
            setHintTextColor(p.muted)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = HakimChatUi.rounded(p.surface, 18f, this@HakimQuranActivity, p.border)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
            }
        }
        root.addView(query)
        root.addView(action("بحث في القرآن كله") { runSearch() })
        status = TextView(this).apply {
            text = "أتحقق من قاعدة القرآن المحلية…"
            textSize = 13f
            setTextColor(p.muted)
            gravity = Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)
        adapter = ResultAdapter()
        root.addView(ListView(this).apply {
            adapter = this@HakimQuranActivity.adapter
            dividerHeight = 0
            setBackgroundColor(p.background)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun ensureCorpusReady() {
        if (HakimVerifiedQuranCorpus.isReady(this)) {
            status.text = "جاهز • ١١٤ سورة • ٦٢٣٦ آية"
            return
        }
        status.text = "أهيئ النص المحلي المتحقق لأول استخدام…"
        Thread {
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            val result = HakimVerifiedQuranCorpus.installBundledMirrorIfNeeded(applicationContext)
            runOnUiThread {
                status.text = if (result.success) "جاهز • ١١٤ سورة • ٦٢٣٦ آية"
                else "تعذر اعتماد النص المحلي بأمان: " + result.message.take(180)
            }
        }.start()
    }

    private fun runSearch() {
        val raw = query.text.toString().trim()
        if (raw.isBlank()) { status.text = "اكتب كلمة بحث أو مرجع آية."; return }
        if (!HakimVerifiedQuranCorpus.isReady(this)) {
            status.text = "قاعدة القرآن المحلية ليست جاهزة بعد."
            ensureCorpusReady()
            return
        }
        status.text = "أبحث محلياً…"
        adapter.replace(emptyList())
        Thread {
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            val direct = parseReference(raw)
            if (direct != null) {
                val a = HakimVerifiedQuranCorpus.ayah(applicationContext, direct.first, direct.second)
                runOnUiThread {
                    if (a == null) status.text = "لم أجد هذا المرجع في النص المحلي المتحقق."
                    else {
                        adapter.replace(listOf(render(a.surahNameAr, a.surah, a.ayah, a.text)))
                        status.text = "مرجع مباشر من النص المحلي المتحقق."
                    }
                }
                return@Thread
            }
            val scan = HakimVerifiedQuranCorpus.fullCorpusScan(applicationContext, raw, 30)
            val rows = scan.candidates.map { render(it.surahNameAr, it.surah, it.ayah, it.text) }
            runOnUiThread {
                adapter.replace(rows)
                status.text = when {
                    !scan.ready -> "قاعدة القرآن المحلية غير جاهزة."
                    !scan.coverageComplete -> "لم يثبت فحص النص كاملاً؛ لم أعتمد النتيجة."
                    rows.isEmpty() -> "فُحصت ٦٢٣٦ آية ولم تظهر مطابقة لفظية. لا أفرض صلة غير مثبتة."
                    else -> "فُحصت ١١٤ سورة و٦٢٣٦ آية • " + eastern(rows.size) + " نتيجة لفظية"
                }
            }
        }.start()
    }

    private fun parseReference(raw: String): Pair<Int, Int>? {
        val western = raw.map {
            when (it) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> it
            }
        }.joinToString("")
        val m = Regex("^\\s*(\\d{1,3})\\s*[:/،,-]\\s*(\\d{1,3})\\s*$").matchEntire(western) ?: return null
        val s = m.groupValues[1].toIntOrNull() ?: return null
        val a = m.groupValues[2].toIntOrNull() ?: return null
        return if (s in 1..114 && a > 0) s to a else null
    }

    private fun render(name: String, surah: Int, ayah: Int, text: String): String =
        "سورة " + name + " • " + eastern(surah) + ":" + eastern(ayah) + "\n" + text

    private fun eastern(value: Int): String = value.toString().map {
        "٠١٢٣٤٥٦٧٨٩"[it.digitToInt()]
    }.joinToString("")

    private fun action(label: String, click: () -> Unit): TextView {
        val p = HakimChatUi.palette(this)
        return TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(p.onAccent)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minHeight = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = HakimChatUi.rounded(p.accent, 20f, this@HakimQuranActivity)
            setOnClickListener { click() }
        }
    }

    private fun dp(v: Int) = HakimChatUi.dp(this, v.toFloat())

    private inner class ResultAdapter : BaseAdapter() {
        private val rows = ArrayList<String>()
        fun replace(next: List<String>) { rows.clear(); rows.addAll(next); notifyDataSetChanged() }
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val p = HakimChatUi.palette(this@HakimQuranActivity)
            val v = (convertView as? TextView) ?: TextView(this@HakimQuranActivity).apply {
                textSize = 18f
                setTextColor(p.text)
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.START
                setLineSpacing(dp(2).toFloat(), 1.22f)
                setTextIsSelectable(true)
                setPadding(dp(14), dp(13), dp(14), dp(13))
            }
            v.text = rows[position]
            v.background = HakimChatUi.rounded(p.surface, 16f, this@HakimQuranActivity, p.border)
            return v
        }
    }
}
