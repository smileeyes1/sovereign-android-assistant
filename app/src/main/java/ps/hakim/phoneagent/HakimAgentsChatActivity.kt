package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import java.util.Locale

/** واجهة واحدة ذكية للمستخدم؛ نص/صوت، اختيار الوكيل اختياري، والوضع الافتراضي تلقائي. */
class HakimAgentsChatActivity : Activity() {
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var agentSpinner: Spinner
    private lateinit var scroll: ScrollView
    private lateinit var statusView: TextView
    private lateinit var voiceReplyButton: Button
    private var busy = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceRepliesEnabled = false
    private val speechRequestCode = 4401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        voiceRepliesEnabled = getSharedPreferences("hakim_ui", MODE_PRIVATE).getBoolean("voice_replies", false)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS &&
                (tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            updateVoiceReplyLabel()
        }
        buildUi()
        setStatus("جاهز • يفهم من أقل إشارة • يتعلم محليًا")
        appendAssistant("أنا حكيم. تحدث معي أو اكتب أقل تلميح: حرف، رمز، كلمة، «كمل»، اسم الموقع، أو اضغط «نفّذ/أكمل» دون كتابة. أقود كيف تلقائيًا داخل حدودك، وأتعلم من النجاح والفشل محليًا، ويمكنك قول «توقف» في أي وقت لإلغاء المهمة فورًا.")
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
        setStatus("فهمت الصوت • أحلل المقصد")
        submit(true)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(18, 20, 18, 18)
        }

        root.addView(TextView(this).apply {
            text = "حكيم — الواجهة الذكية"
            textSize = 25f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 6)
        })

        statusView = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(8, 2, 8, 10)
        }
        root.addView(statusView)

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
            hint = "تحدث أو اكتب أقل ما يخطر ببالك… أو اتركها فارغة واضغط نفّذ/أكمل"
            minLines = 2
            maxLines = 7
            gravity = Gravity.TOP or Gravity.RIGHT
            textSize = 18f
        }
        root.addView(input)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        voiceRow.addView(button("🎙 تحدث") { startVoiceInput() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        voiceReplyButton = button("") { toggleVoiceReplies() }
        voiceRow.addView(voiceReplyButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(voiceRow)
        updateVoiceReplyLabel()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        row.addView(button("نفّذ/أكمل") { submit(true) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("افهم فقط") { submit(false) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        root.addView(button("إيقاف المهمة فورًا") { cancelCurrentMission("أوقف المستخدم المهمة من زر الإيقاف") })
        root.addView(button("النظام والبيانات") { startActivity(Intent(this, HakimSystemSettingsActivity::class.java)) })
        root.addView(button("فتح متصفح حكيم") { startActivity(Intent(this, MainActivity::class.java)) })
        setContentView(root)
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
        updateVoiceReplyLabel()
        if (voiceRepliesEnabled && !ttsReady) {
            Toast.makeText(this, "سيعمل الرد الصوتي عند جاهزية محرك النطق العربي في الجهاز", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateVoiceReplyLabel() {
        if (!::voiceReplyButton.isInitialized) return
        voiceReplyButton.text = if (voiceRepliesEnabled) "🔊 الرد الصوتي: يعمل" else "🔇 الرد الصوتي: متوقف"
    }

    private fun setStatus(text: String) {
        if (::statusView.isInitialized) statusView.text = text
    }

    private fun selectedAgent(): HakimAgentSystem.Agent? {
        val p = agentSpinner.selectedItemPosition
        return if (p <= 0) null else HakimAgentSystem.Agent.values().getOrNull(p - 1)
    }

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

    private fun appendUser(text: String) = append("أنت", text)

    private fun appendAssistant(text: String) {
        append("حكيم", text)
        if (voiceRepliesEnabled && ttsReady && text.isNotBlank()) {
            tts?.speak(text.take(1200), TextToSpeech.QUEUE_ADD, null, "hakim_${System.currentTimeMillis()}")
        }
    }

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
