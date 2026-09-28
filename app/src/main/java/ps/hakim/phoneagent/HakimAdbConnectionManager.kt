package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

class HakimAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
    data class PairResult(
        val paired: Boolean,
        val connected: Boolean,
        val host: String?,
        val pairingPort: Int?,
        val error: String? = null,
    )

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress("127.0.0.1")
        setTimeout(15, TimeUnit.SECONDS)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val now = System.currentTimeMillis()
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=HAKIM Local ADB"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(now - 86_400_000L))
                    .setCertificateNotAfter(Date(now + TEN_YEARS_MS))
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }

        privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        certificate = keyStore.getCertificate(KEY_ALIAS)
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "HAKIM-${Build.MODEL}"

    fun pairAndConnect(context: Context, pairingCode: String, timeoutMillis: Long = 30_000L): PairResult {
        if (!pairingCode.matches(Regex("^[0-9]{6}$"))) {
            return PairResult(false, false, null, null, "INVALID_PAIRING_CODE")
        }

        val latch = CountDownLatch(1)
        var host: String? = null
        var port = -1
        val mdns = AdbMdns(context.applicationContext, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { address, discoveredPort ->
            if (address != null && discoveredPort > 0 && port <= 0) {
                host = address.hostAddress
                port = discoveredPort
                latch.countDown()
            }
        }

        return try {
            mdns.start()
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                PairResult(false, false, null, null, "PAIRING_SERVICE_NOT_FOUND")
            } else {
                val resolvedHost = host ?: "127.0.0.1"
                pair(resolvedHost, port, pairingCode)
                runCatching { disconnect() }
                val connected = autoConnect(context.applicationContext, 20_000L) || isConnected
                PairResult(true, connected, resolvedHost, port, if (connected) null else "PAIRED_BUT_CONNECT_FAILED")
            }
        } catch (t: Throwable) {
            PairResult(false, false, host, port.takeIf { it > 0 }, t.javaClass.simpleName + ":" + (t.message ?: "unknown"))
        } finally {
            runCatching { mdns.stop() }
        }
    }

    fun reconnect(context: Context): Boolean = runCatching {
        autoConnect(context.applicationContext, 20_000L) || isConnected
    }.getOrDefault(false)

    data class RemoteActionResult(
        val ok: Boolean,
        val error: String? = null,
        val output: String? = null,
    )

    /** Verify that the target actually accepts this Hakim ADB identity before authorization is stored. */
    fun verifyRemote(host: String, port: Int): RemoteActionResult {
        if (!isPrivateIpv4(host) || port != 5555) return RemoteActionResult(false, "INVALID_REMOTE_TARGET")
        return try {
            runCatching { disconnect() }
            setThrowOnUnauthorised(true)
            val connected = connect(host, port) || isConnected
            if (connected) RemoteActionResult(true) else RemoteActionResult(false, "ADB_CONNECT_FAILED")
        } catch (t: Throwable) {
            val name = t.javaClass.simpleName
            val message = (t.message ?: name).lowercase()
            val approvalRequired =
                name.contains("Authentication", ignoreCase = true) ||
                "unauthor" in message || "authentication" in message || "auth" in message
            RemoteActionResult(
                false,
                if (approvalRequired) "ADB_TARGET_APPROVAL_REQUIRED" else "ADB_CONNECT_FAILED"
            )
        } finally {
            runCatching { disconnect() }
        }
    }

    /** Remote ADB is restricted to a small Android/TV remote-control vocabulary. */
    fun executeRemoteAction(
        host: String,
        port: Int,
        action: String,
        url: String? = null,
        packageName: String? = null,
    ): RemoteActionResult {
        if (!isPrivateIpv4(host) || port != 5555) return RemoteActionResult(false, "INVALID_REMOTE_TARGET")
        val command = when (action) {
            "home" -> "input keyevent KEYCODE_HOME"
            "back" -> "input keyevent KEYCODE_BACK"
            "up" -> "input keyevent KEYCODE_DPAD_UP"
            "down" -> "input keyevent KEYCODE_DPAD_DOWN"
            "left" -> "input keyevent KEYCODE_DPAD_LEFT"
            "right" -> "input keyevent KEYCODE_DPAD_RIGHT"
            "enter" -> "input keyevent KEYCODE_DPAD_CENTER"
            "play_pause" -> "input keyevent KEYCODE_MEDIA_PLAY_PAUSE"
            "volume_up" -> "input keyevent KEYCODE_VOLUME_UP"
            "volume_down" -> "input keyevent KEYCODE_VOLUME_DOWN"
            "mute" -> "input keyevent KEYCODE_VOLUME_MUTE"
            "open_url" -> {
                val safe = safeHttpUrl(url) ?: return RemoteActionResult(false, "INVALID_URL")
                "am start -W -a android.intent.action.VIEW -d '$safe'"
            }
            "launch_package" -> {
                val pkg = packageName?.trim().orEmpty()
                if (!pkg.matches(Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")))
                    return RemoteActionResult(false, "INVALID_PACKAGE")
                "monkey -p '$pkg' -c android.intent.category.LAUNCHER 1"
            }
            else -> return RemoteActionResult(false, "UNSUPPORTED_ACTION")
        }
        return try {
            runCatching { disconnect() }
            if (!connect(host, port)) return RemoteActionResult(false, "ADB_CONNECT_FAILED")
            val stream = openStream("shell:$command")
            val output = runCatching {
                stream.openInputStream().bufferedReader(Charsets.UTF_8).use { it.readText().take(2000) }
            }.getOrDefault("")
            RemoteActionResult(true, output = output)
        } catch (t: Throwable) {
            val message = (t.message ?: t.javaClass.simpleName).lowercase()
            val code = if ("unauthorized" in message || "auth" in message)
                "ADB_TARGET_APPROVAL_REQUIRED" else "ADB_REMOTE_ACTION_FAILED"
            RemoteActionResult(false, code)
        } finally {
            runCatching { disconnect() }
        }
    }

    private fun safeHttpUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.length !in 8..1500) return null
        if (!(value.startsWith("https://") || value.startsWith("http://"))) return null
        if (value.any { it.isWhitespace() || it == '\'' || it == '"' || it == ';' || it == '\\' }) return null
        return value
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    companion object {
        private const val KEY_ALIAS = "hakim_native_local_adb_v1"
        private const val TEN_YEARS_MS = 3650L * 24L * 60L * 60L * 1000L
        @Volatile private var instance: HakimAdbConnectionManager? = null

        fun get(context: Context): HakimAdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: HakimAdbConnectionManager(context.applicationContext).also { instance = it }
            }
        fun remote(context: Context): HakimAdbConnectionManager =
            HakimAdbConnectionManager(context.applicationContext)
    }
}
