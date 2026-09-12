package ps.hakim.phoneagent

import android.content.Context

/** مصدر حقيقة واحد لحالة اقتران تطبيق حكيم الوحيد. */
object HakimPairingState {
    fun legacyConfigured(context: Context): Boolean {
        val p = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        return p.getString("command_topic", "").orEmpty().isNotBlank() &&
            p.getString("result_topic", "").orEmpty().isNotBlank()
    }

    fun secureConfigured(context: Context): Boolean = HakimUnifiedRelay.isConfigured(context)

    fun configured(context: Context): Boolean =
        !context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getBoolean("pairing_disabled_by_user", false) &&
            (secureConfigured(context) || legacyConfigured(context))

    fun clearAll(context: Context) {
        context.stopService(android.content.Intent(context, HakimService::class.java).setAction(HakimService.ACTION_STOP))
        context.getSharedPreferences("hakim", Context.MODE_PRIVATE).edit()
            .remove("command_topic")
            .remove("result_topic")
            .remove("auth_key")
            .remove(HakimUnifiedRelay.KEY_TOPIC)
            .remove(HakimUnifiedRelay.KEY_RESULT_URL)
            .remove(HakimUnifiedRelay.KEY_RELAY_KEY)
            .remove("pair_token")
            .remove("secure_relay_configured")
            .remove("secure_pairing_at")
            .putBoolean("pairing_disabled_by_user", true)
            .apply()
    }

    fun statusText(context: Context): String {
        val p = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
        if (p.getBoolean("pairing_disabled_by_user", false)) return "الاقتران مفصول بقرارك"
        val secure = secureConfigured(context)
        val legacy = legacyConfigured(context)
        return when {
            secure && p.getString("secure_relay_state", "") == "connected" -> "القناة المشفّرة متصلة"
            secure -> "القناة المشفّرة مهيأة — جارٍ الاتصال/التعافي"
            legacy && HakimService.connected -> "القناة الحالية متصلة"
            legacy && HakimService.running -> "حكيم يعمل — جارٍ الاتصال"
            legacy -> "مقترن — الخدمة متوقفة"
            else -> "يحتاج اقترانًا"
        }
    }
}
