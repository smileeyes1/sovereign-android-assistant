package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * معرفة ذاتية بالقدرات: لا يفترض حكيم أداة أو اتصالًا لم يثبت توفره الآن.
 * وجود القدرة في التطبيق يختلف عن جاهزيتها اللحظية للتنفيذ.
 */
object HakimCapabilityRegistry {
    data class Capability(
        val id: String,
        val available: Boolean,
        val readyNow: Boolean,
        val reason: String
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("available", available)
            .put("ready_now", readyNow)
            .put("reason", reason)
    }

    fun discover(context: Context): List<Capability> {
        val accessibilityReady = HakimAccessibilityService.instance != null
        val web = HakimRuntime.visibleWebView()
        val chatGptInstalled = runCatching {
            context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt") != null
        }.getOrDefault(false)

        return listOf(
            Capability("secure_store", true, true, "AndroidKeyStore/AES-GCM مدمج"),
            Capability("mission_ledger", true, true, "WIP=1 وحالة مشفرة مدمجان"),
            Capability("quranic_governance", true, true, "الإطار القرآني والنزاهة الشرعية مدمجان"),
            Capability("excellence_optimizer", true, true, "محسن التفوق الشامل مدمج"),
            Capability("browser", true, web != null, if (web != null) "WebView حكيم حاضر" else "المتصفح مدمج لكنه ليس حاضرًا الآن"),
            Capability("accessibility_actions", true, accessibilityReady, if (accessibilityReady) "خدمة الوصول متاحة الآن" else "خدمة الوصول غير مفعلة/غير متصلة الآن"),
            Capability("chatgpt_official", true, chatGptInstalled, if (chatGptInstalled) "تطبيق ChatGPT الرسمي مثبت" else "التطبيق الرسمي غير مثبت؛ يبقى مسار الويب الاحتياطي"),
            Capability("profile_vault", true, true, "خزنة البيانات غير الحساسة مدمجة مع ثقة موقع دقيقة"),
            Capability("field_update", true, false, "لا تُعد جاهزة إلا بعد توفر توقيع حكيم الميداني الأصلي والتحقق منه")
        )
    }

    fun isReady(context: Context, id: String): Boolean = discover(context).firstOrNull { it.id == id }?.readyNow == true

    fun promptContext(context: Context): String = buildString {
        appendLine("[معرفة حكيم الذاتية بالقدرات]")
        discover(context).forEach { c ->
            appendLine("• ${c.id}: ${if (c.readyNow) "جاهزة الآن" else if (c.available) "موجودة لكن غير جاهزة الآن" else "غير متاحة"} — ${c.reason}")
        }
        appendLine("لا تدّع قدرة غير جاهزة، ولا تحوّل وجود مكوّن برمجي إلى ادعاء نجاح ميداني. غيّر المسار تلقائيًا عند غياب قدرة، ما دام البديل مشروعًا وآمنًا ومتاحًا.")
    }.take(3600)

    fun status(context: Context): JSONObject = JSONObject()
        .put("self_capability_awareness", true)
        .put("capabilities", JSONArray(discover(context).map { it.toJson() }))
}
