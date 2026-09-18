package ps.hakim.phoneagent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject

/**
 * حاكم موارد محلي تكيفي: يحمي سرعة الواجهة وجودة القرار بتخفيض العمل الخلفي فقط عند ضغط الموارد.
 * لا يخفّض الحاكمية أو دقة القرار أو بوابات الأمان؛ يغيّر توقيت/كثافة الصيانة والشبكة الخلفية فقط.
 */
object HakimResourceGovernor {
    enum class Mode { PERFORMANCE, BALANCED, CONSERVE, PRESSURE }

    data class Snapshot(
        val mode: Mode,
        val availMemoryRatio: Double,
        val lowMemory: Boolean,
        val batteryPercent: Int,
        val charging: Boolean,
        val powerSave: Boolean,
        val thermalStatus: Int,
        val interactive: Boolean,
        val meteredNetwork: Boolean,
        val reason: String
    )

    private const val PREFS = "hakim_resource_governor"
    private const val PRESSURE_HOLD_MS = 5L * 60L * 1000L
    private const val STARTUP_GAP_PERFORMANCE_MS = 10L * 60L * 1000L
    private const val STARTUP_GAP_BALANCED_MS = 20L * 60L * 1000L
    private const val STARTUP_GAP_CONSERVE_MS = 90L * 60L * 1000L

    fun snapshot(context: Context): Snapshot {
        val app = context.applicationContext
        val am = app.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val ratio = if (mem.totalMem > 0L) mem.availMem.toDouble() / mem.totalMem.toDouble() else 1.0

        val battery = app.getSystemService(BatteryManager::class.java)
        val capacity = runCatching { battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 }
            .getOrDefault(-1)
            .let { if (it in 0..100) it else -1 }
        val batteryIntent = runCatching {
            app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val pm = app.getSystemService(PowerManager::class.java)
        val powerSave = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)
        val interactive = runCatching { pm?.isInteractive ?: true }.getOrDefault(true)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE }
                .getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        } else PowerManager.THERMAL_STATUS_NONE

        val metered = runCatching {
            app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
        }.getOrDefault(false)

        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recentPressure = System.currentTimeMillis() - p.getLong("last_pressure_at", 0L) < PRESSURE_HOLD_MS
        val severeThermal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermal >= PowerManager.THERMAL_STATUS_SEVERE
        val moderateThermal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermal >= PowerManager.THERMAL_STATUS_MODERATE
        val criticalBattery = capacity in 0..14 && !charging
        val lowBattery = capacity in 15..29 && !charging

        val mode = when {
            mem.lowMemory || ratio < 0.12 || severeThermal || criticalBattery || recentPressure -> Mode.PRESSURE
            powerSave || ratio < 0.20 || moderateThermal || lowBattery -> Mode.CONSERVE
            charging && capacity >= 50 && ratio >= 0.35 && !moderateThermal -> Mode.PERFORMANCE
            else -> Mode.BALANCED
        }
        val reason = when (mode) {
            Mode.PRESSURE -> "ضغط موارد: نحمي المهمة والواجهة ونؤجل الصيانة غير العاجلة"
            Mode.CONSERVE -> "وضع اقتصاد تكيفي: نحافظ على الجودة ونخفف الخلفية"
            Mode.PERFORMANCE -> "الموارد مناسبة: يسمح بصيانة أسرع دون مزاحمة الواجهة"
            Mode.BALANCED -> "توازن تلقائي بين الاستجابة والصيانة"
        }
        p.edit()
            .putString("last_mode", mode.name)
            .putLong("last_sample_at", System.currentTimeMillis())
            .apply()
        return Snapshot(mode, ratio, mem.lowMemory, capacity, charging, powerSave, thermal, interactive, metered, reason)
    }

    fun noteTrimMemory(context: Context, level: Int) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e = p.edit().putInt("last_trim_level", level).putLong("last_trim_at", System.currentTimeMillis())
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            e.putLong("last_pressure_at", System.currentTimeMillis())
        }
        e.apply()
    }

    fun noteLowMemory(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_pressure_at", System.currentTimeMillis())
            .putLong("last_low_memory_at", System.currentTimeMillis())
            .apply()
    }

    fun canRunNonEssentialBackground(context: Context): Boolean = snapshot(context).mode != Mode.PRESSURE

    fun canUseRealtimeBackgroundNetwork(context: Context): Boolean {
        val s = snapshot(context)
        return s.mode in setOf(Mode.PERFORMANCE, Mode.BALANCED) && !s.powerSave && (!s.meteredNetwork || s.charging)
    }

    fun shouldRunStartupMaintenance(context: Context): Boolean {
        val app = context.applicationContext
        val s = snapshot(app)
        if (s.mode == Mode.PRESSURE) return false
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val gap = when (s.mode) {
            Mode.PERFORMANCE -> STARTUP_GAP_PERFORMANCE_MS
            Mode.BALANCED -> STARTUP_GAP_BALANCED_MS
            Mode.CONSERVE -> STARTUP_GAP_CONSERVE_MS
            Mode.PRESSURE -> Long.MAX_VALUE
        }
        return System.currentTimeMillis() - p.getLong("last_startup_maintenance_at", 0L) >= gap
    }

    fun markStartupMaintenance(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_startup_maintenance_at", System.currentTimeMillis())
            .apply()
    }

    fun startupDeferralMs(context: Context): Long = when (snapshot(context).mode) {
        Mode.PERFORMANCE -> 900L
        Mode.BALANCED -> 1_800L
        Mode.CONSERVE -> 4_000L
        Mode.PRESSURE -> 8_000L
    }

    fun uiRefreshMs(context: Context): Long = when (snapshot(context).mode) {
        Mode.PERFORMANCE -> 2_000L
        Mode.BALANCED -> 3_000L
        Mode.CONSERVE -> 5_000L
        Mode.PRESSURE -> 8_000L
    }

    fun status(context: Context): JSONObject {
        val s = snapshot(context)
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("resource_governor", true)
            .put("mode", s.mode.name)
            .put("available_memory_ratio", s.availMemoryRatio)
            .put("low_memory", s.lowMemory)
            .put("battery_percent", s.batteryPercent)
            .put("charging", s.charging)
            .put("power_save", s.powerSave)
            .put("thermal_status", s.thermalStatus)
            .put("interactive", s.interactive)
            .put("metered_network", s.meteredNetwork)
            .put("reason", s.reason)
            .put("quality_and_governance_never_downgraded", true)
            .put("only_nonessential_background_is_throttled", true)
            .put("no_large_on_device_model_required", true)
            .put("last_trim_level", p.getInt("last_trim_level", 0))
            .put("last_startup_maintenance_at", p.getLong("last_startup_maintenance_at", 0L))
    }
}
