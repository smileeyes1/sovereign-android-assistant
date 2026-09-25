package ps.hakim.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * Local pairing gate for Hakim.
 *
 * A deep link may carry configuration, but it cannot silently replace the
 * current execution channel. A different/new channel requires explicit local
 * confirmation on the phone. Reopening the exact same pairing is idempotent.
 */
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

        val valid = uri?.scheme == "hakim" &&
            uri.host == "pair" &&
            token.length in 32..256 &&
            HakimUnifiedRelay.validConfigurationInput(topic, resultTopic, relayKey)

        if (!valid) {
            Toast.makeText(this, "تعذر اعتماد رابط الاقتران — لم تُحفظ إعدادات ناقصة", Toast.LENGTH_LONG).show()
            returnToHakim()
            return
        }

        if (HakimUnifiedRelay.configurationMatches(this, topic, resultTopic, relayKey)) {
            commitPairing(token, topic, resultTopic, relayKey)
            return
        }

        val replacing = HakimUnifiedRelay.isConfigured(this)
        AlertDialog.Builder(this)
            .setTitle(if (replacing) "تغيير قناة حكيم؟" else "ربط قناة حكيم؟")
            .setMessage(
                if (replacing) {
                    "سيؤدي هذا إلى استبدال قناة التنفيذ المشفّرة الحالية. اعتمد التغيير فقط إذا بدأت عملية الاقتران بنفسك."
                } else {
                    "سيتم ربط حكيم بقناة تنفيذ مشفّرة جديدة. اعتمدها فقط إذا بدأت عملية الاقتران بنفسك."
                }
            )
            .setPositiveButton("اعتماد") { _, _ ->
                commitPairing(token, topic, resultTopic, relayKey)
            }
            .setNegativeButton("رفض") { _, _ ->
                Toast.makeText(this, "لم يتم تغيير اقتران حكيم", Toast.LENGTH_SHORT).show()
                returnToHakim()
            }
            .setOnCancelListener { returnToHakim() }
            .show()
    }

    private fun commitPairing(
        token: String,
        topic: String?,
        resultTopic: String?,
        relayKey: String?
    ) {
        val ok = HakimUnifiedRelay.configure(this, topic, resultTopic, relayKey)
        if (!ok) {
            Toast.makeText(this, "تعذر اعتماد الاقتران", Toast.LENGTH_LONG).show()
            returnToHakim()
            return
        }

        getSharedPreferences("hakim", MODE_PRIVATE).edit()
            .putString("pair_token", token)
            .putBoolean("pairing_disabled_by_user", false)
            .putLong("secure_pairing_at", System.currentTimeMillis())
            .apply()

        HakimUnifiedRelay.start(applicationContext)
        try {
            val service = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service)
            else startService(service)
        } catch (_: Exception) {}

        HakimUnifiedRelay.sendPairingAckAsync(applicationContext)
        Toast.makeText(this, "تم ربط حكيم بالقناة المشفّرة", Toast.LENGTH_LONG).show()
        returnToHakim()
    }

    private fun returnToHakim() {
        startActivity(
            Intent(this, CommandCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
