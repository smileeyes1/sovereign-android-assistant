package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * مدخل حماية الشبكة منخفض العبء.
 *
 * يشغّل NetworkGuardian على الراوتر المنزلي المثبت فقط.
 * لا يستخدم Accessibility أو root أو ADB ولا يتجاوز مصادقة الراوتر.
 */
object HakimNetworkProtectionTask {
    const val VERSION = "HAKIM-NETWORK-PROTECTION-ANDROID-V3-ROUTER-GUARDIAN"

    private const val PREFS = "hakim_network_protection"
    private const val CHANNEL = "hakim_network_protection"
    private const val NOTIFICATION_ID = 74103

    fun start(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("running", false)) return

        HakimLocalPairing.dismissPrompt(app)

        prefs.edit()
            .putBoolean("running", true)
            .putBoolean("pending_after_pairing", false)
            .putString("state", "INSPECTING_ROUTER")
            .putString("method", "router_guardian")
            .putLong("last_attempt_at", System.currentTimeMillis())
            .apply()

        thread(name = "hakim-network-protection-router", isDaemon = true) {
            val result = runCatching {
                HakimNetworkGuardian.inspectAndProtect(app, "mobile_network_protection")
            }.getOrElse {
                JSONObject().put("state", "ERROR")
            }

            val guardianState = result.optString("state", "ERROR")
            val configured = result.optBoolean("family_dns_configured", false)
            val resolverVerified = result.optBoolean("family_resolver_verified", false)
            val verified = guardianState == "FAMILY_DNS_CONFIGURED" &&
                configured && resolverVerified

            val state = if (verified) "VERIFIED" else "BLOCKED"
            val message = when {
                verified ->
                    "تم تفعيل DNS العائلي على الراوتر والتحقق منه."
                guardianState == "ROUTER_AUTH_REQUIRED" ->
                    "وصل حكيم إلى الراوتر، لكن المصادقة مطلوبة قبل أي تعديل."
                guardianState == "TR064_LANHOST_NOT_FOUND" ->
                    "تم إثبات الراوتر، لكن واجهة DNS القياسية غير متاحة."
                guardianState == "ROUTER_FINGERPRINT_NOT_PROVEN" ->
                    "لم تثبت هوية الراوتر بما يكفي؛ لم يُجر أي تعديل."
                guardianState.startsWith("FAMILY_DNS_ROLL") ->
                    "لم يثبت نجاح التغيير؛ نفّذ حكيم مسار الرجوع."
                else ->
                    "لم تُعتمد الحماية بعد؛ لم يُعلن نجاح دون تحقق."
            }

            prefs.edit()
                .putBoolean("running", false)
                .putString("state", state)
                .putString("guardian_state", guardianState.take(80))
                .putBoolean("verified", verified)
                .putBoolean("family_dns_active", verified)
                .putBoolean("pairing_required", false)
                .putLong("last_finished_at", System.currentTimeMillis())
                .apply()

            notify(app, message, verified)
            Handler(Looper.getMainLooper()).post {
                HakimConnectionResilience.recover(app, "network_protection_finished")
                HakimUnifiedRelay.ensureAlive(app, "network_protection_finished")
            }
        }
    }

    /**
     * توافق خلفي مع إصدارات كان مسار الحماية فيها يستأنف بعد ADB.
     * حماية V3 لا تعتمد على الاقتران، لذلك لا يعاد تشغيل المهمة من هذا callback.
     */
    fun resumeAfterPairing(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("pending_after_pairing", false)
            .apply()
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guardian = HakimNetworkGuardian.status(context)
        val guardianVerified =
            guardian.optString("state") == "FAMILY_DNS_CONFIGURED" &&
            guardian.optBoolean("family_dns_configured", false) &&
            guardian.optBoolean("family_resolver_verified", false)

        return JSONObject()
            .put("network_protection", true)
            .put("version", VERSION)
            .put("method", "router_guardian")
            .put("scope", "home_router")
            .put("state", if (guardianVerified) "VERIFIED" else p.getString("state", "IDLE"))
            .put("running", p.getBoolean("running", false))
            .put("verified", guardianVerified)
            .put("family_dns_active", guardianVerified)
            .put("pairing_required", false)
            .put("guardian_state", guardian.optString("state", "NOT_RUN"))
            .put("last_attempt_at", p.getLong("last_attempt_at", 0L))
            .put("last_finished_at", p.getLong("last_finished_at", 0L))
    }

    private fun notify(context: Context, message: String, verified: Boolean) {
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
                .setSmallIcon(
                    if (verified) android.R.drawable.checkbox_on_background
                    else android.R.drawable.stat_sys_warning
                )
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
