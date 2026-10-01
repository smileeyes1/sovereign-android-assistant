package ps.hakim.phoneagent

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * حارس شبكة حكيم:
 * - يستهدف الراوتر المنزلي المثبت 192.168.1.1 فقط، حتى عند المرور عبر مقوٍ/راوتر فرعي.
 * - عند بوابة فرعية لا يستمر إلا بعد إثبات مباشر وقرائي لهوية ZTE F8040 على 192.168.1.1.
 * - كشف أولاً، ثم تعديل DNS فقط عبر خدمة LANHostConfigManagement القياسية.
 * - لا يتجاوز المصادقة، ولا يلمس WAN أو إدارة مزود الخدمة، ولا ينفذ shell/root.
 * - يحفظ خط الأساس قبل التعديل، ويفشل مغلقاً عند غياب بصمة الراوتر المنزلية المثبتة ميدانياً.
 */
object HakimNetworkGuardian {
    const val JOB_ID = 771209
    private const val PERIOD_MS = 15L * 60L * 1000L
    private const val EXPECTED_GATEWAY = "192.168.1.1"
    private const val FAMILY_DNS_1 = "185.228.168.168"
    private const val FAMILY_DNS_2 = "185.228.169.168"
    private const val MAX_BODY = 96 * 1024
    private const val PREFS = "hakim_network_guardian"
    private val running = AtomicBoolean(false)
    @Volatile private var callbackInstalled = false

    data class ServiceEndpoint(val serviceType: String, val controlPath: String, val port: Int)
    data class RawResponse(val status: Int, val body: String)

