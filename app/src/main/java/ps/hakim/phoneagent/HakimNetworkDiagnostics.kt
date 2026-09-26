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
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

/**
 * تشخيص شبكة قرائي فقط:
 * - لا يغير Wi‑Fi أو الراوتر.
 * - لا يمس كلمات المرور أو جلسات الإدارة.
 * - يحذف SSID/BSSID الخام ويعيد بصمات قصيرة فقط.
 * - يقيس LAN مقابل WAN ويكتشف أجهزة UPnP المعلنة محليًا.
 */
object HakimNetworkDiagnostics {
    const val VERSION = "NETWORK-DIAGNOSTICS-2026-09-26-v1"
    private const val MAX_UPNP = 12
    private const val MAX_XML = 48 * 1024

    fun inspect(context: Context): JSONObject {
        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val lp = network?.let { cm.getLinkProperties(it) }
        val wifi = wifiInfo(app, caps)

        val gateway = lp?.routes?.firstOrNull {
            it.isDefaultRoute && it.gateway is Inet4Address
        }?.gateway?.hostAddress.orEmpty()

        val result = JSONObject()
            .put("version", VERSION)
            .put("transport_wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            .put("internet_capability", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            .put("validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            .put("metered", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true)
            .put("gateway", gateway)
            .put("dns", JSONArray(lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList<String>()))
            .put("interface", lp?.interfaceName.orEmpty())
            .put("mtu", lp?.mtu ?: 0)

        if (wifi != null) {
            val frequency = wifi.frequency
            result.put("wifi", JSONObject()
                .put("ssid_hash", hashIdentity(cleanIdentity(wifi.ssid)))
                .put("bssid_hash", hashIdentity(cleanIdentity(wifi.bssid)))
                .put("rssi_dbm", wifi.rssi)
                .put("signal_level_5", WifiManager.calculateSignalLevel(wifi.rssi, 5))
                .put("frequency_mhz", frequency)
                .put("band", when {
                    frequency in 2400..2500 -> "2.4GHz"
                    frequency in 4900..5900 -> "5GHz"
                    frequency in 5925..7125 -> "6GHz"
                    else -> "unknown"
                })
                .put("link_speed_mbps", wifi.linkSpeed)
                .put("rx_link_speed_mbps", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wifi.rxLinkSpeedMbps else -1)
                .put("tx_link_speed_mbps", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wifi.txLinkSpeedMbps else -1)
            )
        } else {
            result.put("wifi", JSONObject().put("available", false))
        }

        if (gateway.isNotBlank()) {
            result.put("gateway_tcp", tcpProbe(gateway, intArrayOf(80, 443), 4))
        }
        result.put("internet_tcp", tcpProbe("1.1.1.1", intArrayOf(443), 4))
        result.put("upnp_devices", discoverUpnp(app))
        result.put("lan_survey", HakimLanSurvey.inspect(app))
        return result
    }

    private fun wifiInfo(context: Context, caps: NetworkCapabilities?): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps?.transportInfo as? WifiInfo)?.let { return it }
        }
        @Suppress("DEPRECATION")
        return runCatching {
            context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
        }.getOrNull()
    }

    private fun cleanIdentity(value: String?): String {
        val s = value.orEmpty().trim().trim('"')
        if (s.isBlank() || s.equals("<unknown ssid>", true) || s == "02:00:00:00:00:00") return ""
        return s
    }

    private fun hashIdentity(value: String): String {
        if (value.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun tcpProbe(host: String, ports: IntArray, count: Int): JSONObject {
        val samples = ArrayList<Int>()
        var chosenPort = -1
        repeat(count) {
            var success = false
            for (port in ports) {
                val start = System.nanoTime()
                try {
                    java.net.Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 1200)
                    }
                    val ms = ((System.nanoTime() - start) / 1_000_000.0).roundToInt()
                    samples.add(ms)
                    chosenPort = port
                    success = true
                    break
                } catch (_: Exception) {}
            }
            if (!success) samples.add(-1)
        }
        val good = samples.filter { it >= 0 }.sorted()
        return JSONObject()
            .put("host", host)
            .put("port", chosenPort)
            .put("samples_ms", JSONArray(samples))
            .put("successes", good.size)
            .put("median_ms", if (good.isEmpty()) -1 else good[good.size / 2])
            .put("min_ms", good.minOrNull() ?: -1)
            .put("max_ms", good.maxOrNull() ?: -1)
    }

    private fun discoverUpnp(context: Context): JSONArray {
        val locations = LinkedHashSet<String>()
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = runCatching { wm?.createMulticastLock("hakim-network-diagnostics") }.getOrNull()
        try {
            lock?.setReferenceCounted(false)
            lock?.acquire()
            DatagramSocket().use { socket ->
                socket.soTimeout = 450
                val msg = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: ssdp:all\r\n\r\n"
                    ).toByteArray(StandardCharsets.US_ASCII)
                socket.send(DatagramPacket(msg, msg.size, java.net.InetAddress.getByName("239.255.255.250"), 1900))
                val deadline = System.currentTimeMillis() + 1400
                while (System.currentTimeMillis() < deadline && locations.size < MAX_UPNP) {
                    val buf = ByteArray(8192)
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) {
                        break
                    }
                    val text = String(packet.data, 0, packet.length, StandardCharsets.ISO_8859_1)
                    val location = text.lineSequence()
                        .firstOrNull { it.startsWith("location:", true) }
                        ?.substringAfter(":")?.trim()
                    if (!location.isNullOrBlank() && safeLocalUrl(location)) locations.add(location)
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }

        val out = JSONArray()
        for (location in locations.take(MAX_UPNP)) {
            val uri = runCatching { URI(location) }.getOrNull() ?: continue
            val xml = fetchLocalXml(location)
            out.put(JSONObject()
                .put("ip", uri.host.orEmpty())
                .put("manufacturer", xmlTag(xml, "manufacturer").take(80))
                .put("model_name", xmlTag(xml, "modelName").take(80))
                .put("model_number", xmlTag(xml, "modelNumber").take(80))
                .put("device_type", xmlTag(xml, "deviceType").take(120))
            )
        }
        return out
    }

    private fun safeLocalUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return false
        val host = uri.host ?: return false
        val addr = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return false
        val b = addr.address
        if (b.size != 4) return false
        val a = b[0].toInt() and 0xff
        val c = b[1].toInt() and 0xff
        return a == 10 || (a == 172 && c in 16..31) || (a == 192 && c == 168)
    }

    private fun fetchLocalXml(raw: String): String {
        return try {
            val conn = URL(raw).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 1000
            conn.readTimeout = 1200
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "HAKIM-Network-Diagnostics/1")
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return ""
            }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(4096)
                while (out.size() < MAX_XML) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            conn.disconnect()
            out.toString(StandardCharsets.UTF_8.name())
        } catch (_: Exception) { "" }
    }

    private fun xmlTag(xml: String, tag: String): String =
        Regex(
            "<(?:\\w+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$tag>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(xml)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
}
