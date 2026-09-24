package ps.hakim.phoneagent

import android.content.Context

/**
 * Monetary safety gate. FREE_ONLY is the default.
 * It never silently falls through to a paid engine.
 */
object HakimFreePolicy {
    private const val PREFS = "hakim_cost_policy"
    private const val KEY_FREE_ONLY = "free_only"
    private const val KEY_GEMINI_FREE_CONFIRMED = "gemini_free_tier_confirmed"

    fun freeOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FREE_ONLY, true)

    fun setFreeOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FREE_ONLY, enabled).apply()
    }

    fun geminiFreeTierConfirmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GEMINI_FREE_CONFIRMED, false)

    fun setGeminiFreeTierConfirmed(context: Context, confirmed: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GEMINI_FREE_CONFIRMED, confirmed).apply()
    }

    fun allows(engineId: String, context: Context): Boolean {
        if (!freeOnly(context)) return true
        return when (engineId) {
            OpenRouterFreeEngine.ID -> true
            GeminiDirectEngine.ID -> geminiFreeTierConfirmed(context)
            else -> false
        }
    }
}
