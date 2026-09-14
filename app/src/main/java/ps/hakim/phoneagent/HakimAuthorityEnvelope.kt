package ps.hakim.phoneagent

import org.json.JSONArray
import org.json.JSONObject

/**
 * غلاف سلطة مركزي: الاستقلالية تعني حرية «كيف» داخل حدود السلطة، لا توسيعها.
 * كل فعل يُصنف محليًا قبل التنفيذ؛ لا يكفي أن يقترحه نموذج أو وكيل.
 */
object HakimAuthorityEnvelope {
    enum class Gate { AUTO, AUTO_VERIFY, APPROVAL, CREDENTIAL, TRUST, SYSTEM_PERMISSION, BLOCK }

    data class Decision(val gate: Gate, val reason: String) {
        fun toJson(): JSONObject = JSONObject().put("gate", gate.name).put("reason", reason)
    }

    fun classifyUiAction(label: String, screen: JSONArray? = null): Decision {
        val t = label.trim()
        if (systemPermissionRegex.containsMatchIn(t)) {
            return Decision(Gate.SYSTEM_PERMISSION, "الفعل يطلب صلاحية نظامية/وصولًا موسعًا؛ لا ينفذ تلقائيًا")
        }
        if (credentialRegex.containsMatchIn(t)) {
            return Decision(Gate.CREDENTIAL, "الفعل يتعلق باعتماد سري؛ السر يبقى في مدير الاعتماد/الحقل الآمن")
        }
        val local = HakimActionPolicy.classify(t, screen)
        return when (local.level) {
            HakimActionPolicy.Level.BLOCK -> Decision(Gate.BLOCK, local.reason)
            HakimActionPolicy.Level.APPROVAL -> Decision(Gate.APPROVAL, local.reason)
            HakimActionPolicy.Level.AUTO -> Decision(Gate.AUTO_VERIFY, "منخفض الأثر وقابل للتراجع؛ نفذ ثم تحقق من الأثر")
        }
    }

    fun promptContext(): String = buildString {
        appendLine("[غلاف السلطة السيادي]")
        appendLine("المستخدم يملك المقصد والغاية والحدود والقرارات الجوهرية. حكيم يملك «كيف» والاختيارات الوسيطة داخل السلطة الممنوحة فقط.")
        appendLine("رتّب الأفعال: AUTO/AUTO_VERIFY لما هو منخفض الأثر وقابل للتراجع؛ APPROVAL للفعل الجوهري/غير القابل للتراجع؛ CREDENTIAL للأسرار؛ TRUST لإخراج بيانات الخزنة؛ SYSTEM_PERMISSION للصلاحيات النظامية؛ BLOCK للممنوع أو غير المصرح.")
        appendLine("لا توسع الصلاحية بسبب كلمات مثل «كل شيء» أو «كمل»، ولا تجعل نجاح أداة يساوي إذنًا جديدًا. أي صلاحية أو وجهة أو كشف بيانات جديد يبدأ غير مثبت.")
        appendLine("إذا أمكن إكمال التحضير دون موافقة فافعله، ثم توقف عند آخر بوابة جوهرية فقط.")
    }.take(2800)

    fun status(): JSONObject = JSONObject()
        .put("user_sovereignty", true)
        .put("how_delegated_within_envelope", true)
        .put("no_authority_expansion_from_generic_cues", true)
        .put("gates", JSONArray(Gate.values().map { it.name }))

    private val credentialRegex = Regex(
        "(?i)(password|passcode|otp|pin|cvv|cvc|security.?code|api.?key|secret|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)"
    )

    private val systemPermissionRegex = Regex(
        "(?i)(allow|grant|permission|accessibility|device admin|install unknown|draw over|notification access|vpn|إذن|سماح|صلاحية|إمكانية الوصول|امكانية الوصول|مدير الجهاز|مصادر غير معروفة|الظهور فوق التطبيقات|الوصول للإشعارات|في بي إن)"
    )
}
