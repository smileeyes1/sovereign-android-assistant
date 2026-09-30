package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * طبقة حماية هاتفية محدودة وقابلة للتحقق.
 *
 * الهدف الوحيد هنا هو ضبط Private DNS إلى مرشح عائلي معروف عبر واجهة النظام
 * عندما تكون خدمة إمكانية الوصول الخاصة بحكيم متاحة. لا توجد كتابة مباشرة
 * إلى Settings.Global ولا root ولا ADB ولا توسيع صلاحيات.
 */
object HakimNetworkProtectionTask {
    const val VERSION = "HAKIM-NETWORK-PROTECTION-ANDROID-V1"
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
            .putString("state", "OPENING_SETTINGS")
            .putLong("last_attempt_at", System.currentTimeMillis())
            .putString("before_mode", before.mode.take(32))
            .putBoolean("before_had_specifier", before.specifier.isNotBlank())
            .apply()

        if (isProtected(before)) {
            finish(app, "VERIFIED", true, "الحماية مفعلة أصلًا على الهاتف.")
            return
        }

        val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Intent("android.settings.PRIVATE_DNS_SETTINGS")
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { app.startActivity(settingsIntent) }
            .onFailure {
                finish(app, "BLOCKED", false, "تعذر فتح إعدادات DNS الخاص.")
                return
            }

        thread(name = "hakim-network-protection", isDaemon = true) {
            val result = applyWithAccessibility(app)
            if (result) {
                finish(app, "VERIFIED", true, "تم تفعيل حماية DNS العائلية على الهاتف والتحقق منها.")
                return@thread
            }

            val after = snapshot(app)
            if (sameState(before, after)) {
                finish(app, "BLOCKED", false, "لم يتغير الإعداد؛ يلزم أن تكون خدمة إمكانية الوصول في حكيم متاحة.")
                return@thread
            }

            val rolledBack = restore(app, before)
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

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = snapshot(context)
        return JSONObject()
            .put("network_protection", true)
            .put("version", VERSION)
            .put("state", p.getString("state", "IDLE"))
            .put("running", p.getBoolean("running", false))
            .put("verified", isProtected(now))
            .put("family_dns_active", now.mode == "hostname" && now.specifier == FAMILY_DNS_HOST)
            .put("last_attempt_at", p.getLong("last_attempt_at", 0L))
            .put("last_finished_at", p.getLong("last_finished_at", 0L))
    }

    private fun applyWithAccessibility(context: Context): Boolean {
        repeat(24) {
            if (isProtected(snapshot(context))) return true
            val service = HakimAccessibilityService.instance
            if (service != null && service.foregroundPackage() == "com.android.settings") {
                service.clickAnyText(
                    listOf(
                        "Private DNS provider hostname",
                        "Private DNS provider",
                        "اسم مضيف موفّر DNS الخاص",
                        "اسم مضيف مزود DNS الخاص",
                        "موفّر DNS الخاص",
                        "مزود DNS الخاص"
                    )
                )
                Thread.sleep(250)
                service.setFirstEditableText(FAMILY_DNS_HOST)
                Thread.sleep(250)
                service.clickAnyText(listOf("Save", "حفظ", "OK", "موافق", "تم"))
            }
            Thread.sleep(600)
        }
        return isProtected(snapshot(context))
    }

    private fun restore(context: Context, before: DnsState): Boolean {
        if (sameState(before, snapshot(context))) return true
        runCatching {
            val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Intent("android.settings.PRIVATE_DNS_SETTINGS")
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }.getOrElse { return false }

        repeat(20) {
            val service = HakimAccessibilityService.instance
            if (service != null && service.foregroundPackage() == "com.android.settings") {
                when (before.mode) {
                    "hostname" -> {
                        service.clickAnyText(
                            listOf(
                                "Private DNS provider hostname",
                                "Private DNS provider",
                                "اسم مضيف موفّر DNS الخاص",
                                "اسم مضيف مزود DNS الخاص"
                            )
                        )
                        Thread.sleep(200)
                        if (before.specifier.isNotBlank()) {
                            service.setFirstEditableText(before.specifier)
                        }
                    }
                    "off" -> service.clickAnyText(listOf("Off", "إيقاف", "متوقف"))
                    else -> service.clickAnyText(listOf("Automatic", "تلقائي", "تلقائية"))
                }
                Thread.sleep(200)
                service.clickAnyText(listOf("Save", "حفظ", "OK", "موافق", "تم"))
            }
            Thread.sleep(500)
            if (sameState(before, snapshot(context))) return true
        }
        return sameState(before, snapshot(context))
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

    private fun sameState(a: DnsState, b: DnsState): Boolean =
        a.mode == b.mode && a.specifier == b.specifier

    private fun finish(context: Context, state: String, verified: Boolean, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("running", false)
            .putString("state", state)
            .putBoolean("verified", verified)
            .putLong("last_finished_at", System.currentTimeMillis())
            .apply()

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
                .setSmallIcon(if (verified) android.R.drawable.checkbox_on_background else android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .build()
        )

        Handler(Looper.getMainLooper()).post {
            HakimConnectionResilience.recover(context, "network_protection_finished")
            HakimUnifiedRelay.ensureAlive(context, "network_protection_finished")
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "حماية الشبكة", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}
