package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** سجل أدوات موحد: القدرة + السلطة + النجاح + التحقق + التراجع + الأثر. */
object HakimSovereignToolRegistry {
    const val VERSION = "SOVEREIGN-TOOL-REGISTRY-2026-09-16-v1"

    enum class Impact { READ_ONLY, REVERSIBLE_LOCAL, REVERSIBLE_EXTERNAL, HIGH_IMPACT }

    data class ToolSpec(
        val id: String,
        val title: String,
        val capabilities: List<String>,
        val permissionRequirements: List<String>,
        val successCondition: String,
        val verification: String,
        val rollback: String,
        val impact: Impact,
        val localFirst: Boolean,
        val freeFirst: Boolean,
        val available: Boolean,
        val source: String = "builtin"
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("title", title)
            .put("capabilities", JSONArray(capabilities))
            .put("permission_requirements", JSONArray(permissionRequirements))
            .put("success_condition", successCondition)
            .put("verification", verification)
            .put("rollback", rollback)
            .put("impact", impact.name)
            .put("local_first", localFirst)
            .put("free_first", freeFirst)
            .put("available", available)
            .put("source", source)
    }

    fun all(context: Context): List<ToolSpec> = builtins(context) + HakimSkillFactory.verifiedToolSpecs(context)

    fun byId(context: Context, id: String): ToolSpec? = all(context).firstOrNull { it.id == id }

    private fun builtins(context: Context): List<ToolSpec> {
        val web = HakimRuntime.visibleWebView()
        val browserReady = HakimWebAutomation.isUsable(web)
        val accessibilityAuthorized = HakimAccessibilityService.instance != null
        val adbPaired = context.getSharedPreferences("hakim", Context.MODE_PRIVATE).getBoolean("local_adb_paired", false)
        return listOf(
            ToolSpec("browser.dom", "DOM المحلي", listOf("snapshot", "click_ref", "type_ref", "select_ref", "verify"), listOf("صفحة ويب ظاهرة داخل حكيم"), "تغير حالة DOM أو تحقق الشرط", "إعادة لقطة DOM ومقارنة الحالة", "العودة/إعادة تحميل الصفحة عند إمكان ذلك", Impact.REVERSIBLE_LOCAL, true, true, browserReady),
            ToolSpec("browser.js", "JavaScript المأذون", listOf("semantic_click", "semantic_type", "select", "scroll", "dialog_safe"), listOf("JavaScript مفعّل داخل WebView حكيم", "سكريبتات حكيم الثابتة فقط"), "ينفذ الفعل وتظهر postcondition", "لقطة DOM بعد الفعل", "Back/reload أو إعادة القيمة عندما تكون قابلة للعكس", Impact.REVERSIBLE_LOCAL, true, true, browserReady),
            ToolSpec("browser.coordinate", "الإحداثيات المحلية", listOf("element_center", "tap", "swipe"), listOf("عنصر مرئي وإحداثيات مثبتة داخل WebView"), "الفعل يغير الصفحة كما هو متوقع", "لقطة DOM/URL بعد الفعل", "Back أو إعادة التخطيط", Impact.REVERSIBLE_LOCAL, true, true, browserReady),
            ToolSpec("android.ui.authorized", "أتمتة Android المأذونة", listOf("snapshot", "tap", "swipe", "set_text", "back"), listOf("خدمة وصول أو ADB محلي فُعّل صراحةً من المستخدم"), "تغير واجهة التطبيق المستهدف وفق الهدف", "إعادة قراءة الواجهة", "Back/إيقاف المهمة؛ لا يوجد توسيع صلاحية تلقائي", Impact.REVERSIBLE_LOCAL, true, true, accessibilityAuthorized || adbPaired),
            ToolSpec("browser.tabs", "التبويبات", listOf("new", "switch", "close", "persist_url_title"), listOf("متصفح حكيم"), "التبويب المطلوب يصبح نشطًا", "مطابقة active_id والصفحة", "العودة للتبويب السابق", Impact.REVERSIBLE_LOCAL, true, true, true),
            ToolSpec("browser.upload", "رفع الملفات", listOf("android_file_picker", "web_file_chooser"), listOf("اختيار المستخدم للملف", "سماح الموقع بالرفع"), "الموقع يستقبل الملف ويظهر حالة قبول", "حالة الصفحة بعد الرفع", "إلغاء قبل الإرسال النهائي أو حذف الرفع إن أتاح الموقع", Impact.REVERSIBLE_EXTERNAL, true, true, true),
            ToolSpec("browser.download", "التنزيلات", listOf("download_manager", "session_cookie_headers", "resume_status"), listOf("رابط تنزيل مأذون"), "DownloadManager يبلغ SUCCESSFUL", "استعلام معرف التنزيل وحجم الملف", "حذف الملف المنزّل محليًا عند طلب المستخدم", Impact.REVERSIBLE_LOCAL, true, true, true),
            ToolSpec("browser.session", "الجلسات والكوكيز", listOf("first_party_session", "cookie_flush", "local_persistence"), listOf("جلسة أنشأها المستخدم أو وافق عليها"), "استمرار الجلسة بين الصفحات", "إعادة تحميل صفحة الجلسة", "مسح بيانات الموقع فقط بطلب صريح", Impact.REVERSIBLE_LOCAL, true, true, true),
            ToolSpec("files", "الملفات", listOf("open", "share", "organize", "receive"), listOf("اختيار/وصول Android المأذون"), "الملف يوجد في الوجهة المقصودة", "فحص الاسم/الحجم/المسار", "إعادة النقل أو حذف الناتج الجديد عند الإمكان", Impact.REVERSIBLE_LOCAL, true, true, true),
            ToolSpec("services", "الخدمات والتكاملات", listOf("connections", "local_relay", "service_handoff"), listOf("حساب/خدمة متصلة ومأذونة"), "الخدمة تعيد نتيجة مثبتة", "قراءة الحالة/الاستجابة", "فصل الموصل أو التراجع حسب الخدمة", Impact.REVERSIBLE_EXTERNAL, false, true, true),
            ToolSpec("verification", "التحقق", listOf("postcondition", "mission_evidence", "regression_check"), emptyList(), "الدليل يطابق معيار النجاح", "تحقق مستقل بعد التنفيذ", "KEEP_BASELINE عند غياب الدليل", Impact.READ_ONLY, true, true, true),
            ToolSpec("recovery", "التعافي", listOf("resume", "reroute", "last_verified_success", "network_recovery"), emptyList(), "تستأنف المهمة دون تكرار النجاح المثبت", "مقارنة المرحلة والدليل قبل/بعد", "إيقاف آمن عند تضارب الحالة", Impact.READ_ONLY, true, true, true),
            ToolSpec("skill.factory", "مصنع المهارات", listOf("gap_detect", "declarative_skill", "sandbox_validate", "register_verified", "rollback"), listOf("لا صلاحية جديدة؛ الأدوات المولدة داخل القدرات القائمة فقط"), "المهارة تمر التحقق البنيوي والمحاكاة قبل التسجيل", "sandboxValidate + registry status VERIFIED", "تعطيل/حذف المهارة المحلية", Impact.REVERSIBLE_LOCAL, true, true, true)
        )
    }

    fun status(context: Context): JSONObject {
        val tools = all(context)
        return JSONObject()
            .put("version", VERSION)
            .put("tool_count", tools.size)
            .put("available_count", tools.count { it.available })
            .put("local_first", true)
            .put("free_first", true)
            .put("permission_expansion_automatic", false)
            .put("high_impact_gate_preserved", true)
            .put("tools", JSONArray(tools.map { it.toJson() }))
    }
}