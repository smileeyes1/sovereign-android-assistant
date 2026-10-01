package ps.hakim.phoneagent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONObject

/**
 * حارس تجاوز الهاتف — قراءة محلية محدودة فقط.
 *
 * لا يقرأ سجل التصفح أو أسماء النطاقات المطلوبة، ولا يغيّر إعدادات أندرويد.
 * يقرر فقط هل هناك طبقة يمكنها تجاوز DNS الشبكة: Private DNS/VPN/Proxy.
 */
object HakimPhoneBypassGuard {
    private const val ADGUARD_DEFAULT = "dns.adguard-dns.com"
    private const val ADGUARD_UNFILTERED = "unfiltered.adguard-dns.com"
    private const val ADGUARD_FAMILY = "family.adguard-dns.com"
    private const val CLEANBROWSING_FAMILY = "family-filter-dns.cleanbrowsing.org"

    fun status(context: Context): JSONObject {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val lp = network?.let { cm.getLinkProperties(it) }

        val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp?.isPrivateDnsActive == true
        } else false
        val privateDnsHost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp?.privateDnsServerName.orEmpty().trim().lowercase()
        } else ""

        val classification = classify(privateDnsActive, privateDnsHost)
        val knownFamily = classification in setOf(
            "family_adguard",
            "family_cleanbrowsing"
        )
        val knownNonFamily = classification in setOf(
            "adguard_default_non_family",
            "adguard_unfiltered"
        )
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val proxy = lp?.httpProxy != null
        val privateDnsOverrideUnverified =
            privateDnsActive && !knownFamily && classification != "network_default"

        val bypassRisk = vpn || proxy || knownNonFamily || privateDnsOverrideUnverified
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "other"
        }

        return JSONObject()
            .put("transport", transport)
            .put("private_dns_active", privateDnsActive)
            .put("private_dns_classification", classification)
            .put("private_dns_known_family", knownFamily)
            .put("private_dns_known_non_family", knownNonFamily)
            .put("private_dns_override_unverified", privateDnsOverrideUnverified)
            .put("vpn_active", vpn)
            .put("proxy_active", proxy)
            .put("bypass_risk", bypassRisk)
            .put(
                "phone_dns_layer_state",
                when {
                    knownFamily && !vpn && !proxy -> "family_private_dns"
                    knownNonFamily -> "known_non_family_private_dns"
                    privateDnsOverrideUnverified -> "custom_private_dns_unverified"
                    vpn -> "vpn_override"
                    proxy -> "proxy_override"
                    else -> "network_dns"
                }
            )
    }

    private fun classify(active: Boolean, host: String): String {
        if (!active) return "network_default"
        return when (host) {
            ADGUARD_DEFAULT -> "adguard_default_non_family"
            ADGUARD_UNFILTERED -> "adguard_unfiltered"
            ADGUARD_FAMILY -> "family_adguard"
            CLEANBROWSING_FAMILY -> "family_cleanbrowsing"
            "" -> "private_dns_active_unknown"
            else -> "custom_private_dns"
        }
    }
}
