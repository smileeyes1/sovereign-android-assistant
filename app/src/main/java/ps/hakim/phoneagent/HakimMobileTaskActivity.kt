package ps.hakim.phoneagent

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * مدخل هاتف-أول لمهام حكيم المحددة مسبقًا.
 * لا يقبل أوامر حرة من الروابط ولا يغيّر حالة الجهاز قبل موافقة محلية صريحة.
 */
class HakimMobileTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        val valid = data?.scheme == "hakim" &&
            data.host == "task" &&
            data.path == "/network-protection"

        if (!valid) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("حماية الشبكة")
            .setMessage(
                "سيبدأ حكيم بطبقة الحماية الأولى على هذا الهاتف: " +
                    "ضبط DNS العائلي الخاص والتحقق منه. " +
                    "لن يستخدم root ولن يغيّر WAN أو الراوتر في هذه الخطوة."
            )
            .setNegativeButton("إلغاء") { _, _ -> finish() }
            .setPositiveButton("موافقة وبدء") { _, _ ->
                HakimNetworkProtectionTask.start(applicationContext)
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
