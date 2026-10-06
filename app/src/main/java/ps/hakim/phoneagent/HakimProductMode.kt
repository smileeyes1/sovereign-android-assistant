package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject

/**
 * حدود توزيع المنتج.
 *
 * Consumer: تجربة مستخدم عامة، بلا Termux/VPN/Accessibility automation أو تحديث APK جانبي.
 * Advanced: يحافظ على قدرات حكيم السيادية الحالية خلف بواباتها وموافقاتها.
 */
object HakimProductMode {
    const val VERSION = "HAKIM-PRODUCT-MODE-2026-10-06-v1"

    fun isAdvanced(context: Context): Boolean =
        context.resources.getBoolean(R.bool.hakim_advanced_mode)

    fun isConsumer(context: Context): Boolean = !isAdvanced(context)

    fun allowsAdvancedDeviceControl(context: Context): Boolean = isAdvanced(context)

    fun allowsSideloadUpdates(context: Context): Boolean =
        context.resources.getBoolean(R.bool.hakim_sideload_updates)

    fun distribution(context: Context): String =
        if (isAdvanced(context)) "advanced" else "consumer"

    fun status(context: Context): JSONObject = JSONObject()
        .put("product_mode_version", VERSION)
        .put("distribution", distribution(context))
        .put("advanced_device_control", allowsAdvancedDeviceControl(context))
        .put("sideload_updates", allowsSideloadUpdates(context))
}