    fun install(context: Context) {
        val app = context.applicationContext
        schedule(app)
        installNetworkCallback(app)
        inspectAsync(app, "install")
    }

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, HakimNetworkGuardianJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build()
            scheduler.schedule(job)
        } catch (e: Exception) {
            record(context, "SCHEDULE_FAILED", e.javaClass.simpleName)
        }
    }

    @Synchronized
    private fun installNetworkCallback(context: Context) {
        if (callbackInstalled) return
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = inspectAsync(context, "network_available")
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        inspectAsync(context, "wifi_capabilities")
                    }
                }
            })
            callbackInstalled = true
        } catch (e: Exception) {
            record(context, "CALLBACK_FAILED", e.javaClass.simpleName)
        }
    }

    fun inspectAsync(context: Context, reason: String) {
        if (!running.compareAndSet(false, true)) return
        Thread {
            try {
                inspectAndProtect(context.applicationContext, reason)
            } catch (t: Throwable) {
                record(context, "ERROR", t.javaClass.simpleName + ":" + t.message.orEmpty().take(160))
            } finally {
                running.set(false)
            }
        }.start()
    }

    fun inspectAndProtect(context: Context, reason: String): JSONObject {
        val now = System.currentTimeMillis()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return finish(context, "NO_ACTIVE_NETWORK", reason, now)
        val caps = cm.getNetworkCapabilities(network)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return finish(context, "NOT_WIFI", reason, now)
        }
        val lp = cm.getLinkProperties(network) ?: return finish(context, "NO_LINK_PROPERTIES", reason, now)
        val gateway = lp.routes.firstOrNull { route ->
            route.isDefaultRoute && route.gateway is Inet4Address
        }?.gateway?.hostAddress

        val directGateway = gateway == EXPECTED_GATEWAY
        val secondaryPrivateGateway = gateway != null && isPrivateIpv4(gateway)
        val upstreamF8040 = if (directGateway) false else
            secondaryPrivateGateway && probeExpectedF8040()
        if (!directGateway && !upstreamF8040) {
            return finish(context, "OUTSIDE_HOME_GATEWAY", reason, now, gateway ?: "")
        }
        val targetGateway = EXPECTED_GATEWAY

        val p = prefs(context)
        p.edit()
            .putString("gateway", gateway.orEmpty())
            .putString("target_gateway", targetGateway)
            .putBoolean("via_secondary_gateway", !directGateway)
            .putBoolean("upstream_f8040_proven", upstreamF8040)
            .putString("observed_dns", lp.dnsServers.joinToString(",") { it.hostAddress.orEmpty() })
            .putLong("last_seen_home_at", now)
            .apply()

        val descriptions = discoverDescriptions(targetGateway)
        val fingerprint = descriptions.joinToString("\n").take(MAX_BODY)
        val descriptionZte = fingerprint.contains("ZTE", ignoreCase = true)
        val descriptionFamily = fingerprint.contains("ZXHN", ignoreCase = true) ||
            fingerprint.contains("F6600P", ignoreCase = true)

        val fieldIdentity = if (directGateway) gatewayFieldIdentity(context, force = true)
            else GatewayFieldIdentity("", "", "", -1)
        val fieldZteF8040 =
            upstreamF8040 || (
                fieldIdentity.vendor.equals("ZTE", ignoreCase = true) &&
                fieldIdentity.model.equals("F8040", ignoreCase = true) &&
                fieldIdentity.role == "router_or_gateway" &&
                fieldIdentity.httpStatus in 200..399
            )

        val zte = descriptionZte || fieldZteF8040
        val approvedModel = descriptionFamily || fieldZteF8040

        p.edit()
            .putBoolean("fingerprint_zte", zte)
            .putBoolean("fingerprint_zxhn", descriptionFamily)
            .putBoolean("fingerprint_f8040", fieldZteF8040)
            .putString("field_router_vendor", fieldIdentity.vendor.take(40))
            .putString("field_router_model", fieldIdentity.model.take(40))
            .putInt("description_count", descriptions.size)
            .apply()

        if (!zte || !approvedModel) {
            return finish(context, "ROUTER_FINGERPRINT_NOT_PROVEN", reason, now, targetGateway)
        }

        val endpoint = findLanHostConfigEndpoint(descriptions)
        if (endpoint == null) {
            return finish(context, "TR064_LANHOST_NOT_FOUND", reason, now, targetGateway)
        }

        p.edit()
            .putString("lanhost_service_type", endpoint.serviceType)
            .putString("lanhost_control_path", endpoint.controlPath)
            .putInt("lanhost_control_port", endpoint.port)
            .apply()

        val before = soap(endpoint, targetGateway, "GetDNSServers", "")
        if (before.status == 401 || before.status == 403) {
            p.edit().putBoolean("router_auth_required", true).apply()
            return finish(context, "ROUTER_AUTH_REQUIRED", reason, now, targetGateway)
        }
        if (before.status !in 200..299) {
            return finish(context, "TR064_READ_FAILED_${before.status}", reason, now, targetGateway)
        }

        val currentDns = xmlTag(before.body, "NewDNSServers").trim()
        if (currentDns.isNotBlank() && !p.contains("baseline_dns")) {
            p.edit().putString("baseline_dns", currentDns).putLong("baseline_dns_at", now).apply()
        }

        val wanted = "$FAMILY_DNS_1,$FAMILY_DNS_2"
        val changedByGuardian = !containsBothFamilyDns(currentDns)
        if (changedByGuardian) {
            val setBody = "<NewDNSServers>$wanted</NewDNSServers>"
            val set = soap(endpoint, targetGateway, "SetDNSServer", setBody)
            if (set.status == 401 || set.status == 403) {
                p.edit().putBoolean("router_auth_required", true).apply()
                return finish(context, "ROUTER_AUTH_REQUIRED", reason, now, targetGateway)
            }
            if (set.status !in 200..299) {
                return finish(context, "TR064_SET_DNS_FAILED_${set.status}", reason, now, targetGateway)
            }
        }

        val after = soap(endpoint, targetGateway, "GetDNSServers", "")
        if (after.status !in 200..299) {
            if (changedByGuardian) rollbackDns(context, endpoint, targetGateway, currentDns)
            return finish(context, "TR064_VERIFY_READ_FAILED_${after.status}", reason, now, targetGateway)
        }
        val verifiedDns = xmlTag(after.body, "NewDNSServers").trim()
        val configured = containsBothFamilyDns(verifiedDns)
        p.edit()
            .putString("verified_router_dns", verifiedDns)
            .putBoolean("family_dns_configured", configured)
            .putLong("last_router_dns_verify_at", System.currentTimeMillis())
            .apply()

        val resolverGood = dnsQuery(FAMILY_DNS_1, "cleanbrowsing.org")
        val resolverBlocked = dnsQuery(FAMILY_DNS_1, "pornhub.com")
        val familyResolverVerified = resolverGood == 0 && resolverBlocked == 3
        p.edit()
            .putBoolean("family_resolver_verified", familyResolverVerified)
            .putInt("family_resolver_good_rcode", resolverGood)
            .putInt("family_resolver_blocked_rcode", resolverBlocked)
            .apply()

        if (!configured || !familyResolverVerified) {
            if (changedByGuardian) {
                val rolledBack = rollbackDns(context, endpoint, targetGateway, currentDns)
                return finish(
                    context,
                    if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED",
                    reason,
                    now,
                    targetGateway
                )
            }
            return finish(context, "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED", reason, now, targetGateway)
        }

        return finish(context, "FAMILY_DNS_CONFIGURED", reason, now, targetGateway)
    }

    fun status(context: Context): JSONObject {
        val p = prefs(context)
        return JSONObject()
            .put("state", p.getString("state", "NOT_RUN"))
            .put("gateway", p.getString("gateway", ""))
            .put("observed_dns", p.getString("observed_dns", ""))
            .put("fingerprint_zte", p.getBoolean("fingerprint_zte", false))
            .put("fingerprint_zxhn", p.getBoolean("fingerprint_zxhn", false))
            .put("fingerprint_f8040", p.getBoolean("fingerprint_f8040", false))
            .put("via_secondary_gateway", p.getBoolean("via_secondary_gateway", false))
            .put("upstream_f8040_proven", p.getBoolean("upstream_f8040_proven", false))
            .put("router_auth_required", p.getBoolean("router_auth_required", false))
            .put("baseline_dns_saved", p.contains("baseline_dns"))
            .put("family_dns_configured", p.getBoolean("family_dns_configured", false))
            .put("family_resolver_verified", p.getBoolean("family_resolver_verified", false))
            .put("rollback_state", p.getString("rollback_state", "not_needed"))
            .put("last_run_at", p.getLong("last_run_at", 0L))
            .put("last_reason", p.getString("last_reason", ""))
            .put("last_detail", p.getString("last_detail", ""))
            .put("full_bypass_prevention", false)
            .put("dns_redirect_forced", false)
            .put("dot_blocked", false)
            .put("doh_controlled", false)
            .put("vpn_blocked", false)
    }

    private data class GatewayFieldIdentity(
        val vendor: String,
        val model: String,
        val role: String,
        val httpStatus: Int
    )

    private fun gatewayFieldIdentity(context: Context, force: Boolean = false): GatewayFieldIdentity {
        val survey = runCatching { HakimLanSurvey.inspect(context, force = force) }.getOrNull()
            ?: return GatewayFieldIdentity("", "", "", -1)
        val hosts = survey.optJSONArray("hosts")
            ?: return GatewayFieldIdentity("", "", "", -1)
        for (i in 0 until hosts.length()) {
            val host = hosts.optJSONObject(i) ?: continue
            if (!host.optBoolean("gateway", false)) continue
            return GatewayFieldIdentity(
                vendor = host.optString("vendor_hint").trim().take(40),
                model = host.optString("model_hint").trim().take(40),
                role = host.optString("role_hint").trim().take(40),
                httpStatus = host.optInt("http_status", -1)
            )
        }
        return GatewayFieldIdentity("", "", "", -1)
    }

    private fun probeExpectedF8040(): Boolean {
        return runCatching {
            val conn = URL("https://$EXPECTED_GATEWAY/").openConnection() as HttpsURLConnection
            val trustAll = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            }
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            conn.sslSocketFactory = ssl.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { host, _ -> host == EXPECTED_GATEWAY }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 900
            conn.readTimeout = 1200
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "HAKIM-Network-Guardian/2")
            val status = conn.responseCode
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                buildString {
                    var total = 0
                    while (total < 24_000) {
                        val line = reader.readLine() ?: break
                        append(line).append('\n')
                        total += line.length
                    }
                }
            }.orEmpty()
            conn.disconnect()
            val text = body.lowercase()
            status in 200..399 && "zte" in text && "f8040" in text
        }.getOrDefault(false)
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168)
    }

    private fun finish(context: Context, state: String, reason: String, at: Long, detail: String = ""): JSONObject {
        prefs(context).edit()
            .putString("state", state)
            .putString("last_reason", reason.take(80))
            .putString("last_detail", detail.take(240))
            .putLong("last_run_at", at)
            .apply()
        return status(context)
    }

    private fun record(context: Context, state: String, detail: String) {
        finish(context, state, "internal", System.currentTimeMillis(), detail)
    }

    private fun discoverDescriptions(gateway: String): List<String> {
        val urls = LinkedHashSet<String>()
        urls.addAll(ssdpLocations(gateway))
        listOf(
            "http://$gateway:49000/tr64desc.xml",
            "http://$gateway:49000/igddesc.xml",
            "http://$gateway/tr64desc.xml",
            "http://$gateway/igddesc.xml",
            "http://$gateway/rootDesc.xml",
            "http://$gateway/upnp/IGD.xml"
        ).forEach { urls.add(it) }

        val docs = ArrayList<String>()
        for (url in urls.take(12)) {
            val u = runCatching { URI(url) }.getOrNull() ?: continue
            if (u.host != gateway || u.scheme != "http") continue
            val port = if (u.port > 0) u.port else 80
            if (port !in setOf(80, 49000)) continue
            val path = (u.rawPath ?: "/").ifBlank { "/" }
            val r = rawHttp(gateway, port, "GET", path, emptyMap(), "")
            if (r.status in 200..299 && r.body.contains('<')) {
                docs.add("SOURCE=$url\n${r.body.take(MAX_BODY)}")
            }
        }
        return docs
    }

    private fun ssdpLocations(gateway: String): Set<String> {
        val found = LinkedHashSet<String>()
        val targets = listOf(
            "urn:dslforum-org:device:InternetGatewayDevice:1",
            "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
            "upnp:rootdevice"
        )
        for (st in targets) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 650
                    val msg = (
                        "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 1\r\n" +
                            "ST: $st\r\n\r\n"
                        ).toByteArray(StandardCharsets.US_ASCII)
                    socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1900))
                    val deadline = System.currentTimeMillis() + 900
                    while (System.currentTimeMillis() < deadline) {
                        val buf = ByteArray(8192)
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (_: Exception) {
                            break
                        }
                        val text = String(packet.data, 0, packet.length, StandardCharsets.ISO_8859_1)
                        val location = text.lineSequence()
                            .firstOrNull { it.startsWith("location:", ignoreCase = true) }
                            ?.substringAfter(":")?.trim()
                        if (!location.isNullOrBlank()) {
                            val uri = runCatching { URI(location) }.getOrNull()
                            if (uri?.host == gateway && uri.scheme == "http") found.add(location)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return found
    }

    private fun findLanHostConfigEndpoint(descriptions: List<String>): ServiceEndpoint? {
        val serviceRegex = Regex("<service>(.*?)</service>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (doc in descriptions) {
            val source = doc.lineSequence().firstOrNull()?.substringAfter("SOURCE=", "") ?: ""
            val sourceUri = runCatching { URI(source) }.getOrNull()
            val defaultPort = sourceUri?.port?.takeIf { it > 0 } ?: 80
            for (m in serviceRegex.findAll(doc)) {
                val block = m.groupValues[1]
                val serviceType = xmlTag(block, "serviceType")
                if (!serviceType.contains("LANHostConfigManagement", ignoreCase = true)) continue
                val control = xmlTag(block, "controlURL")
                if (control.isBlank()) continue
                val controlUri = runCatching { URI(control) }.getOrNull()
                val port = controlUri?.port?.takeIf { it > 0 } ?: defaultPort
                val path = when {
                    control.startsWith("http://") -> controlUri?.rawPath ?: "/"
                    control.startsWith("/") -> control
                    else -> "/$control"
                }
                if (port !in setOf(80, 49000)) continue
                return ServiceEndpoint(serviceType.trim(), path, port)
            }
        }
        return null
    }

    private fun soap(endpoint: ServiceEndpoint, gateway: String, action: String, inner: String): RawResponse {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$action xmlns:u=\"${endpoint.serviceType}\">$inner</u:$action>" +
            "</s:Body></s:Envelope>"
        return rawHttp(
            gateway,
            endpoint.port,
            "POST",
            endpoint.controlPath,
            mapOf(
                "Content-Type" to "text/xml; charset=\"utf-8\"",
                "SOAPAction" to "\"${endpoint.serviceType}#$action\""
            ),
            body
        )
    }

    private fun rawHttp(
        host: String,
        port: Int,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String
    ): RawResponse {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1800)
                socket.soTimeout = 2200
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                val safePath = if (path.startsWith("/")) path else "/$path"
                val request = buildString {
                    append("$method $safePath HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Connection: close\r\n")
                    append("User-Agent: HAKIM-Network-Guardian/1\r\n")
                    headers.forEach { (k, v) -> append("$k: $v\r\n") }
                    if (bytes.isNotEmpty()) append("Content-Length: ${bytes.size}\r\n")
                    append("\r\n")
                }.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    write(request)
                    if (bytes.isNotEmpty()) write(bytes)
                    flush()
                }
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (out.size() < MAX_BODY + 8192) {
                    val n = try { socket.getInputStream().read(buf) } catch (_: Exception) { -1 }
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                val raw = out.toString(StandardCharsets.UTF_8.name())
                val status = Regex("HTTP/1\\.[01]\\s+(\\d{3})").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                val responseBody = raw.substringAfter("\r\n\r\n", "").take(MAX_BODY)
                RawResponse(status, responseBody)
            }
        } catch (_: Exception) {
            RawResponse(-1, "")
        }
    }

    private fun dnsQuery(server: String, name: String): Int {
        return try {
            val id = (System.nanoTime() and 0xffff).toInt()
            val out = ByteArrayOutputStream()
            out.write((id ushr 8) and 0xff); out.write(id and 0xff)
            out.write(0x01); out.write(0x00)
            out.write(0x00); out.write(0x01)
            out.write(0x00); out.write(0x00)
            out.write(0x00); out.write(0x00)
            out.write(0x00); out.write(0x00)
            for (label in name.split('.')) {
                val b = label.toByteArray(StandardCharsets.US_ASCII)
                out.write(b.size); out.write(b)
            }
            out.write(0)
            out.write(0); out.write(1)
            out.write(0); out.write(1)
            val query = out.toByteArray()
            DatagramSocket().use { socket ->
                socket.soTimeout = 2200
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
                val buf = ByteArray(2048)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)
                if (response.length < 12) -1 else buf[3].toInt() and 0x0f
            }
        } catch (_: Exception) { -1 }
    }

    private fun rollbackDns(
        context: Context,
        endpoint: ServiceEndpoint,
        gateway: String,
        baseline: String
    ): Boolean {
        if (baseline.isBlank()) {
            prefs(context).edit().putString("rollback_state", "baseline_blank").apply()
            return false
        }
        val response = soap(
            endpoint,
            gateway,
            "SetDNSServer",
            "<NewDNSServers>$baseline</NewDNSServers>"
        )
        if (response.status !in 200..299) {
            prefs(context).edit()
                .putString("rollback_state", "set_failed_${response.status}")
                .putLong("rollback_at", System.currentTimeMillis())
                .apply()
            return false
        }
        val verify = soap(endpoint, gateway, "GetDNSServers", "")
        val restored = verify.status in 200..299 && xmlTag(verify.body, "NewDNSServers").trim() == baseline.trim()
        prefs(context).edit()
            .putString("rollback_state", if (restored) "verified" else "verify_failed")
            .putLong("rollback_at", System.currentTimeMillis())
            .apply()
        return restored
    }

    private fun containsBothFamilyDns(value: String): Boolean =
        value.split(',', ';', ' ').map { it.trim() }.toSet().let {
            it.contains(FAMILY_DNS_1) && it.contains(FAMILY_DNS_2)
        }

    private fun xmlTag(xml: String, tag: String): String =
        Regex("<(?:\\w+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)?.groupValues?.get(1)?.trim().orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
