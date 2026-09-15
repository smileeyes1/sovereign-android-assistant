package ps.hakim.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * سجل أدوات حكيم الموحّد لواجهة العمل.
 * لا يمنح صلاحيات جديدة؛ هو طبقة إعلان/فتح فوق القدرات الموجودة أصلًا.
 */
object HakimWorkToolHub {
    const val VERSION = "WORK-TOOL-HUB-2026-09-15-v1"

    enum class Id {
        BROWSER,
        RESEARCH,
        FILES_AND_SHARING,
        SERVICES_AND_CONNECTIONS,
        LOCAL_AUTOMATION,
        SYSTEM_AND_PRIVACY,
        VERIFICATION,
        RECOVERY
    }

    data class Tool(
        val id: Id,
        val title: String,
        val detail: String,
        val available: Boolean,
        val externalEffect: Boolean = false
    ) {
        fun menuLabel(): String = buildString {
            append(title)
            append(if (available) "  ✓" else "  —")
        }
    }

    fun tools(context: Context): List<Tool> {
        val webReady = true // MainActivity متوفر محليًا، والاتصال الفعلي يُحكم وقت الاستخدام.
        val localLink = context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .getBoolean("local_adb_paired", false)
        return listOf(
            Tool(Id.BROWSER, "متصفح حكيم", "فتح المواقع وقراءتها وتنفيذ الخطوات المسموحة", webReady),
            Tool(Id.RESEARCH, "البحث والتحقق", "بحث واستدلال ثم إعادة تخطيط قبل الفعل عند الحاجة", true),
            Tool(Id.FILES_AND_SHARING, "الملفات والمشاركة", "استقبال الملفات والنصوص وتنظيم مسار العمل عبر مركز القيادة", true),
            Tool(Id.SERVICES_AND_CONNECTIONS, "الخدمات والاتصالات", "مركز الربط والخدمات والاتصال المحلي", true),
            Tool(Id.LOCAL_AUTOMATION, "التنفيذ المحلي", "أتمتة محلية بأقل صلاحية؛ الاتصال الحالي ${if (localLink) "جاهز" else "غير مقترن"}", true),
            Tool(Id.SYSTEM_AND_PRIVACY, "النظام والخصوصية", "البيانات المحلية والثقة والحدود وإعدادات حكيم", true),
            Tool(Id.VERIFICATION, "التحقق", "فحص أثر المهمة وحالة التنفيذ بدل إعلان النجاح بلا دليل", true),
            Tool(Id.RECOVERY, "التعافي والاستمرار", "استعادة المهمة وتبديل المسار دون تكرار النجاح المثبت", true)
        )
    }

    fun open(activity: Activity, id: Id) {
        when (id) {
            Id.BROWSER -> activity.startActivity(Intent(activity, MainActivity::class.java))
            Id.FILES_AND_SHARING -> activity.startActivity(Intent(activity, CommandCenterActivity::class.java))
            Id.SERVICES_AND_CONNECTIONS,
            Id.LOCAL_AUTOMATION -> activity.startActivity(
                Intent(activity, UnifiedHomeActivity::class.java).putExtra("hakim_control_center", true)
            )
            Id.SYSTEM_AND_PRIVACY -> activity.startActivity(Intent(activity, HakimSystemSettingsActivity::class.java))
            Id.RESEARCH -> {
                bringChat(activity)
                Toast.makeText(activity, "اكتب ما تريد البحث عنه؛ حكيم سيستخدم البحث والتحقق عند الحاجة.", Toast.LENGTH_LONG).show()
            }
            Id.VERIFICATION -> {
                val mission = HakimMissionLedger.active(activity)
                val message = when {
                    mission == null -> "لا توجد مهمة نشطة للتحقق منها."
                    mission.phase == HakimMissionLedger.Phase.VERIFY -> "حكيم يتحقق من النتيجة الآن: ${mission.evidence.take(180)}"
                    else -> "حالة المهمة: ${humanPhase(mission.phase)} • آخر دليل: ${mission.evidence.take(180).ifBlank { "لم يسجل دليلًا بعد" }}"
                }
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
            Id.RECOVERY -> {
                val mission = HakimMissionLedger.active(activity)
                if (mission == null) {
                    Toast.makeText(activity, "لا توجد مهمة تحتاج استعادة.", Toast.LENGTH_SHORT).show()
                } else {
                    bringChat(activity)
                    Toast.makeText(activity, "المهمة محفوظة؛ سيعيد حكيم قراءة الحالة الحالية قبل المتابعة.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun promptContext(context: Context): String = buildString {
        appendLine("[أدوات مساحة عمل حكيم]")
        appendLine("استخدم أداة حقيقية فقط عندما تفيد المهمة. لا تعرض سلسلة التفكير؛ اعرض للمستخدم اسم الأداة والخطوة التنفيذية والحالة والدليل البشري المختصر.")
        tools(context).forEach { tool ->
            append("• ").append(tool.title).append(": ").append(tool.detail)
            appendLine(if (tool.available) " [متاحة]" else " [غير متاحة حاليًا]")
        }
        appendLine("لا توسع الصلاحيات بسبب توفر أداة؛ البوابات الحاكمة والأثر العالي والأسرار تبقى نافذة.")
    }

    fun status(context: Context): JSONObject {
        val list = tools(context)
        return JSONObject()
            .put("version", VERSION)
            .put("registered_tools", JSONArray(list.map { it.id.name }))
            .put("available_tool_count", list.count { it.available })
            .put("tool_count", list.size)
            .put("browser_tool", true)
            .put("services_center", true)
            .put("files_and_sharing_center", true)
            .put("local_automation_tool", true)
            .put("verification_tool", true)
            .put("recovery_tool", true)
            .put("new_privilege_granted", false)
            .put("high_impact_gate_preserved", true)
    }

    private fun bringChat(activity: Activity) {
        activity.startActivity(
            Intent(activity, HakimAgentsChatActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun humanPhase(phase: HakimMissionLedger.Phase): String = when (phase) {
        HakimMissionLedger.Phase.UNDERSTAND -> "فهم المطلوب"
        HakimMissionLedger.Phase.PLAN -> "التخطيط"
        HakimMissionLedger.Phase.EXECUTE -> "التنفيذ"
        HakimMissionLedger.Phase.VERIFY -> "التحقق"
        HakimMissionLedger.Phase.RECOVER -> "التعافي وإعادة التخطيط"
        HakimMissionLedger.Phase.WAITING_APPROVAL -> "بانتظار موافقة"
        HakimMissionLedger.Phase.WAITING_CREDENTIAL -> "بانتظار اعتماد حساس"
        HakimMissionLedger.Phase.WAITING_TRUST -> "بانتظار ثقة الخدمة"
        HakimMissionLedger.Phase.COMPLETE -> "مكتملة"
        HakimMissionLedger.Phase.CANCELLED -> "ملغاة"
        HakimMissionLedger.Phase.BLOCKED -> "متوقفة بأمان"
    }
}
