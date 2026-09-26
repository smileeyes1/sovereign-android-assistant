package ps.hakim.phoneagent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs

/**
 * مسح بيئة Wi‑Fi قرائي فقط.
 *
 * يعتمد على نتائج المسح المخبأة لدى النظام؛ لا يطلب تشغيل مسح نشط،
 * لا يغيّر الشبكة، ولا يعيد SSID/BSSID الخام.
 */
object HakimWifiEnvironmentSurvey {
    const val VERSION = "WIFI-ENVIRONMENT-2026-09-26-v1"
    private const val MAX_APS = 48

    fun inspect(context: Context): JSONObject {
        val app = context.applicationContext
        val wm = app.getSystemService(WifiManager::class.java)
        val fineGranted = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val out = JSONObject()
            .put("version", VERSION)
            .put("fine_location_granted", fineGranted)
            .put("coarse_location_granted", coarseGranted)
            .put("active_scan_requested", false)

        if (wm == null) return out.put("ok", false).put("error", "wifi_manager_unavailable")
        val results = try {
            @Suppress("DEPRECATION")
            wm.scanResults.orEmpty()
        } catch (e: SecurityException) {
            return out.put("ok", false).put("error", "scan_permission_unavailable")
        } catch (e: Exception) {
            return out.put("ok", false).put("error", "scan_failed_" + e.javaClass.simpleName)
        }

        val sorted = results.sortedByDescending { it.level }.take(MAX_APS)
        val aps = JSONArray()
        val channelCounts24 = linkedMapOf<Int, Int>()
        val channelCounts5 = linkedMapOf<Int, Int>()
        val channelCounts6 = linkedMapOf<Int, Int>()
        val ssidGroups = linkedMapOf<String, MutableList<JSONObject>>()

        for (r in sorted) {
            val freq = r.frequency
            val channel = channelFor(freq)
            val band = bandFor(freq)
            val ssid = cleanIdentity(runCatching { r.SSID }.getOrNull())
            val bssid = cleanIdentity(r.BSSID)
            val ssidHash = hashIdentity(ssid)
            val bssidHash = hashIdentity(bssid)
            val item = JSONObject()
                .put("ssid_hash", ssidHash)
                .put("bssid_hash", bssidHash)
                .put("frequency_mhz", freq)
                .put("channel", channel)
                .put("band", band)
                .put("rssi_dbm", r.level)
                .put("security", securityHint(r))
            aps.put(item)

            if (channel > 0) {
                when (band) {
                    "2.4GHz" -> channelCounts24[channel] = (channelCounts24[channel] ?: 0) + 1
                    "5GHz" -> channelCounts5[channel] = (channelCounts5[channel] ?: 0) + 1
                    "6GHz" -> channelCounts6[channel] = (channelCounts6[channel] ?: 0) + 1
                }
            }
            if (ssidHash.isNotBlank()) {
                ssidGroups.getOrPut(ssidHash) { mutableListOf() }.add(item)
            }
        }

        val groups = JSONArray()
        for ((ssidHash, members) in ssidGroups.entries.sortedByDescending { it.value.size }.take(24)) {
            val channels = members.map { it.optInt("channel", -1) }.filter { it > 0 }.distinct().sorted()
            val bands = members.map { it.optString("band") }.filter { it.isNotBlank() }.distinct().sorted()
            groups.put(JSONObject()
                .put("ssid_hash", ssidHash)
                .put("ap_count", members.size)
                .put("channels", JSONArray(channels))
                .put("bands", JSONArray(bands))
                .put("best_rssi_dbm", members.maxOfOrNull { it.optInt("rssi_dbm", -127) } ?: -127)
                .put("possible_roaming_set", members.size >= 2)
            )
        }

        val scores = JSONObject()
        for (candidate in intArrayOf(1,6,11)) {
            var score = 0
            for ((ch, count) in channelCounts24) {
                val d = abs(ch - candidate)
                val weight = when {
                    d == 0 -> 5
                    d == 1 -> 4
                    d == 2 -> 3
                    d == 3 -> 2
                    d == 4 -> 1
                    else -> 0
                }
                score += count * weight
            }
            scores.put(candidate.toString(), score)
        }
        val suggested = intArrayOf(1,6,11).minByOrNull { scores.optInt(it.toString(), Int.MAX_VALUE) } ?: 6

        return out
            .put("ok", true)
            .put("scan_result_count", results.size)
            .put("reported_ap_count", aps.length())
            .put("access_points", aps)
            .put("ssid_groups", groups)
            .put("channel_counts_24", mapToJson(channelCounts24))
            .put("channel_counts_5", mapToJson(channelCounts5))
            .put("channel_counts_6", mapToJson(channelCounts6))
            .put("channel_pressure_24", scores)
            .put("suggested_24_channel", suggested)
            .put("suggested_24_width_mhz", 20)
    }

    private fun mapToJson(map: Map<Int, Int>): JSONObject {
        val out = JSONObject()
        for ((k,v) in map.toSortedMap()) out.put(k.toString(), v)
        return out
    }

    private fun securityHint(r: ScanResult): String {
        val c = r.capabilities.orEmpty().uppercase()
        return when {
            "WPA3" in c || "SAE" in c -> "WPA3"
            "WPA2" in c || "RSN" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP"
            c.isBlank() || "[ESS]" == c -> "OPEN_OR_UNKNOWN"
            else -> "OTHER"
        }
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

    private fun bandFor(freq: Int): String = when {
        freq in 2400..2500 -> "2.4GHz"
        freq in 4900..5900 -> "5GHz"
        freq in 5925..7125 -> "6GHz"
        else -> "unknown"
    }

    private fun channelFor(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq in 5000..5900 -> (freq - 5000) / 5
        freq == 5935 -> 2
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> -1
    }
}
