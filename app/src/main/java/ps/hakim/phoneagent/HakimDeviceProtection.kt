package ps.hakim.phoneagent

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import org.json.JSONObject

/**
 * طبقة حماية الجهاز خارج شبكة المنزل.
 *
 * لا تدّعي منع كل VPN/DoH. وظيفتها المثبتة فقط جعل محلل DNS النظامي يمر عبر
 * DNS عائلي محلي على الهاتف، على Wi-Fi وبيانات الهاتف، بعد موافقة VpnService.
 */
object HakimDeviceProtection {
    private const val PREFS = "hakim_device_protection"

    fun markConsentGranted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true)
            .putString("state", "STARTING")
            .putLong("consent_granted_at", System.currentTimeMillis())
            .apply()
    }

    fun markConsentRequired(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", "CONSENT_REQUIRED")
            .putBoolean("active", false)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun markConsentDenied(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", "CONSENT_REQUIRED")
            .putBoolean("active", false)
            .putLong("consent_denied_at", System.currentTimeMillis())
            .apply()
    }

    fun consentGranted(context: Context): Boolean =
        runCatching { VpnService.prepare(context) == null }.getOrDefault(false)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("enabled", false)

    fun ensureRunning(context: Context): Boolean {
        val app = context.applicationContext
        if (!enabled(app)) return false
        if (!consentGranted(app)) {
            markConsentRequired(app)
            return false
        }
        return runCatching {
            val i = Intent(app, HakimFamilyDnsVpnService::class.java)
                .setAction(HakimFamilyDnsVpnService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(i)
            } else {
                app.startService(i)
            }
            true
        }.getOrElse {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("state", "START_FAILED")
                .putString("last_error", it.javaClass.simpleName.take(80))
                .putBoolean("active", false)
                .putLong("updated_at", System.currentTimeMillis())
                .apply()
            false
        }
    }

    fun markActive(context: Context, active: Boolean, state: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("active", active)
            .putString("state", state.take(80))
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun noteUpstreamSuccess(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putLong("upstream_success_at", System.currentTimeMillis())
            .putLong("dns_proxy_success_count", p.getLong("dns_proxy_success_count", 0L) + 1L)
            .putString("state", "ACTIVE")
            .putBoolean("active", true)
            .remove("last_error")
            .apply()
    }

    fun noteFailure(context: Context, reason: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putLong("dns_proxy_failure_count", p.getLong("dns_proxy_failure_count", 0L) + 1L)
            .putString("last_error", reason.take(120))
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val successAt = p.getLong("upstream_success_at", 0L)
        val upstreamRecent = successAt > 0L && now - successAt < 15L * 60L * 1000L
        return JSONObject()
            .put("enabled", p.getBoolean("enabled", false))
            .put("consent_granted", consentGranted(context))
            .put("active", p.getBoolean("active", false))
            .put("state", p.getString("state", "NOT_CONFIGURED"))
            .put("upstream_verified_recently", upstreamRecent)
            .put("last_upstream_success_at", successAt)
            .put("dns_proxy_success_count", p.getLong("dns_proxy_success_count", 0L))
            .put("dns_proxy_failure_count", p.getLong("dns_proxy_failure_count", 0L))
            .put("full_bypass_prevention", false)
    }
}
