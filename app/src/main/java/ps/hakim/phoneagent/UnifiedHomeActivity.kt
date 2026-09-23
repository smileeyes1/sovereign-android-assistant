package ps.hakim.phoneagent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.app.AlertDialog
import android.widget.TextView

class UnifiedHomeActivity : Activity() {
    private lateinit var adbStatus: TextView
    private lateinit var directModelStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        maybeBootstrapLocalAdb()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        HakimLocalPairing.reconnectAsync(this)
        adbStatus.postDelayed({ refresh() }, 1500L)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            beginLocalAdbSetup()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
        }

        root.addView(TextView(this).apply {
            text = "حكيم — التطبيق الموحّد"
            textSize = 27f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 18)
        })

        adbStatus = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 16)
        }
        root.addView(adbStatus)

        root.addView(button("تأسيس ADB المحلي") {
            ensureNotificationPermissionThenSetup()
        })

        root.addView(button("إعادة الاتصال") {
            HakimLocalPairing.reconnectAsync(this)
            adbStatus.postDelayed({ refresh() }, 1200L)
        })

        directModelStatus = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 8)
        }
        root.addView(directModelStatus)

        root.addView(button("إعداد Gemini المباشر") {
            showGeminiKeyDialog()
        })

        root.addView(button("اختبار المحرك المباشر") {
            testDirectEngine()
        })

        root.addView(button("إنشاء مفتاح Gemini مجاني") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        })

        root.addView(button("مسح مفتاح Gemini") {
            HakimSecretStore.remove(this, GeminiDirectEngine.SECRET_GEMINI_KEY)
            getSharedPreferences(GeminiDirectEngine.PREFS, MODE_PRIVATE)
                .edit()
                .remove("gemini_direct_field_verified")
                .apply()
            refresh()
        })

        root.addView(button("محادثة جديدة للمحرك") {
            GeminiDirectEngine(this).clearConversation()
            android.widget.Toast.makeText(this, "تم بدء سياق مباشر جديد.", android.widget.Toast.LENGTH_SHORT).show()
        })

        root.addView(button("مركز القيادة") {
            startActivity(Intent(this, CommandCenterActivity::class.java))
        })

        root.addView(button("متصفح حكيم") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        root.addView(TextView(this).apply {
            text = "وضع حكيم الافتراضي: مجاني فقط. أنشئ من AI Studio مفتاح Authorization جديدًا لمشروع Free Tier غير مربوط بالفوترة، ثم ألصقه مرة واحدة. يُحفظ محليًا مشفّرًا في AndroidKeyStore ولا يُطبع في السجل. أسماء الله والقرآن حاكمة للقيم والحدود، وليست آلية تقنية خفية."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 22, 12, 8)
        })

        setContentView(root)
        refresh()
    }

    private fun showGeminiKeyDialog() {
        val input = EditText(this).apply {
            hint = "ألصق مفتاح Gemini API"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Gemini مباشر داخل حكيم")
            .setMessage("استخدم مفتاح Authorization جديدًا من AI Studio لمشروع Free Tier غير مربوط بالفوترة. يُحفظ مشفّرًا في AndroidKeyStore ولا يظهر في المحادثة أو السجل.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    HakimSecretStore.put(this, GeminiDirectEngine.SECRET_GEMINI_KEY, key)
                    refresh()
                    testDirectEngine()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun testDirectEngine() {
        if (!HakimSecretStore.has(this, GeminiDirectEngine.SECRET_GEMINI_KEY)) {
            android.widget.Toast.makeText(this, "أدخل مفتاح Gemini أولًا.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        directModelStatus.text = "يختبر حكيم المحرك المباشر…"
        Thread {
            val result = GeminiDirectEngine(this).complete(
                "أجب بالعربية بكلمة واحدة فقط: جاهز",
                emptyList(),
                onDelta = {}
            )
            runOnUiThread {
                when (result) {
                    is HakimInferenceEngine.Result.Success -> {
                        getSharedPreferences(GeminiDirectEngine.PREFS, MODE_PRIVATE)
                            .edit()
                            .putBoolean("gemini_direct_field_verified", true)
                            .putLong("gemini_direct_verified_at", System.currentTimeMillis())
                            .apply()
                        directModelStatus.text = "✓ Gemini مباشر متصل؛ الرد يعود داخل حكيم"
                    }
                    is HakimInferenceEngine.Result.NeedsAuthorization -> {
                        directModelStatus.text = "تعذر التفويض: " + result.reason
                    }
                    is HakimInferenceEngine.Result.Unavailable -> {
                        directModelStatus.text = "غير متاح: " + result.reason
                    }
                    is HakimInferenceEngine.Result.Failure -> {
                        directModelStatus.text = "فشل الاختبار: " + result.reason
                    }
                }
            }
        }.start()
    }

    private fun maybeBootstrapLocalAdb() {
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        if (prefs.getBoolean("local_adb_paired", false)) {
            HakimLocalPairing.reconnectAsync(this)
            return
        }
        if (prefs.getBoolean("local_adb_first_run_started", false)) return
        prefs.edit().putBoolean("local_adb_first_run_started", true).apply()
        ensureNotificationPermissionThenSetup()
    }

    private fun ensureNotificationPermissionThenSetup() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        } else {
            beginLocalAdbSetup()
        }
    }

    private fun beginLocalAdbSetup() {
        HakimLocalPairing.openWirelessDebuggingSettings(this)
        refresh()
    }

    private fun refresh() {
        if (::adbStatus.isInitialized) adbStatus.text = HakimLocalPairing.currentSummary(this)
        if (::directModelStatus.isInitialized) {
            val configured = HakimSecretStore.has(this, GeminiDirectEngine.SECRET_GEMINI_KEY)
            val verified = getSharedPreferences(GeminiDirectEngine.PREFS, MODE_PRIVATE)
                .getBoolean("gemini_direct_field_verified", false)
            directModelStatus.text = when {
                configured && verified -> "✓ المحرك المباشر: Gemini متصل ومتحقق على هذا الجهاز"
                configured -> "المحرك المباشر: مفتاح محفوظ — يلزم اختبار الاتصال"
                else -> "المحرك المباشر: غير مُعدّ بعد"
            }
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 18f
        setOnClickListener { action() }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 42043
    }
}
