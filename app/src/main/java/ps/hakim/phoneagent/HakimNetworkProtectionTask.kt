package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * طبقة حماية هاتفية محدودة وقابلة للتحقق.
 *
 * تستخدم ADB المحلي المأذون والمقترن على نفس الهاتف لضبط Private DNS فقط.
 * لا توجد خدمة وصول حساسة، ولا root، ولا صلاحية إعدادات خاصة، ولا shell عام.
 */
object HakimNetworkProtectionTask {
    const val VERSION = "HAKIM-NETWORK-PROTECTION-ANDROID-V2-LOCAL-ADB"
    const val FAMILY_DNS_HOST = "family-filter-dns.cleanbrowsing.org"

    private const val PREFS = "hakim_network_protection"
    private const val CHANNEL = "hakim_network_protection"
    private const val NOTIFICATION_ID = 74103

    private data class DnsState(
        val mode: String,
        val specifier: String
    )

    fun start(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("running", false)) return

        val before = snapshot(app)
        prefs.edit()
            .putBoolean("running", true)
            .putBoolean("pending_after_pairing", false)
            .putString("state", "CONNECTING_LOCAL_ADB")
            .putLong("last_attempt_at", System.currentTimeMillis())
            .putString("before_mode", before.mode)
            .putString("before_specifier", before.specifier)
            .apply()

        if (isProtected(before)) {
            finish(app, "VERIFIED", true, "الحماية مفعلة أصلًا على الهاتف.")
            return
        }

        thread(name = "hakim-network-protection-adb", isDaemon = true) {
            val manager = HakimAdbConnectionManager.get(app)
            val hakimPrefs = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            val paired = hakimPrefs.getBoolean("local_adb_paired", false)
            val connected = manager.isConnected || (paired && manager.reconnect(app))

            if (!connected) {
                requirePairing(app)
                return@thread
            }

            prefs.edit().putString("state", "APPLYING").apply()
            val applied = manager.applyFamilyPrivateDns(FAMILY_DNS_HOST)
            val verified = applied.success &&
                applied.mode == "hostname" &&
                applied.specifier == FAMILY_DNS_HOST

            if (verified) {
                repeat(8) {
                    if (isProtected(snapshot(app))) return@repeat
                    Thread.sleep(250)
                }
                finish(app, "VERIFIED", true, "تم تفعيل حماية DNS العائلية على الهاتف والتحقق منها.")
                return@thread
            }

            prefs.edit().putString("state", "ROLLING_BACK").apply()
            val rolledBack = manager.restorePrivateDns(before.mode, before.specifier)
            finish(
                app,
                if (rolledBack) "ROLLED_BACK" else "ROLLBACK_FAILED",
                false,
                if (rolledBack) {
                    "لم يثبت نجاح الحماية، فأعاد حكيم إعداد DNS السابق."
                } else {
                    "لم يثبت نجاح الحماية وتعذر التحقق من الرجوع؛ لا تُعتبر المهمة مكتملة."
                }
            )
        }
    }

    fun resumeAfterPairing(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pending_after_pairing", false)) return
        prefs.edit()
            .putBoolean("pending_after_pairing", false)
            .putBoolean("running", false)
            .apply()
        start(app)
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = snapshot(context)
        return JSONObject()
            .put("network_protection", true)
            .put("version", VERSION)
            .put("method", "local_adb")
            .put("state", p.getString("state", "IDLE"))
            .put("running", p.getBoolean("running", false))
            .put("verified", isProtected(now))
            .put("family_dns_active", isProtected(now))
            .put("pairing_required", p.getBoolean("pending_after_pairing", false))
            .put("last_attempt_at", p.getLong("last_attempt_at", 0L))
            .put("last_finished_at", p.getLong("last_finished_at", 0L))
    }

    private fun requirePairing(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("running", false)
            .putBoolean("pending_after_pairing", true)
            .putString("state", "PAIRING_REQUIRED")
            .apply()

        notify(
            context,
            "يلزم إقران ADB المحلي مرة واحدة. افتح «إقران الجهاز باستخدام رمز الاقتران» وأدخل الرمز في إشعار حكيم."
        )
        Handler(Looper.getMainLooper()).post {
            HakimLocalPairing.openWirelessDebuggingSettings(context)
        }
    }

    private fun snapshot(context: Context): DnsState {
        val resolver = context.contentResolver
        val mode = runCatching {
            Settings.Global.getString(resolver, "private_dns_mode")
        }.getOrNull().orEmpty().trim().lowercase()
        val specifier = runCatching {
            Settings.Global.getString(resolver, "private_dns_specifier")
        }.getOrNull().orEmpty().trim().lowercase()
        return DnsState(mode, specifier)
    }

    private fun isProtected(state: DnsState): Boolean =
        state.mode == "hostname" && state.specifier == FAMILY_DNS_HOST

    private fun finish(context: Context, state: String, verified: Boolean, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("running", false)
            .putBoolean("pending_after_pairing", false)
            .putString("state", state)
            .putBoolean("verified", verified)
            .putLong("last_finished_at", System.currentTimeMillis())
            .apply()

        notify(context, message)

        Handler(Looper.getMainLooper()).post {
            HakimConnectionResilience.recover(context, "network_protection_finished")
            HakimUnifiedRelay.ensureAlive(context, "network_protection_finished")
        }
    }

    private fun notify(context: Context, message: String) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        manager.notify(
            NOTIFICATION_ID,
            builder
                .setContentTitle("حكيم — حماية الشبكة")
                .setContentText(message)
                .setStyle(android.app.Notification.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "حماية الشبكة", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
