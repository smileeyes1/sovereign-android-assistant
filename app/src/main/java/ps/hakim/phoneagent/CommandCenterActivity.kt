package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.TextUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat

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
    private lateinit var operations: TextView
    private lateinit var titleView: TextView
    private lateinit var composerArea: LinearLayout
    private lateinit var executeRow: LinearLayout
    private lateinit var toolsRow: LinearLayout
    private var operationsExpanded = false
    @Volatile private var currentDirectEngine: HakimInferenceEngine? = null
    private var streamingBase = ""
    private val streamingBuffer = StringBuilder()
    private val attachments = mutableListOf<HakimAttachmentGateway.Attachment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        buildUi()
        loadConversation()
        refreshOperations()
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
            clipToPadding = false
        }

        titleView = TextView(this).apply {
            text = "حكيم"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 4)
        }
        root.addView(titleView)

        status = TextView(this).apply {
            text = "جاهز"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 8)
        }
        root.addView(status)

        operations = TextView(this).apply {
            text = "جاهز"
            textSize = 13f
            gravity = Gravity.RIGHT
            setPadding(12, 6, 12, 6)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            contentDescription = "حالة التنفيذ؛ اضغط لعرض أو إخفاء التفاصيل"
            setOnClickListener {
                operationsExpanded = !operationsExpanded
                refreshOperations()
            }
        }
        root.addView(operations)

        conversationScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        conversation = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(16, 14, 16, 14)
        }
        conversationScroll.addView(conversation)
        root.addView(
            conversationScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        composerArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        command = EditText(this).apply {
            hint = "اكتب رسالتك إلى حكيم"
            minLines = 1
            maxLines = 4
            textSize = 19f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(16, 14, 16, 14)
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.post {
                        val rect = android.graphics.Rect()
                        v.getDrawingRect(rect)
                        v.requestRectangleOnScreen(rect, true)
                    }
                }
            }
        }
        composerArea.addView(
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
        composerArea.addView(attachmentStatus)

        executeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        executeRow.addView(
            actionButton("أنجز") { executeBestRoute(command.text.toString().trim()) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
        )
        executeRow.addView(
            actionButton("إلغاء") {
                currentDirectEngine?.cancel()
                currentDirectEngine = null
                HakimExecutiveLoop.cancel(this)
                refreshOperations()
                status.text = "أُلغي التنفيذ الجاري"
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        composerArea.addView(executeRow)

        toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        toolsRow.addView(
            actionButton("إرفاق") {
                startActivityForResult(HakimAttachmentGateway.pickerIntent(), ATTACHMENT_PICKER_REQUEST)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        toolsRow.addView(
            actionButton("صوت") { startSpeechInput() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        toolsRow.addView(
            actionButton("المتصفح") {
                openInHakim(command.text.toString().trim())
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        toolsRow.addView(
            actionButton("إدارة") {
                startActivity(Intent(this, UnifiedHomeActivity::class.java))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        composerArea.addView(toolsRow)

        root.addView(
            composerArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val safeBottom = maxOf(bars.bottom, if (imeVisible) ime.bottom else 0)

            root.setPadding(
                24 + bars.left,
                12 + bars.top,
                24 + bars.right,
                0
            )
            composerArea.setPadding(0, 0, 0, safeBottom)

            titleView.visibility = if (imeVisible) View.GONE else View.VISIBLE
            status.visibility = if (imeVisible) View.GONE else View.VISIBLE
            toolsRow.visibility = if (imeVisible) View.GONE else View.VISIBLE

            if (imeVisible && operationsExpanded) {
                operationsExpanded = false
                refreshOperations()
            }
            if (imeVisible) {
                command.post {
                    val rect = android.graphics.Rect()
                    command.getDrawingRect(rect)
                    command.requestRectangleOnScreen(rect, true)
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun executeBestRoute(text: String) {
        if (text.isBlank() && attachments.isEmpty()) {
            toast("اكتب الغاية أو أرفق محتوى")
            return
        }
        capture(text, "best_route")
        appendConversation("أنت", if (text.isBlank()) "مرفقات فقط" else text)
        val directed = HakimIntentDirector.build(this, text, attachments.size)
        HakimExecutiveLoop.start(this, text, directed.acceptance)
        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.PLANNING, "صياغة أمر تنفيذي أعلى للمحرك وفق المقصد ومعيار الاكتمال")
        val decision = HakimModelToolRouter.decide(this, text, attachments)
        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.ROUTING, decision.reason)
        refreshOperations()
        status.text = "يجري التنفيذ"

        when (decision.channel) {
            HakimModelToolRouter.Channel.LOCAL_RESPONSE -> {
                HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.EXECUTING, "تنفيذ محلي دون إرسال بيانات")
                refreshOperations()
                val reply = HakimModelToolRouter.localReply(text).orEmpty()
                val visibleReply = reply.ifBlank { "تم تنفيذ المقصد محليًا." }
                appendConversation("حكيم", visibleReply)
                HakimExecutiveLoop.complete(this, "الرد ظاهر داخل سجل محادثة حكيم")
                refreshOperations()
                status.text = "اكتمل"
                command.setText("")
                recordRoute("local_response", true)
            }
            HakimModelToolRouter.Channel.DIRECT_MODEL ->
                executeDirectModel(text, directed.instruction, decision.engineId)
            HakimModelToolRouter.Channel.LOCAL_BROWSER -> openInHakim(text)
            HakimModelToolRouter.Channel.PROVIDER_APP -> sendToProviderApp(text, decision)
            HakimModelToolRouter.Channel.SYSTEM_SHARE -> shareToAny(text)
            HakimModelToolRouter.Channel.PROVIDER_WEB -> openProviderWeb(text, decision)
        }
    }

    private fun executeDirectModel(text: String, instruction: String, engineId: String?) {
        val snapshot = attachments.toList()
        val primary = HakimEngineRegistry.directEngines(this)
            .firstOrNull { it.id == engineId }
            ?: run {
                appendConversation("حكيم", "المحرك المباشر المحدد لم يعد متاحًا. افتح «إدارة» لإعداده أو جرّب لاحقًا.")
                HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, "المحرك المباشر غير متاح وقت التنفيذ")
                refreshOperations()
                status.text = "يلزم إعداد المحرك المباشر"
                return
            }

        val candidates = listOf(primary) +
            HakimEngineRegistry.fallbackGeneralChat(this, primary.id, snapshot)

        beginStreamingReply()
        Thread {
            var finalResult: HakimInferenceEngine.Result? = null
            var finalEngine: HakimInferenceEngine? = null

            for ((index, engine) in candidates.withIndex()) {
                currentDirectEngine = engine
                val hadDelta = java.util.concurrent.atomic.AtomicBoolean(false)

                runOnUiThread {
                    HakimExecutiveLoop.record(
                        this,
                        HakimExecutiveLoop.Phase.EXECUTING,
                        if (index == 0) {
                            "إجابة مباشرة داخل حكيم عبر " + engine.displayName
                        } else {
                            "تحويل تلقائي إلى " + engine.displayName + " بعد تعذر المحرك السابق"
                        }
                    )
                    refreshOperations()
                    status.text = "يجيب " + engine.displayName
                }

                val result = engine.complete(instruction, snapshot) { delta ->
                    hadDelta.set(true)
                    runOnUiThread {
                        if (currentDirectEngine === engine) appendStreamingDelta(delta)
                    }
                }

                if (currentDirectEngine !== engine) return@Thread

                if (result is HakimInferenceEngine.Result.Failure &&
                    result.retryable &&
                    !hadDelta.get() &&
                    index < candidates.lastIndex
                ) {
                    recordRoute("direct:" + engine.id, false)
                    HakimExecutiveLoop.advanceCycle(
                        this,
                        "فشل " + engine.displayName + " دون إخراج؛ يجرب حكيم محركًا مجانيًا بديلًا"
                    )
                    continue
                }

                finalResult = result
                finalEngine = engine
                break
            }

            val result = finalResult
                ?: HakimInferenceEngine.Result.Failure(
                    "استنفدت المحركات المباشرة المجانية المتاحة دون نتيجة.",
                    retryable = false
                )
            val engine = finalEngine ?: candidates.last()

            runOnUiThread {
                if (currentDirectEngine !== engine) return@runOnUiThread
                currentDirectEngine = null

                when (result) {
                    is HakimInferenceEngine.Result.Success -> {
                        finishStreamingReply(result.text)
                        HakimExecutiveLoop.complete(
                            this,
                            "عاد الرد من " + engine.displayName + " إلى محادثة حكيم نفسها"
                        )
                        recordRoute("direct:" + engine.id, true)
                        command.setText("")
                        attachments.clear()
                        refreshAttachmentStatus()
                        status.text = "اكتمل"
                    }
                    is HakimInferenceEngine.Result.NeedsAuthorization -> {
                        discardEmptyStreamingReply()
                        appendConversation("حكيم", result.reason + " افتح «إدارة» لإكمال الإعداد.")
                        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, result.reason)
                        recordRoute("direct:" + engine.id, false)
                        status.text = "يلزم تفويض المحرك"
                    }
                    is HakimInferenceEngine.Result.Unavailable -> {
                        discardEmptyStreamingReply()
                        appendConversation("حكيم", result.reason)
                        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, result.reason)
                        recordRoute("direct:" + engine.id, false)
                        status.text = "المسار المباشر غير متاح لهذا الإدخال"
                    }
                    is HakimInferenceEngine.Result.Failure -> {
                        discardEmptyStreamingReply()
                        appendConversation("حكيم", result.reason)
                        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, result.reason)
                        recordRoute("direct:" + engine.id, false)
                        status.text = "لم تكتمل المهمة"
                    }
                }
                refreshOperations()
            }
        }.start()
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
                HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.EXECUTING, "توجيه المهمة إلى " + provider.label)
                refreshOperations()
                startActivity(out)
                HakimExecutiveLoop.waitExternal(this, provider.label)
                refreshOperations()
                appendConversation("حكيم", "احتاجت هذه المهمة قناة خارجية؛ فتحتها الآن. فتح التطبيق وحده ليس نجاحًا للمهمة.")
                status.text = "بانتظار أثر القناة الخارجية"
                return
            } catch (_: Exception) {
                HakimModelToolRouter.recordOutcome(this, provider.id, false)
                recordRoute("provider:" + provider.id, false)
            }
        }

        if (HakimExecutiveLoop.advanceCycle(this, "تعذرت القنوات المباشرة؛ تغيير المسار بدل تكرار الفشل")) {
            HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.ROUTING, "المشاركة الآمنة كمسار احتياطي")
        }
        refreshOperations()
        appendConversation("حكيم", "تعذرت القنوات المباشرة؛ سأستخدم المشاركة الآمنة كمسار احتياطي.")
        status.text = "مسار احتياطي"
        shareToAny(text)
    }

    private fun openProviderWeb(text: String, decision: HakimModelToolRouter.Decision) {
        val provider = decision.provider ?: run {
            openInHakim(text)
            return
        }
        val governed = HakimExecutiveLoop.providerInstruction(this, text)
        copyText(governed)
        getSharedPreferences("hakim", MODE_PRIVATE)
            .edit()
            .putString("last_url", provider.webUrl)
            .apply()
        recordRoute("provider_web:" + provider.id, null)
        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.EXECUTING, "فتح قناة ويب رسمية داخل حكيم")
        HakimExecutiveLoop.waitExternal(this, provider.label)
        refreshOperations()
        appendConversation("حكيم", "فتحت القناة الرسمية المختارة. لن أعتبر المهمة ناجحة قبل تحقق الأثر.")
        status.text = "قناة خارجية"
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun shareToAny(text: String) {
        if (text.isBlank() && attachments.isEmpty()) return
        capture(text, "share_out")
        recordRoute("share", null)
        val governed = HakimExecutiveLoop.providerInstruction(this, text)
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
        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.EXECUTING, "فتح المتصفح للمسار الذي يحتاج الويب")
        HakimExecutiveLoop.waitExternal(this, "المتصفح")
        refreshOperations()
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
            if (attachments.isEmpty()) {
                attachmentStatus.text = ""
                attachmentStatus.visibility = View.GONE
            } else {
                attachmentStatus.visibility = View.VISIBLE
                attachmentStatus.text = HakimAttachmentGateway.summary(attachments)
            }
        }
    }

    private fun refreshOperations() {
        if (!::operations.isInitialized) return
        if (operationsExpanded) {
            operations.maxLines = 7
            operations.ellipsize = null
            operations.text = HakimExecutiveLoop.operationText(this)
        } else {
            operations.maxLines = 1
            operations.ellipsize = TextUtils.TruncateAt.END
            operations.text = HakimExecutiveLoop.latestOperationText(this)
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

    private fun beginStreamingReply() {
        streamingBase = conversation.text.toString().trim()
        streamingBuffer.setLength(0)
        renderStreamingReply()
    }

    private fun appendStreamingDelta(delta: String) {
        if (delta.isBlank()) return
        streamingBuffer.append(delta)
        renderStreamingReply()
    }

    private fun renderStreamingReply() {
        val prefix = if (streamingBase.isBlank()) "" else streamingBase + "\n\n"
        conversation.text = prefix + "حكيم:\n" + streamingBuffer.toString()
        scrollConversationToBottom()
    }

    private fun finishStreamingReply(finalText: String) {
        if (streamingBuffer.isEmpty() && finalText.isNotBlank()) {
            streamingBuffer.append(finalText)
            renderStreamingReply()
        }
        persistConversation()
        streamingBase = ""
        streamingBuffer.setLength(0)
    }

    private fun discardEmptyStreamingReply() {
        if (streamingBuffer.isEmpty() && streamingBase.isNotBlank()) {
            conversation.text = streamingBase
        } else if (streamingBuffer.isNotEmpty()) {
            persistConversation()
        }
        streamingBase = ""
        streamingBuffer.setLength(0)
    }

    private fun persistConversation() {
        val kept = conversation.text.toString().takeLast(12_000)
        conversation.text = kept
        getSharedPreferences("hakim_conversation", MODE_PRIVATE)
            .edit()
            .putString("recent", kept)
            .apply()
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
