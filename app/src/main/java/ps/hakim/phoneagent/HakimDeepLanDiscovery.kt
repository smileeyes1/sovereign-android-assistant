package ps.hakim.phoneagent

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * اكتشاف عميق قرائي فقط للشبكة المحلية.
 * لا يسجّل الدخول، لا يجرّب كلمات مرور، لا يغير Wi‑Fi/راوتر، ولا يرسل MAC/SSID خام.
 */
object HakimDeepLanDiscovery {
    const val VERSION = "DEEP-LAN-DISCOVERY-2026-09-26-v1"
    private val MANAGEMENT_PORTS = intArrayOf(80, 443, 8080, 8443, 22)
    private val HISTORIC_TARGETS = listOf("192.168.13.11", "192.168.13.100")

    fun inspect(context: Context): JSONObject {
        val out = JSONObject()
            .put("version", VERSION)
            .put("ok", true)

        out.put("neighbors", neighborSnapshot())
        out.put("wifi_neighbors", wifiNeighbors(context))

        val historic = JSONArray()
        for (ip in HISTORIC_TARGETS) {
            historic.put(probeHost(ip, historic = true))
        }
        out.put("historic_targets", historic)
        return out
    }

    fun probeHost(ip: String, historic: Boolean = false): JSONObject {
        if (!isPrivateIpv4(ip)) {
            return JSONObject().put("ip", ip).put("ok", false).put("error", "non_private")
        }

        val open = JSONArray()
        var ssh = ""
        var http = JSONObject()
        var tls = JSONObject()

        for (port in MANAGEMENT_PORTS) {
            if (tcpOpen(ip, port, 350)) {
                open.put(port)
                if (port == 22) ssh = sshBanner(ip)
                if ((port == 80 || port == 8080) && http.length() == 0) {
                    http = httpFingerprint(ip, port)
                }
                if ((port == 443 || port == 8443) && tls.length() == 0) {
                    tls = tlsFingerprint(ip, port)
                }
            }
        }

        return JSONObject()
            .put("ip", ip)
            .put("historic", historic)
            .put("reachable_hint", open.length() > 0 || isReachable(ip))
            .put("open_ports", open)
            .put("ssh_banner", ssh.take(180))
            .put("http", http)
            .put("tls", tls)
    }

