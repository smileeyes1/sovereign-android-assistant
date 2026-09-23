package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class CommandCenterActivity : Activity() {
    companion object {
        private const val ATTACHMENT_PICKER_REQUEST = 7301
        private const val SPEECH_REQUEST = 7302
    }

    private lateinit var command: EditText
    private lateinit var status: TextView
    private lateinit var updateStatus: TextView
    private lateinit var attachmentStatus: TextView
    private val attachments = mutableListOf<HakimAttachmentGateway.Attachment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        buildUi()
        handleIntent(intent)
        refreshUpdateStatus()
        refreshAttachmentStatus()
        maybeOnboardAutoUpdate()
    }

    override fun onResume() {
        super.onResume()
        refreshUpdateStatus()
        if (AutoUpdater.canInstallPackages(this)) {
            AutoUpdater.checkAsync(this)
            updateStatus.postDelayed({ refreshUpdateStatus() }, 1800L)
        }
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
                    "أضيفت المرفقات محليًا؛ لن تُرسل إلا عبر المسار الذي يختاره حكيم."
                }
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
            setPadding(28, 30, 28, 24)
        }

        root.addView(TextView(this).apply {
            text = "حكيم"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        })

        status = TextView(this).apply {
            text = "جاهز لتحقيق مقصدك"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(8, 2, 8, 14)
        }
        root.addView(status)

        command = EditText(this).apply {
            hint = "ماذا تريد أن أنجز؟"
            minLines = 3
            maxLines = 8
            textSize = 19f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(18, 18, 18, 18)
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
            setPadding(8, 6, 8, 4)
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

        updateStatus = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(8, 6, 8, 2)
        }
        root.addView(updateStatus)

        root.addView(actionButton("التفاصيل") {
            val show = updateStatus.visibility != View.VISIBLE
            updateStatus.visibility = if (show) View.VISIBLE else View.GONE
            if (show) refreshUpdateStatus()
        })

        setContentView(root)
    }

    private fun executeBestRoute(text: String) {
        if (text.isBlank() && attachments.isEmpty()) {
            toast("اكتب الغاية أو أرفق محتوى")
            return
        }
        capture(text, "best_route")
        val decision = HakimModelToolRouter.decide(this, text, attachments)
        status.text = "يجري تنفيذ مقصدك عبر أفضل مسار متاح."

        when (decision.channel) {
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
                HakimModelToolRouter.recordOutcome(this, provider.id, true)
                status.text = "تم تسليم المهمة إلى القناة المختارة؛ لم يُعتمد النجاح بعد."
                return
            } catch (_: Exception) {
                HakimModelToolRouter.recordOutcome(this, provider.id, false)
                recordRoute("provider:" + provider.id, false)
            }
        }

        status.text = "تعذرت القنوات المباشرة؛ يستخدم حكيم المشاركة الآمنة كمسار احتياطي."
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
        status.text = "فُتحت القناة الرسمية المختارة، ولم يُعتمد النجاح قبل تحقق الأثر."
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

    private fun maybeOnboardAutoUpdate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || AutoUpdater.canInstallPackages(this)) return
        val p = getSharedPreferences("hakim", MODE_PRIVATE)
        val version = currentVersionCode()
        if (p.getLong("auto_update_onboarding_version", -1L) == version) return
        p.edit().putLong("auto_update_onboarding_version", version).apply()
        updateStatus.text = "التحديث التلقائي يحتاج السماح من هذا المصدر مرة واحدة."
    }

    private fun refreshUpdateStatus() {
        if (::updateStatus.isInitialized) updateStatus.text = AutoUpdater.statusSummary(this)
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(): Long = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) {
        0L
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
