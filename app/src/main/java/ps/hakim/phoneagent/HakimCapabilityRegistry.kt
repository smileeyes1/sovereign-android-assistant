package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل القدرات الموحد R28.
 *
 * القدرة المسجلة لا تعني توفرها ولا تفويضها ولا نجاحها. الملكية هنا تعني
 * تنفيذًا مستقلًا داخل حكيم أو خطة مستقلة؛ ولا تنسب أي بنية خاصة بمزوّد خارجي.
 */
object HakimCapabilityRegistry {
    const val VERSION = "CAPABILITY-REGISTRY-2026-10-06-r29"

    data class Contract(
        val id: String,
        val title: String,
        val family: String,
        val ownership: String,
        val implemented: Boolean,
        val readOnly: Boolean,
        val remoteReadable: Boolean,
        val reversible: Boolean,
        val sensitive: Boolean,
        val approvalMode: String,
        val adapter: String,
        val kernelCapability: String,
        val androidPermissions: List<String> = emptyList()
    )

    private val contracts = listOf(
        Contract("system.status", "حالة حكيم التشغيلية", "system", "owned", true, true, true, true, false, "none", "orchestrator", "observe_status"),
        Contract("browser.read", "قراءة صفحة المتصفح المأذونة", "browser", "owned", true, true, true, true, false, "none", "hakim_service", "observe_browser"),
        Contract("ui.observe", "قراءة واجهة أندرويد المنقحة", "device", "owned", true, true, true, true, true, "runtime_permission", "accessibility", "observe_ui"),
        Contract("notifications.read", "قراءة الإشعارات المنقحة", "device", "owned", true, true, true, true, true, "runtime_permission", "notification_listener", "observe_notifications"),
        Contract("termux.status", "حالة قناة Termux المحلية", "device", "owned", true, true, true, true, false, "none", "termux", "observe_termux"),
        Contract("screenshot.capture", "لقطة شاشة", "device", "owned", true, true, false, true, true, "explicit_approval", "accessibility", "observe_ui"),
        Contract("browser.back", "الرجوع في المتصفح", "browser", "owned", true, false, false, true, false, "explicit_approval", "hakim_service", "navigate_ui"),
        Contract("ui.action", "فعل واجهة محدود", "device", "owned", true, false, false, true, true, "explicit_approval", "accessibility", "navigate_ui"),
        Contract("app.launch", "فتح تطبيق أو رابط", "device", "owned", true, false, false, true, false, "explicit_approval", "android_intent", "browser_open"),

        Contract("calendar.read", "قراءة التقويم", "personal_data", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.READ_CALENDAR")),
        Contract("calendar.create", "إنشاء حدث في التقويم", "personal_data", "planned", false, false, false, true, true, "explicit_approval", "planned", "local_file_write",
            listOf("android.permission.WRITE_CALENDAR")),
        Contract("contacts.read", "قراءة جهات الاتصال", "personal_data", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.READ_CONTACTS")),
        Contract("contacts.create", "إنشاء جهة اتصال", "personal_data", "planned", false, false, false, true, true, "explicit_approval", "planned", "local_file_write",
            listOf("android.permission.WRITE_CONTACTS")),
        Contract("sms.search", "البحث في الرسائل", "communications", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.READ_SMS")),
        Contract("sms.draft", "إعداد مسودة رسالة", "communications", "planned", false, false, false, true, true, "explicit_approval", "planned", "send_external"),
        Contract("sms.send", "إرسال رسالة", "communications", "planned", false, false, false, false, true, "explicit_approval", "planned", "send_external",
            listOf("android.permission.SEND_SMS")),
        Contract("calls.search", "قراءة سجل المكالمات", "communications", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.READ_CALL_LOG")),
        Contract("call.place", "بدء مكالمة", "communications", "planned", false, false, false, false, true, "explicit_approval", "planned", "send_external",
            listOf("android.permission.CALL_PHONE")),
        Contract("location.get", "قراءة الموقع المأذون", "device", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.ACCESS_FINE_LOCATION")),
        Contract("geofence.set", "إنشاء سياج جغرافي", "device", "planned", false, false, false, true, true, "explicit_approval", "planned", "local_file_write",
            listOf("android.permission.ACCESS_FINE_LOCATION")),
        Contract("alarm.set", "إنشاء منبه أو تذكير", "productivity", "planned", false, false, false, true, false, "explicit_approval", "planned", "local_file_write"),
        Contract("health.read", "قراءة بيانات صحية مأذونة", "health", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status"),
        Contract("ble.scan", "مسح أجهزة بلوتوث قريبة", "device", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.BLUETOOTH_SCAN")),
        Contract("camera.capture", "التقاط صورة مأذونة", "media", "planned", false, false, false, true, true, "explicit_approval", "planned", "local_file_write",
            listOf("android.permission.CAMERA")),
        Contract("voice.profile.local", "حفظ عينة الصوت الشخصية محليًا", "voice", "owned", true, false, false, true, true, "explicit_scope", "voice_profile_store", "local_file_write"),
        Contract("video.capabilities", "قدرات مصنع الفيديو السيادي", "media", "owned", true, true, true, true, false, "none", "video_factory", "plan_media"),
        Contract("video.plan", "خطة إنتاج فيديو سينمائي", "media", "owned", true, true, true, true, false, "none", "video_factory", "plan_media"),
        Contract("voice.dictate", "إملاء صوتي", "voice", "planned", false, true, false, true, true, "explicit_scope", "planned", "observe_status",
            listOf("android.permission.RECORD_AUDIO")),
        Contract("artifact.create", "إنشاء ملف أو أثر", "artifacts", "planned", false, false, false, true, false, "explicit_scope", "planned", "local_file_write"),
        Contract("connector.call", "استدعاء موصل خارجي", "connectors", "planned", false, false, false, true, true, "connector_scope", "planned", "send_external"),
        Contract("computer.session", "جلسة حاسوب وكيل", "computer", "planned", false, false, false, true, true, "explicit_scope", "planned", "send_external")
    )

    fun contract(id: String): Contract? = contracts.firstOrNull { it.id == id }

    fun catalog(context: Context? = null): JSONArray = JSONArray().apply {
        contracts.forEach { c ->
            val item = JSONObject()
                .put("id", c.id)
                .put("title", c.title)
                .put("family", c.family)
                .put("ownership", c.ownership)
                .put("implemented", c.implemented)
                .put("read_only", c.readOnly)
                .put("remote_readable", c.remoteReadable)
                .put("reversible", c.reversible)
                .put("sensitive", c.sensitive)
                .put("approval_mode", c.approvalMode)
                .put("adapter", c.adapter)
                .put("kernel_capability", c.kernelCapability)
                .put("android_permissions", JSONArray(c.androidPermissions))
            if (context != null) item.put("availability", availability(context, c.id))
            put(item)
        }
    }

    fun availability(context: Context, id: String): JSONObject {
        val c = contract(id) ?: return JSONObject().put("available", false).put("reason", "unknown_capability")
        if (!c.implemented) return JSONObject().put("available", false).put("reason", "planned_not_implemented")
        val available = when (id) {
            "system.status" -> true
            "browser.read", "browser.back" -> HakimUnifiedRelay.isConfigured(context)
            "ui.observe", "ui.action" -> HakimAccessibilityService.instance != null
            "notifications.read" -> HakimNotificationListener.isConnected()
            "termux.status" -> HakimTermuxControl.isInstalled(context)
            "screenshot.capture" -> Build.VERSION.SDK_INT >= 30 && HakimAccessibilityService.instance != null
            "app.launch", "voice.profile.local", "video.capabilities", "video.plan" -> true
            else -> false
        }
        return JSONObject()
            .put("available", available)
            .put("reason", if (available) "runtime_ready" else "runtime_dependency_unavailable")
    }

    fun summary(context: Context): JSONObject {
        var implemented = 0
        var remotelyReadable = 0
        var currentlyAvailable = 0
        contracts.forEach { c ->
            if (c.implemented) implemented += 1
            if (c.implemented && c.readOnly && c.remoteReadable) remotelyReadable += 1
            if (availability(context, c.id).optBoolean("available", false)) currentlyAvailable += 1
        }
        return JSONObject()
            .put("registry_version", VERSION)
            .put("catalog_size", contracts.size)
            .put("implemented_count", implemented)
            .put("remote_readable_count", remotelyReadable)
            .put("currently_available_count", currentlyAvailable)
            .put("planned_count", contracts.size - implemented)
            .put("catalog", catalog(context))
    }
}
