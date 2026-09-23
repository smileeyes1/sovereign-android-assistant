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
        HakimFreePolicy.setFreeOnly(this, true)
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

        root.addView(TextView(this).apply {
            text = "وضع الذكاء: مجاني فقط — لا يستخدم حكيم محركًا مدفوعًا تلقائيًا"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 10)
        })

        root.addView(button("إعداد OpenRouter المجاني") {
            showOpenRouterKeyDialog()
        })

        root.addView(button("اختبار أفضل محرك مجاني") {
            testBestFreeEngine()
        })

        root.addView(button("إنشاء مفتاح OpenRouter مجاني") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/settings/keys")))
        })

        root.addView(button("مسح مفتاح OpenRouter") {
            HakimSecretStore.remove(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)
            refresh()
        })

        root.addView(button("إعداد Gemini Free Tier اختياري") {
            showGeminiKeyDialog()
        })

        root.addView(button("تأكيد/إلغاء Gemini Free Tier") {
            val next = !HakimFreePolicy.geminiFreeTierConfirmed(this)
            HakimFreePolicy.setGeminiFreeTierConfirmed(this, next)
            refresh()
        })

        root.addView(button("إنشاء/عرض مفتاح Gemini") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        })

        root.addView(button("مسح مفتاح Gemini") {
            HakimSecretStore.remove(this, GeminiDirectEngine.SECRET_GEMINI_KEY)
            HakimFreePolicy.setGeminiFreeTierConfirmed(this, false)
            getSharedPreferences(GeminiDirectEngine.PREFS, MODE_PRIVATE)
                .edit()
                .remove("gemini_direct_field_verified")
                .apply()
            refresh()
        })

        root.addView(button("محادثة جديدة للمحركات") {
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
            text = "يحفظ حكيم المفاتيح داخل AndroidKeyStore ولا يطبعها في السجل. OpenRouter/free صفر السعر للرموز لكنه محدود بالحصة المجانية. Gemini لا يدخل مصفوفة المجاني إلا بعد تأكيدك أن المفتاح تابع لـ Free Tier."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 22, 12, 8)
        })

        setContentView(root)
        refresh()
    }

    private fun showOpenRouterKeyDialog() {
        val input = EditText(this).apply {
            hint = "ألصق مفتاح OpenRouter"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("OpenRouter المجاني داخل حكيم")
            .setMessage("يستخدم حكيم المسار openrouter/free فقط. يُحفظ المفتاح مشفّرًا ولا يُطبع في السجل.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    HakimSecretStore.put(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY, key)
                    refresh()
                    testBestFreeEngine()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showGeminiKeyDialog() {
        val input = EditText(this).apply {
            hint = "ألصق مفتاح Gemini API"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Gemini مباشر داخل حكيم")
            .setMessage("يُحفظ المفتاح مشفّرًا في AndroidKeyStore ولا يظهر في المحادثة أو السجل.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotBlank()) {
                    HakimSecretStore.put(this, GeminiDirectEngine.SECRET_GEMINI_KEY, key)
                    refresh()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun testBestFreeEngine() {
        val ranked = HakimWisdomMatrix.choose(
            this,
            "أجب بالعربية بكلمة واحدة فقط: جاهز",
            emptyList()
        )
        val engine = ranked?.engine
        if (engine == null) {
            directModelStatus.text = "لا يوجد محرك مجاني مباشر مهيأ. ابدأ بـ OpenRouter المجاني."
            return
        }

        directModelStatus.text = "يختبر حكيم " + engine.displayName + "…"
        val started = System.currentTimeMillis()
        Thread {
            val result = engine.complete(
                "أجب بالعربية بكلمة واحدة فقط: جاهز",
                emptyList(),
                onDelta = {}
            )
            val latency = (System.currentTimeMillis() - started).coerceAtLeast(0L)
            HakimEngineTelemetry.record(
                this,
                engine.id,
                result is HakimInferenceEngine.Result.Success,
                latency
            )
            runOnUiThread {
                directModelStatus.text = when (result) {
                    is HakimInferenceEngine.Result.Success ->
                        "✓ " + engine.displayName + " متصل؛ الرد يعود داخل حكيم"
                    is HakimInferenceEngine.Result.NeedsAuthorization ->
                        "تعذر التفويض: " + result.reason
                    is HakimInferenceEngine.Result.Unavailable ->
                        "غير متاح: " + result.reason
                    is HakimInferenceEngine.Result.Failure ->
                        "فشل الاختبار: " + result.reason
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
            val openRouter = HakimSecretStore.has(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)
            val gemini = HakimSecretStore.has(this, GeminiDirectEngine.SECRET_GEMINI_KEY)
            val geminiFree = HakimFreePolicy.geminiFreeTierConfirmed(this)
            val available = HakimWisdomMatrix.rank(this, "", emptyList())
            directModelStatus.text = buildString {
                append("المحركات المجانية المباشرة: ")
                if (available.isEmpty()) append("غير مهيأة")
                else append(available.joinToString(" ← ") { it.engine.displayName })
                append("\nOpenRouter=")
                append(if (openRouter) "مهيأ" else "غير مهيأ")
                append(" | Gemini=")
                append(if (gemini && geminiFree) "Free Tier مؤكد" else if (gemini) "مفتاح موجود غير مؤكد مجانيًا" else "غير مهيأ")
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
