package ps.hakim.phoneagent

import android.app.Application

class HakimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PairingDefaults.ensure(getSharedPreferences("hakim", MODE_PRIVATE))
    }
}
