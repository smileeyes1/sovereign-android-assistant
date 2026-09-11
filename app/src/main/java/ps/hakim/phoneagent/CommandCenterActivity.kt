package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class CommandCenterActivity : Activity() {
    companion object {
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }

    private lateinit var command: EditText
    private lateinit var status: TextView
    private var inboundShare: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(18, 18, 18, 18)
        }

        root.addView(TextView(this).apply {
            text = "حكيم — مركز القيادة"
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 12)
        })

        status = TextView(this).apply {
            text = "الأساس الافتراضي: الذكاء ×٧ • التلقائية ×٧ • الفائدة ×٧ • الاكتمال ×٧\nكل توجيه صريح يُلتقط ويُصنف، وترافق القواعد أوامر ChatGPT تلقائيًا."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 14)
        }
        root.addView(status)

        command = EditText(this).apply {
            hint = "اكتب الغاية فقط…"
            minLines = 4
            maxLines = 10
            textSize = 18f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(14, 14, 14, 14)
        }
        root.addView(command, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(actionButton("نفّذ بأفضل مسار") {
            val text = command.text.toString().trim()
            if (text.isBlank() && inboundShare == null) {
                toast("اكتب أمرًا أو شارك محتوى إلى حكيم")
            } else if (looksLikeUrl(text)) {
                openInHakim(text)
            } else {
                sendToChatGPT(text)
            }
        })

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row1.addView(actionButton("إلى شات جي بي تي") { sendToChatGPT(command.text.toString().trim()) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(actionButton("فتح/بحث في حكيم") { openInHakim(command.text.toString().trim()) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row2.addView(actionButton("إلى أي تطبيق") { shareToAny(command.text.toString().trim()) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(actionButton("نسخ") { copyCommand() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row2)

        root.addView(actionButton("متصفح حكيم") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        root.addView(TextView(this).apply {
            text = "السجل المحلي للتوجيهات مشفّر بمفتاح الجهاز. الأحدث الصريح يعلو عند التعارض، والتصحيح يعلو على السابق، والمهمة المؤقتة لا تصبح قاعدة عامة."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 18, 10, 4)
        })

        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 16f
        setOnClickListener { action() }
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_VIEW -> {
                val u = i.data?.toString().orEmpty()
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    openInHakim(u)
                    finish()
                }
            }
            Intent.ACTION_SEND -> {
                inboundShare = Intent(i)
                val text = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    command.setText(text)
                    capture(text, "share_in")
                }
                status.text = "وصل محتوى من تطبيق آخر — قواعد حكيم الافتراضية فعالة تلقائيًا."
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    command.setText(text)
                    capture(text, "process_text")
                }
                status.text = "وصل نص محدد — تم التقاطه محليًا وحكيم جاهز لتوجيهه."
            }
        }
    }

    private fun sendToChatGPT(text: String) {
        capture(text, "chatgpt")
        recordRoute("chatgpt", null)
        val governedText = buildGovernedText(text)
        val out = if (inboundShare != null) Intent(inboundShare) else Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }
        out.action = Intent.ACTION_SEND
        if (out.type.isNullOrBlank()) out.type = "text/plain"
        out.putExtra(Intent.EXTRA_TEXT, governedText)
        out.setPackage(CHATGPT_PACKAGE)
        out.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(out)
            recordRoute("chatgpt", true)
        } catch (_: Exception) {
            copyText(governedText)
            val launch = packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
            if (launch != null) {
                startActivity(launch)
                toast("تم نسخ الأمر المحكوم وفتح شات جي بي تي")
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")))
                    toast("تم نسخ الأمر المحكوم وفتح شات جي بي تي على الويب")
                } catch (_: Exception) {
                    shareToAny(text)
                }
            }
            recordRoute("chatgpt", false)
        }
    }

    private fun shareToAny(text: String) {
        capture(text, "share_out")
        recordRoute("share", null)
        val out = if (inboundShare != null) Intent(inboundShare) else Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }
        out.action = Intent.ACTION_SEND
        if (out.type.isNullOrBlank()) out.type = "text/plain"
        if (text.isNotBlank()) out.putExtra(Intent.EXTRA_TEXT, text)
        out.setPackage(null)
        out.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(out, "اختر التطبيق"))
            recordRoute("share", true)
        } catch (_: Exception) {
            recordRoute("share", false)
            toast("لا يوجد تطبيق مناسب لهذا المحتوى")
        }
    }

    private fun openInHakim(raw: String) {
        capture(raw, "browser")
        recordRoute("browser", null)
        val q = raw.trim()
        if (q.isBlank()) {
            startActivity(Intent(this, MainActivity::class.java))
            recordRoute("browser", true)
            return
        }
        val url = when {
            q.startsWith("https://") || q.startsWith("http://") -> q
            q.contains(".") && !q.contains(" ") -> "https://$q"
            else -> "https://www.google.com/search?q=" + Uri.encode(q)
        }
        getSharedPreferences("hakim", MODE_PRIVATE).edit().putString("last_url", url).apply()
        startActivity(Intent(this, MainActivity::class.java))
        recordRoute("browser", true)
    }

    private fun buildGovernedText(text: String): String {
        val prefix = HakimConstitution.promptPrefix(this)
        return if (text.isBlank()) prefix else "$prefix\n$text"
    }

    private fun capture(text: String, source: String) {
        if (text.isBlank()) return
        HakimRuleLedger.capture(this, text, source)
    }

    private fun recordRoute(route: String, success: Boolean?) {
        if (success == null) HakimLearning.recordAttempt(this, route)
        else HakimLearning.recordResult(this, route, success)
    }

    private fun looksLikeUrl(text: String): Boolean {
        val q = text.trim()
        return q.startsWith("https://") || q.startsWith("http://") || (q.contains(".") && !q.contains(" "))
    }

    private fun copyCommand() {
        val text = command.text.toString().trim()
        if (text.isBlank()) return
        capture(text, "copy")
        copyText(text)
        toast("تم النسخ")
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("حكيم", text))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
