package ps.hakim.phoneagent

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * حارس شبكة حكيم:
 * - يستهدف الراوتر المنزلي المثبت 192.168.1.1 فقط، حتى عند المرور عبر مقوٍ/راوتر فرعي.
 * - عند بوابة فرعية لا يستمر إلا بعد إثبات مباشر وقرائي لهوية ZTE F8040 على 192.168.1.1.
 * - كشف أولاً، ثم تعديل DNS فقط عبر LANHostConfigManagement أو عقد ZTE LAN/DHCP المثبت ميدانياً.
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
    private const val MAX_WEB_BODY = 256 * 1024
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
            probeF8040WebSurface(context)
            val webState = tryStrictZteWebDns(context)
            return finish(context, webState, reason, now, targetGateway)
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
        val familyResolverVerified = resolverGood == 0 && resolverBlocked in setOf(0, 3)
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
            .put("web_probe_ran", p.getBoolean("web_probe_ran", false))
            .put("web_probe_root_status", p.getInt("web_probe_root_status", -1))
            .put("web_probe_title", p.getString("web_probe_title", ""))
            .put("web_probe_server", p.getString("web_probe_server", ""))
            .put("web_probe_login_required", p.getBoolean("web_probe_login_required", false))
            .put("web_probe_auth_action", p.getString("web_probe_auth_action", ""))
            .put("web_probe_candidate_paths", p.getString("web_probe_candidate_paths", ""))
            .put("router_auth_required", p.getBoolean("router_auth_required", false))
            .put("baseline_dns_saved", p.contains("baseline_dns") || p.contains("web_baseline_dns1"))
            .put("web_dns_adapter", p.getString("web_dns_adapter", "none"))
            .put("web_dns_compatible", p.getBoolean("web_dns_compatible", false))
            .put("web_dns_apply_attempted", p.getBoolean("web_dns_apply_attempted", false))
            .put("web_dns_readback_verified", p.getBoolean("web_dns_readback_verified", false))
            .put("web_dns_rollback_verified", p.getBoolean("web_dns_rollback_verified", false))
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

    private data class WebSurfaceResponse(
        val status: Int,
        val body: String,
        val server: String
    )

    private const val ZTE_GCH_DHCP_PATH = "/getpage.gch?pid=1002&nextpage=net_dhcp_dynamic_t.gch"
    private const val ZTE_LUA_LAN_PATH = "/getpage.lua?pid=1002&nextpage=Localnet_LanMgrIpv4_t.lp"
    private const val ZTE_ROOT_PATH = "/"
    private const val ZTE_MODERN_VIEW_PATH = "/?_type=menuView&_tag=lanMgrIpv4&Menu3Location=0"
    private const val ZTE_MODERN_DHCP_PATH = "/?_type=menuData&_tag=Localnet_LanMgrIpv4_DHCPBasicCfg_lua.lua"
    private const val ZTE_INTEGRITY_PUBLIC_KEY_B64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAodPTerkUVCYmv28SOfRV" +
        "7UKHVujx/HjCUTAWy9l0L5H0JV0LfDudTdMNPEKloZsNam3YrtEnq6jqMLJV4ASb" +
        "1d6axmIgJ636wyTUS99gj4BKs6bQSTUSE8h/QkUYv4gEIt3saMS0pZpd90y6+B/9" +
        "hZxZE/RKU8e+zgRqp1/762TB7vcjtjOwXRDEL0w71Jk9i8VUQ59MR1Uj5E8X3WIc" +
        "fYSK5RWBkMhfaTRM6ozS9Bqhi40xlSOb3GBxCmliCifOJNLoO9kFoWgAIw5hkSIb" +
        "GH+4Csop9Uy8VvmmB+B3ubFLN35qIa5OG5+SDXn4L7FeAA5lRiGxRi8tsWrtew8w" +
        "nwIDAQAB"


    private data class StrictWebResponse(
        val status: Int,
        val body: String,
        val cookie: String = ""
    )

    private fun tryStrictZteWebDns(context: Context): String {
        val p = prefs(context)
        p.edit()
            .putBoolean("router_auth_required", false)
            .putBoolean("web_dns_compatible", false)
            .putBoolean("web_dns_apply_attempted", false)
            .putBoolean("web_dns_readback_verified", false)
            .putBoolean("web_dns_rollback_verified", false)
            .apply()

        val gch = strictWebRequest("GET", ZTE_GCH_DHCP_PATH)
        if (gch.status == 401 || gch.status == 403 || looksLikeZteLogin(gch.body)) {
            p.edit()
                .putString("web_dns_adapter", "gch")
                .putBoolean("router_auth_required", true)
                .apply()
            return "ROUTER_AUTH_REQUIRED"
        }

        if (gch.status in 200..299 && strictGchDnsCompatible(gch.body)) {
            p.edit()
                .putString("web_dns_adapter", "gch")
                .putBoolean("web_dns_compatible", true)
                .apply()
            return applyStrictGchDns(context, gch)
        }

        val lua = strictWebRequest("GET", ZTE_LUA_LAN_PATH)
        if (lua.status == 401 || lua.status == 403 || looksLikeZteLogin(lua.body)) {
            p.edit()
                .putString("web_dns_adapter", "lua")
                .putBoolean("router_auth_required", true)
                .apply()
            return "ROUTER_AUTH_REQUIRED"
        }
        val luaCompatible = lua.status in 200..299 &&
            listOf("DHCPBasicCfg_container", "DnsServerSource", "Btn_apply_DHCPBasicCfg")
                .all { lua.body.contains(it, ignoreCase = true) }
        if (luaCompatible) {
            // This family is identified read-only. Its mutation contract varies by firmware;
            // continue to the separately proven modern menuData contract before giving up.
            p.edit()
                .putString("web_dns_adapter", "lua_readonly")
                .putBoolean("web_dns_compatible", false)
                .apply()
        }

        val modern = tryModernZteMenuDns(context)
        if (modern != "TR064_LANHOST_NOT_FOUND") return modern
        if (p.getString("web_dns_adapter", "none") == "lua_readonly") {
            return "TR064_LANHOST_NOT_FOUND"
        }
        p.edit().putString("web_dns_adapter", "none").apply()
        return "TR064_LANHOST_NOT_FOUND"
    }

    private data class ModernContext(
        val cookie: String,
        val token: String,
        val values: LinkedHashMap<String, String>,
        val integCheck: Boolean
    )

    private fun tryModernZteMenuDns(context: Context): String {
        val p = prefs(context)
        val probe = openModernContext()
        if (probe.first == "AUTH") {
            p.edit()
                .putString("web_dns_adapter", "modern_menu")
                .putBoolean("router_auth_required", true)
                .putBoolean("web_dns_compatible", false)
                .apply()
            return "ROUTER_AUTH_REQUIRED"
        }
        val ctx = probe.second ?: run {
            p.edit().putString("web_dns_adapter", "none").apply()
            return "TR064_LANHOST_NOT_FOUND"
        }

        val required = listOf(
            "ServerEnable", "IPAddr", "SubnetMask", "MinAddress", "MaxAddress",
            "DNSServer1", "DNSServer2", "DnsServerSource", "LeaseTime"
        )
        if (!required.all { ctx.values.containsKey(it) } ||
            ctx.values["IPAddr"] != EXPECTED_GATEWAY ||
            ctx.values["DnsServerSource"] !in setOf("0", "1")
        ) {
            p.edit()
                .putString("web_dns_adapter", "modern_menu")
                .putBoolean("web_dns_compatible", false)
                .apply()
            return "TR064_LANHOST_NOT_FOUND"
        }

        p.edit()
            .putString("web_dns_adapter", "modern_menu")
            .putBoolean("web_dns_compatible", true)
            .putBoolean("router_auth_required", false)
            .apply()

        return applyModernZteMenuDns(context, ctx)
    }

    private fun openModernContext(): Pair<String, ModernContext?> {
        val root = strictWebRequest("GET", ZTE_ROOT_PATH)
        if (isAuthResponse(root)) return "AUTH" to null
        if (root.status !in 200..399) return "MISS" to null

        var cookie = mergeCookies("", root.cookie)
        val view = strictWebRequest("GET", ZTE_MODERN_VIEW_PATH, cookie = cookie)
        cookie = mergeCookies(cookie, view.cookie)
        if (isAuthResponse(view)) return "AUTH" to null
        if (view.status !in 200..299) return "MISS" to null

        val markers = listOf(
            "DHCPBasicCfg",
            "Localnet_LanMgrIpv4_DHCPBasicCfg_lua.lua",
            "OBJ_Br0AndDhcpsHosCfg_ID",
            "OBJ_LANDNS_ID",
            "OBJ_WINSADDR_ID"
        )
        if (!markers.all { view.body.contains(it, ignoreCase = true) }) return "MISS" to null

        val token = extractModernSessionToken(view.body)
        if (token.isBlank()) return "MISS" to null

        val integMatch = Regex("""["']IntegCheck["']\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
            .find(view.body)?.groupValues?.get(1)?.lowercase()
        val integCheck = when (integMatch) {
            "true" -> {
                if (!view.body.contains("odPTerkUVCYmv28SOfRV")) return "MISS" to null
                true
            }
            "false" -> false
            else -> return "MISS" to null
        }

        val data = strictWebRequest("GET", ZTE_MODERN_DHCP_PATH, cookie = cookie)
        cookie = mergeCookies(cookie, data.cookie)
        if (isAuthResponse(data)) return "AUTH" to null
        if (data.status !in 200..299) return "MISS" to null

        val values = LinkedHashMap<String, String>()
        for (id in listOf("OBJ_Br0AndDhcpsHosCfg_ID", "OBJ_LANDNS_ID", "OBJ_WINSADDR_ID")) {
            val obj = extractModernObject(data.body, id) ?: return "MISS" to null
            for ((k, v) in obj) {
                if (k !in values || k != "_InstID") values[k] = v
            }
        }
        return "OK" to ModernContext(cookie, token, values, integCheck)
    }

    private fun applyModernZteMenuDns(context: Context, initial: ModernContext): String {
        val p = prefs(context)
        val baseline1 = initial.values["DNSServer1"].orEmpty()
        val baseline2 = initial.values["DNSServer2"].orEmpty()
        val baselineSource = initial.values["DnsServerSource"].orEmpty()

        if (!p.contains("web_baseline_dns1")) {
            p.edit()
                .putString("web_baseline_dns1", baseline1)
                .putString("web_baseline_dns2", baseline2)
                .putString("web_baseline_dns3", "")
                .putString("web_baseline_dns_source", baselineSource)
                .putLong("web_baseline_dns_at", System.currentTimeMillis())
                .apply()
        }

        val alreadyConfigured =
            baseline1 == FAMILY_DNS_1 &&
            baseline2 == FAMILY_DNS_2 &&
            baselineSource == "0"

        if (!alreadyConfigured) {
            val baselineForm = buildModernDhcpForm(initial.values, baseline1, baseline2, baselineSource, initial.token)
                ?: return "TR064_LANHOST_NOT_FOUND"
            val wantedForm = buildModernDhcpForm(initial.values, FAMILY_DNS_1, FAMILY_DNS_2, "0", initial.token)
                ?: return "TR064_LANHOST_NOT_FOUND"
            if (nonDnsFingerprint(baselineForm) != nonDnsFingerprint(wantedForm)) {
                return "TR064_LANHOST_NOT_FOUND"
            }

            val body = encodeModernForm(wantedForm)
            val headers = modernHeaders(body, initial.integCheck) ?: return "TR064_LANHOST_NOT_FOUND"
            p.edit().putBoolean("web_dns_apply_attempted", true).apply()
            val posted = strictWebRequest(
                "POST",
                ZTE_MODERN_DHCP_PATH,
                formBody = body,
                cookie = initial.cookie,
                extraHeaders = headers
            )
            if (isAuthResponse(posted)) {
                p.edit().putBoolean("router_auth_required", true).apply()
                return "ROUTER_AUTH_REQUIRED"
            }
            if (posted.status !in 200..399) {
                return "TR064_LANHOST_NOT_FOUND"
            }
        }

        val verifyProbe = openModernContext()
        if (verifyProbe.first == "AUTH") {
            p.edit().putBoolean("router_auth_required", true).apply()
            if (!alreadyConfigured) rollbackModernZteMenuDns(context, baseline1, baseline2, baselineSource)
            return "ROUTER_AUTH_REQUIRED"
        }
        val verify = verifyProbe.second
        if (verify == null) {
            if (!alreadyConfigured) {
                val rolledBack = rollbackModernZteMenuDns(context, baseline1, baseline2, baselineSource)
                return if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED"
            }
            return "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED"
        }

        val configured =
            verify.values["DNSServer1"] == FAMILY_DNS_1 &&
            verify.values["DNSServer2"] == FAMILY_DNS_2 &&
            verify.values["DnsServerSource"] == "0"
        p.edit().putBoolean("web_dns_readback_verified", configured).apply()

        if (!configured) {
            if (!alreadyConfigured) {
                val rolledBack = rollbackModernZteMenuDns(context, baseline1, baseline2, baselineSource)
                return if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED"
            }
            return "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED"
        }

        val resolverGood = dnsQuery(FAMILY_DNS_1, "cleanbrowsing.org")
        val resolverAdult = dnsQuery(FAMILY_DNS_1, "pornhub.com")
        val resolverVerified = resolverGood == 0 && resolverAdult in setOf(0, 3)
        p.edit()
            .putBoolean("family_dns_configured", true)
            .putBoolean("family_resolver_verified", resolverVerified)
            .putInt("family_resolver_good_rcode", resolverGood)
            .putInt("family_resolver_blocked_rcode", resolverAdult)
            .putLong("last_router_dns_verify_at", System.currentTimeMillis())
            .apply()

        if (!resolverVerified) {
            if (!alreadyConfigured) {
                val rolledBack = rollbackModernZteMenuDns(context, baseline1, baseline2, baselineSource)
                p.edit().putBoolean("family_dns_configured", false).apply()
                return if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED"
            }
            return "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED"
        }

        return "FAMILY_DNS_CONFIGURED"
    }

    private fun rollbackModernZteMenuDns(
        context: Context,
        dns1: String,
        dns2: String,
        source: String
    ): Boolean {
        val p = prefs(context)
        val probe = openModernContext().second ?: return false
        val form = buildModernDhcpForm(probe.values, dns1, dns2, source, probe.token) ?: return false
        val body = encodeModernForm(form)
        val headers = modernHeaders(body, probe.integCheck) ?: return false
        val posted = strictWebRequest(
            "POST",
            ZTE_MODERN_DHCP_PATH,
            formBody = body,
            cookie = probe.cookie,
            extraHeaders = headers
        )
        if (posted.status !in 200..399) return false

        val verify = openModernContext().second ?: return false
        val restored =
            verify.values["DNSServer1"].orEmpty() == dns1 &&
            verify.values["DNSServer2"].orEmpty() == dns2 &&
            verify.values["DnsServerSource"].orEmpty() == source
        p.edit()
            .putBoolean("web_dns_rollback_verified", restored)
            .putString("rollback_state", if (restored) "verified" else "verify_failed")
            .putLong("rollback_at", System.currentTimeMillis())
            .apply()
        return restored
    }

    private fun buildModernDhcpForm(
        values: Map<String, String>,
        dns1: String,
        dns2: String,
        dnsSource: String,
        token: String
    ): LinkedHashMap<String, String>? {
        val mask = values["SubnetMask"] ?: values["SubMask"] ?: return null
        val required = listOf("ServerEnable", "IPAddr", "MinAddress", "MaxAddress", "LeaseTime")
        if (!required.all { values.containsKey(it) }) return null
        if (values["IPAddr"] != EXPECTED_GATEWAY || token.isBlank()) return null

        val form = linkedMapOf(
            "IF_ACTION" to "Apply",
            "IF_URL_HOST" to EXPECTED_GATEWAY,
            "_InstID" to "",
            "IPAddr" to values["IPAddr"].orEmpty(),
            "SubMask" to mask,
            "SubnetMask" to mask,
            "MinAddress" to values["MinAddress"].orEmpty(),
            "MaxAddress" to values["MaxAddress"].orEmpty(),
            "IPRouters" to values["IPRouters"].orEmpty(),
            "DNSServer1" to dns1,
            "DNSServer2" to dns2,
            "LeaseTime" to values["LeaseTime"].orEmpty(),
            "ServerEnable" to values["ServerEnable"].orEmpty(),
            "DnsServerSource" to dnsSource,
            "_sessionTOKEN" to token
        )
        if (!isIpv4(dns1) || !isIpv4(dns2) || dnsSource !in setOf("0", "1")) return null
        if (!isIpv4(form["IPAddr"].orEmpty()) ||
            !isIpv4(form["MinAddress"].orEmpty()) ||
            !isIpv4(form["MaxAddress"].orEmpty()) ||
            !isIpv4(mask)
        ) return null
        return form
    }

    private fun encodeModernForm(values: Map<String, String>): String =
        values.entries.joinToString("&") { (k, v) ->
            encodeComponent(k) + "=" + encodeComponent(v)
        }

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun modernHeaders(body: String, integCheck: Boolean): Map<String, String>? {
        val out = linkedMapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to "https://$EXPECTED_GATEWAY/"
        )
        if (integCheck) {
            val check = zteIntegrityCheck(body)
            if (check.isBlank()) return null
            out["Check"] = check
        }
        return out
    }

    private fun zteIntegrityCheck(body: String): String = runCatching {
        val digestHex = java.security.MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val key = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.decode(ZTE_INTEGRITY_PUBLIC_KEY_B64, Base64.DEFAULT))
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        Base64.encodeToString(
            cipher.doFinal(digestHex.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
    }.getOrDefault("")

    private fun extractModernSessionToken(body: String): String {
        val matches = Regex(
            """_sessionTmpToken\s*=\s*["']([^"']{1,512})["']""",
            RegexOption.IGNORE_CASE
        ).findAll(body).toList()
        return decodeZteEscapes(matches.lastOrNull()?.groupValues?.get(1).orEmpty()).take(256)
    }

    private fun extractModernObject(body: String, objectId: String): LinkedHashMap<String, String>? {
        if (!Regex("""^[A-Za-z0-9_]{3,80}$""").matches(objectId)) return null
        val block = Regex(
            """<$objectId(?:\s[^>]*)?>(.*?)</$objectId>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(body)?.groupValues?.get(1) ?: return null
        val instance = Regex(
            """<Instance(?:\s[^>]*)?>(.*?)</Instance>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(block)?.groupValues?.get(1) ?: return null
        val out = LinkedHashMap<String, String>()
        val pairs = Regex(
            """<ParaName(?:\s[^>]*)?>([^<]{1,120})</ParaName>\s*<ParaValue(?:\s[^>]*)?>([^<]{0,2048})</ParaValue>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (m in pairs.findAll(instance).take(128)) {
            val key = xmlDecode(m.groupValues[1].trim())
            val value = xmlDecode(m.groupValues[2].trim())
            if (Regex("""^[A-Za-z0-9_:-]{1,80}$""").matches(key)) out[key] = value.take(1024)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun xmlDecode(value: String): String =
        value.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    private fun mergeCookies(a: String, b: String): String {
        val map = LinkedHashMap<String, String>()
        for (raw in listOf(a, b).flatMap { it.split(";") }) {
            val item = raw.trim()
            val eq = item.indexOf('=')
            if (eq <= 0) continue
            val name = item.substring(0, eq).trim()
            val value = item.substring(eq + 1).trim()
            if (Regex("""^[A-Za-z0-9_.-]{1,80}$""").matches(name) && value.length <= 2048) {
                map[name] = value
            }
        }
        return map.entries.joinToString("; ") { it.key + "=" + it.value }.take(4096)
    }

    private fun isAuthResponse(response: StrictWebResponse): Boolean =
        response.status == 401 || response.status == 403 ||
            response.status in 300..399 || looksLikeZteLogin(response.body)

    private fun isIpv4(value: String): Boolean {
        val parts = value.split(".").mapNotNull { it.toIntOrNull() }
        return parts.size == 4 && parts.all { it in 0..255 }
    }

    private fun strictGchDnsCompatible(body: String): Boolean {
        if (body.length < 1000 || looksLikeZteLogin(body)) return false
        val required = listOf(
            "Frm_DnsServerSource",
            "Frm_DNSServer1",
            "Frm_DNSServer2",
            "Btn_Submit",
            "DNSServer1",
            "DNSServer2",
            "DnsServerSource",
            "IF_ACTION",
            "function pageSubmit",
            "Transfer_meaning"
        )
        if (!required.all { body.contains(it, ignoreCase = true) }) return false
        val values = extractTransferMeanings(body)
        if (values["ViewName"] != "IGD.LD1.HostCfg") return false
        if (values["Configurable"] != "1") return false
        if (values["BasicIPAddr"] != EXPECTED_GATEWAY) return false
        if (values["DnsServerSource"] !in setOf("0", "1")) return false
        return values.containsKey("DNSServer1") && values.containsKey("DNSServer2")
    }

    private fun applyStrictGchDns(context: Context, initial: StrictWebResponse): String {
        val p = prefs(context)
        val initialValues = extractTransferMeanings(initial.body)
        val initialToken = extractSessionToken(initial.body)
        if (initialToken.isBlank()) {
            // A compatible page without a local mutation token is not safe to modify.
            return "TR064_LANHOST_NOT_FOUND"
        }

        val baseline1 = initialValues["DNSServer1"].orEmpty()
        val baseline2 = initialValues["DNSServer2"].orEmpty()
        val baseline3 = initialValues["DNSServer3"].orEmpty()
        val baselineSource = initialValues["DnsServerSource"].orEmpty()

        if (!p.contains("web_baseline_dns1")) {
            p.edit()
                .putString("web_baseline_dns1", baseline1)
                .putString("web_baseline_dns2", baseline2)
                .putString("web_baseline_dns3", baseline3)
                .putString("web_baseline_dns_source", baselineSource)
                .putLong("web_baseline_dns_at", System.currentTimeMillis())
                .apply()
        }

        val alreadyConfigured =
            baseline1 == FAMILY_DNS_1 &&
            baseline2 == FAMILY_DNS_2 &&
            baselineSource == "0"

        if (!alreadyConfigured) {
            val form = LinkedHashMap(initialValues)
            val beforeNonDns = nonDnsFingerprint(form)
            form["DNSServer1"] = FAMILY_DNS_1
            form["DNSServer2"] = FAMILY_DNS_2
            if (form.containsKey("DNSServer3")) form["DNSServer3"] = "0.0.0.0"
            form["DnsServerSource"] = "0"
            form["IF_ACTION"] = "apply"
            if (beforeNonDns != nonDnsFingerprint(form)) {
                return "TR064_LANHOST_NOT_FOUND"
            }
            form["_SESSION_TOKEN"] = initialToken
            form.putIfAbsent("IF_UPLOADING", "N/A")
            form.putIfAbsent("temClickURL", "")

            p.edit().putBoolean("web_dns_apply_attempted", true).apply()
            val posted = strictWebRequest(
                "POST",
                ZTE_GCH_DHCP_PATH,
                encodeForm(form),
                initial.cookie
            )
            if (posted.status == 401 || posted.status == 403 || looksLikeZteLogin(posted.body)) {
                p.edit().putBoolean("router_auth_required", true).apply()
                return "ROUTER_AUTH_REQUIRED"
            }
            if (posted.status !in 200..399) {
                return "TR064_LANHOST_NOT_FOUND"
            }
        }

        val verify = strictWebRequest("GET", ZTE_GCH_DHCP_PATH)
        if (verify.status !in 200..299 || looksLikeZteLogin(verify.body)) {
            if (!alreadyConfigured) {
                val rolledBack = rollbackStrictGchDns(context, baseline1, baseline2, baseline3, baselineSource)
                return if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED"
            }
            return "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED"
        }

        val after = extractTransferMeanings(verify.body)
        val configured =
            after["DNSServer1"] == FAMILY_DNS_1 &&
            after["DNSServer2"] == FAMILY_DNS_2 &&
            after["DnsServerSource"] == "0"
        p.edit().putBoolean("web_dns_readback_verified", configured).apply()

        if (!configured) {
            if (!alreadyConfigured) {
                val rolledBack = rollbackStrictGchDns(context, baseline1, baseline2, baseline3, baselineSource)
                return if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED"
            }
            return "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED"
        }

        val resolverGood = dnsQuery(FAMILY_DNS_1, "cleanbrowsing.org")
        val resolverAdult = dnsQuery(FAMILY_DNS_1, "pornhub.com")
        val resolverVerified = resolverGood == 0 && resolverAdult in setOf(0, 3)
        p.edit()
            .putBoolean("family_dns_configured", true)
            .putBoolean("family_resolver_verified", resolverVerified)
            .putInt("family_resolver_good_rcode", resolverGood)
            .putInt("family_resolver_blocked_rcode", resolverAdult)
            .putLong("last_router_dns_verify_at", System.currentTimeMillis())
            .apply()

        if (!resolverVerified) {
            if (!alreadyConfigured) {
                val rolledBack = rollbackStrictGchDns(context, baseline1, baseline2, baseline3, baselineSource)
                p.edit().putBoolean("family_dns_configured", false).apply()
                return if (rolledBack) "FAMILY_DNS_ROLLED_BACK_UNVERIFIED" else "FAMILY_DNS_ROLLBACK_UNVERIFIED"
            }
            return "FAMILY_DNS_EXISTING_CONFIG_UNVERIFIED"
        }
        return "FAMILY_DNS_CONFIGURED"
    }

    private fun rollbackStrictGchDns(
        context: Context,
        dns1: String,
        dns2: String,
        dns3: String,
        source: String
    ): Boolean {
        val current = strictWebRequest("GET", ZTE_GCH_DHCP_PATH)
        if (current.status !in 200..299 || looksLikeZteLogin(current.body)) return false
        val values = extractTransferMeanings(current.body)
        val token = extractSessionToken(current.body)
        if (!strictGchDnsCompatible(current.body) || token.isBlank()) return false

        val form = LinkedHashMap(values)
        val beforeNonDns = nonDnsFingerprint(form)
        form["DNSServer1"] = dns1
        form["DNSServer2"] = dns2
        if (form.containsKey("DNSServer3")) form["DNSServer3"] = dns3
        form["DnsServerSource"] = source
        form["IF_ACTION"] = "apply"
        if (beforeNonDns != nonDnsFingerprint(form)) return false
        form["_SESSION_TOKEN"] = token
        form.putIfAbsent("IF_UPLOADING", "N/A")
        form.putIfAbsent("temClickURL", "")

        val posted = strictWebRequest("POST", ZTE_GCH_DHCP_PATH, encodeForm(form), current.cookie)
        if (posted.status !in 200..399) return false

        val verify = strictWebRequest("GET", ZTE_GCH_DHCP_PATH)
        val after = extractTransferMeanings(verify.body)
        val restored =
            verify.status in 200..299 &&
            after["DNSServer1"].orEmpty() == dns1 &&
            after["DNSServer2"].orEmpty() == dns2 &&
            after["DNSServer3"].orEmpty() == dns3 &&
            after["DnsServerSource"].orEmpty() == source
        prefs(context).edit()
            .putBoolean("web_dns_rollback_verified", restored)
            .putString("rollback_state", if (restored) "verified" else "verify_failed")
            .putLong("rollback_at", System.currentTimeMillis())
            .apply()
        return restored
    }

    private fun nonDnsFingerprint(values: Map<String, String>): String {
        val ignored = setOf(
            "DNSServer1", "DNSServer2", "DNSServer3", "DnsServerSource",
            "IF_ACTION", "_SESSION_TOKEN"
        )
        return values.entries
            .filter { it.key !in ignored }
            .sortedBy { it.key }
            .joinToString("\n") { it.key + "=" + it.value }
            .let { raw ->
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.toByteArray(StandardCharsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            }
    }

    private fun extractTransferMeanings(body: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val re = Regex(
            """Transfer_meaning\(\s*['"]([A-Za-z0-9_:-]{1,80})['"]\s*,\s*['"]([^'"]{0,1024})['"]\s*\)""",
            RegexOption.IGNORE_CASE
        )
        for (m in re.findAll(body).take(512)) {
            out[m.groupValues[1]] = decodeZteEscapes(m.groupValues[2])
        }
        return out
    }

    private fun extractSessionToken(body: String): String =
        Regex("""var\s+session_token\s*=\s*["']([A-Za-z0-9._:-]{4,256})["']""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1).orEmpty()

    private fun decodeZteEscapes(raw: String): String =
        Regex("""\\x([0-9A-Fa-f]{2})""").replace(raw) {
            it.groupValues[1].toInt(16).toChar().toString()
        }

    private fun looksLikeZteLogin(body: String): Boolean {
        val lower = body.lowercase()
        return body.contains("id=\"LoginId\"", true) ||
            body.contains("id='LoginId'", true) ||
            body.contains("name=\"fLogin\"", true) ||
            body.contains("name='fLogin'", true) ||
            (lower.contains("frm_username") && lower.contains("frm_password")) ||
            (lower.contains("login") && lower.contains("password") && !lower.contains("dhcp"))
    }

    private fun encodeForm(values: Map<String, String>): String =
        values.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }

    private fun strictWebRequest(
        method: String,
        pathWithQuery: String,
        formBody: String = "",
        cookie: String = "",
        extraHeaders: Map<String, String> = emptyMap()
    ): StrictWebResponse {
        val allowed = setOf(
            ZTE_GCH_DHCP_PATH,
            ZTE_LUA_LAN_PATH,
            ZTE_ROOT_PATH,
            ZTE_MODERN_VIEW_PATH,
            ZTE_MODERN_DHCP_PATH
        )
        if (pathWithQuery !in allowed) return StrictWebResponse(-1, "")
        if (method !in setOf("GET", "POST")) return StrictWebResponse(-1, "")
        if (method == "POST" && pathWithQuery !in setOf(ZTE_GCH_DHCP_PATH, ZTE_MODERN_DHCP_PATH)) {
            return StrictWebResponse(-1, "")
        }
        if (formBody.length > 128 * 1024) return StrictWebResponse(-1, "")
        if (extraHeaders.keys.any { it !in setOf("X-Requested-With", "Referer", "Check") }) {
            return StrictWebResponse(-1, "")
        }

        return runCatching {
            val conn = URL("https://$EXPECTED_GATEWAY$pathWithQuery").openConnection() as HttpsURLConnection
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
            conn.connectTimeout = 1600
            conn.readTimeout = 2600
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "HAKIM-F8040-StrictDNS/2")
            if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie.take(4096))
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v.take(4096)) }
            if (method == "POST") {
                val bytes = formBody.toByteArray(StandardCharsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val setCookies = conn.headerFields.entries
                .filter { it.key?.equals("Set-Cookie", true) == true }
                .flatMap { it.value.orEmpty() }
                .map { it.substringBefore(";").trim() }
                .filter { it.isNotBlank() }
                .joinToString("; ")
                .take(4096)
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val bodyLimit = if (pathWithQuery in setOf(ZTE_MODERN_VIEW_PATH, ZTE_MODERN_DHCP_PATH)) {
                MAX_WEB_BODY
            } else {
                MAX_BODY
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                buildString {
                    var total = 0
                    while (total < bodyLimit) {
                        val line = reader.readLine() ?: break
                        append(line).append('\n')
                        total += line.length
                    }
                }
            }.orEmpty()
            conn.disconnect()
            StrictWebResponse(status, body, setCookies)
        }.getOrElse { StrictWebResponse(-1, "") }
    }

    /**
     * مجس قرائي فقط لواجهة F8040.
     * لا يرسل POST، لا يحتفظ بملفات تعريف الارتباط، ولا يعيد قيم حقول الإدخال أو الرموز.
     */
    private fun probeF8040WebSurface(context: Context) {
        val root = safeF8040Get("/")
        val title = Regex(
            "<title[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(root.body)?.groupValues?.get(1)
            ?.replace(Regex("\\s+"), " ")?.trim()?.take(120).orEmpty()

        val lower = root.body.lowercase()
        val loginRequired =
            Regex("<input[^>]+type=[\"']?password", RegexOption.IGNORE_CASE).containsMatchIn(root.body) ||
            ("login" in lower && ("password" in lower || "username" in lower || "user name" in lower))

        val formActionRaw = Regex(
            "<form[^>]+action=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE
        ).find(root.body)?.groupValues?.get(1).orEmpty()
        val authAction = normalizeLocalPath(formActionRaw)

        val candidates = linkedSetOf<String>()
        fun consider(raw: String) {
            val path = normalizeLocalPath(raw)
            if (path.isBlank()) return
            val key = path.lowercase()
            if (listOf("dns","dhcp","lan","network","api","login","config","status","internet").any { key.contains(it) }) {
                candidates.add(path.take(180))
            }
        }

        Regex(
            "(?:href|src|action)=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE
        ).findAll(root.body).take(80).forEach { consider(it.groupValues[1]) }

        val scripts = Regex(
            "<script[^>]+src=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE
        ).findAll(root.body)
            .map { normalizeLocalPath(it.groupValues[1]) }
            .filter { it.endsWith(".js", ignoreCase = true) }
            .distinct()
            .take(8)
            .toList()

        val pathLiteral = Regex("[\"'](/[^'\"\\s<>]{1,180})[\"']")
        for (script in scripts) {
            val js = safeF8040Get(script)
            if (js.status !in 200..299 || js.body.isBlank()) continue
            pathLiteral.findAll(js.body).take(160).forEach { consider(it.groupValues[1]) }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("web_probe_ran", true)
            .putInt("web_probe_root_status", root.status)
            .putString("web_probe_title", safeProbeText(title, 120))
            .putString("web_probe_server", safeProbeText(root.server, 120))
            .putBoolean("web_probe_login_required", loginRequired)
            .putString("web_probe_auth_action", authAction.take(180))
            .putString("web_probe_candidate_paths", candidates.take(12).joinToString("|").take(1800))
            .putLong("web_probe_at", System.currentTimeMillis())
            .apply()
    }

    private fun safeF8040Get(path: String): WebSurfaceResponse {
        val normalized = normalizeLocalPath(path).ifBlank { "/" }
        return runCatching {
            val conn = URL("https://$EXPECTED_GATEWAY$normalized").openConnection() as HttpsURLConnection
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
            conn.connectTimeout = 1100
            conn.readTimeout = 1600
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "HAKIM-F8040-SafeProbe/1")
            val status = conn.responseCode
            val server = conn.getHeaderField("Server").orEmpty().take(120)
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                buildString {
                    var total = 0
                    while (total < 64_000) {
                        val line = reader.readLine() ?: break
                        append(line).append('\n')
                        total += line.length
                    }
                }
            }.orEmpty()
            conn.disconnect()
            WebSurfaceResponse(status, body, server)
        }.getOrElse { WebSurfaceResponse(-1, "", "") }
    }

    private fun normalizeLocalPath(raw: String): String {
        val value = raw.trim()
        if (value.isBlank() || value.startsWith("#") || value.startsWith("javascript:", true) ||
            value.startsWith("data:", true)) return ""
        val uri = runCatching {
            if (value.startsWith("http://", true) || value.startsWith("https://", true)) URI(value)
            else URI("https://$EXPECTED_GATEWAY/${value.trimStart('/')}")
        }.getOrNull() ?: return ""
        if (uri.host != EXPECTED_GATEWAY) return ""
        if (uri.scheme != "https" && uri.scheme != "http") return ""
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
        if (!path.startsWith("/") || path.contains("..")) return ""
        return path.take(220)
    }

    private fun safeProbeText(raw: String, max: Int): String =
        raw.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            .replace(Regex("\\s+"), " ").trim().take(max)

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
