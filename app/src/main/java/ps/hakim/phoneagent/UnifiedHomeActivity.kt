package ps.hakim.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * إعدادات المنتج النهائية. التفاصيل الهندسية والمزودون والمفاتيح لا تظهر
 * في الواجهة العادية؛ تبقى داخل طبقات التنفيذ والتشخيص.
 */
class UnifiedHomeActivity : Activity() {
    private lateinit var intelligenceStatus: TextView
    private lateinit var organizationStatus: TextView
    private lateinit var updateStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimFreePolicy.setFreeOnly(this, true)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(android.graphics.Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "الإعدادات"
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 20)
            HakimUiKit.title(this)
        })

        intelligenceStatus = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 8)
        }
        root.addView(intelligenceStatus)

        organizationStatus = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 18)
        }
        root.addView(organizationStatus)

        updateStatus = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 8)
        }
        root.addView(updateStatus)

        root.addView(button("فحص تحديث حكيم") {
            updateStatus.text = "يفحص وجود تحديث موثّق…"
            AutoUpdater.checkAsync(this)
            updateStatus.postDelayed({
                updateStatus.text = AutoUpdater.statusSummary(this)
            }, 1800L)
        })

        root.addView(button("ربط خدمة الذكاء", primary = true) {
            OpenRouterOAuthManager.start(this)
        })

        root.addView(button("اختبار الاتصال") {
            testConnection()
        })

        root.addView(button("بدء محادثة جديدة") {
            GeminiDirectEngine(this).clearConversation()
            getSharedPreferences("hakim_conversation", MODE_PRIVATE)
                .edit()
                .remove("recent")
                .apply()
            intelligenceStatus.text = "بدأت محادثة جديدة."
        })

        root.addView(button("الخصوصية والأمان") {
            showPrivacy()
        })

        root.addView(button("مسح سجل المحادثة المحلي") {
            confirmClearLocalData()
        })

        root.addView(button("إلغاء ربط خدمات الذكاء") {
            confirmDisconnectIntelligence()
        })

        root.addView(button("إعدادات التطبيق في أندرويد") {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        })

        root.addView(button("العودة إلى حكيم") { finish() })

        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val openRouter = HakimSecretStore.has(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)
        val gemini = HakimSecretStore.has(this, GeminiDirectEngine.SECRET_GEMINI_KEY) &&
            HakimFreePolicy.geminiFreeTierConfirmed(this)
        intelligenceStatus.text = if (openRouter || gemini) {
            "خدمة الذكاء: جاهزة"
        } else {
            "خدمة الذكاء: تحتاج ربطًا لمرة واحدة"
        }

        updateStatus.text = AutoUpdater.statusSummary(this)

        val policy = HakimEnterprisePolicy.current(this)
        organizationStatus.text = if (policy.managed) {
            "هذا الجهاز مُدار بسياسة المؤسسة."
        } else {
            "الوضع الشخصي — الصلاحيات والبيانات بأقل نطاق افتراضيًا."
        }
    }

    private fun testConnection() {
        val ranked = HakimWisdomMatrix.choose(this, "أجب بكلمة: جاهز", emptyList())
        val engine = ranked?.engine
        if (engine == null) {
            intelligenceStatus.text = "خدمة الذكاء غير جاهزة بعد."
            return
        }

        intelligenceStatus.text = "يفحص الاتصال…"
        Thread {
            val result = engine.complete("أجب بكلمة واحدة فقط: جاهز", emptyList(), onDelta = {})
            runOnUiThread {
                intelligenceStatus.text = when (result) {
                    is HakimInferenceEngine.Result.Success -> "خدمة الذكاء: جاهزة"
                    is HakimInferenceEngine.Result.NeedsAuthorization -> "تحتاج الخدمة ربطًا لمرة واحدة."
                    is HakimInferenceEngine.Result.Unavailable -> "الخدمة غير متاحة مؤقتًا."
                    is HakimInferenceEngine.Result.Failure -> "تعذر الاتصال مؤقتًا."
                }
            }
        }.start()
    }

    private fun confirmClearLocalData() {
        AlertDialog.Builder(this)
            .setTitle("مسح البيانات المحلية")
            .setMessage("سيُمسح سجل المحادثة وسجل التدقيق المحلي وذاكرة آخر مخرج. لن تُحذف ملفاتك المحفوظة في التنزيلات.")
            .setPositiveButton("مسح") { _, _ ->
                getSharedPreferences("hakim_conversation", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("hakim_local_artifacts", MODE_PRIVATE).edit().clear().apply()
                HakimAuditTrail.clear(this)
                intelligenceStatus.text = "مُسحت البيانات المحلية."
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDisconnectIntelligence() {
        AlertDialog.Builder(this)
            .setTitle("إلغاء الربط")
            .setMessage("سيُحذف اعتماد خدمات الذكاء المحفوظ من هذا الجهاز.")
            .setPositiveButton("إلغاء الربط") { _, _ ->
                HakimSecretStore.remove(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)
                HakimSecretStore.remove(this, GeminiDirectEngine.SECRET_GEMINI_KEY)
                HakimFreePolicy.setGeminiFreeTierConfirmed(this, false)
                refresh()
            }
            .setNegativeButton("رجوع", null)
            .show()
    }

    private fun showPrivacy() {
        AlertDialog.Builder(this)
            .setTitle("الخصوصية والأمان")
            .setMessage(
                "يعالج حكيم ما يستطيع محليًا أولًا. لا تُرسل المرفقات أو النصوص إلى خدمة خارجية إلا عندما تحتاج المهمة ذلك، " +
                    "وتُحفظ أسرار الاتصال في مخزن أندرويد الآمن. قد تفرض مؤسستك قيودًا إضافية على الويب أو المرفقات أو الذكاء الخارجي."
            )
            .setPositiveButton("حسنًا", null)
            .show()
    }

    private fun button(label: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 17f
        if (primary) HakimUiKit.primary(this) else HakimUiKit.secondary(this)
        setOnClickListener { action() }
    }
}
