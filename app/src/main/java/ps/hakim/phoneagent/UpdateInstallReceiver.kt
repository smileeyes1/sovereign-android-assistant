package ps.hakim.phoneagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL_RESULT = "ps.hakim.phoneagent.UPDATE_INSTALL_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                AutoUpdater.recordInstallSuccess(context)
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm != null) AutoUpdater.notifyConfirmation(context, confirm)
                else AutoUpdater.recordInstallFailure(context, status, "طلب أندرويد تأكيدًا لكن لم يصل Intent التأكيد")
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                AutoUpdater.recordInstallFailure(context, status, msg)
            }
        }
    }
}
