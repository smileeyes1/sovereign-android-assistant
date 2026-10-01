package ps.hakim.phoneagent

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * بوابة موافقة أندرويد لمرة واحدة على VpnService.
 * لا تعرض إعدادات تقنية ولا تنفذ شيئًا إذا رفض المستخدم الموافقة.
 */
class HakimFamilyDnsVpnActivity : ComponentActivity() {
    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            HakimDeviceProtection.markConsentGranted(this)
            HakimDeviceProtection.ensureRunning(this)
        } else {
            HakimDeviceProtection.markConsentDenied(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val consent = VpnService.prepare(this)
        if (consent == null) {
            HakimDeviceProtection.markConsentGranted(this)
            HakimDeviceProtection.ensureRunning(this)
            finish()
        } else {
            HakimDeviceProtection.markConsentRequired(this)
            consentLauncher.launch(consent)
        }
    }
}