    private fun neighborSnapshot(): JSONObject {
        val byIp = LinkedHashMap<String, JSONObject>()

        val ipNeigh = runCommand(listOf("/system/bin/ip", "neigh", "show"), 1200)
        if (ipNeigh.isNotBlank()) {
            for (line in ipNeigh.lineSequence()) {
                val parts = line.trim().split(Regex("\\s+"))
                val ip = parts.firstOrNull().orEmpty()
                if (!isPrivateIpv4(ip)) continue
                val lladdrIndex = parts.indexOf("lladdr")
                val mac = if (lladdrIndex >= 0 && lladdrIndex + 1 < parts.size) parts[lladdrIndex + 1] else ""
                val state = parts.lastOrNull().orEmpty().take(32)
                byIp[ip] = JSONObject()
                    .put("ip", ip)
                    .put("mac_oui", macOui(mac))
                    .put("mac_hash", hashIdentity(normalizeMac(mac)))
                    .put("state", state)
                    .put("source", "ip_neigh")
            }
        }

        runCatching {
            val arp = File("/proc/net/arp")
            if (arp.isFile && arp.canRead()) {
                arp.readLines().drop(1).forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 4) return@forEach
                    val ip = parts[0]
                    val mac = parts[3]
                    if (!isPrivateIpv4(ip)) return@forEach
                    val prev = byIp[ip] ?: JSONObject().put("ip", ip)
                    if (prev.optString("mac_oui").isBlank()) prev.put("mac_oui", macOui(mac))
                    if (prev.optString("mac_hash").isBlank()) prev.put("mac_hash", hashIdentity(normalizeMac(mac)))
                    prev.put("arp_flags", parts.getOrNull(2).orEmpty().take(16))
                    if (!prev.has("source")) prev.put("source", "proc_arp")
                    byIp[ip] = prev
                }
            }
        }

        val arr = JSONArray()
        byIp.values.sortedBy { ipOrder(it.optString("ip")) }.take(96).forEach { arr.put(it) }
        return JSONObject()
            .put("count", arr.length())
            .put("entries", arr)
    }

    private fun wifiNeighbors(context: Context): JSONObject {
        val result = JSONObject()
        val arr = JSONArray()
        return try {
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val scans = wm?.scanResults.orEmpty()
                .sortedByDescending { it.level }
                .take(32)
            for (s in scans) {
                val ssid = runCatching { s.SSID }.getOrDefault("")
                val bssid = runCatching { s.BSSID }.getOrDefault("")
                arr.put(JSONObject()
                    .put("ssid_hash", hashIdentity(ssid))
                    .put("bssid_hash", hashIdentity(normalizeMac(bssid)))
                    .put("bssid_oui", macOui(bssid))
                    .put("frequency_mhz", s.frequency)
                    .put("rssi_dbm", s.level)
                )
            }
            result.put("available", true).put("count", arr.length()).put("aps", arr)
        } catch (e: SecurityException) {
            result.put("available", false).put("error", "permission")
        } catch (e: Exception) {
            result.put("available", false).put("error", e.javaClass.simpleName)
        }
    }

    private fun sshBanner(ip: String): String {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 22), 500)
                socket.soTimeout = 700
                val buf = ByteArray(256)
                val n = socket.getInputStream().read(buf)
                if (n > 0) String(buf, 0, n, Charsets.US_ASCII)
                    .replace(Regex("[\\r\\n\\t]+"), " ").trim() else ""
            }
        } catch (_: Exception) { "" }
    }

    private fun httpFingerprint(ip: String, port: Int): JSONObject {
        return try {
            val suffix = if (port == 80) "" else ":" + port
            val conn = URL("http://" + ip + suffix + "/").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 700
            conn.readTimeout = 850
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "HAKIM-DeepLan/1")
            val status = conn.responseCode
            val server = conn.getHeaderField("Server").orEmpty().take(160)
            val location = conn.getHeaderField("Location").orEmpty().take(240)
            val auth = conn.getHeaderField("WWW-Authenticate").orEmpty().take(180)
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    buildString {
                        var total = 0
                        while (total < 32_000) {
                            val line = reader.readLine() ?: break
                            append(line).append('\n')
                            total += line.length
                        }
                    }
                }
            } else ""
            conn.disconnect()
            val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(body)?.groupValues?.get(1)
                ?.replace(Regex("\\s+"), " ")?.trim()?.take(180).orEmpty()
            JSONObject()
                .put("status", status)
                .put("server", server)
                .put("location", sanitizeLocalLocation(location))
                .put("auth_scheme", auth.substringBefore(' ').take(40))
                .put("title", title)
        } catch (e: Exception) {
            JSONObject().put("error", e.javaClass.simpleName)
        }
    }

    private fun tlsFingerprint(ip: String, port: Int): JSONObject {
        return try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            })
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, trustAll, SecureRandom())
            val socket = ctx.socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(ip, port), 700)
            socket.soTimeout = 900
            socket.startHandshake()
            val cert = socket.session.peerCertificates.firstOrNull() as? X509Certificate
            val protocol = socket.session.protocol.orEmpty()
            val cipher = socket.session.cipherSuite.orEmpty()
            socket.close()
            if (cert == null) JSONObject().put("protocol", protocol).put("cipher", cipher)
            else JSONObject()
                .put("protocol", protocol)
                .put("cipher", cipher)
                .put("subject", cert.subjectX500Principal.name.take(220))
                .put("issuer", cert.issuerX500Principal.name.take(220))
                .put("serial_hash", hashIdentity(cert.serialNumber.toString(16)))
                .put("not_after_ms", cert.notAfter.time)
        } catch (e: Exception) {
            JSONObject().put("error", e.javaClass.simpleName)
        }
    }

    private fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        } catch (_: Exception) { false }

    private fun isReachable(ip: String): Boolean =
        runCatching { InetAddress.getByName(ip).isReachable(450) }.getOrDefault(false)

    private fun runCommand(args: List<String>, timeoutMs: Long): String {
        return try {
            val p = ProcessBuilder(args).redirectErrorStream(true).start()
            if (!p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                p.destroy()
                ""
            } else {
                p.inputStream.bufferedReader().use { it.readText().take(32_000) }
            }
        } catch (_: Exception) { "" }
    }

    private fun normalizeMac(raw: String): String =
        raw.trim().uppercase().replace('-', ':')

    private fun macOui(raw: String): String {
        val mac = normalizeMac(raw)
        return if (Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$").matches(mac)) mac.substring(0, 8) else ""
    }

    private fun hashIdentity(value: String): String {
        if (value.isBlank()) return ""
        val d = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sanitizeLocalLocation(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val u = java.net.URI(raw)
            val host = u.host ?: return@runCatching ""
            if (!isPrivateIpv4(host)) return@runCatching ""
            val port = if (u.port > 0) ":" + u.port else ""
            u.scheme.orEmpty() + "://" + host + port + u.path.orEmpty().take(160)
        }.getOrDefault("")
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    private fun ipOrder(ip: String): Long {
        val p = ip.split(".").mapNotNull { it.toLongOrNull() }
        if (p.size != 4) return Long.MAX_VALUE
        return (p[0] shl 24) + (p[1] shl 16) + (p[2] shl 8) + p[3]
    }
}
