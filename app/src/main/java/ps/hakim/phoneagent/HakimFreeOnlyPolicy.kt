package ps.hakim.phoneagent

import android.content.Context

/**
 * سياسة صفر تكلفة افتراضية.
 *
 * لا تفعّل الفوترة ولا تختار نموذجًا غير معروف بوجود Free Tier. الأسعار قد
 * تتغير من المزود، لذلك الضمان المالي الكامل يتطلب مشروع مزود غير مربوط
 * بحساب فوترة. حكيم لا يستطيع فرض ذلك على حساب خارجي من داخل التطبيق.
 */
object HakimFreeOnlyPolicy {
    const val PREFS = "hakim_cost_policy"
    const val KEY_FREE_ONLY = "free_only"

    private val knownFreeGeminiModels = listOf(
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gemini-3.5-flash-lite"
    )

    fun freeOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FREE_ONLY, true)

    fun allowedGeminiModels(context: Context): List<String> =
        if (freeOnly(context)) knownFreeGeminiModels else knownFreeGeminiModels

    fun isKnownFreeGeminiModel(model: String): Boolean = model in knownFreeGeminiModels
}
