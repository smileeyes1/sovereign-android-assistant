package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/**
 * تشخيص شبكة منزلي للقراءة فقط.
 * لا يغيّر DHCP/DNS/Wi-Fi/NAT ولا يسجل الدخول للراوتر.
 */
object HakimNetworkDiagnostics {
    private const val MAX_BODY = 64 * 1024
    private val VENDORS = listOf(
        "ZTE", "ZXHN", "TP-Link", "TP-LINK", "Tenda", "Huawei", "D-Link",
        "MERCUSYS", "Xiaomi", "Nokia", "FiberHome", "TOTOLINK", "MikroTik"
    )

    fun snapshot(context: Context): JSONObject {
        val out = JSONObject().put("read_only", true).put("version", "NETWORK-DIAG-20307-v1")
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val lp = network?.let { cm.getLinkProperties(it) }

        out.put("transport_wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
        out.put("transport_cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
        out.put("validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        out.put("interface_name", lp?.interfaceName.orEmpty())

        val gateway = lp?.routes?.firstOrNull {
            it.isDefaultRoute && it.gateway is Inet4Address
        }?.gateway?.hostAddress.orEmpty()
        out.put("gateway", gateway)
        out.put("dns", JSONArray().apply {
            lp?.dnsServers?.filterIsInstance<Inet4Address>()?.forEach { put(it.hostAddress.orEmpty()) }
        })
        out.put("addresses", JSONArray().apply {
            lp?.linkAddresses?.filter { it.address is Inet4Address }?.forEach {
                put("${it.address.hostAddress}/${it.prefixLength}")
            }
        })

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = runCatching { wifi?.connectionInfo }.getOrNull()
        out.put("wifi", wifiSnapshot(info, wifi))

        if (gateway.isNotBlank()) {
            out.put("gateway_tcp_ms", bestTcpLatency(gateway, listOf(80, 443, 49000)))
            out.put("gateway_dns_ms", udpDnsLatency(gateway, "example.com"))
        } else {
            out.put("gateway_tcp_ms", -1)
            out.put("gateway_dns_ms", -1)
        }
        out.put("internet_https_ms", https204Latency())

        val candidates = LinkedHashSet<String>()
        if (gateway.isNotBlank()) candidates.add(gateway)
        listOf("192.168.0.1", "192.168.1.1", "192.168.100.1").forEach { candidates.add(it) }

        val admin = JSONArray()
        var respondingAdmins = 0
        for (host in candidates.take(4)) {
            val probe = probeAdmin(host)
            if (probe.optBoolean("reachable", false)) respondingAdmins += 1
            admin.put(probe)
        }
        out.put("admin_candidates", admin)

        val upnp = discoverUpnp()
        out.put("upnp_devices", upnp)

        val rssi = info?.rssi ?: -127
        val frequency = info?.frequency ?: 0
        val weak = rssi != -127 && rssi < -70
        val twoFour = frequency in 2400..2500
        out.put("assessment", JSONObject()
            .put("weak_wifi_signal", weak)
            .put("two_point_four_ghz", twoFour)
            .put("multiple_router_admins_reachable", respondingAdmins > 1)
            .put("secondary_router_suspected", respondingAdmins > 1 || upnp.length() > 1)
        )
        return out
    }

    private fun wifiSnapshot(info: WifiInfo?, wifi: WifiManager?): JSONObject {
        if (info == null) return JSONObject().put("available", false)
        val ssid = runCatching { info.ssid }.getOrDefault("")
            .removePrefix(""").removeSuffix(""")
            .takeUnless { it == "<unknown ssid>" }.orEmpty()
        val bssid = runCatching { info.bssid }.getOrNull().orEmpty()
        val o = JSONObject()
            .put("available", true)
            .put("ssid_available", ssid.isNotBlank())
            .put("ssid", ssid.take(80))
            .put("bssid_suffix", bssid.takeLast(5))
            .put("rssi_dbm", info.rssi)
            .put("link_speed_mbps", info.linkSpeed)
            .put("frequency_mhz", info.frequency)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            o.put("rx_link_speed_mbps", runCatching { info.rxLinkSpeedMbps }.getOrDefault(-1))
            o.put("tx_link_speed_mbps", runCatching { info.txLinkSpeedMbps }.getOrDefault(-1))
        }

        val same = JSONArray()
        if (ssid.isNotBlank() && wifi != null) {
            runCatching {
                wifi.scanResults
                    .filter { it.SSID == ssid }
                    .sortedByDescending { it.level }
                    .take(12)
                    .forEach {
                        same.put(JSONObject()
                            .put("bssid_suffix", it.BSSID.orEmpty().takeLast(5))
                            .put("rssi_dbm", it.level)
                            .put("frequency_mhz", it.frequency)
                        )
                    }
            }
        }
        o.put("same_ssid_ap_count", same.length())
        o.put("same_ssid_aps", same)
        return o
    }

    private fun bestTcpLatency(host: String, ports: List<Int>): Int {
        var best = Int.MAX_VALUE
        for (port in ports) {
            repeat(2) {
                val started = System.nanoTime()
                val ok = runCatching {
                    Socket().use { s -> s.connect(InetSocketAddress(host, port), 700) }
                    true
                }.getOrDefault(false)
                if (ok) {
                    val ms = ((System.nanoTime() - started) / 1_000_000.0).roundToInt()
                    if (ms < best) best = ms
                }
            }
        }
        return if (best == Int.MAX_VALUE) -1 else best
    }

    private fun udpDnsLatency(server: String, name: String): Int {
        return try {
            val id = (System.nanoTime() and 0xffff).toInt()
            val out = ByteArrayOutputStream()
            out.write((id ushr 8) and 0xff); out.write(id and 0xff)
            out.write(0x01); out.write(0x00); out.write(0); out.write(1)
            repeat(6) { out.write(0) }
            for (label in name.split('.')) {
                val bytes = label.toByteArray(StandardCharsets.US_ASCII)
                out.write(bytes.size); out.write(bytes)
            }
            out.write(0); out.write(0); out.write(1); out.write(0); out.write(1)
            val q = out.toByteArray()
            val started = System.nanoTime()
            DatagramSocket().use { socket ->
                socket.soTimeout = 1200
                socket.send(DatagramPacket(q, q.size, InetAddress.getByName(server), 53))
                val buf = ByteArray(2048)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)
            }
            ((System.nanoTime() - started) / 1_000_000.0).roundToInt()
        } catch (_: Exception) { -1 }
    }

    private fun https204Latency(): Int {
        return try {
            val started = System.nanoTime()
            val c = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 2200
                c.readTimeout = 2200
                c.instanceFollowRedirects = false
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "HAKIM-Network-Diag/1")
                val code = c.responseCode
                runCatching { c.inputStream.close() }
                if (code in 200..399) ((System.nanoTime() - started) / 1_000_000.0).roundToInt() else -1
            } finally { c.disconnect() }
        } catch (_: Exception) { -1 }
    }

    private fun probeAdmin(host: String): JSONObject {
        val result = JSONObject().put("host", host).put("reachable", false)
        if (!isPrivateV4(host)) return result
        for (port in listOf(80, 49000)) {
            val response = rawHttp(host, port, "/")
            if (response.first in 100..599) {
                val raw = response.second
                val headers = raw.substringBefore("\r\n\r\n", "")
                val body = raw.substringAfter("\r\n\r\n", "")
                val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(body)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val server = headers.lineSequence()
                    .firstOrNull { it.startsWith("Server:", true) }
                    ?.substringAfter(":")?.trim().orEmpty()
                val vendor = vendorHint("$headers\n$title\n$body")
                return result
                    .put("reachable", true)
                    .put("port", port)
                    .put("http_status", response.first)
                    .put("title", title.take(120))
                    .put("server", server.take(120))
                    .put("vendor_hint", vendor)
            }
        }
        return result
    }

    private fun discoverUpnp(): JSONArray {
        val locations = LinkedHashSet<String>()
        val msg = (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 1\r\n" +
                "ST: upnp:rootdevice\r\n\r\n"
        ).toByteArray(StandardCharsets.US_ASCII)
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 350
                socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1900))
                val until = System.currentTimeMillis() + 700
                while (System.currentTimeMillis() < until && locations.size < 12) {
                    val buf = ByteArray(8192)
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (_: Exception) { break }
                    val text = String(packet.data, 0, packet.length, StandardCharsets.ISO_8859_1)
                    val location = text.lineSequence()
                        .firstOrNull { it.startsWith("location:", true) }
                        ?.substringAfter(":")?.trim()
                    if (!location.isNullOrBlank()) locations.add(location)
                }
            }
        }

        val out = JSONArray()
        for (location in locations.take(8)) {
            val uri = runCatching { URI(location) }.getOrNull() ?: continue
            val host = uri.host ?: continue
            if (!isPrivateV4(host) || uri.scheme != "http") continue
            val port = if (uri.port > 0) uri.port else 80
            if (port !in setOf(80, 49000)) continue
            val raw = rawHttp(host, port, uri.rawPath?.ifBlank { "/" } ?: "/")
            if (raw.first !in 200..299) continue
            val body = raw.second.substringAfter("\r\n\r\n", "")
            out.put(JSONObject()
                .put("host", host)
                .put("friendly_name", xmlTag(body, "friendlyName").take(100))
                .put("manufacturer", xmlTag(body, "manufacturer").take(100))
                .put("model_name", xmlTag(body, "modelName").take(100))
                .put("model_number", xmlTag(body, "modelNumber").take(80))
            )
        }
        return out
    }

    private fun rawHttp(host: String, port: Int, path: String): Pair<Int,String> {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 900)
                socket.soTimeout = 1200
                val req = (
                    "GET ${if (path.startsWith("/")) path else "/$path"} HTTP/1.1\r\n" +
                        "Host: $host:$port\r\n" +
                        "Connection: close\r\n" +
                        "User-Agent: HAKIM-Network-Diag/1\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII)
                socket.getOutputStream().apply { write(req); flush() }
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (out.size() < MAX_BODY) {
                    val n = try { socket.getInputStream().read(buf) } catch (_: Exception) { -1 }
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                val raw = out.toString(StandardCharsets.UTF_8.name())
                val status = Regex("HTTP/1\\.[01]\\s+(\\d{3})").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                status to raw
            }
        } catch (_: Exception) { -1 to "" }
    }

    private fun vendorHint(text: String): String {
        val found = VENDORS.firstOrNull { text.contains(it, ignoreCase = true) } ?: return ""
        return found.replace("TP-LINK", "TP-Link")
    }

    private fun isPrivateV4(host: String): Boolean {
        return runCatching {
            val a = InetAddress.getByName(host)
            a is Inet4Address && (a.isSiteLocalAddress || host.startsWith("192.168."))
        }.getOrDefault(false)
    }

    private fun xmlTag(xml: String, tag: String): String =
        Regex("<(?:\\w+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(xml)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
}
