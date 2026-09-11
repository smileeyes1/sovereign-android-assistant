package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class HakimGatewayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HakimConstitution.install(this)
        HakimLearning.initialize(this)

        val raw = when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.dataString.orEmpty()
            Intent.ACTION_SEND -> intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            Intent.ACTION_PROCESS_TEXT -> intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
            else -> ""
        }.trim()

        if (raw.isNotBlank()) {
            val url = when {
                raw.startsWith("https://") || raw.startsWith("http://") -> raw
                raw.contains(".") && !raw.contains(" ") -> "https://$raw"
                else -> "https://www.google.com/search?q=" + Uri.encode(raw.take(4000))
            }
            getSharedPreferences("hakim", MODE_PRIVATE).edit().putString("last_url", url).apply()
            HakimLearning.recordAttempt(this, "gateway")
            HakimLearning.recordResult(this, "gateway", true)
        }

        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
