package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل قدرات حكيم.
 *
 * يصف ما هو منفذ فعلًا وما هو مخطط فقط. وجود القدرة في السجل لا يمنح الإذن بتنفيذها.
 * لا يضيف هذا السجل أي صلاحية Android جديدة، ولا يحول القدرة المخططة إلى قدرة متاحة.
 */
object HakimCapabilityRegistry {
    const val VERSION = "CAPABILITY-REGISTRY-2026-10-05-v1"

    data class Contract(
        val id: String,
        val title: String,
        val family: String,
        val implemented: Boolean,
        val readOnly: Boolean,
        val remoteReadable: Boolean,
        val reversible: Boolean,
        val sensitive: Boolean,
        val approvalMode: String,
        val adapter: String,
        val androidPermissions: List<String> = emptyList()
    )

    private val contracts = listOf(
        Contract("system.status", "حالة حكيم التنفيذية", "system", true, true, true, true, false, "none", "execution_fabric"),
        Contract("browser.read", "قراءة صفحة المتصفح المأذونة", "browser", true, true, true, true, false, "none", "hakim_service"),
        Contract("browser.back", "الرجوع في المتصفح", "browser", true, false, false, true, false, "explicit_approval", "hakim_service"),
        Contract("ui.observe", "قراءة واجهة أندرويد المنقحة", "device", true, true, true, true, true, "none", "accessibility"),
        Contract("ui.action", "تنفيذ فعل على واجهة أندرويد", "device", true, false, false, true, true, "explicit_approval", "accessibility"),
        Contract("notifications.read", "قراءة الإشعارات المنقحة", "device", true, true, true, true, true, "none", "notification_listener"),
        Contract("screenshot.capture", "لقطة شاشة", "device", true, true, false, true, true, "explicit_approval", "accessibility"),
        Contract("app.launch", "فتح تطبيق أو رابط", "device", true, false, false, true, false, "explicit_approval", "android_intent"),

        Contract("calendar.read", "قراءة التقويم", "personal_data", false, true, false, true, true, "explicit_scope", "planned",
            listOf("android.permission.READ_CALENDAR")),
        Contract("calendar.create", "إنشاء حدث في التقويم", "personal_data", false, false, false, true, true, "explicit_approval", "planned",
            listOf("android.permission.WRITE_CALENDAR")),
        Contract("contacts.read", "قراءة جهات الاتصال", "personal_data", false, true, false, true, true, "explicit_scope", "planned",
            listOf("android.permission.READ_CONTACTS")),
        Contract("contacts.create", "إنشاء جهة اتصال", "personal_data", false, false, false, true, true, "explicit_approval", "planned",
            listOf("android.permission.WRITE_CONTACTS")),
        Contract("sms.search", "البحث في الرسائل", "communications", false, true, false, true, true, "explicit_scope", "planned",
            listOf("android.permission.READ_SMS")),
        Contract("sms.draft", "إعداد مسودة رسالة", "communications", false, false, false, true, true, "explicit_approval", "planned"),
        Contract("sms.send", "إرسال رسالة", "communications", false, false, false, false, true, "explicit_approval", "planned",
            listOf("android.permission.SEND_SMS")),
        Contract("location.get", "قراءة الموقع المأذون", "device", false, true, false, true, true, "explicit_scope", "planned",
            listOf("android.permission.ACCESS_FINE_LOCATION")),
        Contract("geofence.set", "إنشاء سياج جغرافي", "device", false, false, false, true, true, "explicit_approval", "planned",
            listOf("android.permission.ACCESS_FINE_LOCATION")),
        Contract("alarm.set", "إنشاء منبه أو تذكير", "productivity", false, false, false, true, false, "explicit_approval", "planned"),
        Contract("health.read", "قراءة بيانات صحية مأذونة", "health", false, true, false, true, true, "explicit_scope", "planned"),
        Contract("ble.scan", "مسح أجهزة بلوتوث قريبة", "device", false, true, false, true, true, "explicit_scope", "planned",
            listOf("android.permission.BLUETOOTH_SCAN")),
        Contract("voice.dictate", "إملاء صوتي", "voice", false, true, false, true, true, "explicit_scope", "planned",
            listOf("android.permission.RECORD_AUDIO")),
        Contract("artifact.create", "إنشاء ملف أو أثر", "artifacts", false, false, false, true, false, "explicit_scope", "planned"),
        Contract("connector.call", "استدعاء موصل خارجي", "connectors", false, false, false, true, true, "connector_scope", "planned"),
        Contract("computer.session", "جلسة حاسوب وكيل", "computer", false, false, false, true, true, "explicit_scope", "planned")
    )

    fun contract(id: String): Contract? = contracts.firstOrNull { it.id == id }

    fun catalog(context: Context? = null): JSONArray = JSONArray().apply {
        contracts.forEach { c ->
            val item = JSONObject()
                .put("id", c.id)
                .put("title", c.title)
                .put("family", c.family)
                .put("implemented", c.implemented)
                .put("read_only", c.readOnly)
                .put("remote_readable", c.remoteReadable)
                .put("reversible", c.reversible)
                .put("sensitive", c.sensitive)
                .put("approval_mode", c.approvalMode)
                .put("adapter", c.adapter)
                .put("android_permissions", JSONArray(c.androidPermissions))
            if (context != null) item.put("availability", availability(context, c.id))
            put(item)
        }
    }

    fun availability(context: Context, id: String): JSONObject {
        val c = contract(id)
            ?: return JSONObject().put("available", false).put("reason", "unknown_capability")
        if (!c.implemented) {
            return JSONObject().put("available", false).put("reason", "planned_not_implemented")
        }
        val available = when (id) {
            "system.status" -> true
            "browser.read", "browser.back" -> HakimService.running
            "ui.observe", "ui.action" -> HakimAccessibilityService.instance != null
            "notifications.read" -> HakimNotificationListener.isConnected()
            "screenshot.capture" -> Build.VERSION.SDK_INT >= 30 && HakimAccessibilityService.instance != null
            "app.launch" -> true
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
