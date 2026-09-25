package ps.hakim.phoneagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Legacy compatibility receiver.
 *
 * Hakim no longer installs APKs itself. Kept only so older intents cannot
 * accidentally revive the removed installer path.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSharedPreferences("hakim", Context.MODE_PRIVATE)
            .edit()
            .putString("last_legacy_install_callback", "ignored")
            .putLong("last_legacy_install_callback_at", System.currentTimeMillis())
            .apply()
    }
}
