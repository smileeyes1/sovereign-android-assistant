package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * مسح LAN قرائي ومحدود بالشبكة الخاصة المتصل بها الهاتف.
 * لا يغير أي إعداد، لا يجرب كلمات مرور، ولا يرسل بيانات اعتماد.
 */
object HakimLanSurvey {
    const val VERSION = "LAN-SURVEY-2026-09-26-v1"
    private const val PREFS = "hakim_lan_survey"
    private const val CACHE_MS = 15 * 60_000L
    private val PORTS = intArrayOf(80, 443, 8080, 8443, 53, 22)

    fun inspect(context: Context, force: Boolean = false): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force) {
            val cachedAt = prefs.getLong("cached_at", 0L)
            val raw = prefs.getString("cached_json", null)
            if (raw != null && now - cachedAt in 0 until CACHE_MS) {
                return runCatching { JSONObject(raw).put("cached", true) }.getOrElse { JSONObject() }
            }
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return JSONObject()
            .put("version", VERSION).put("ok", false).put("error", "no_active_network")
        val lp = cm.getLinkProperties(network) ?: return JSONObject()
            .put("version", VERSION).put("ok", false).put("error", "no_link_properties")

        val ipv4 = lp.linkAddresses
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
        val localIp = ipv4?.address?.hostAddress.orEmpty()
        val prefix = ipv4?.prefixLength ?: 24
        val gateway = lp.routes.firstOrNull {
            it.isDefaultRoute && it.gateway is Inet4Address
        }?.gateway?.hostAddress.orEmpty()

        if (!isPrivateIpv4(localIp)) {
            return JSONObject().put("version", VERSION).put("ok", false)
                .put("error", "non_private_or_unknown_ipv4")
        }

        val targets = subnetTargets(localIp, prefix)
        val found = ConcurrentLinkedQueue<JSONObject>()
        val pool = Executors.newFixedThreadPool(48)
        val latch = CountDownLatch(targets.size)
        for (ip in targets) {
            pool.execute {
                try {
                    val open = JSONArray()
                    for (port in PORTS) {
                        if (tcpOpen(ip, port, 180)) open.put(port)
                    }
                    if (open.length() > 0) {
                        val fp = httpFingerprint(ip, open)
                        found.add(JSONObject()
                            .put("ip", ip)
                            .put("gateway", ip == gateway)
                            .put("open_ports", open)
                            .put("http_title", fp.optString("title"))
                            .put("http_server", fp.optString("server"))
                            .put("http_status", fp.optInt("status", -1))
                            .put("role_hint", roleHint(fp.optString("title"), fp.optString("server"), open))
                        )
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        pool.shutdownNow()

        val sorted = found.toList().sortedWith(compareBy<JSONObject> {
            !it.optBoolean("gateway")
        }.thenBy {
            it.optString("ip").split(".").lastOrNull()?.toIntOrNull() ?: 999
        })

        val hosts = JSONArray()
        var adminCandidates = 0
        var repeaterCandidates = 0
        for (host in sorted.take(64)) {
            hosts.put(host)
            val ports = host.optJSONArray("open_ports")
            if (ports != null && (containsInt(ports,80) || containsInt(ports,443) || containsInt(ports,8080) || containsInt(ports,8443))) {
                adminCandidates += 1
            }
            if (host.optString("role_hint") == "repeater_or_ap") repeaterCandidates += 1
        }

        val result = JSONObject()
            .put("version", VERSION)
            .put("ok", true)
            .put("cached", false)
            .put("local_ip", localIp)
            .put("prefix_length", prefix)
            .put("gateway", gateway)
            .put("scanned_hosts", targets.size)
            .put("responding_service_hosts", hosts.length())
            .put("management_candidates", adminCandidates)
            .put("repeater_or_ap_candidates", repeaterCandidates)
            .put("hosts", hosts)

        prefs.edit()
            .putLong("cached_at", now)
            .putString("cached_json", result.toString())
            .apply()
        return result
    }

    private fun subnetTargets(ip: String, prefix: Int): List<String> {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return emptyList()
        val effectivePrefix = prefix.coerceAtLeast(24).coerceAtMost(30)
        val value = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        val mask = -1 shl (32 - effectivePrefix)
        val network = value and mask
        val size = 1 shl (32 - effectivePrefix)
        val out = ArrayList<String>(size.coerceAtMost(254))
        for (n in 1 until size - 1) {
            val v = network + n
            val candidate = "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"
            if (candidate != ip) out.add(candidate)
        }
        return out
    }

    private fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean =
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        } catch (_: Exception) { false }

    private fun httpFingerprint(ip: String, ports: JSONArray): JSONObject {
        val candidates = arrayListOf<String>()
        if (containsInt(ports,80)) candidates.add("http://$ip/")
        if (containsInt(ports,8080)) candidates.add("http://$ip:8080/")
        for (url in candidates) {
            val result = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 500
                conn.readTimeout = 650
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "HAKIM-LAN-Survey/1")
                val status = conn.responseCode
                val server = conn.getHeaderField("Server").orEmpty().take(120)
                val stream = if (status in 200..399) conn.inputStream else conn.errorStream
                val body = if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        buildString {
                            var total = 0
                            while (total < 24_000) {
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
                    ?.replace(Regex("\\s+"), " ")?.trim()?.take(160).orEmpty()
                JSONObject().put("status", status).put("server", server).put("title", title)
            }.getOrNull()
            if (result != null) return result
        }
        return JSONObject()
    }

    private fun roleHint(title: String, server: String, ports: JSONArray): String {
        val text = (title + " " + server).lowercase()
        val repeaterWords = listOf("repeater","range extender","extender","access point","wireless ap","mesh","mercusys","tenda","tp-link","d-link","totolink","xiaomi","netis")
        if (repeaterWords.any { text.contains(it) }) return "repeater_or_ap"
        if (text.contains("router") || text.contains("gateway") || text.contains("modem")) return "router_or_gateway"
        if (containsInt(ports,80) || containsInt(ports,443) || containsInt(ports,8080) || containsInt(ports,8443)) return "web_managed_device"
        return "network_service"
    }

    private fun containsInt(array: JSONArray, value: Int): Boolean {
        for (i in 0 until array.length()) if (array.optInt(i) == value) return true
        return false
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }
}
