package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * هوية توزيع حكيم 20338.
 *
 * فصل القناة لا يمنح قدرات جديدة. Consumer يستبعد الميزات المتقدمة من التشغيل
 * والـ Manifest، بينما Advanced يحافظ على خط ps.hakim.stable.
 */
object HakimProductProfile {
    const val VERSION = "PRODUCT-FOUNDATION-20338-v1"

    val advanced: Boolean
        get() = BuildConfig.HAKIM_ADVANCED

    val consumer: Boolean
        get() = !BuildConfig.HAKIM_ADVANCED

    val channel: String
        get() = BuildConfig.HAKIM_DISTRIBUTION_CHANNEL

    fun status(context: Context): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("distribution", channel)
        .put("application_id", context.packageName)
        .put("advanced_features", advanced)
        .put("play_candidate", consumer)
        .put("side_load_update_flow_allowed", advanced)
        .put("termux_control_allowed", advanced)
        .put("device_vpn_allowed", advanced)
        .put("accessibility_agent_allowed", advanced)
}
