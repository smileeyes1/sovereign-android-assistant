package ps.hakim.phoneagent

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class UnifiedHomeActivity : Activity() {
    private lateinit var adbStatus: TextView

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
            setPadding(8, 8, 8, 12)
        })

        root.addView(TextView(this).apply {
            text = "أقل إشارة تكفي: حكيم يستعيد المقصد، يختار الوكلاء، يستخدم بياناتك المحلية المصرح بها، وينفذ الآمن حتى نهاية المسار أو بوابة القرار الجوهري."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 14)
        })

        root.addView(button("تحدث مع حكيم المحلي — بدون سحابة") {
            getSharedPreferences("hakim", MODE_PRIVATE).edit()
                .putString("last_url", "http://localhost:8790/")
                .apply()
            startActivity(Intent(this, MainActivity::class.java))
        })

        root.addView(button("محادثة الوكلاء — ابدأ من هنا") {
            startActivity(Intent(this, HakimAgentsChatActivity::class.java))
        })

        root.addView(button("النظام الحاكم والبيانات") {
            startActivity(Intent(this, HakimSystemSettingsActivity::class.java))
        })

        root.addView(button("اختيار حكيم كشاشة الهاتف الرئيسية") {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "افتح الإعدادات ← التطبيقات الافتراضية ← تطبيق الشاشة الرئيسية واختر حكيم", Toast.LENGTH_LONG).show()
            }
        })

        adbStatus = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 8)
        }
        root.addView(adbStatus)

        root.addView(TextView(this).apply {
            text = "تأسيس الاتصال المحلي — مرة واحدة: أندرويد يطلب في أول مرة فقط رمز اقتران من ٦ أرقام. هذا حاجز أمان للنظام نفسه؛ بعد نجاحه يحفظ حكيم هويته في AndroidKeyStore ويعيد الاتصال تلقائيًا دون إعادة الرمز عادةً."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 0, 12, 10)
        })

        root.addView(button("تأسيس ADB المحلي — الاتصال المحلي لمرة واحدة") {
            startGuidedLocalAdbSetup()
        })

        root.addView(button("إعادة الاتصال تلقائيًا") {
            HakimLocalPairing.reconnectAsync(this)
            Toast.makeText(this, "يحاول حكيم استعادة الاتصال المحلي تلقائيًا", Toast.LENGTH_SHORT).show()
            adbStatus.postDelayed({ refresh() }, 1200L)
        })

        root.addView(button("مركز القيادة") {
            startActivity(Intent(this, CommandCenterActivity::class.java))
        })

        root.addView(button("متصفح حكيم") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        root.addView(TextView(this).apply {
            text = "يحفظ حكيم مفاتيح الربط وبياناته المحلية داخل AndroidKeyStore، ويعيد الاتصال تلقائيًا. كلمات المرور ورموز التحقق والبطاقات لا تُخزن في خزنة حكيم ولا تُرسل كنص إلى نموذج الذكاء."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(12, 22, 12, 8)
        })

        setContentView(root)
        refresh()
    }

    private fun maybeBootstrapLocalAdb() {
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        if (prefs.getBoolean("local_adb_paired", false)) {
            HakimLocalPairing.reconnectAsync(this)
            return
        }
        // لا نقذف المستخدم إلى إعدادات المطورين تلقائيًا؛ نعرض الدليل مرة واحدة أولًا.
        if (prefs.getBoolean("local_adb_first_run_started", false)) return
        prefs.edit().putBoolean("local_adb_first_run_started", true).apply()
        adbStatus.postDelayed({ showLocalAdbSetupGuide(firstRun = true) }, 500L)
    }

    private fun startGuidedLocalAdbSetup() {
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        if (prefs.getBoolean("local_adb_paired", false)) {
            HakimLocalPairing.reconnectAsync(this)
            Toast.makeText(this, "حكيم مقترن أصلًا؛ أعيد الاتصال بدل طلب رمز جديد", Toast.LENGTH_LONG).show()
            adbStatus.postDelayed({ refresh() }, 1200L)
            return
        }
        showLocalAdbSetupGuide(firstRun = false)
    }

    private fun showLocalAdbSetupGuide(firstRun: Boolean) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(if (firstRun) "تأسيس حكيم المحلي — مرة واحدة" else "الاتصال المحلي — خطوة أندرويد الوحيدة")
            .setMessage(
                "أندرويد يفرض رمز اقتران من ٦ أرقام في أول مرة، ولا يسمح لأي تطبيق بقراءة هذا الرمز تلقائيًا.\n\n" +
                    "سأفتح لك شاشة «التصحيح اللاسلكي». هناك اضغط «إقران الجهاز باستخدام رمز الاقتران»، واترك نافذة الرمز مفتوحة. سيظهر إشعار حكيم لإدخال الأرقام الستة فقط.\n\n" +
                    "بعد النجاح يحفظ حكيم هويته محليًا ويعيد الاتصال تلقائيًا؛ لن أطلب منك إعدادًا تقنيًا إضافيًا ما لم يفرضه أندرويد."
            )
            .setPositiveButton("افتح شاشة الاقتران") { _, _ -> ensureNotificationPermissionThenSetup() }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    private fun ensureNotificationPermissionThenSetup() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        } else {
            beginLocalAdbSetup()
        }
    }

    private fun beginLocalAdbSetup() {
        val prefs = getSharedPreferences("hakim", MODE_PRIVATE)
        if (prefs.getBoolean("local_adb_paired", false)) {
            HakimLocalPairing.reconnectAsync(this)
            refresh()
            return
        }
        HakimLocalPairing.openWirelessDebuggingSettings(this)
        refresh()
    }

    private fun refresh() {
        if (::adbStatus.isInitialized) adbStatus.text = HakimLocalPairing.currentSummary(this)
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
