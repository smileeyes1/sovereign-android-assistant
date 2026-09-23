package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CommandCenterActivity : Activity() {
    companion object {
        private const val ATTACHMENT_PICKER_REQUEST = 7301
        private const val SPEECH_REQUEST = 7302
    }

    private lateinit var command: EditText
    private lateinit var status: TextView
    private lateinit var attachmentStatus: TextView
    private lateinit var conversation: TextView
    private lateinit var conversationScroll: ScrollView
    private val attachments = mutableListOf<HakimAttachmentGateway.Attachment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        buildUi()
        loadConversation()
        handleIntent(intent)
        refreshAttachmentStatus()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SPEECH_REQUEST) {
            if (resultCode == RESULT_OK) {
                val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
                if (heard.isNotBlank()) {
                    val existing = command.text.toString().trim()
                    command.setText(listOf(existing, heard).filter { it.isNotBlank() }.joinToString(" "))
                    command.setSelection(command.text.length)
                    status.text = "تم تحويل الصوت إلى نص."
                    appendConversation("حكيم", "تم التقاط الصوت وتحويله إلى نص؛ يمكنك تعديله أو الضغط على «أنجز».")
                }
            }
            return
        }
        if (requestCode == ATTACHMENT_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                val picked = HakimAttachmentGateway.fromResult(this, data)
                val known = attachments.map { it.uri }.toMutableSet()
                picked.filter { known.add(it.uri) }.forEach { attachments += it }
                refreshAttachmentStatus()
                status.text = if (picked.isEmpty()) {
                    "لم يصل مرفق صالح."
                } else {
                    "أضيفت المرفقات محليًا."
                }
                appendConversation(
                    "حكيم",
                    if (picked.isEmpty()) "لم يصل مرفق صالح." else "أضيفت المرفقات محليًا ولن تُرسل إلا عند الحاجة للمهمة."
                )
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 18)
        }

        root.addView(TextView(this).apply {
            text = "حكيم"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 4)
        })

        status = TextView(this).apply {
            text = "جاهز"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 8)
        }
        root.addView(status)

        conversationScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        conversation = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(16, 14, 16, 14)
            minHeight = 160
        }
        conversationScroll.addView(
            conversation,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            conversationScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        command = EditText(this).apply {
            hint = "اكتب رسالتك إلى حكيم"
            minLines = 2
            maxLines = 6
            textSize = 19f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(16, 14, 16, 14)
        }
        root.addView(
            command,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        attachmentStatus = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.RIGHT
            setPadding(8, 5, 8, 4)
        }
        root.addView(attachmentStatus)

        root.addView(actionButton("أنجز") {
            executeBestRoute(command.text.toString().trim())
        })

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        tools.addView(
            actionButton("إرفاق") {
                startActivityForResult(HakimAttachmentGateway.pickerIntent(), ATTACHMENT_PICKER_REQUEST)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tools.addView(
            actionButton("صوت") { startSpeechInput() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tools.addView(
            actionButton("المتصفح") {
                openInHakim(command.text.toString().trim())
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        tools.addView(
            actionButton("إدارة") {
                startActivity(Intent(this, UnifiedHomeActivity::class.java))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(tools)

        setContentView(root)
    }

    private fun executeBestRoute(text: String) {
        if (text.isBlank() && attachments.isEmpty()) {
            toast("اكتب الغاية أو أرفق محتوى")
            return
        }
        capture(text, "best_route")
        appendConversation("أنت", if (text.isBlank()) "مرفقات فقط" else text)
        val decision = HakimModelToolRouter.decide(this, text, attachments)
        status.text = "يجري التنفيذ"

        when (decision.channel) {
            HakimModelToolRouter.Channel.LOCAL_RESPONSE -> {
                val reply = HakimModelToolRouter.localReply(text).orEmpty()
                val visibleReply = reply.ifBlank { "تم تنفيذ المقصد محليًا." }
                appendConversation("حكيم", visibleReply)
                status.text = "تم الرد داخل حكيم"
                command.setText("")
                recordRoute("local_response", true)
            }
            HakimModelToolRouter.Channel.LOCAL_BROWSER -> openInHakim(text)
            HakimModelToolRouter.Channel.PROVIDER_APP -> sendToProviderApp(text, decision)
            HakimModelToolRouter.Channel.SYSTEM_SHARE -> shareToAny(text)
            HakimModelToolRouter.Channel.PROVIDER_WEB -> openProviderWeb(text, decision)
        }
    }

    private fun sendToProviderApp(text: String, decision: HakimModelToolRouter.Decision) {
        val candidates = (listOfNotNull(decision.provider) + decision.fallbacks)
            .distinctBy { it.id }

        for (provider in candidates) {
            recordRoute("provider:" + provider.id, null)
            val out = HakimModelToolRouter.governedShareIntent(
                this,
                text,
                attachments,
                provider.packageName
            )
            try {
                startActivity(out)
                appendConversation("حكيم", "احتاجت هذه المهمة قناة خارجية؛ فتحتها الآن. فتح التطبيق وحده ليس نجاحًا للمهمة.")
                status.text = "بانتظار أثر القناة الخارجية"
                return
            } catch (_: Exception) {
                HakimModelToolRouter.recordOutcome(this, provider.id, false)
                recordRoute("provider:" + provider.id, false)
            }
        }

        appendConversation("حكيم", "تعذرت القنوات المباشرة؛ سأستخدم المشاركة الآمنة كمسار احتياطي.")
        status.text = "مسار احتياطي"
        shareToAny(text)
    }

    private fun openProviderWeb(text: String, decision: HakimModelToolRouter.Decision) {
        val provider = decision.provider ?: run {
            openInHakim(text)
            return
        }
        val governed = HakimIntentEngine.governedPrompt(this, text)
        copyText(governed)
        getSharedPreferences("hakim", MODE_PRIVATE)
            .edit()
            .putString("last_url", provider.webUrl)
            .apply()
        recordRoute("provider_web:" + provider.id, null)
        appendConversation("حكيم", "فتحت القناة الرسمية المختارة. لن أعتبر المهمة ناجحة قبل تحقق الأثر.")
        status.text = "قناة خارجية"
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun shareToAny(text: String) {
        if (text.isBlank() && attachments.isEmpty()) return
        capture(text, "share_out")
        recordRoute("share", null)
        val governed = HakimIntentEngine.governedPrompt(this, text)
        val out = HakimAttachmentGateway.buildShareIntent(this, governed, attachments)
        try {
            startActivity(Intent.createChooser(out, "اختر القناة المتوافقة"))
        } catch (_: Exception) {
            recordRoute("share", false)
            toast("لا توجد قناة متوافقة مع هذا المحتوى")
        }
    }

    private fun openInHakim(raw: String) {
        capture(raw, "browser")
        recordRoute("browser", null)
        val url = HakimModelToolRouter.browserTarget(raw)
        getSharedPreferences("hakim", MODE_PRIVATE).edit().putString("last_url", url).apply()
        appendConversation("حكيم", "فتحت المتصفح للمسار الذي يحتاج الويب.")
        status.text = "المتصفح"
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_VIEW -> {
                val u = i.data?.toString().orEmpty()
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    command.setText(u)
                    status.text = "وصل رابط إلى حكيم."
                }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val text = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    command.setText(text)
                    capture(text, "share_in")
                }
                val incoming = HakimAttachmentGateway.fromInboundShare(this, i)
                val known = attachments.map { it.uri }.toMutableSet()
                incoming.filter { known.add(it.uri) }.forEach { attachments += it }
                refreshAttachmentStatus()
                status.text = "وصل محتوى من تطبيق آخر؛ بقي محليًا حتى اختيار المسار."
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                if (text.isNotBlank()) {
                    command.setText(text)
                    capture(text, "process_text")
                }
            }
        }
    }

    private fun startSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث إلى حكيم")
        }
        try {
            startActivityForResult(intent, SPEECH_REQUEST)
        } catch (_: Exception) {
            status.text = "الإدخال الصوتي غير متاح على هذا الجهاز حاليًا."
        }
    }

    private fun refreshAttachmentStatus() {
        if (::attachmentStatus.isInitialized) {
            attachmentStatus.text = HakimAttachmentGateway.summary(attachments)
        }
    }

    private fun loadConversation() {
        if (!::conversation.isInitialized) return
        val saved = getSharedPreferences("hakim_conversation", MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
        conversation.text = if (saved.isBlank()) {
            "حكيم:\nجاهز. اكتب مقصدك وسأعرض الرد هنا بوضوح."
        } else {
            saved
        }
        scrollConversationToBottom()
    }

    private fun appendConversation(role: String, message: String) {
        if (!::conversation.isInitialized || message.isBlank()) return
        val current = conversation.text.toString().trim()
        val entry = role + ":\n" + message.trim()
        val next = if (current.isBlank()) entry else current + "\n\n" + entry
        val kept = next.takeLast(12_000)
        conversation.text = kept
        getSharedPreferences("hakim_conversation", MODE_PRIVATE)
            .edit()
            .putString("recent", kept)
            .apply()
        scrollConversationToBottom()
    }

    private fun scrollConversationToBottom() {
        if (!::conversationScroll.isInitialized) return
        conversationScroll.post { conversationScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 16f
        setOnClickListener { action() }
    }

    private fun capture(text: String, source: String) {
        if (text.isBlank()) return
        HakimRuleLedger.capture(this, text, source)
    }

    private fun recordRoute(route: String, success: Boolean?) {
        if (success == null) HakimLearning.recordAttempt(this, route)
        else HakimLearning.recordResult(this, route, success)
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("حكيم", text))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
