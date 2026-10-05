package ps.hakim.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI as JavaUri
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * قناة حكيم الموحدة: HC1/AES-256-GCM فوق ناقل عام، مع HMAC داخلي، انتهاء صلاحية،
 * منع إعادة التنفيذ، وموافقة أندرويد للأفعال المتغيرة للحالة.
 */
object HakimUnifiedRelay {
    const val PREFS = "hakim"
    const val KEY_TOPIC = "relay_topic"
    const val KEY_RESULT_TOPIC = "relay_result_topic"
    /** Legacy plaintext preference key retained only for one-time migration. */
    const val KEY_RELAY_KEY = "relay_hmac_key"
    const val KEY_BRIDGE_BASE = "relay_bridge_base"
    const val KEY_BRIDGE_FALLBACK_BASE = "relay_bridge_fallback_base"
    private const val SECRET_RELAY_KEY = "hakim-secure-relay-key-v1"
    private const val DEFAULT_BRIDGE_BASE = "https://hakim-chatgpt-bridge-production.up.railway.app"

    private const val APPROVAL_CHANNEL = "hakim_remote_approval"
    private const val ACTION_APPROVE = "ps.hakim.stable.REMOTE_APPROVE"
    private const val ACTION_REJECT = "ps.hakim.stable.REMOTE_REJECT"
    private const val EXTRA_REQUEST_ID = "request_id"
    private const val CARRIER_PREFIX = "HC1."
    private const val CARRIER_AAD = "HAKIM-CARRIER-v1"
    private const val RESULT_PREFIX = "HR1."
    private const val RESULT_AAD = "HAKIM-RESULT-v1"
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val REQUEST_ID = Regex("^[A-Za-z0-9._:-]{8,128}$")
    private val SIGNATURE = Regex("^[0-9a-fA-F]{64}$")
    private val RELAY_KEY = Regex("^[A-Za-z0-9_-]{40,100}$")
    private val READ_ONLY_OPS = setOf("status", "ui", "notifications", "screenshot", "browser_read", "chatgpt_read", "chatgpt_navigate", "termux_status", "capabilities", "capability_read")
    private val SAFE_AUTOMATIC_OPS = setOf("termux_probe", "termux_recover")
    private val ALLOWED_OPS = READ_ONLY_OPS + SAFE_AUTOMATIC_OPS + setOf("action", "launch", "browser_back", "chatgpt_action")
    private val running = AtomicBoolean(false)
    @Volatile private var connected = false
    @Volatile private var loopGeneration = 0L
    @Volatile private var lastLoopProgressAt = 0L
    private val executor = Executors.newSingleThreadExecutor()

    fun isRunning(): Boolean = running.get()
    fun isConnected(): Boolean = connected

    @Synchronized
    fun stop() {
        running.set(false)
        connected = false
        loopGeneration += 1L
    }

    fun loopProgressAgeMs(): Long {
        val last = lastLoopProgressAt
        return if (last <= 0L) Long.MAX_VALUE else (System.currentTimeMillis() - last).coerceAtLeast(0L)
    }

    fun validConfigurationInput(topic: String?, resultTopic: String?, relayKey: String?): Boolean {
        if (topic.isNullOrBlank() || !Regex("^[A-Za-z0-9_-]{20,120}$").matches(topic)) return false
        if (resultTopic.isNullOrBlank() || !Regex("^[A-Za-z0-9_-]{20,120}$").matches(resultTopic)) return false
        if (relayKey.isNullOrBlank() || !RELAY_KEY.matches(relayKey)) return false
        return true
    }

    fun configurationMatches(
        context: Context,
        topic: String?,
        resultTopic: String?,
        relayKey: String?,
        bridgeBase: String? = null,
        fallbackBridgeBase: String? = null
    ): Boolean {
        if (!validConfigurationInput(topic, resultTopic, relayKey)) return false
        val requestedBridge = if (bridgeBase.isNullOrBlank()) DEFAULT_BRIDGE_BASE else normalizeBridgeBase(bridgeBase) ?: return false
        val requestedFallback = if (fallbackBridgeBase.isNullOrBlank()) null else normalizeBridgeBase(fallbackBridgeBase) ?: return false
        if (requestedFallback != null && requestedFallback == requestedBridge) return false
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baseMatches = p.getString(KEY_TOPIC, null) == topic &&
            p.getString(KEY_RESULT_TOPIC, null) == resultTopic &&
            relayKey(context) == relayKey &&
            bridgeBase(context) == requestedBridge
        if (!baseMatches) return false
        if (requestedFallback == null) return true
        return fallbackBridgeBase(context) == requestedFallback
    }

