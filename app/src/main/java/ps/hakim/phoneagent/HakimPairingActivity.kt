package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** بوابة الاقتران الخاصة بتطبيق حكيم الوحيد. */
class HakimPairingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePair(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePair(intent)
    }

    private fun handlePair(i: Intent?) {
        val uri = i?.data
        val token = uri?.getQueryParameter("token").orEmpty()
        val topic = uri?.getQueryParameter("relay_topic")
        val resultTopic = uri?.getQueryParameter("relay_result_topic")
        val relayKey = uri?.getQueryParameter("relay_key")
        val bridgeBase = uri?.getQueryParameter("bridge_base")

        val ok = uri?.scheme == "hakim" && uri.host == "pair" &&
            token.length in 32..256 &&
            HakimUnifiedRelay.configure(this, topic, resultTopic, relayKey, bridgeBase)

        if (ok) {
            getSharedPreferences("hakim", MODE_PRIVATE).edit()
                .putString("pair_token", token)
                .putBoolean("pairing_disabled_by_user", false)
                .putLong("secure_pairing_at", System.currentTimeMillis())
                .apply()
            HakimUnifiedRelay.start(applicationContext)
            try {
                val service = Intent(this, HakimService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
            } catch (_: Exception) {}
            HakimUnifiedRelay.sendPairingAckAsync(applicationContext)
            Toast.makeText(this, "تم ربط حكيم بالقناة المشفّرة", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "تعذر اعتماد رابط الاقتران — لم تُحفظ إعدادات ناقصة", Toast.LENGTH_LONG).show()
        }

        startActivity(Intent(this, CommandCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
