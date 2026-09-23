package ps.hakim.phoneagent

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
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
        private const val ATTACHMENT_PICKER_REQUEST = 7301
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
            setPadding(18, 18, 18, 18)
        }

        root.addView(TextView(this).apply {
            text = "حكيم"
            textSize = 27f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        })

        status = TextView(this).apply {
            text = "اكتب الغاية فقط. حكيم يختار الأداة أو النموذج أو المتصفح ثم يحافظ على أقل صلاحية وكلفة."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 10)
        }
        root.addView(status)

        updateStatus = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(8, 2, 8, 4)
        }
        root.addView(updateStatus)

        command = EditText(this).apply {
            hint = "ماذا تريد؟"
            minLines = 4
            maxLines = 10
            textSize = 18f
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(14, 14, 14, 14)
        }
        root.addView(
            command,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        attachmentStatus = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.RIGHT
            setPadding(8, 8, 8, 4)
        }
        root.addView(attachmentStatus)

        val attachmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        attachmentRow.addView(
            actionButton("إرفاق صورة/ملف/فيديو") {
                startActivityForResult(HakimAttachmentGateway.pickerIntent(), ATTACHMENT_PICKER_REQUEST)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        attachmentRow.addView(
            actionButton("مسح المرفقات") {
                attachments.clear()
                refreshAttachmentStatus()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(attachmentRow)

        root.addView(actionButton("نفّذ بأفضل مسار") {
            executeBestRoute(command.text.toString().trim())
        })

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        utilityRow.addView(
            actionButton("مشاركة آمنة") {
                shareToAny(command.text.toString().trim())
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        utilityRow.addView(
            actionButton("المتصفح") {
                openInHakim(command.text.toString().trim())
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(utilityRow)

        root.addView(actionButton("فحص التحديث") {
            if (!AutoUpdater.canInstallPackages(this)) {
                AutoUpdater.openInstallPermissionSettings(this)
            } else {
                AutoUpdater.checkAsync(this)
                toast("يجري فحص التحديث")
                updateStatus.postDelayed({ refreshUpdateStatus() }, 1800L)
            }
        })

        root.addView(TextView(this).apply {
            text = "الأولوية: أداة حاسمة أو ويب حديث عند الحاجة، ثم قناة نموذج متاحة رسميًا، ثم بديل آمن. لا تُفترض API مدفوعة ولا تُنسخ أسرار الحسابات بين المزودين."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(10, 16, 10, 4)
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
        val providerLabel = decision.provider?.label ?: "أداة محلية"
        status.text = "المسار: " + providerLabel + "\n" + decision.reason

        when (decision.channel) {
            HakimModelToolRouter.Channel.LOCAL_BROWSER -> openInHakim(text)
            HakimModelToolRouter.Channel.PROVIDER_APP -> sendToProviderApp(text, decision)
            HakimModelToolRouter.Channel.SYSTEM_SHARE -> shareToAny(text)
            HakimModelToolRouter.Channel.PROVIDER_WEB -> openProviderWeb(text, decision)
        }
    }

    private fun sendToProviderApp(text: String, decision: HakimModelToolRouter.Decision) {
        val provider = decision.provider ?: run {
            shareToAny(text)
            return
        }
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
            recordRoute("provider:" + provider.id, true)
        } catch (_: Exception) {
            HakimModelToolRouter.recordOutcome(this, provider.id, false)
            recordRoute("provider:" + provider.id, false)
            val retry = HakimModelToolRouter.decide(this, text, attachments)
            if (retry.channel == HakimModelToolRouter.Channel.PROVIDER_APP &&
                retry.provider != null &&
                retry.provider.id != provider.id
            ) {
                status.text = "تعذر " + provider.label + "؛ ينتقل حكيم تلقائيًا إلى " + retry.provider.label
                sendToProviderApp(text, retry)
            } else {
                shareToAny(text)
            }
        }
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
        status.text = "فتح حكيم جلسة " + provider.label + " الرسمية. نُسخ الأمر المحكوم احتياطًا دون نقل أسرار حساب."
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
            recordRoute("share", true)
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
        recordRoute("browser", true)
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
