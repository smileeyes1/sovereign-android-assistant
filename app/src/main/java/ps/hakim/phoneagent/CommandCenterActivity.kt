package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONObject
import java.util.UUID

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
    private val browserHandler = Handler(Looper.getMainLooper())

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
            text = ""
            visibility = View.GONE
            contentDescription = "تفاصيل تشغيل داخلية"
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
            actionButton("صوت") {
                val blocked = HakimEnterprisePolicy.blockReason(this, "voice")
                if (blocked != null) {
                    appendConversation("حكيم", blocked)
                    status.text = "مقيّد بسياسة المؤسسة"
                } else {
                    startSpeechInput()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        toolsRow.addView(
            actionButton("الإعدادات") {
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

    private fun executeBestRoute(text: String, appendUserMessage: Boolean = true) {
        if (text.isBlank() && attachments.isEmpty()) {
            toast("اكتب الغاية أو أرفق محتوى")
            return
        }
        capture(text, "best_route")
        if (appendUserMessage) {
            appendConversation("أنت", if (text.isBlank()) "مرفقات فقط" else text)
        }
        val directed = HakimIntentDirector.build(this, text, attachments.size)
        HakimExecutiveLoop.start(this, text, directed.acceptance)
        HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.PLANNING, "صياغة أمر تنفيذي أعلى للمحرك وفق المقصد ومعيار الاكتمال")
        val toolPlan = HakimSilentToolOrchestrator.plan(this, text, attachments.isNotEmpty())
        HakimExecutiveLoop.record(
            this,
            HakimExecutiveLoop.Phase.PLANNING,
            "خطة الأدوات: " + toolPlan.orderedTools.joinToString(" ← ") + "؛ التنفيذ الصامت أولًا"
        )
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
            HakimModelToolRouter.Channel.LOCAL_ARTIFACT ->
                executeLocalArtifact(text)
            HakimModelToolRouter.Channel.POLICY_BLOCKED -> {
                appendConversation("حكيم", decision.reason)
                HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, decision.reason)
                status.text = "مقيّد بسياسة المؤسسة"
                recordRoute("enterprise_policy", false)
                refreshOperations()
            }
            HakimModelToolRouter.Channel.DIRECT_MODEL ->
                executeDirectModel(text, directed.instruction, decision.engineId)
            HakimModelToolRouter.Channel.FREE_ENGINE_SETUP ->
                beginFreeEngineSetup(text)
            HakimModelToolRouter.Channel.SILENT_BROWSER ->
                executeSilentBrowser(text, directed.instruction)
            HakimModelToolRouter.Channel.LOCAL_BROWSER -> openInHakim(text)
            HakimModelToolRouter.Channel.PROVIDER_APP -> sendToProviderApp(text, decision)
            HakimModelToolRouter.Channel.SYSTEM_SHARE -> shareToAny(text)
            HakimModelToolRouter.Channel.PROVIDER_WEB -> openProviderWeb(text, decision)
        }
    }

    private fun executeSilentBrowser(text: String, baseInstruction: String) {
        val taskId = "browser-" + UUID.randomUUID().toString().take(12)
        val taskPrefs = getSharedPreferences("hakim_browser_tasks", MODE_PRIVATE)
        taskPrefs.edit()
            .remove(taskId + "_state")
            .remove(taskId + "_result")
            .remove(taskId + "_error")
            .apply()

        HakimExecutiveLoop.record(
            this,
            HakimExecutiveLoop.Phase.EXECUTING,
            "استخدام المتصفح المدمج في الخلفية؛ فتح الصفحة وحده ليس نجاحًا"
        )
        refreshOperations()
        status.text = "يعمل على طلبك…"

        val intent = Intent(this, HakimService::class.java)
            .setAction(HakimService.ACTION_BROWSER_TASK)
            .putExtra(HakimService.EXTRA_BROWSER_TASK_ID, taskId)
            .putExtra(HakimService.EXTRA_BROWSER_QUERY, text)

        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.onFailure {
            appendConversation("حكيم", HakimProductUx.publicError("تعذر الاتصال بمسار الويب"))
            HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, "تعذر بدء خدمة المتصفح المدمج")
            status.text = "تعذر مسار المتصفح"
            return
        }

        pollSilentBrowser(taskId, text, baseInstruction, 0)
    }

    private fun pollSilentBrowser(taskId: String, text: String, baseInstruction: String, attempt: Int) {
        val taskPrefs = getSharedPreferences("hakim_browser_tasks", MODE_PRIVATE)
        when (taskPrefs.getString(taskId + "_state", "").orEmpty()) {
            "COMPLETE" -> {
                val raw = taskPrefs.getString(taskId + "_result", "{}").orEmpty()
                val page = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("text", raw) }
                val pageText = page.optString("text").trim()
                val title = page.optString("title").trim()
                val url = page.optString("url").trim()

                HakimExecutiveLoop.record(
                    this,
                    HakimExecutiveLoop.Phase.VERIFYING,
                    "استلم حكيم أثر المتصفح وأعاد إدخاله في مسار المهمة"
                )
                refreshOperations()

                val engine = HakimEngineRegistry.bestGeneralChat(this, text, attachments)
                if (
                    engine != null &&
                    pageText.isNotBlank() &&
                    !HakimSilentToolOrchestrator.isDirectUrl(text)
                ) {
                    val evidence = pageText.take(8_000)
                    val augmented = buildString {
                        appendLine(baseInstruction)
                        appendLine()
                        appendLine("أداة المتصفح المدمج عادت بالأدلة الآتية. استخدمها لإكمال مقصد المستخدم، ولا تطلب منه فتح الصفحة أو نسخ المحتوى:")
                        if (title.isNotBlank()) appendLine("العنوان: " + title)
                        if (url.isNotBlank()) appendLine("الرابط: " + url)
                        appendLine("المحتوى المرئي:")
                        append(evidence)
                    }.take(14_000)
                    status.text = "يجهّز النتيجة…"
                    executeDirectModel(text, augmented, engine.id)
                } else {
                    val ready = pageText.ifBlank {
                        if (url.isNotBlank()) "وصل حكيم إلى: $url" else "اكتمل التصفح دون نص قابل للاستخراج."
                    }.take(10_000)
                    appendConversation("حكيم", ready)
                    HakimExecutiveLoop.complete(this, "عاد أثر المتصفح إلى محادثة حكيم نفسها")
                    recordRoute("silent_browser", true)
                    command.setText("")
                    status.text = "اكتمل"
                    refreshOperations()
                }
            }
            "FAILED" -> {
                val reason = taskPrefs.getString(taskId + "_error", "تعذر التصفح").orEmpty()
                appendConversation("حكيم", HakimProductUx.publicError(reason))
                HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, reason)
                recordRoute("silent_browser", false)
                status.text = "تعذر التصفح"
                refreshOperations()
            }
            else -> {
                if (attempt >= 30) {
                    appendConversation("حكيم", "استغرق التنفيذ وقتًا أطول من المتوقع. لم أعتبر المهمة مكتملة.")
                    HakimExecutiveLoop.record(this, HakimExecutiveLoop.Phase.GATED, "مهلة التصفح الصامت")
                    recordRoute("silent_browser", false)
                    status.text = "انتهت مهلة التصفح"
                    refreshOperations()
                } else {
                    browserHandler.postDelayed({
                        pollSilentBrowser(taskId, text, baseInstruction, attempt + 1)
                    }, 500L)
                }
            }
        }
    }

    private fun executeLocalArtifact(text: String) {
        HakimExecutiveLoop.record(
            this,
            HakimExecutiveLoop.Phase.EXECUTING,
            "إنشاء الملف محليًا داخل الهاتف دون OpenRouter أو نموذج خارجي"
        )
        refreshOperations()
        status.text = "يجهّز الملف…"

        Thread {
            val result = HakimLocalArtifactFactory.create(this, text)
            runOnUiThread {
                result.onSuccess { created ->
                    appendConversation(
                        "حكيم",
                        "أنشأت ورقة العمل PDF محليًا وحفظتها في ${created.savedAt}. لا يحتاج هذا الطلب إلى OpenRouter."
                    )
                    HakimExecutiveLoop.complete(this, "تم إنشاء ملف PDF وحفظه محليًا")
                    recordRoute("local_artifact_pdf", true)
                    status.text = "اكتمل PDF"
                    command.setText("")
                    refreshOperations()

                    if (created.uri != null) {
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(created.uri, created.kind)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { startActivity(view) }
                            .onFailure {
                                toast("تم حفظ PDF في ${created.savedAt}")
                            }
                    }
                }.onFailure { error ->
                    appendConversation(
                        "حكيم",
                        "تعذر إنشاء PDF محليًا: " + (error.message ?: "خطأ غير معروف")
                    )
                    HakimExecutiveLoop.record(
                        this,
                        HakimExecutiveLoop.Phase.GATED,
                        "فشل مصنع الملفات المحلي؛ لم يُفتح OAuth ولم يُرسل الطلب خارجيًا"
                    )
                    recordRoute("local_artifact_pdf", false)
                    status.text = "تعذر إنشاء PDF"
                    refreshOperations()
                }
            }
        }.start()
    }

    private fun beginFreeEngineSetup(text: String) {
        HakimExecutiveLoop.record(
            this,
            HakimExecutiveLoop.Phase.GATED,
            "يلزم ربط محرك مجاني مباشر لمرة واحدة؛ لن يفتح حكيم ChatGPT تلقائيًا"
        )
        refreshOperations()
        status.text = "ربط الذكاء المجاني"
        appendConversation(
            "حكيم",
            "تحتاج هذه الميزة ربط خدمة ذكاء لمرة واحدة. بعد موافقتك سيعود العمل إلى حكيم ويكمل طلبك هنا."
        )
        OpenRouterOAuthManager.start(this, pendingPrompt = text)
    }

    private fun executeDirectModel(
        text: String,
        instruction: String,
        engineId: String?,
        excluded: Set<String> = emptySet()
    ) {
        val engine = HakimEngineRegistry.directEngines(this)
            .firstOrNull { it.id == engineId && it.id !in excluded }
            ?: HakimWisdomMatrix.choose(this, text, attachments, excluded)?.engine
            ?: run {
                appendConversation(
                    "حكيم",
                    "تحتاج هذه الميزة إعدادًا لمرة واحدة. افتح «الإعدادات» لإكمال الربط."
                )
                HakimExecutiveLoop.record(
                    this,
                    HakimExecutiveLoop.Phase.GATED,
                    "لا يوجد محرك مباشر مجاني مؤهل للمقصد والمدخلات الحالية"
                )
                refreshOperations()
                status.text = "يلزم إعداد محرك مجاني"
                return
            }

        currentDirectEngine = engine
        HakimExecutiveLoop.record(
            this,
            HakimExecutiveLoop.Phase.EXECUTING,
            "إجابة مباشرة داخل حكيم عبر " + engine.displayName
        )
        refreshOperations()
        status.text = "يعمل على طلبك…"
        beginStreamingReply()

        val snapshot = attachments.toList()
        val startedAt = System.currentTimeMillis()

        Thread {
            val result = engine.complete(instruction, snapshot) { delta ->
                runOnUiThread {
                    if (currentDirectEngine === engine) appendStreamingDelta(delta)
                }
            }
            val latency = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            HakimEngineTelemetry.record(
                this,
                engine.id,
                success = result is HakimInferenceEngine.Result.Success,
                latencyMs = latency
            )

            runOnUiThread {
                if (currentDirectEngine !== engine) return@runOnUiThread
                currentDirectEngine = null

                when (result) {
                    is HakimInferenceEngine.Result.Success -> {
                        HakimResiliencePolicy.recordSuccess(this, engine.id)
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
                        retryDirectOrBlock(
                            text = text,
                            instruction = instruction,
                            failedEngine = engine,
                            excluded = excluded,
                            reason = result.reason,
                            finalStatus = "يلزم تفويض محرك مجاني"
                        )
                    }

                    is HakimInferenceEngine.Result.Unavailable -> {
                        retryDirectOrBlock(
                            text = text,
                            instruction = instruction,
                            failedEngine = engine,
                            excluded = excluded,
                            reason = result.reason,
                            finalStatus = "لا يوجد مسار مجاني مباشر لهذا الإدخال"
                        )
                    }

                    is HakimInferenceEngine.Result.Failure -> {
                        HakimResiliencePolicy.recordFailure(this, engine.id, result.retryable, result.reason)
                        if (result.retryable) {
                            retryDirectOrBlock(
                                text = text,
                                instruction = instruction,
                                failedEngine = engine,
                                excluded = excluded,
                                reason = result.reason,
                                finalStatus = "انتهت المسارات المجانية المتاحة"
                            )
                        } else {
                            discardEmptyStreamingReply()
                            appendConversation("حكيم", HakimProductUx.publicError(result.reason))
                            HakimExecutiveLoop.record(
                                this,
                                HakimExecutiveLoop.Phase.GATED,
                                result.reason
                            )
                            recordRoute("direct:" + engine.id, false)
                            status.text = "لم تكتمل المهمة"
                        }
                    }
                }
                refreshOperations()
            }
        }.start()
    }

    private fun retryDirectOrBlock(
        text: String,
        instruction: String,
        failedEngine: HakimInferenceEngine,
        excluded: Set<String>,
        reason: String,
        finalStatus: String
    ) {
        recordRoute("direct:" + failedEngine.id, false)
        val nextExcluded = excluded + failedEngine.id
        val fallback = HakimWisdomMatrix.choose(this, text, attachments, nextExcluded)?.engine

        if (fallback != null && HakimExecutiveLoop.advanceCycle(
                this,
                "تعذر " + failedEngine.displayName + "؛ تحويل تلقائي إلى محرك مجاني آخر"
            )
        ) {
            resetStreamingReplyForRetry()
            HakimExecutiveLoop.record(
                this,
                HakimExecutiveLoop.Phase.ROUTING,
                "المحرك البديل: " + fallback.displayName
            )
            refreshOperations()
            status.text = "يجرّب مسارًا آخر…"
            executeDirectModel(text, instruction, fallback.id, nextExcluded)
            return
        }

        discardEmptyStreamingReply()
        appendConversation(
            "حكيم",
            reason + " لا يوجد محرك مجاني مباشر آخر مؤهل الآن."
        )
        HakimExecutiveLoop.record(
            this,
            HakimExecutiveLoop.Phase.GATED,
            reason
        )
        status.text = finalStatus
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
        status.text = "بانتظار موافقتك"
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
        status.text = "يعمل على طلبك…"
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return

        if (i.getBooleanExtra("resume_after_free_oauth", false)) {
            val pending = OpenRouterOAuthManager.takePendingPrompt(this).orEmpty()
            if (HakimSecretStore.has(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)) {
                status.text = "تم ربط الذكاء المجاني"
                if (pending.isNotBlank()) {
                    command.setText(pending)
                    command.post { executeBestRoute(pending, appendUserMessage = false) }
                }
            } else {
                status.text = "لم يكتمل ربط الذكاء المجاني"
            }
            return
        }
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
            "حكيم:\nمرحبًا. اكتب ما تريد، وسأتولى التنفيذ وأعيد لك النتيجة هنا."
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

    private fun resetStreamingReplyForRetry() {
        conversation.text = streamingBase
        streamingBase = ""
        streamingBuffer.setLength(0)
        scrollConversationToBottom()
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
        HakimAuditTrail.record(this, route, success)
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("حكيم", text))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
