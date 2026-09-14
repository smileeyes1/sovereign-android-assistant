package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** واجهة واحدة للمستخدم؛ اختيار الوكيل اختياري، والوضع الافتراضي تلقائي. */
class HakimAgentsChatActivity : Activity() {
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var agentSpinner: Spinner
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        buildUi()
        appendAssistant("أنا حكيم. اكتب طلبك بطريقتك الطبيعية، وأنا أختار الوكلاء والمسار المناسب تلقائيًا. لا تحتاج لكتابة أوامر تقنية.")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(18, 20, 18, 18)
        }

        root.addView(TextView(this).apply {
            text = "حكيم — محادثة الوكلاء"
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 12)
        })

        val labels = mutableListOf("تلقائي — حكيم يختار")
        labels.addAll(HakimAgentSystem.Agent.values().map { it.title })
        agentSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@HakimAgentsChatActivity, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        root.addView(agentSpinner)

        scroll = ScrollView(this)
        transcript = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.RIGHT
            setPadding(12, 14, 12, 14)
            textDirection = View.TEXT_DIRECTION_RTL
        }
        scroll.addView(transcript)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        input = EditText(this).apply {
            hint = "مثال: افتح الموقع وسجّل البيانات المطلوبة، أو ابحث لي عن الأفضل ثم نفّذ المناسب"
            minLines = 3
            maxLines = 7
            gravity = Gravity.TOP or Gravity.RIGHT
            textSize = 18f
        }
        root.addView(input)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row.addView(button("نفّذ") { submit(true) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("خطّط") { submit(false) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        root.addView(button("فتح متصفح حكيم") { startActivity(Intent(this, MainActivity::class.java)) })
        setContentView(root)
    }

    private fun selectedAgent(): HakimAgentSystem.Agent? {
        val p = agentSpinner.selectedItemPosition
        return if (p <= 0) null else HakimAgentSystem.Agent.values().getOrNull(p - 1)
    }

    private fun submit(execute: Boolean) {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return
        appendUser(raw)
        input.setText("")

        val preferred = selectedAgent()
        val plan = HakimAgentSystem.plan(this, raw, preferred)
        appendAssistant(HakimAgentSystem.summary(this, raw, preferred))

        if (!execute) return
        val local = HakimNaturalActionEngine.execute(this, raw)
        if (local.handled) {
            appendAssistant(local.message)
            return
        }

        if (plan.sensitiveInputDetected) {
            appendAssistant("وجدت في النص ما يبدو سرًا أو اعتمادًا حساسًا. لم أرسله لأي نموذج. احذف السر من الرسالة، وسيستخدم حكيم جلسة الموقع أو مدير اعتماد أندرويد عند الحاجة.")
            return
        }

        sendToReasoningEngine(HakimAgentSystem.agentPrompt(this, raw, preferred))
    }

    private fun sendToReasoningEngine(governedPrompt: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, governedPrompt)
            setPackage("com.openai.chatgpt")
        }
        try {
            startActivity(send)
            appendAssistant("حوّلت المهمة المركبة إلى محرك الذكاء مع نظام الوكلاء ودستور حكيم. ارجع إلى هذه المحادثة في أي وقت لمواصلة التنفيذ.")
        } catch (_: Exception) {
            copy(governedPrompt)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")))
                appendAssistant("نسخت المهمة المحكومة وفتحت محرك الذكاء داخل الويب؛ الصقها إذا لم تظهر تلقائيًا.")
            } catch (_: Exception) {
                appendAssistant("تعذر فتح محرك الذكاء. تم حفظ المهمة في الحافظة حتى لا تضيع.")
            }
        }
    }

    private fun appendUser(text: String) = append("أنت", text)
    private fun appendAssistant(text: String) = append("حكيم", text)

    private fun append(who: String, text: String) {
        transcript.append(if (transcript.text.isEmpty()) "" else "\n\n")
        transcript.append("$who:\n$text")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("حكيم", text))
        Toast.makeText(this, "تم نسخ المهمة المحكومة", Toast.LENGTH_SHORT).show()
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 17f
        setOnClickListener { action() }
    }
}
