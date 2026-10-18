package ps.hakim.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * The exported deep link is untrusted input. Pairing is never committed
 * until the phone owner explicitly approves the destination on screen.
 */
class HakimPairingActivity : Activity() {
    private var pairingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePair(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pairingDialog?.dismiss()
        pairingDialog = null
        setIntent(intent)
        handlePair(intent)
    }

    override fun onDestroy() {
        pairingDialog?.dismiss()
        pairingDialog = null
        super.onDestroy()
    }

    private fun rejectInvalidPairing() {
        Toast.makeText(this, "رابط الاقتران غير صالح — لم يتغير اتصال حكيم", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handlePair(i: Intent?) {
        val uri = i?.data
        if (i?.action != Intent.ACTION_VIEW || uri?.scheme != "hakim" ||
            uri.host != "pair" || !uri.isHierarchical) {
            rejectInvalidPairing()
            return
        }

        val token = uri.getQueryParameter("token").orEmpty()
        val topic = uri.getQueryParameter("relay_topic")
        val resultTopic = uri.getQueryParameter("relay_result_topic")
        val relayKey = uri.getQueryParameter("relay_key")
        val bridgeBase = uri.getQueryParameter("bridge_base")
        val destinationHost = HakimUnifiedRelay.pairingDestinationHost(
            topic, resultTopic, relayKey, bridgeBase
        )
        if (token.length !in 32..256 || destinationHost == null) {
            rejectInvalidPairing()
            return
        }

        // Never display tokens or keys from a deep link, even in a confirmation message.
        pairingDialog = AlertDialog.Builder(this)
            .setTitle("تأكيد اقتران حكيم")
            .setMessage(
                "هناك رابط خارجي يطلب ربط حكيم بقناة تحكم على هاتفك.\n\n" +
                    "جهة الاتصال: $destinationHost\n\n" +
                    "لا توافق إلا إذا بدأتَ عملية الربط بنفسك وتثق بهذه الجهة. " +
                    "الإلغاء يُبقي اتصال حكيم كما كان."
            )
            .setPositiveButton("أوافق على الاقتران") { _, _ ->
                confirmPairing(token, topic, resultTopic, relayKey, bridgeBase)
            }
            .setNegativeButton("إلغاء") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        pairingDialog?.show()
    }

    private fun confirmPairing(
        token: String, topic: String?, resultTopic: String?,
        relayKey: String?, bridgeBase: String?
    ) {
        // This must remain exclusively inside the explicit, user-tapped positive callback.
        val configured = HakimUnifiedRelay.configure(this, topic, resultTopic, relayKey, bridgeBase)
        if (!configured) {
            Toast.makeText(this, "تعذر تأكيد الاقتران؛ لم يبدأ اتصال جديد", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val saved = getSharedPreferences("hakim", MODE_PRIVATE).edit()
            .putString("pair_token", token)
            .putBoolean("pairing_disabled_by_user", false)
            .putLong("secure_pairing_at", System.currentTimeMillis())
            .commit()
        if (!saved) {
            Toast.makeText(this, "تعذر حفظ الاقتران", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        HakimUnifiedRelay.start(applicationContext)
        try {
            val service = Intent(this, HakimService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
        } catch (_: Exception) {}
        HakimUnifiedRelay.sendPairingAckAsync(applicationContext)
        Toast.makeText(this, "تم تأكيد اقتران حكيم", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, CommandCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
