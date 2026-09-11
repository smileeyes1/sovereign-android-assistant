package ps.hakim.phoneagent

import android.app.Application

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HakimConstitution.install(this)
        HakimLearning.initialize(this)
        PairingDefaults.ensure(getSharedPreferences("hakim", MODE_PRIVATE))
        AutoUpdater.schedule(this)
        AutoUpdater.startRealtimeListener(this)
        HakimSelfCheck.schedule(this)
        AutoUpdater.checkAsync(this)
        HakimSelfCheck.runAsync(this)
    }
}
