package ps.hakim.phoneagent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

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
            setPadding(8, 8, 8, 18)
        })

        adbStatus = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 16)
        }
        root.addView(adbStatus)

        root.addView(button("تأسيس الاتصال المحلي") {
            ensureNotificationPermissionThenSetup()
        })

        root.addView(button("إعادة الاتصال") {
            HakimLocalPairing.reconnectAsync(this)
            adbStatus.postDelayed({ refresh() }, 1200L)
        })

        root.addView(button("مركز القيادة") {
            startActivity(Intent(this, CommandCenterActivity::class.java))
        })

        root.addView(button("متصفح حكيم") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        root.addView(TextView(this).apply {
            text = "يحفظ حكيم مفتاح ADB داخل AndroidKeyStore، ويعيد الاتصال تلقائيًا بعد التشغيل أو استبدال الحزمة. لا يُخزن رمز الاقتران."
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