    fun configure(
        context: Context,
        topic: String?,
        resultTopic: String?,
        relayKey: String?,
        bridgeBase: String? = null,
        fallbackBridgeBase: String? = null
    ): Boolean {
        if (!validConfigurationInput(topic, resultTopic, relayKey)) return false
        val normalizedBridge = if (bridgeBase.isNullOrBlank()) DEFAULT_BRIDGE_BASE else normalizeBridgeBase(bridgeBase) ?: return false
        val normalizedFallback = if (fallbackBridgeBase.isNullOrBlank()) null else normalizeBridgeBase(fallbackBridgeBase) ?: return false
        if (normalizedFallback != null && normalizedFallback == normalizedBridge) return false
        return runCatching {
            HakimSecretStore.put(context, SECRET_RELAY_KEY, relayKey!!)
            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_TOPIC, topic)
                .putString(KEY_RESULT_TOPIC, resultTopic)
                .putString(KEY_BRIDGE_BASE, normalizedBridge)
                .remove(KEY_RELAY_KEY)
                .putBoolean("secure_relay_configured", true)
            // An old pairing link that knows only the primary must not erase a verified fallback.
            if (normalizedFallback != null) edit.putString(KEY_BRIDGE_FALLBACK_BASE, normalizedFallback)
            edit.commit()
        }.getOrDefault(false)
    }

    private fun normalizeBridgeBase(raw: String): String? = runCatching {
        val uri = JavaUri(raw.trim())
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.query != null || uri.fragment != null || (uri.path.isNotEmpty() && uri.path != "/")
        ) return@runCatching null
        val port = if (uri.port > 0) ":" + uri.port else ""
        "https://" + uri.host + port
    }.getOrNull()

    private fun bridgeBase(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BRIDGE_BASE, DEFAULT_BRIDGE_BASE)
            ?.let { normalizeBridgeBase(it) }
            ?: DEFAULT_BRIDGE_BASE

    private fun fallbackBridgeBase(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BRIDGE_FALLBACK_BASE, null)
            ?.let { normalizeBridgeBase(it) }
            ?.takeIf { it != bridgeBase(context) }

    private fun bridgeBases(context: Context): List<String> =
        listOfNotNull(bridgeBase(context), fallbackBridgeBase(context)).distinct()

    private fun orderedBridgeBases(context: Context, preferred: String? = null): List<String> {
        val normalizedPreferred = preferred?.let { normalizeBridgeBase(it) }
        return (listOfNotNull(normalizedPreferred) + bridgeBases(context)).distinct()
    }

    fun isConfigured(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !p.getString(KEY_TOPIC, "").isNullOrBlank() &&
            !p.getString(KEY_RESULT_TOPIC, "").isNullOrBlank() &&
            !relayKey(context).isNullOrBlank()
    }

    private fun relayKey(context: Context): String? {
        HakimSecretStore.get(context, SECRET_RELAY_KEY)?.takeIf { RELAY_KEY.matches(it) }?.let {
            return it
        }

        // One-time migration from the legacy plaintext SharedPreferences slot.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getString(KEY_RELAY_KEY, null)?.trim()
        if (legacy.isNullOrBlank() || !RELAY_KEY.matches(legacy)) return null

        return runCatching {
            HakimSecretStore.put(context, SECRET_RELAY_KEY, legacy)
            prefs.edit().remove(KEY_RELAY_KEY).apply()
            legacy
        }.getOrNull()
    }

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (running.get()) return
        running.set(true)
        loopGeneration += 1L
        val generation = loopGeneration
        lastLoopProgressAt = System.currentTimeMillis()
        ensureApprovalChannel(app)
        executor.execute { loop(app, generation) }
    }

    @Synchronized
    fun restart(context: Context, reason: String) {
        val app = context.applicationContext
        connected = false
        running.set(true)
        loopGeneration += 1L
        val generation = loopGeneration
        lastLoopProgressAt = System.currentTimeMillis()
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("secure_relay_restart_reason", reason.take(80))
            .putLong("secure_relay_restart_at", System.currentTimeMillis())
            .apply()
        ensureApprovalChannel(app)
        executor.execute { loop(app, generation) }
    }

    fun ensureAlive(context: Context, reason: String, staleMs: Long = 180_000L): Boolean {
        val app = context.applicationContext
        if (!isConfigured(app)) return false
        if (!running.get()) {
            start(app)
            return true
        }
        if (loopProgressAgeMs() > staleMs) {
            restart(app, reason)
            return true
        }
        return false
    }

    private fun loop(context: Context, generation: Long) {
        var retryMs = 2_000L
        var directFailures = 0
        while (running.get() && generation == loopGeneration) {
            lastLoopProgressAt = System.currentTimeMillis()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val topic = prefs.getString(KEY_TOPIC, null)
            val resultTopic = prefs.getString(KEY_RESULT_TOPIC, null)
            val relayKey = relayKey(context)
            if (topic.isNullOrBlank() || resultTopic.isNullOrBlank() || relayKey.isNullOrBlank()) {
                connected = false
                prefs.edit().putString("secure_relay_state", "unconfigured").apply()
                sleep(10_000L)
                continue
            }

            if (HakimFaultContainment.shouldAttempt(context, "secure_relay", "direct_poll")) {
                try {
                    val activeBridge = directPollAcrossBridges(context, topic, resultTopic, relayKey)
                    HakimFaultContainment.recordSuccess(context, "secure_relay", "direct_poll")
                    lastLoopProgressAt = System.currentTimeMillis()
                    if (generation != loopGeneration || !running.get()) break
                    directFailures = 0
                    retryMs = 2_000L
                    connected = true
                    val connectedAt = System.currentTimeMillis()
                    prefs.edit()
                        .putString("secure_relay_state", "direct_connected")
                        .putString(
                            "secure_relay_active_slot",
                            if (activeBridge == bridgeBase(context)) "primary" else "secondary"
                        )
                        .putString("connection_recovery_state", "healthy")
                        .putLong("secure_relay_seen_at", connectedAt)
                        .putLong("last_recovery_ok_at", connectedAt)
                        .putLong("last_connected_at", connectedAt)
                        .remove("secure_relay_error")
                        .remove("last_recovery_error")
                        .apply()
                    HakimCloudContinuity.refreshIfDue(
                        context,
                        activeBridge,
                        topic,
                        relayKey
                    )
                    HakimHealthBeacon.sendAsync(context, "secure_relay_connected")
                    HakimAutonomousContinuation.pulse(context, "secure_relay_connected")
                    continue
                } catch (e: Exception) {
                    HakimFaultContainment.recordFailure(context, "secure_relay", "direct_poll", e)
                    connected = false
                    directFailures += 1
                    prefs.edit()
                        .putString("secure_relay_state", "direct_recovering")
                        .putString("connection_recovery_state", "secure_relay_recovering")
                        .putString("secure_relay_error", e.javaClass.simpleName)
                        .apply()
                }
            } else {
                connected = false
                directFailures = maxOf(directFailures, 3)
                prefs.edit()
                    .putString("secure_relay_state", "direct_circuit_open")
                    .putString("connection_recovery_state", "secure_relay_recovering")
                    .apply()
            }

            if (directFailures >= 3 && HakimFaultContainment.shouldAttempt(context, "secure_relay", "legacy_poll")) {
                try {
                    listenLegacyOnce(context, topic, resultTopic, relayKey)
                    HakimFaultContainment.recordSuccess(context, "secure_relay", "legacy_poll")
                    lastLoopProgressAt = System.currentTimeMillis()
                    if (generation != loopGeneration || !running.get()) break
                    directFailures = 0
                    retryMs = 2_000L
                    connected = true
                    val connectedAt = System.currentTimeMillis()
                    prefs.edit()
                        .putString("secure_relay_state", "legacy_fallback")
                        .putString("connection_recovery_state", "healthy")
                        .putLong("secure_relay_seen_at", connectedAt)
                        .putLong("last_recovery_ok_at", connectedAt)
                        .putLong("last_connected_at", connectedAt)
                        .apply()
                    HakimHealthBeacon.sendAsync(context, "secure_relay_connected")
                    HakimAutonomousContinuation.pulse(context, "secure_relay_legacy_connected")
                    continue
                } catch (e: Exception) {
                    HakimFaultContainment.recordFailure(context, "secure_relay", "legacy_poll", e)
                    connected = false
                }
            }

            HakimConnectionResilience.scheduleSoon(context, "secure_relay_failure")
            lastLoopProgressAt = System.currentTimeMillis()
            sleep(retryMs)
            retryMs = (retryMs * 2).coerceAtMost(60_000L)
        }
    }

    private fun directPollAcrossBridges(
        context: Context,
        topic: String,
        resultTopic: String,
        relayKey: String
    ): String {
        val bases = bridgeBases(context)
        var firstSuccess: String? = null
        var firstFailure: Exception? = null
        val waitMs = if (bases.size > 1) 4_000 else 25_000
        for (base in bases) {
            try {
                directPollOnce(context, base, topic, resultTopic, relayKey, waitMs)
                if (firstSuccess == null) firstSuccess = base
            } catch (e: Exception) {
                if (firstFailure == null) firstFailure = e
            }
        }
        return firstSuccess ?: throw (firstFailure ?: IllegalStateException("direct_no_bridge"))
    }

    private fun directPollOnce(
        context: Context,
        base: String,
        topic: String,
        resultTopic: String,
        relayKey: String,
        waitMs: Int
    ) {
        val encodedTopic = URLEncoder.encode(topic, "UTF-8")
        val url = base + "/device/v1/commands?topic=" + encodedTopic + "&wait_ms=" + waitMs
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = if (waitMs <= 4_000) 8_000 else 15_000
            conn.readTimeout = if (waitMs <= 4_000) 12_000 else 35_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer " + relayKey)
            val code = conn.responseCode
            if (code == 204) return
            if (code !in 200..299) throw IllegalStateException("direct_http_" + code)
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val payload = JSONObject(body)
            val requestId = payload.optString("request_id")
            val carrier = payload.optString("carrier")
            if (requestId.isBlank() || carrier.isBlank()) throw IllegalStateException("direct_invalid_command")
            val handled = handleCarrier(context, carrier, resultTopic, relayKey, base)
            if (handled == requestId) ackDirect(base, topic, relayKey, requestId)
        } finally {
            conn.disconnect()
        }
    }

    private fun ackDirect(base: String, topic: String, relayKey: String, requestId: String) {
        val encodedTopic = URLEncoder.encode(topic, "UTF-8")
        val encodedId = URLEncoder.encode(requestId, "UTF-8")
        val url = base + "/device/v1/commands/" + encodedId + "/ack?topic=" + encodedTopic
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer " + relayKey)
            conn.outputStream.use { it.write(ByteArray(0)) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("direct_ack_failed")
            runCatching { conn.inputStream.close() }
        } finally {
            conn.disconnect()
        }
    }

    private fun listenLegacyOnce(context: Context, topic: String, resultTopic: String, relayKey: String) {
        val conn = URL("https://ntfy.sh/" + topic + "/json?poll=1&since=2m").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/x-ndjson")
            if (conn.responseCode !in 200..299) throw IllegalStateException("legacy_http_error")
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    handleNtfyLine(context, line, resultTopic, relayKey)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun handleNtfyLine(context: Context, line: String, resultTopic: String, relayKey: String) {
        val event = runCatching { JSONObject(line) }.getOrNull() ?: return
        if (event.optString("event") != "message") return
        handleCarrier(context, event.optString("message").trim(), resultTopic, relayKey, null)
    }

    private fun handleCarrier(
        context: Context,
        carrier: String,
        resultTopic: String,
        relayKey: String,
        preferredBridgeBase: String? = null
    ): String? {
        if (carrier.length !in 32..131072) return null
        val raw = decryptCarrier(carrier, relayKey) ?: return null
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val requestId = envelope.optString("request_id")
        val op = envelope.optString("op")
        val expiresAt = envelope.optLong("expires_at_ms", 0L)
        val payloadB64 = envelope.optString("payload_b64")
        val signature = envelope.optString("signature")

        if (!REQUEST_ID.matches(requestId) || !ALLOWED_OPS.contains(op)) return null
        if (payloadB64.length > 32768 || !SIGNATURE.matches(signature)) return null
        if (!validSignature(relayKey, requestId, op, expiresAt, payloadB64, signature)) return null
        if (expiresAt <= System.currentTimeMillis()) {
            sendResult(context, resultTopic, requestId, "expired", JSONObject().put("error", "request_expired"), preferredBridgeBase)
            return requestId
        }
        if (!claimRemoteRequest(context, requestId)) {
            sendResult(context, resultTopic, requestId, "duplicate", JSONObject().put("error", "duplicate_request"), preferredBridgeBase)
            return requestId
        }

        if (READ_ONLY_OPS.contains(op) || SAFE_AUTOMATIC_OPS.contains(op)) {
            val result = executeEnvelope(context, envelope)
            sendResult(context, resultTopic, requestId, if (result.optBoolean("ok", false)) "ok" else "error", result, preferredBridgeBase)
        } else {
            savePending(context, envelope, resultTopic, preferredBridgeBase)
            showApproval(context, requestId, op)
        }
        return requestId
    }

    private fun decryptCarrier(carrier: String, relayKey: String): String? {
        if (!carrier.startsWith(CARRIER_PREFIX)) return null
        val packed = runCatching {
            Base64.decode(carrier.removePrefix(CARRIER_PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }.getOrNull() ?: return null
        if (packed.size < GCM_NONCE_BYTES + 16) return null
        val nonce = packed.copyOfRange(0, GCM_NONCE_BYTES)
        val ciphertext = packed.copyOfRange(GCM_NONCE_BYTES, packed.size)
        return runCatching {
            val keyMaterial = "$CARRIER_AAD\u0000$relayKey".toByteArray(Charsets.UTF_8)
            val aesKey = MessageDigest.getInstance("SHA-256").digest(keyMaterial)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(CARRIER_AAD.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun validSignature(
        relayKey: String,
        requestId: String,
        op: String,
        expiresAt: Long,
        payloadB64: String,
        signature: String
    ): Boolean {
        val canonical = "$requestId\n$op\n$expiresAt\n$payloadB64"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(relayKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            signature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }

    @Synchronized
    private fun claimRemoteRequest(context: Context, requestId: String): Boolean {
        val prefs = context.getSharedPreferences("hakim_secure_idempotency", Context.MODE_PRIVATE)
        val ids = LinkedHashSet(prefs.getStringSet("ids", emptySet()) ?: emptySet())
        if (ids.contains(requestId)) return false
        ids.add(requestId)
        while (ids.size > 128) ids.remove(ids.first())
        return prefs.edit().putStringSet("ids", ids).commit()
    }

    private fun savePending(
        context: Context,
        envelope: JSONObject,
        resultTopic: String,
        preferredBridgeBase: String?
    ) {
        val id = envelope.optString("request_id")
        val edit = context.getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE).edit()
            .putString("$id.envelope", envelope.toString())
            .putString("$id.result_topic", resultTopic)
        val normalizedBridge = preferredBridgeBase?.let { normalizeBridgeBase(it) }
        if (normalizedBridge != null) edit.putString("$id.bridge_base", normalizedBridge)
        else edit.remove("$id.bridge_base")
        edit.apply()
    }

    private fun takePending(context: Context, requestId: String): Triple<JSONObject, String, String?>? {
        val prefs = context.getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE)
        val raw = prefs.getString("$requestId.envelope", null) ?: return null
        val topic = prefs.getString("$requestId.result_topic", null) ?: return null
        val preferredBridge = prefs.getString("$requestId.bridge_base", null)?.let { normalizeBridgeBase(it) }
        prefs.edit()
            .remove("$requestId.envelope")
            .remove("$requestId.result_topic")
            .remove("$requestId.bridge_base")
            .apply()
        return runCatching { Triple(JSONObject(raw), topic, preferredBridge) }.getOrNull()
    }

    private fun showApproval(context: Context, requestId: String, op: String) {
        ensureApprovalChannel(context)
        val approve = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            Intent(context, HakimRemoteApprovalReceiver::class.java).setAction(ACTION_APPROVE).putExtra(EXTRA_REQUEST_ID, requestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reject = PendingIntent.getBroadcast(
            context,
            requestId.hashCode() xor 0x55AA,
            Intent(context, HakimRemoteApprovalReceiver::class.java).setAction(ACTION_REJECT).putExtra(EXTRA_REQUEST_ID, requestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, APPROVAL_CHANNEL)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(context)
        }
        context.getSystemService(NotificationManager::class.java).notify(
            requestId.hashCode(),
            notification
                .setContentTitle("حكيم — موافقة مطلوبة")
                .setContentText(when (op) {
                    "browser_back" -> "الرجوع إلى الصفحة السابقة في متصفح حكيم"
                    "chatgpt_action" -> "تنفيذ إجراء داخل حساب ChatGPT (مثل إرسال رسالة)"
                    else -> "طلب تحكم على الهاتف: $op"
                })
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_input_add, "موافقة", approve)
                .addAction(android.R.drawable.ic_delete, "رفض", reject)
                .build()
        )
    }

    fun handleApproval(context: Context, requestId: String, approved: Boolean) {
        val pending = takePending(context, requestId) ?: return
        val (envelope, resultTopic, preferredBridgeBase) = pending
        if (!approved) {
            sendResult(context, resultTopic, requestId, "rejected", JSONObject().put("ok", false).put("error", "rejected_by_user"), preferredBridgeBase)
            return
        }
        if (envelope.optLong("expires_at_ms", 0L) <= System.currentTimeMillis()) {
            sendResult(context, resultTopic, requestId, "expired", JSONObject().put("ok", false).put("error", "request_expired"), preferredBridgeBase)
            return
        }
        if (!HakimFaultContainment.canExecuteHighImpact(context)) {
            sendResult(
                context,
                resultTopic,
                requestId,
                "blocked",
                JSONObject().put("ok", false).put("error", "fault_containment_blocked"),
                preferredBridgeBase
            )
            return
        }
        val op = envelope.optString("op").ifBlank { "unknown" }
        if (!HakimFaultContainment.shouldAttempt(context, "remote_op", op)) {
            sendResult(
                context,
                resultTopic,
                requestId,
                "blocked",
                JSONObject().put("ok", false).put("error", "operation_circuit_open"),
                preferredBridgeBase
            )
            return
        }
        executor.execute {
            val result = executeEnvelope(context.applicationContext, envelope)
            sendResult(
                context,
                resultTopic,
                requestId,
                if (result.optBoolean("ok", false)) "ok" else "error",
                result,
                preferredBridgeBase
            )
        }
    }

    private fun decodePayload(envelope: JSONObject): JSONObject {
        val payloadB64 = envelope.optString("payload_b64")
        if (payloadB64.isBlank()) return JSONObject()
        return runCatching {
            val raw = String(
                Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            JSONObject(raw)
        }.getOrElse { JSONObject() }
    }

    private fun executeEnvelope(context: Context, envelope: JSONObject): JSONObject {
        val op = envelope.optString("op")
        val route = HakimToolRouter.decide(context, op)
        if (!route.allowed) {
            return JSONObject()
                .put("ok", false)
                .put("error", route.reason)
                .put("capability", route.capability)
        }
        val payload = decodePayload(envelope)
        return try {
            val result = when (op) {
            "status" -> status(context)
            "capabilities" -> HakimCapabilityFabric.status(context)
            "capability_read" -> HakimCapabilityFabric.executeReadOnly(
                context,
                envelope.optString("request_id"),
                payload
            )
            "browser_read" -> HakimService.readActiveBrowser()
            "browser_back" -> HakimService.backActiveBrowser()
            "chatgpt_read" -> HakimService.chatGptRead(payload)
            "chatgpt_navigate" -> HakimService.chatGptNavigate(payload)
            "chatgpt_action" -> HakimService.chatGptAction(payload)
            "termux_status" -> HakimTermuxControl.status(context).put("ok", true)
            "termux_probe" -> HakimTermuxControl.probe(context, "secure_relay_probe")
            "termux_recover" -> HakimTermuxControl.recover(context, "secure_relay_recover")
            "ui" -> {
                val service = HakimAccessibilityService.instance
                if (service == null) JSONObject().put("ok", false).put("error", "accessibility_unavailable")
                else JSONObject().put("ok", true).put("nodes", service.uiSnapshot())
            }
            "notifications" -> {
                if (!HakimNotificationListener.isConnected()) JSONObject().put("ok", false).put("error", "notification_listener_unavailable")
                else JSONObject().put("ok", true).put("notifications", HakimNotificationListener.snapshot())
            }
            "screenshot" -> {
                val image = HakimAccessibilityService.instance?.screenshotBase64()
                if (image.isNullOrBlank()) JSONObject().put("ok", false).put("error", "screenshot_unavailable")
                else JSONObject().put("ok", true).put("mime", "image/png").put("base64", image)
            }
            "action" -> {
                val ok = HakimAccessibilityService.instance?.action(payload) == true
                JSONObject().put("ok", ok).put("error", if (ok) JSONObject.NULL else "action_failed")
            }
            "launch" -> launch(context, payload)
                else -> JSONObject().put("ok", false).put("error", "unsupported_operation")
            }
            if (result.optBoolean("ok", false)) {
                HakimFaultContainment.recordSuccess(context, "remote_op", op)
            } else {
                HakimFaultContainment.recordFailure(
                    context,
                    "remote_op",
                    op.ifBlank { "unknown" },
                    IllegalStateException("operation_result_failed")
                )
            }
            result
        } catch (e: Exception) {
            HakimFaultContainment.recordFailure(
                context,
                "remote_op",
                op.ifBlank { "unknown" },
                e
            )
            JSONObject().put("ok", false).put("error", "operation_failed_safely")
        }
    }

    private fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val liveConnected = isConnected()
        val persistedRelayState = p.getString("secure_relay_state", "unknown") ?: "unknown"
        val coherentRelayState = if (!liveConnected && persistedRelayState == "direct_connected") {
            "direct_recovering"
        } else {
            persistedRelayState
        }
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo?.longVersionCode ?: 0L
        else @Suppress("DEPRECATION") packageInfo?.versionCode?.toLong() ?: 0L
        val self = context.getSharedPreferences("hakim_governance", Context.MODE_PRIVATE)
        return JSONObject()
            .put("ok", true)
            .put("package", context.packageName)
            .put("version_code", version)
            .put("version_name", packageInfo?.versionName.orEmpty())
            .put("single_app", true)
            .put("secure_relay", isConfigured(context))
            .put("secure_relay_state", coherentRelayState)
            .put("direct_bridge", true)
            .put("secure_relay_running", isRunning())
            .put("secure_relay_connected", liveConnected)
            .put("orchestrator", HakimOrchestrator.status(context))
            .put("operation", HakimExecutiveLoop.publicStatus(context))
            .put("task_manager", HakimTaskManager.publicStatus(context))
            .put("cloud_continuity", HakimCloudContinuity.publicStatus(context))
            .put("browser_service_running", HakimService.running)
            .put("legacy_channel_connected", HakimService.connected)
            .put("accessibility", HakimAccessibilityService.instance != null)
            .put("notification_listener", HakimNotificationListener.isConnected())
            .put("auto_update", AutoUpdater.diagnostics(context))
            .put("network_guardian", HakimNetworkGuardian.status(context))
            .put("device_protection", HakimDeviceProtection.status(context))
            .put("network_diagnostics", HakimNetworkDiagnostics.inspect(context))
            .put("execution_fabric", HakimExecutionFabric.status(context))
            .put("capability_kernel", HakimCapabilityKernel.status(context))
            .put("capability_fabric", HakimCapabilityFabric.status(context))
            .put("fault_containment", HakimFaultContainment.status(context))
            .put("self_improvement", HakimSelfImprovementLoop.status(context))
            .put("cognitive_policy", HakimCognitivePolicy.status(context))
            .put("termux_control", HakimTermuxControl.status(context))
            .put("self_check", self.getString("last_self_check_status", "NOT_TESTED"))
            .put("learning", HakimLearning.snapshot(context))
    }

    private fun launch(context: Context, payload: JSONObject): JSONObject {
        val packageName = payload.optString("package").trim()
        val url = payload.optString("url").trim()
        val intent = when {
            packageName.isNotBlank() -> context.packageManager.getLaunchIntentForPackage(packageName)
            url.startsWith("https://") || url.startsWith("http://") -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
            else -> null
        } ?: return JSONObject().put("ok", false).put("error", "launch_target_unavailable")
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            JSONObject().put("ok", true)
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.javaClass.simpleName)
        }
    }

    fun sendHealthBeacon(context: Context, health: JSONObject): Boolean {
        if (!isConfigured(context)) return false
        val resultTopic = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RESULT_TOPIC, "").orEmpty()
        if (resultTopic.isBlank()) return false
        return sendResult(context, resultTopic, health.optString("request_id"), "health", health)
    }

    fun sendPairingAckAsync(context: Context) {
        val app = context.applicationContext
        executor.execute {
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val resultTopic = p.getString(KEY_RESULT_TOPIC, "").orEmpty()
            if (resultTopic.isBlank()) return@execute
            val requestId = "pair-${System.currentTimeMillis()}"
            val result = status(app).put("event", "paired")
            sendResult(app, resultTopic, requestId, "paired", result)
        }
    }

    private fun encryptResult(relayKey: String, payload: JSONObject): String {
        val nonce = ByteArray(GCM_NONCE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val keyMaterial = "$RESULT_AAD\u0000$relayKey".toByteArray(Charsets.UTF_8)
        val aesKey = MessageDigest.getInstance("SHA-256").digest(keyMaterial)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(RESULT_AAD.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        val packed = ByteArray(nonce.size + encrypted.size)
        System.arraycopy(nonce, 0, packed, 0, nonce.size)
        System.arraycopy(encrypted, 0, packed, nonce.size, encrypted.size)
        return RESULT_PREFIX + Base64.encodeToString(packed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sendResult(
        context: Context,
        resultTopic: String,
        requestId: String,
        status: String,
        result: JSONObject,
        preferredBridgeBase: String? = null
    ): Boolean {
        val relayKey = relayKey(context) ?: return false
        val payload = JSONObject()
            .put("request_id", requestId)
            .put("status", status)
            .put("received_at_ms", System.currentTimeMillis())
            .put("result", result)
        val carrier = encryptResult(relayKey, payload)

        val encodedTopic = URLEncoder.encode(resultTopic, "UTF-8")
        for (base in orderedBridgeBases(context, preferredBridgeBase)) {
            val directOk = runCatching {
                val url = base + "/device/v1/results?topic=" + encodedTopic
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 20_000
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Authorization", "Bearer " + relayKey)
                    conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    conn.outputStream.use { it.write(carrier.toByteArray(Charsets.UTF_8)) }
                    val ok = conn.responseCode in 200..299
                    runCatching { (if (ok) conn.inputStream else conn.errorStream)?.close() }
                    ok
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(false)

            if (directOk) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("secure_relay_last_result_at", System.currentTimeMillis())
                    .putString(
                        "secure_relay_last_result_state",
                        if (base == bridgeBase(context)) "direct_primary_sent" else "direct_secondary_sent"
                    )
                    .apply()
                return true
            }
        }

        val legacyOk = runCatching {
            val conn = URL("https://ntfy.sh/" + resultTopic).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.outputStream.use { it.write(carrier.toByteArray(Charsets.UTF_8)) }
                val ok = conn.responseCode in 200..299
                runCatching { (if (ok) conn.inputStream else conn.errorStream)?.close() }
                ok
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("secure_relay_last_result_at", System.currentTimeMillis())
            .putString("secure_relay_last_result_state", if (legacyOk) "legacy_sent" else "failed")
            .apply()
        return legacyOk
    }

    private fun ensureApprovalChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(APPROVAL_CHANNEL, "موافقات حكيم البعيدة", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }
}

class HakimRemoteApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("request_id") ?: return
        when (intent.action) {
            "ps.hakim.stable.REMOTE_APPROVE" -> HakimUnifiedRelay.handleApproval(context, requestId, true)
            "ps.hakim.stable.REMOTE_REJECT" -> HakimUnifiedRelay.handleApproval(context, requestId, false)
        }
    }
}
