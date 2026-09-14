package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * واجهة حكيم الرئيسية: محادثة عربية حديثة خفيفة، قريبة من بساطة واجهات GPT دون نسخ هوية بصرية خاصة.
 * تستخدم ListView المعاد تدويره بدل transcript متضخم، وتؤخر TTS حتى الحاجة حفاظًا على الهاتف.
 */
class HakimAgentsChatActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var messageList: ListView
    private lateinit var messageAdapter: HakimChatMessageAdapter
    private lateinit var statusView: TextView
    private lateinit var agentButton: TextView
    private lateinit var stopButton: TextView
    private lateinit var sendButton: TextView
    private var preferredAgent: HakimAgentSystem.Agent? = null
    private var busy = false
        set(value) {
            field = value
            if (::stopButton.isInitialized) stopButton.visibility = if (value) View.VISIBLE else View.GONE
            if (::sendButton.isInitialized) sendButton.alpha = if (value) 0.72f else 1f
        }
    private val uiHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceRepliesEnabled = false
    private val speechRequestCode = 4401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        HakimProactiveEngine.initialize(this)
        voiceRepliesEnabled = getSharedPreferences("hakim_ui", MODE_PRIVATE).getBoolean("voice_replies", false)
        buildUi()
        if (voiceRepliesEnabled) initializeTtsIfNeeded()
        setStatus("جاهز • يفهم من أقل إشارة • يتعلم ويبادر محليًا")
        appendAssistant(
            "أنا حكيم. اكتب أو تحدث بطريقتك الطبيعية، حتى لو كانت الإشارة قصيرة. " +
                "أقود كيف داخل حدودك، وأتعلم من النجاح والفشل، وأبادر بالأعمال المفيدة الآمنة. " +
                "يمكنك قول «توقف» في أي وقت."
        )
    }

    override fun onResume() {
        super.onResume()
        uiHandler.postDelayed({ maybeResumeProactively() }, 650L)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != speechRequestCode) return
        if (resultCode != RESULT_OK) {
            setStatus("جاهز • لم يصل كلام واضح")
            return
        }
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty().trim()
        if (heard.isBlank()) {
            setStatus("جاهز • لم يصل كلام واضح")
            return
        }
        input.setText(heard)
        input.setSelection(input.text.length)
        setStatus("فهمت الصوت • أحلل المقصد")
        submit(true)
    }

    private fun buildUi() {
        val palette = HakimChatUi.palette(this)
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(palette.background)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HakimChatUi.dp(this@HakimAgentsChatActivity, 12f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 8f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 12f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 6f)
            )
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        titleBlock.addView(TextView(this).apply {
            text = "حكيم"
            textSize = 22f
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            textDirection = View.TEXT_DIRECTION_RTL
        })
        statusView = TextView(this).apply {
            textSize = 12.5f
            setTextColor(palette.muted)
            includeFontPadding = false
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(0, HakimChatUi.dp(this@HakimAgentsChatActivity, 3f), 0, 0)
        }
        titleBlock.addView(statusView)
        header.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        agentButton = pill("تلقائي — حكيم يختار", palette.surface, palette.text) { showAgentMenu(agentButton) }
        header.addView(agentButton)

        stopButton = pill("■", palette.danger, palette.onAccent) {
            cancelCurrentMission("أوقف المستخدم المهمة من زر الإيقاف")
        }.apply {
            contentDescription = "إيقاف المهمة فورًا"
            visibility = View.GONE
        }
        header.addView(stopButton)

        val menuButton = pill("⋮", palette.surface, palette.text) { showToolsMenu(it) }.apply {
            contentDescription = "أدوات حكيم"
            textSize = 24f
        }
        header.addView(menuButton)
        root.addView(header)

        messageAdapter = HakimChatMessageAdapter(this)
        messageList = ListView(this).apply {
            adapter = messageAdapter
            divider = null
            dividerHeight = 0
            setBackgroundColor(palette.background)
            isVerticalScrollBarEnabled = false
            isSmoothScrollbarEnabled = false
            transcriptMode = ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL
            setPadding(0, HakimChatUi.dp(this@HakimAgentsChatActivity, 8f), 0, HakimChatUi.dp(this@HakimAgentsChatActivity, 8f))
            clipToPadding = false
        }
        root.addView(messageList, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val composerOuter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                HakimChatUi.dp(this@HakimAgentsChatActivity, 10f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 6f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 10f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 10f)
            )
            setBackgroundColor(palette.background)
        }

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HakimChatUi.dp(this@HakimAgentsChatActivity, 6f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 5f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 6f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 5f)
            )
            background = HakimChatUi.rounded(palette.surface, 22f, this@HakimAgentsChatActivity, palette.border)
        }

        val mic = iconAction("🎙", palette.surfaceStrong, palette.text, "تحدث مع حكيم") { startVoiceInput() }
        composer.addView(mic)

        input = EditText(this).apply {
            hint = "اكتب ما تريد… أو اتركه فارغًا ثم أرسل لأكمل"
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 17f
            minLines = 1
            maxLines = 5
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(
                HakimChatUi.dp(this@HakimAgentsChatActivity, 8f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 7f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 8f),
                HakimChatUi.dp(this@HakimAgentsChatActivity, 7f)
            )
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submit(true)
                    true
                } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        sendButton = iconAction("↑", palette.accent, palette.onAccent, "نفّذ/أكمل") { submit(true) }.apply {
            textSize = 24f
        }
        composer.addView(sendButton)
        composerOuter.addView(composer)

        composerOuter.addView(TextView(this).apply {
            text = "حكيم قد يتوقف فقط عند قرار جوهري أو سر أو صلاحية لا يمكن تجاوزها بأمان."
            textSize = 11f
            setTextColor(palette.muted)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(8, HakimChatUi.dp(this@HakimAgentsChatActivity, 5f), 8, 0)
        })
        root.addView(composerOuter)

        setContentView(root)
    }

    private fun showAgentMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 100, 0, "تلقائي — حكيم يختار")
        HakimAgentSystem.Agent.values().forEachIndexed { index, agent ->
            popup.menu.add(0, 1000 + index, index + 1, agent.title)
        }
        popup.setOnMenuItemClickListener { item ->
            preferredAgent = if (item.itemId == 100) null else HakimAgentSystem.Agent.values().getOrNull(item.itemId - 1000)
            updateAgentLabel()
            true
        }
        popup.show()
    }

    private fun showToolsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (voiceRepliesEnabled) "إيقاف الرد الصوتي" else "تفعيل الرد الصوتي")
        popup.menu.add(0, 2, 1, "افهم فقط")
        popup.menu.add(0, 3, 2, "إيقاف المهمة فورًا")
        popup.menu.add(0, 4, 3, "النظام والبيانات")
        popup.menu.add(0, 5, 4, "مركز حكيم والاتصال المحلي")
        popup.menu.add(0, 6, 5, "فتح متصفح حكيم")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> toggleVoiceReplies()
                2 -> submit(false)
                3 -> cancelCurrentMission("أوقف المستخدم المهمة من قائمة الأدوات")
                4 -> startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
                5 -> startActivity(Intent(this, UnifiedHomeActivity::class.java).putExtra("hakim_control_center", true))
                6 -> startActivity(Intent(this, MainActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun updateAgentLabel() {
        if (::agentButton.isInitialized) agentButton.text = preferredAgent?.title ?: "تلقائي — حكيم يختار"
    }

    private fun maybeResumeProactively() {
        if (busy || isFinishing || isDestroyed) return
        val mission = HakimProactiveEngine.foregroundOpportunity(this) ?: return
        val plan = HakimAgentSystem.plan(this, mission.goal, null)
        if (plan.sensitiveInputDetected || plan.needsApproval || plan.route == "blocked") return
        HakimProactiveEngine.markForegroundResume(this, mission)
        appendAssistant("وجدت مهمة مفيدة وآمنة غير مكتملة، وسأستأنفها تلقائيًا من حالتها الحالية دون أن أطلب منك تكرار الأمر.")
        setStatus("أبادر تلقائيًا بمهمة آمنة غير مكتملة…")
        busy = true
        if (plan.route == "browser" && hasLastWebUrl()) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitForHakimBrowser(0) { ready ->
                if (HakimMissionLedger.isCancelled(this)) {
                    busy = false
                    setStatus("المهمة ملغاة")
                } else if (!ready) {
                    setStatus("أستعيد المسار بالاستدلال…")
                    runReasoningCycle(HakimAgentSystem.agentPrompt(this, "أكمل", null), 0)
                } else {
                    executeLocalThenAutonomous("أكمل", null, mission.goal, plan.route)
                }
            }
        } else {
            executeLocalThenAutonomous("أكمل", null, mission.goal, plan.route)
        }
    }

    private fun initializeTtsIfNeeded() {
        if (tts != null) return
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS &&
                (tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            if (voiceRepliesEnabled && !ttsReady) {
                Toast.makeText(this, "محرك النطق العربي غير جاهز على الجهاز", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVoiceInput() {
        if (busy) {
            appendAssistant("أنا أنفذ الآن. يمكنك قول «توقف» بعد انتهاء الاستماع أو استخدام زر الإيقاف.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث مع حكيم")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            setStatus("أستمع إليك الآن…")
            startActivityForResult(intent, speechRequestCode)
        } catch (_: Exception) {
            setStatus("جاهز • الإدخال الصوتي غير متاح على الجهاز")
            Toast.makeText(this, "خدمة التعرف على الكلام غير متاحة حاليًا", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleVoiceReplies() {
        voiceRepliesEnabled = !voiceRepliesEnabled
        getSharedPreferences("hakim_ui", MODE_PRIVATE).edit().putBoolean("voice_replies", voiceRepliesEnabled).apply()
        if (voiceRepliesEnabled) {
            initializeTtsIfNeeded()
            Toast.makeText(this, "الرد الصوتي مفعّل", Toast.LENGTH_SHORT).show()
        } else {
            tts?.stop()
            Toast.makeText(this, "الرد الصوتي متوقف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setStatus(text: String) {
        if (::statusView.isInitialized) statusView.text = text
    }

    private fun selectedAgent(): HakimAgentSystem.Agent? = preferredAgent

    private fun submit(execute: Boolean) {
        val typed = input.text.toString().trim()
        if (isCancelCue(typed)) {
            appendUser(typed)
            input.setText("")
            cancelCurrentMission("ألغى المستخدم المهمة بكلمة: ${typed.take(40)}")
            return
        }
        if (busy) {
            appendAssistant("أنا ما زلت أنفذ الدورة الحالية؛ لن أبدأ دورة موازية قد تتعارض معها. يمكنك قول «توقف» لإلغائها فورًا.")
            return
        }
        val cue = typed.ifBlank { "أكمل" }
        appendUser(if (typed.isBlank()) "…" else typed)
        input.setText("")
        setStatus("أفهم المقصد وأختار أفضل مسار…")

        val preferred = selectedAgent()
        val inference = HakimIntentContext.infer(this, cue)
        val plan = HakimAgentSystem.plan(this, cue, preferred)
        appendAssistant(HakimAgentSystem.summary(this, cue, preferred))

        if (!execute) {
            setStatus("جاهز • تم الفهم دون تنفيذ")
            return
        }
        if (plan.sensitiveInputDetected) {
            setStatus("متوقف بأمان • اعتماد حساس")
            appendAssistant("وجدت في النص ما يبدو سرًا أو اعتمادًا حساسًا. لم أرسله لأي نموذج. سيستخدم حكيم جلسة الموقع أو مدير اعتماد أندرويد/الحقل الآمن عند الحاجة.")
            return
        }

        busy = true
        setStatus("أنفذ داخل غلاف السلطة…")
        if (plan.route == "browser" && hasLastWebUrl()) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitForHakimBrowser(0) { ready ->
                if (HakimMissionLedger.isCancelled(this)) {
                    busy = false
                    setStatus("المهمة ملغاة")
                    return@waitForHakimBrowser
                }
                if (!ready) {
                    setStatus("أستعيد المسار بالاستدلال…")
                    appendAssistant("تعذر استعادة صفحة المتصفح الفعلية بثقة؛ لن أنفذ على شاشة خاطئة.")
                    runReasoningCycle(HakimAgentSystem.agentPrompt(this, cue, preferred), 0)
                } else {
                    executeLocalThenAutonomous(cue, preferred, inference.resolvedRequest, plan.route)
                }
            }
        } else {
            executeLocalThenAutonomous(cue, preferred, inference.resolvedRequest, plan.route)
        }
    }

    private fun cancelCurrentMission(reason: String) {
        val active = HakimMissionLedger.active(this)
        uiHandler.removeCallbacksAndMessages(null)
        busy = false
        if (active == null) {
            setStatus("جاهز")
            appendAssistant("لا توجد مهمة نشطة لإيقافها.")
            return
        }
        HakimMissionLedger.cancel(this, reason)
        setStatus("المهمة ملغاة • لن تُستأنف تلقائيًا")
        appendAssistant("أوقفت المهمة فورًا. لن أستأنف نفس الغاية تلقائيًا. يمكنك بدء غاية جديدة متى شئت.")
    }

    private fun isCancelCue(text: String): Boolean = Regex(
        "(?i)^(توقف|توقّف|قف|الغ|ألغ|ألغي|الغِ|إلغاء|إلغاء المهمة|اوقف|أوقف|stop|cancel)$"
    ).matches(text.trim())

    private fun executeLocalThenAutonomous(
        cue: String,
        preferred: HakimAgentSystem.Agent?,
        resolvedGoal: String,
        route: String
    ) {
        if (HakimMissionLedger.isCancelled(this)) {
            busy = false
            setStatus("المهمة ملغاة")
            return
        }
        setStatus("أجرب أقصر تنفيذ محلي آمن…")
        val local = HakimNaturalActionEngine.execute(this, resolvedGoal, cue)
        if (local.handled) {
            appendAssistant(local.message)
            if (local.success && route == "browser" && HakimIntentContext.isMinimalCue(cue)) {
                uiHandler.postDelayed({ if (!HakimMissionLedger.isCancelled(this)) runAutonomousCycle(cue, preferred, resolvedGoal) }, 500L)
            } else {
                busy = false
                setStatus(if (local.success) "جاهز • تم التنفيذ المحلي" else "جاهز • لم يثبت التنفيذ")
            }
            return
        }
        runAutonomousCycle(cue, preferred, resolvedGoal)
    }

    private fun runAutonomousCycle(
        cue: String,
        preferred: HakimAgentSystem.Agent?,
        resolvedGoal: String
    ) {
        setStatus("أراقب الشاشة وأنفذ وأتحقق…")
        HakimAutonomousExecutor.run(
            activity = this,
            goal = resolvedGoal,
            onProgress = { message ->
                if (!HakimMissionLedger.isCancelled(this)) {
                    setStatus("أنفذ وأتحقق وأتعلم من الأثر…")
                    appendAssistant(message)
                }
            },
            onComplete = { outcome ->
                if (HakimMissionLedger.isCancelled(this)) {
                    busy = false
                    setStatus("المهمة ملغاة")
                    bringChatToFront()
                    return@run
                }
                when {
                    outcome.completed -> {
                        busy = false
                        bringChatToFront()
                        setStatus("جاهز • تحقق النجاح وتعلمت من النتيجة")
                        appendAssistant("اكتملت الدورة المحلية بعد ${outcome.steps} خطوة، وظهرت علامة نجاح مرئية.")
                    }
                    outcome.needsCredential -> {
                        busy = false
                        setStatus("بانتظار اعتماد حساس منك")
                        appendAssistant("وصلت إلى خطوة اعتماد حساسة. لم ألمس السر؛ استخدم مدير اعتماد أندرويد أو أدخل السر في الحقل الآمن، ثم قل فقط «كمل».")
                        Toast.makeText(this, "أدخل الاعتماد في الحقل الآمن ثم ارجع لحكيم وقل: كمل", Toast.LENGTH_LONG).show()
                    }
                    outcome.needsDataTrust -> {
                        busy = false
                        setStatus("بانتظار ثقة الموقع قبل استخدام بياناتك")
                        appendAssistant("الموقع الحالي يحتاج استخدام بيانات خزنة حكيم لكنه غير معتمد لذلك. راجع الموقع وفعّل الثقة به؛ بعدها يكفي «كمل».")
                        startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
                    }
                    outcome.needsApproval -> {
                        busy = false
                        bringChatToFront()
                        setStatus("بانتظار موافقتك على آخر فعل عالي الأثر")
                        appendAssistant("حضّرت ما يمكن بأمان وتوقفت قبل الفعل عالي الأثر/الصلاحية. عند موافقتك الصريحة أتابع الفعل النهائي.")
                    }
                    else -> {
                        if (outcome.progressed) appendAssistant("أنجزت ${outcome.steps} خطوة محلية. ${outcome.reason}")
                        setStatus("أحتاج استدلالًا إضافيًا دون تكرار المنجز…")
                        runReasoningCycle(HakimAgentSystem.agentPrompt(this, cue, preferred), 0)
                    }
                }
            }
        )
    }

    private fun waitForHakimBrowser(attempt: Int, onReady: (Boolean) -> Unit) {
        if (HakimMissionLedger.isCancelled(this) || isFinishing || isDestroyed) {
            onReady(false)
            return
        }
        val web = HakimRuntime.visibleWebView()
        if (web != null && web.progress >= 70) {
            onReady(true)
            return
        }
        if (attempt >= 14) {
            onReady(web != null)
            return
        }
        uiHandler.postDelayed({ waitForHakimBrowser(attempt + 1, onReady) }, 300L)
    }

    private fun hasLastWebUrl(): Boolean {
        val u = getSharedPreferences("hakim", MODE_PRIVATE).getString("last_url", "").orEmpty()
        return u.startsWith("http://") || u.startsWith("https://")
    }

    /** استدلال -> خطة مقيدة -> تنفيذ -> إعادة استدلال، بحد يمنع الدوران. */
    private fun runReasoningCycle(governedPrompt: String, cycle: Int) {
        if (HakimMissionLedger.isCancelled(this)) {
            busy = false
            setStatus("المهمة ملغاة")
            return
        }
        busy = true
        setStatus("أستدل بخطة مقيدة ثم أعود للتحقق المحلي…")
        HakimReasoningBridge.ask(
            activity = this,
            basePrompt = governedPrompt,
            onProgress = { message -> if (!HakimMissionLedger.isCancelled(this)) appendAssistant(message) },
            onComplete = reasoningDone@ { result ->
                if (HakimMissionLedger.isCancelled(this)) {
                    busy = false
                    setStatus("المهمة ملغاة")
                    return@reasoningDone
                }
                if (!result.available) {
                    busy = false
                    setStatus("أنتقل لمسار الذكاء الرسمي الاحتياطي…")
                    fallbackShare(governedPrompt, result.reason)
                    return@reasoningDone
                }
                val plan = result.plan
                if (plan == null) {
                    busy = false
                    bringChatToFront()
                    setStatus("جاهز • لم تصل خطة موثوقة")
                    appendAssistant("عاد محرك الاستدلال دون خطة تنفيذ موثوقة؛ لم أنفذ أي تخمين. ${result.reason}")
                    return@reasoningDone
                }
                if (plan.message.isNotBlank()) appendAssistant(plan.message)
                setStatus("أنفذ الخطة المقيدة وأتحقق من كل خطوة…")
                HakimReasoningPlanExecutor.run(
                    activity = this,
                    plan = plan,
                    onProgress = { message -> if (!HakimMissionLedger.isCancelled(this)) appendAssistant(message) },
                    onComplete = { outcome ->
                        if (HakimMissionLedger.isCancelled(this)) {
                            busy = false
                            setStatus("المهمة ملغاة")
                            bringChatToFront()
                            return@run
                        }
                        when {
                            outcome.needsApproval -> {
                                busy = false
                                bringChatToFront()
                                setStatus("بانتظار موافقتك على فعل عالي الأثر")
                                appendAssistant("توقفت قبل خطوة عالية الأثر/الصلاحية اقترحها الاستدلال. لا تُنفذ إلا بموافقتك الصريحة.")
                            }
                            outcome.needsDataTrust -> {
                                busy = false
                                bringChatToFront()
                                setStatus("بانتظار ثقة الموقع")
                                appendAssistant("الخطة تحتاج قيمة من خزنتك في موقع غير معتمد. سأطلب اعتماد الموقع بدل كشف البيانات تلقائيًا.")
                                startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
                            }
                            outcome.blocked -> {
                                busy = false
                                bringChatToFront()
                                setStatus("متوقف بأمان • الخطة خرجت عن السلطة")
                                appendAssistant("رفضت خطوة من خطة الاستدلال لأنها خرجت عن غلاف السلطة/الأمان المحلي: ${outcome.reason}")
                            }
                            plan.done || outcome.completed -> {
                                busy = false
                                bringChatToFront()
                                setStatus("جاهز • اكتملت جولة الاستدلال والتنفيذ والتحقق")
                                appendAssistant("اكتملت جولة الاستدلال والتنفيذ والتحقق.")
                            }
                            outcome.progressed && cycle < 2 -> {
                                val follow = HakimAgentSystem.agentPrompt(this, "أكمل", null) +
                                    "\n[نتيجة الجولة السابقة]\n${outcome.reason}\nواصل من الحالة الحالية ولا تكرر المنجز."
                                runReasoningCycle(follow, cycle + 1)
                            }
                            else -> {
                                busy = false
                                bringChatToFront()
                                setStatus("جاهز • توقف لأن الاستمرار غير مثبت")
                                appendAssistant("توقفت الدورة بعد التحقق لأن الاستمرار لم يعد مثبتًا وآمنًا: ${outcome.reason}")
                            }
                        }
                    }
                )
            }
        )
    }

    private fun fallbackShare(governedPrompt: String, reason: String) {
        if (HakimMissionLedger.isCancelled(this)) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, governedPrompt)
            setPackage("com.openai.chatgpt")
        }
        try {
            startActivity(send)
            appendAssistant("تعذر الجسر الآلي ($reason)، فانتقلت للمسار الرسمي الاحتياطي دون فقد المهمة.")
        } catch (_: Exception) {
            copy(governedPrompt)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")))
                appendAssistant("تعذر التطبيق الرسمي؛ نسخت المهمة المحكومة وفتحت الويب كمسار احتياطي.")
            } catch (_: Exception) {
                setStatus("المهمة محفوظة • محرك الذكاء غير متاح")
                appendAssistant("تعذر فتح محرك الذكاء. تم حفظ المهمة في الحافظة حتى لا تضيع.")
            }
        }
    }

    private fun bringChatToFront() {
        startActivity(Intent(this, HakimAgentsChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun appendUser(text: String) = append(HakimChatMessageAdapter.Role.USER, text)

    private fun appendAssistant(text: String) {
        append(HakimChatMessageAdapter.Role.ASSISTANT, text)
        if (voiceRepliesEnabled && ttsReady && text.isNotBlank()) {
            tts?.speak(text.take(1200), TextToSpeech.QUEUE_ADD, null, "hakim_${System.currentTimeMillis()}")
        }
    }

    private fun append(role: HakimChatMessageAdapter.Role, text: String) {
        if (!::messageAdapter.isInitialized) return
        messageAdapter.append(role, text)
        messageList.post {
            val last = messageAdapter.count - 1
            if (last >= 0) messageList.setSelection(last)
        }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("حكيم", text))
        Toast.makeText(this, "تم نسخ المهمة المحكومة", Toast.LENGTH_SHORT).show()
    }

    private fun pill(label: String, fill: Int, textColor: Int, action: (View) -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(textColor)
        gravity = Gravity.CENTER
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        setPadding(
            HakimChatUi.dp(this@HakimAgentsChatActivity, 10f),
            HakimChatUi.dp(this@HakimAgentsChatActivity, 8f),
            HakimChatUi.dp(this@HakimAgentsChatActivity, 10f),
            HakimChatUi.dp(this@HakimAgentsChatActivity, 8f)
        )
        background = HakimChatUi.rounded(fill, 18f, this@HakimAgentsChatActivity)
        setOnClickListener { action(it) }
    }

    private fun iconAction(label: String, fill: Int, textColor: Int, description: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 20f
        setTextColor(textColor)
        gravity = Gravity.CENTER
        includeFontPadding = false
        contentDescription = description
        minWidth = HakimChatUi.dp(this@HakimAgentsChatActivity, 44f)
        minHeight = HakimChatUi.dp(this@HakimAgentsChatActivity, 44f)
        background = HakimChatUi.rounded(fill, 22f, this@HakimAgentsChatActivity)
        setOnClickListener { action() }
    }
}
