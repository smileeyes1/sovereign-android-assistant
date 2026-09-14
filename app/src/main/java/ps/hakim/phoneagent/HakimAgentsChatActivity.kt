package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var busy = false
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        buildUi()
        appendAssistant("أنا حكيم. يكفي أقل تلميح: كلمة، «كمل»، «هاي»، اسم الموقع، أو اضغط «نفّذ/أكمل» دون كتابة. أستعيد المقصد والسياق وأكمل الآمن تلقائيًا.")
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
            hint = "قل أقل ما يخطر ببالك… أو اتركها فارغة واضغط نفّذ/أكمل"
            minLines = 2
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
        row.addView(button("نفّذ/أكمل") { submit(true) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("افهم فقط") { submit(false) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        root.addView(button("النظام والبيانات") { startActivity(Intent(this, HakimSystemSettingsActivity::class.java)) })
        root.addView(button("فتح متصفح حكيم") { startActivity(Intent(this, MainActivity::class.java)) })
        setContentView(root)
    }

    private fun selectedAgent(): HakimAgentSystem.Agent? {
        val p = agentSpinner.selectedItemPosition
        return if (p <= 0) null else HakimAgentSystem.Agent.values().getOrNull(p - 1)
    }

    private fun submit(execute: Boolean) {
        if (busy) {
            appendAssistant("أنا ما زلت أنفذ الدورة الحالية؛ لن أبدأ دورة موازية قد تتعارض معها.")
            return
        }
        val typed = input.text.toString().trim()
        val cue = typed.ifBlank { "أكمل" }
        appendUser(if (typed.isBlank()) "…" else typed)
        input.setText("")

        val preferred = selectedAgent()
        val inference = HakimIntentContext.infer(this, cue)
        val plan = HakimAgentSystem.plan(this, cue, preferred)
        appendAssistant(HakimAgentSystem.summary(this, cue, preferred))

        if (!execute) return
        if (plan.sensitiveInputDetected) {
            appendAssistant("وجدت في النص ما يبدو سرًا أو اعتمادًا حساسًا. لم أرسله لأي نموذج. سيستخدم حكيم جلسة الموقع أو مدير اعتماد أندرويد/الحقل الآمن عند الحاجة.")
            return
        }

        busy = true
        if (plan.route == "browser" && hasLastWebUrl()) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitForHakimBrowser(0) { ready ->
                if (!ready) {
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

    private fun executeLocalThenAutonomous(
        cue: String,
        preferred: HakimAgentSystem.Agent?,
        resolvedGoal: String,
        route: String
    ) {
        val local = HakimNaturalActionEngine.execute(this, resolvedGoal, cue)
        if (local.handled) {
            appendAssistant(local.message)
            if (local.success && route == "browser" && HakimIntentContext.isMinimalCue(cue)) {
                uiHandler.postDelayed({ runAutonomousCycle(cue, preferred, resolvedGoal) }, 500L)
            } else {
                busy = false
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
        HakimAutonomousExecutor.run(
            activity = this,
            goal = resolvedGoal,
            onProgress = { message -> appendAssistant(message) },
            onComplete = { outcome ->
                when {
                    outcome.completed -> {
                        busy = false
                        bringChatToFront()
                        appendAssistant("اكتملت الدورة المحلية بعد ${outcome.steps} خطوة، وظهرت علامة نجاح مرئية.")
                    }
                    outcome.needsCredential -> {
                        busy = false
                        appendAssistant("وصلت إلى خطوة اعتماد حساسة. لم ألمس السر؛ استخدم مدير اعتماد أندرويد أو أدخل السر في الحقل الآمن، ثم قل فقط «كمل».")
                        Toast.makeText(this, "أدخل الاعتماد في الحقل الآمن ثم ارجع لحكيم وقل: كمل", Toast.LENGTH_LONG).show()
                    }
                    outcome.needsDataTrust -> {
                        busy = false
                        appendAssistant("الموقع الحالي يحتاج استخدام بيانات خزنة حكيم لكنه غير معتمد لذلك. راجع الموقع وفعّل الثقة به؛ بعدها يكفي «كمل».")
                        startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
                    }
                    outcome.needsApproval -> {
                        busy = false
                        bringChatToFront()
                        appendAssistant("حضّرت ما يمكن بأمان وتوقفت قبل الفعل عالي الأثر. عند موافقتك الصريحة أتابع الفعل النهائي.")
                    }
                    else -> {
                        if (outcome.progressed) appendAssistant("أنجزت ${outcome.steps} خطوة محلية. ${outcome.reason}")
                        runReasoningCycle(HakimAgentSystem.agentPrompt(this, cue, preferred), 0)
                    }
                }
            }
        )
    }

    private fun waitForHakimBrowser(attempt: Int, onReady: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed) {
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
        busy = true
        HakimReasoningBridge.ask(
            activity = this,
            basePrompt = governedPrompt,
            onProgress = { message -> appendAssistant(message) },
            onComplete = reasoningDone@ { result ->
                if (!result.available) {
                    busy = false
                    fallbackShare(governedPrompt, result.reason)
                    return@reasoningDone
                }
                val plan = result.plan
                if (plan == null) {
                    busy = false
                    bringChatToFront()
                    appendAssistant("عاد محرك الاستدلال دون خطة تنفيذ موثوقة؛ لم أنفذ أي تخمين. ${result.reason}")
                    return@reasoningDone
                }
                if (plan.message.isNotBlank()) appendAssistant(plan.message)
                HakimReasoningPlanExecutor.run(
                    activity = this,
                    plan = plan,
                    onProgress = { message -> appendAssistant(message) },
                    onComplete = { outcome ->
                        when {
                            outcome.needsApproval -> {
                                busy = false
                                bringChatToFront()
                                appendAssistant("توقفت قبل خطوة عالية الأثر اقترحها الاستدلال. لا تُنفذ إلا بموافقتك الصريحة.")
                            }
                            outcome.needsDataTrust -> {
                                busy = false
                                bringChatToFront()
                                appendAssistant("الخطة تحتاج قيمة من خزنتك في موقع غير معتمد. سأطلب اعتماد الموقع بدل كشف البيانات تلقائيًا.")
                                startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
                            }
                            outcome.blocked -> {
                                busy = false
                                bringChatToFront()
                                appendAssistant("رفضت خطوة من خطة الاستدلال لأنها خالفت حاكم الأمان المحلي: ${outcome.reason}")
                            }
                            plan.done || outcome.completed -> {
                                busy = false
                                bringChatToFront()
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
                                appendAssistant("توقفت الدورة بعد التحقق لأن الاستمرار لم يعد مثبتًا وآمنًا: ${outcome.reason}")
                            }
                        }
                    }
                )
            }
        )
    }

    private fun fallbackShare(governedPrompt: String, reason: String) {
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
                appendAssistant("تعذر فتح محرك الذكاء. تم حفظ المهمة في الحافظة حتى لا تضيع.")
            }
        }
    }

    private fun bringChatToFront() {
        startActivity(Intent(this, HakimAgentsChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
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
