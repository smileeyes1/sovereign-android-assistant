package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** مساحة عمل احترافية موحدة؛ لا تضيف أذونات ولا مكتبات UI ثقيلة. */
class HakimProfessionalHubActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildUi() {
        val p = HakimChatUi.palette(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(p.background)
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        content.addView(TextView(this).apply {
            text = "حكيم"
            textSize = 29f
            setTextColor(p.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
        })
        content.addView(TextView(this).apply {
            text = "مساحة عمل واحدة • محلي أولاً • خصوصية وأقل صلاحية"
            textSize = 14f
            setTextColor(p.muted)
            gravity = Gravity.START
            setPadding(0, dp(4), 0, dp(12))
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(p.text)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = HakimChatUi.rounded(p.surface, 16f, this@HakimProfessionalHubActivity, p.border)
        }
        content.addView(status)

        content.addView(section("العمل", p.text))
        content.addView(card("محادثة حكيم", "اكتب الغاية فقط، وشاهد العمل الجاري والتحقق.", "فتح المحادثة") {
            startActivity(Intent(this, HakimAgentsChatActivity::class.java))
        })
        content.addView(card("القرآن المحلي", "بحث وقراءة من النص المحلي المتحقق دون شبكة.", "فتح القرآن") {
            startActivity(Intent(this, HakimQuranActivity::class.java))
        })

        content.addView(section("الأدوات", p.text))
        content.addView(card("متصفح حكيم", "تصفح وبحث وتنفيذ داخل المتصفح المدمج.", "فتح المتصفح") {
            startActivity(Intent(this, MainActivity::class.java))
        })
        content.addView(card("الاتصال المحلي", "إدارة الاتصال والمسارات المحلية المصرح بها.", "فتح مركز الاتصال") {
            startActivity(Intent(this, UnifiedHomeActivity::class.java).putExtra("hakim_control_center", true))
        })

        content.addView(section("الثقة والصحة", p.text))
        content.addView(card("حالة النظام", "الصلاحيات، الخصوصية، الصحة، والمصادر المحلية.", "فتح الإعدادات") {
            startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
        })
        content.addView(card("فحص ذاتي", "فحص محلي دون إرسال بيانات أو إخفاء الفشل.", "ابدأ الفحص") { runSelfCheck() })
        content.addView(card("التحديث الموثوق", "تحقق من التحديث دون تثبيت صامت أو تغيير الهوية.", "فحص التحديث") {
            AutoUpdater.checkAsync(this)
            status.postDelayed({ refreshStatus() }, 1800L)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.background)
            addView(content)
        })
    }

    private fun refreshStatus() {
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val quran = if (HakimVerifiedQuranCorpus.isReady(this)) "جاهز" else "يحتاج تهيئة"
        val secure = if (HakimUnifiedRelay.isConfigured(this)) "مهيأ" else "غير مهيأ"
        val local = if (getSharedPreferences("hakim", MODE_PRIVATE).getBoolean("local_adb_paired", false)) "مقترن" else "غير مقترن"
        status.text = "الإصدار: " + (info?.versionName ?: "غير معلوم") +
            "\nالقرآن المحلي: " + quran +
            "\nالقناة الآمنة: " + secure +
            "\nالاتصال المحلي: " + local
    }

    private fun runSelfCheck() {
        status.text = "أجري فحصاً ذاتياً محلياً…"
        Thread {
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
            val report = runCatching { HakimSelfCheck.run(applicationContext) }.getOrNull()
            runOnUiThread {
                status.text = if (report == null) {
                    "تعذر إكمال الفحص الذاتي دون افتراض النجاح."
                } else {
                    "الفحص الذاتي: " + report.optString("status", "غير معلوم") +
                        "\nالقرآن المحلي: " +
                        if (HakimVerifiedQuranCorpus.isReady(this)) "جاهز" else "غير جاهز"
                }
            }
        }.start()
    }

    private fun section(label: String, color: Int) = TextView(this).apply {
        text = label
        textSize = 17f
        setTextColor(color)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.START
        setPadding(0, dp(18), 0, dp(7))
    }

    private fun card(title: String, description: String, actionLabel: String, action: () -> Unit): LinearLayout {
        val p = HakimChatUi.palette(this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = HakimChatUi.rounded(p.surface, 18f, this@HakimProfessionalHubActivity, p.border)
            addView(TextView(this@HakimProfessionalHubActivity).apply {
                text = title
                textSize = 17f
                setTextColor(p.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@HakimProfessionalHubActivity).apply {
                text = description
                textSize = 13.5f
                setTextColor(p.muted)
                setPadding(0, dp(4), 0, dp(9))
            })
            addView(TextView(this@HakimProfessionalHubActivity).apply {
                text = actionLabel
                textSize = 14f
                setTextColor(p.onAccent)
                gravity = Gravity.CENTER
                minHeight = dp(44)
                isClickable = true
                isFocusable = true
                background = HakimChatUi.rounded(p.accent, 18f, this@HakimProfessionalHubActivity)
                setOnClickListener { action() }
            })
        }.also {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, dp(9))
            it.layoutParams = lp
        }
    }

    private fun dp(v: Int) = HakimChatUi.dp(this, v.toFloat())
}
