package ps.hakim.phoneagent

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
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

    data class PrivateDnsResult(
        val success: Boolean,
        val mode: String = "",
        val specifier: String = "",
        val error: String? = null,
    )

    /**
     * قدرة ADB محلية محدودة لإعداد Private DNS فقط.
     * لا تستقبل أوامر shell حرة ولا توسع السطح التنفيذي العام.
     */
    fun readPrivateDns(): PrivateDnsResult = runCatching {
        val mode = runFixedShell("settings get global private_dns_mode").normalizedSetting()
        val specifier = runFixedShell("settings get global private_dns_specifier").normalizedSetting()
        PrivateDnsResult(true, mode, specifier, null)
    }.getOrElse {
        PrivateDnsResult(false, error = it.javaClass.simpleName)
    }

    fun applyFamilyPrivateDns(host: String): PrivateDnsResult {
        if (!host.matches(Regex("^[a-z0-9.-]{1,253}$")) || !host.contains('.')) {
            return PrivateDnsResult(false, error = "INVALID_DNS_HOST")
        }
        return runCatching {
            runFixedShell("settings put global private_dns_specifier $host")
            runFixedShell("settings put global private_dns_mode hostname")
            val after = readPrivateDns()
            if (after.success && after.mode == "hostname" && after.specifier == host) {
                after
            } else {
                PrivateDnsResult(false, after.mode, after.specifier, "VERIFY_FAILED")
            }
        }.getOrElse {
            PrivateDnsResult(false, error = it.javaClass.simpleName)
        }
    }

    fun restorePrivateDns(mode: String, specifier: String): Boolean {
        val normalizedMode = mode.trim().lowercase()
        if (normalizedMode.isNotBlank() && normalizedMode !in setOf("off", "opportunistic", "hostname")) {
            return false
        }
        if (specifier.isNotBlank() &&
            (!specifier.matches(Regex("^[a-z0-9.-]{1,253}$")) || !specifier.contains('.'))
        ) {
            return false
        }

        return runCatching {
            if (specifier.isBlank()) {
                runFixedShell("settings delete global private_dns_specifier")
            } else {
                runFixedShell("settings put global private_dns_specifier $specifier")
            }

            if (normalizedMode.isBlank()) {
                runFixedShell("settings delete global private_dns_mode")
            } else {
                runFixedShell("settings put global private_dns_mode $normalizedMode")
            }

            val restored = readPrivateDns()
            if (!restored.success) false
            else {
                val modeOk = if (normalizedMode.isBlank()) restored.mode.isBlank() else restored.mode == normalizedMode
                val specOk = if (specifier.isBlank()) restored.specifier.isBlank() else restored.specifier == specifier
                modeOk && specOk
            }
        }.getOrDefault(false)
    }

    private fun runFixedShell(command: String): String {
        val allowed = command.startsWith("settings get global private_dns_") ||
            command.startsWith("settings put global private_dns_mode ") ||
            command.startsWith("settings put global private_dns_specifier ") ||
            command == "settings delete global private_dns_mode" ||
            command == "settings delete global private_dns_specifier"
        if (!allowed || command.length > 96) throw SecurityException("ADB_COMMAND_NOT_ALLOWED")
        if (!isConnected) throw IllegalStateException("ADB_NOT_CONNECTED")

        val stream = openStream("shell:$command")
        return try {
            val input = stream.openInputStream()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val n = try {
                    input.read(buffer)
                } catch (_: IOException) {
                    break
                }
                if (n <= 0) break
                out.write(buffer, 0, n)
                if (out.size() > 4096) throw IOException("ADB_OUTPUT_TOO_LARGE")
            }
            out.toString(StandardCharsets.UTF_8.name()).trim()
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun String.normalizedSetting(): String {
        val value = trim().lowercase()
        return if (value == "null" || value == "undefined") "" else value
    }


    companion object {
        private const val KEY_ALIAS = "hakim_native_local_adb_v1"
        private const val TEN_YEARS_MS = 3650L * 24L * 60L * 60L * 1000L
        @Volatile private var instance: HakimAdbConnectionManager? = null

        fun get(context: Context): HakimAdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: HakimAdbConnectionManager(context.applicationContext).also { instance = it }
            }
    }
}
