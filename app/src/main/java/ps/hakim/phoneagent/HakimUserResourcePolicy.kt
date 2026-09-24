package ps.hakim.phoneagent

import android.content.Context

/**
 * User-owned resource boundary.
 *
 * Product invariant:
 * - Hakim never ships or silently falls back to an app-owner inference credential.
 * - External inference is eligible only when the credential/resource belongs to the current user
 *   and the monetary policy still considers the engine free.
 * - local deterministic work remains available without any external account.
 */
object HakimUserResourcePolicy {
    const val VERSION = "USER-OWNED-FREE-2026-09-24-v1"
    const val MODE_USER_OWNED_FREE_ONLY = "USER_OWNED_FREE_ONLY"

    private const val PREFS = "hakim_user_owned_resources"
    private const val KEY_MODE = "mode"

    fun enforce(context: Context) {
        HakimFreePolicy.setFreeOnly(context, true)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, MODE_USER_OWNED_FREE_ONLY)
            .apply()
    }

    fun markUserOwned(context: Context, resourceId: String) {
        require(resourceId.isNotBlank())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ownedKey(resourceId), true)
            .putLong(ownedAtKey(resourceId), System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context, resourceId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(ownedKey(resourceId))
            .remove(ownedAtKey(resourceId))
            .apply()
    }

    fun isUserOwned(context: Context, resourceId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ownedKey(resourceId), false)) return true

        // Migration from already-established user-owned connections.
        return when (resourceId) {
            OpenRouterFreeEngine.ID ->
                context.getSharedPreferences("hakim_openrouter_oauth", Context.MODE_PRIVATE)
                    .getBoolean("connected", false)

            GeminiDirectEngine.ID ->
                HakimSecretStore.has(context, GeminiDirectEngine.SECRET_GEMINI_KEY) &&
                    HakimFreePolicy.geminiFreeTierConfirmed(context)

            else -> false
        }
    }

    fun allows(context: Context, resourceId: String): Boolean =
        HakimFreePolicy.freeOnly(context) &&
            HakimFreePolicy.allows(resourceId, context) &&
            isUserOwned(context, resourceId)

    fun currentMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_USER_OWNED_FREE_ONLY)
            .orEmpty()
            .ifBlank { MODE_USER_OWNED_FREE_ONLY }

    /** Central app-owner billing is a forbidden architecture path. */
    fun centralBillingAllowed(): Boolean = false

    /** Hakim must never auto-upgrade a user from free to paid. */
    fun automaticPaidUpgradeAllowed(): Boolean = false

    private fun ownedKey(resourceId: String) = "owned_" + resourceId
    private fun ownedAtKey(resourceId: String) = "owned_at_" + resourceId
}
