package ps.hakim.phoneagent

import android.content.Context

/** Field distribution deliberately has no native ADB implementation. */
class HakimAdbConnectionManager private constructor() {
    data class PairResult(val paired: Boolean, val connected: Boolean, val host: String?,
                          val pairingPort: Int?, val error: String? = null)
    data class PrivateDnsResult(val success: Boolean, val mode: String = "",
                                val specifier: String = "", val error: String? = null)
    val isConnected: Boolean get() = false
    fun pairAndConnect(context: Context, pairingCode: String, timeoutMillis: Long = 30_000L): PairResult =
        PairResult(false, false, null, null, "ADB_NOT_AVAILABLE_IN_FIELD")
    fun reconnect(context: Context): Boolean = false
    fun readPrivateDns(): PrivateDnsResult = PrivateDnsResult(false, error = "ADB_NOT_AVAILABLE_IN_FIELD")
    fun applyFamilyPrivateDns(host: String): PrivateDnsResult = PrivateDnsResult(false, error = "ADB_NOT_AVAILABLE_IN_FIELD")
    fun restorePrivateDns(mode: String, specifier: String): Boolean = false
    companion object {
        private val instance = HakimAdbConnectionManager()
        fun get(context: Context): HakimAdbConnectionManager = instance
    }
}
