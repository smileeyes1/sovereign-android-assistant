package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * جاهزية قابلة للفحص بدل أوصاف تسويقية مثل «الأذكى» أو «الفئة العليا».
 * نجاح المصدر لا يساوي نجاحًا ميدانيًا؛ field_verified لا يصير true هنا تلقائيًا.
 */
object HakimProfessionalReadiness {
    const val STANDARD = "HAKIM-ELITE-PRO-2026-09-15-v1"

    fun status(context: Context): JSONObject {
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val field = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
        val selfCheck = field.getString("last_self_check_status", "NOT_TESTED").orEmpty()
        val fieldVerified = field.getBoolean("field_verified_current_build", false)
        val required = JSONArray(listOf(
            "wisdom_gate",
            "deliberation_audit",
            "reasoning_protocol_v2",
            "least_privilege_execution",
            "post_action_verification",
            "rollback_contract",
            "quranic_normative_integrity",
            "source_separation",
            "security_privacy_authority",
            "regression_ci",
            "real_phone_field_matrix"
        ))
        return JSONObject()
            .put("standard", STANDARD)
            .put("package", context.packageName)
            .put("version_name", packageInfo?.versionName.orEmpty())
            .put("wisdom_engine", HakimEliteWisdomEngine.status())
            .put("deliberation_quality", HakimDeliberationQuality.status())
            .put("excellence_optimizer", HakimExcellenceOptimizer.status())
            .put("quranic_framework", HakimQuranicFramework.status())
            .put("required_gates", required)
            .put("source_professional_contract", true)
            .put("self_check", selfCheck)
            .put("field_verified_current_build", fieldVerified)
            .put("claim", if (fieldVerified) "FIELD_VERIFIED" else "NOT_FIELD_VERIFIED")
            .put("no_marketing_superlative_without_evidence", true)
    }
}
