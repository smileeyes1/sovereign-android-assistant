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
    const val KEY_RELAY_KEY = "relay_hmac_key"

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
    private val READ_ONLY_OPS = setOf("status", "ui", "notifications", "screenshot")
    private val ALLOWED_OPS = READ_ONLY_OPS + setOf("action", "launch")
    private val running = AtomicBoolean(false)
    @Volatile private var connected = false
    private val executor = Executors.newSingleThreadExecutor()

    fun isRunning(): Boolean = running.get()
    fun isConnected(): Boolean = connected

    fun stop() {
        running.set(false)
        connected = false
    }

    fun configure(context: Context, topic: String?, resultTopic: String?, relayKey: String?): Boolean {
        if (topic.isNullOrBlank() || !Regex("^[A-Za-z0-9_-]{20,120}$").matches(topic)) return false
        if (resultTopic.isNullOrBlank() || !Regex("^[A-Za-z0-9_-]{20,120}$").matches(resultTopic)) return false
        if (relayKey.isNullOrBlank() || !RELAY_KEY.matches(relayKey)) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOPIC, topic)
            .putString(KEY_RESULT_TOPIC, resultTopic)
            .putString(KEY_RELAY_KEY, relayKey)
            .putBoolean("secure_relay_configured", true)
            .commit()
    }

    fun isConfigured(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !p.getString(KEY_TOPIC, "").isNullOrBlank() &&
            !p.getString(KEY_RESULT_TOPIC, "").isNullOrBlank() &&
            !p.getString(KEY_RELAY_KEY, "").isNullOrBlank()
    }

    fun start(context: Context) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) return
        ensureApprovalChannel(app)
        executor.execute { loop(app) }
    }

    private fun loop(context: Context) {
        var retryMs = 2_000L
        while (running.get()) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val topic = prefs.getString(KEY_TOPIC, null)
            val resultTopic = prefs.getString(KEY_RESULT_TOPIC, null)
            val relayKey = prefs.getString(KEY_RELAY_KEY, null)
            if (topic.isNullOrBlank() || resultTopic.isNullOrBlank() || relayKey.isNullOrBlank()) {
                connected = false
                prefs.edit().putString("secure_relay_state", "unconfigured").apply()
                sleep(10_000L)
                continue
            }
            try {
                val conn = URL("https://ntfy.sh/$topic/json").openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 75_000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/x-ndjson")
                conn.inputStream.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        retryMs = 2_000L
                        connected = true
                        prefs.edit().putString("secure_relay_state", "connected").putLong("secure_relay_seen_at", System.currentTimeMillis()).remove("secure_relay_error").apply()
                        while (running.get()) {
                            val line = reader.readLine() ?: break
                            prefs.edit().putLong("secure_relay_seen_at", System.currentTimeMillis()).apply()
                            handleNtfyLine(context, line, resultTopic, relayKey)
                        }
                    }
                }
                connected = false
                conn.disconnect()
            } catch (e: Exception) {
                connected = false
                prefs.edit().putString("secure_relay_state", "recovering").putString("secure_relay_error", e.javaClass.simpleName).apply()
                sleep(retryMs)
                retryMs = (retryMs * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun handleNtfyLine(context: Context, line: String, resultTopic: String, relayKey: String) {
        val event = runCatching { JSONObject(line) }.getOrNull() ?: return
        if (event.optString("event") != "message") return
        val carrier = event.optString("message").trim()
        if (carrier.length !in 32..65536) return
        val raw = decryptCarrier(carrier, relayKey) ?: return
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = envelope.optString("request_id")
        val op = envelope.optString("op")
        val expiresAt = envelope.optLong("expires_at_ms", 0L)
        val payloadB64 = envelope.optString("payload_b64")
        val signature = envelope.optString("signature")

        if (!REQUEST_ID.matches(requestId) || !ALLOWED_OPS.contains(op)) return
        if (payloadB64.length > 32768 || !SIGNATURE.matches(signature)) return
        if (!validSignature(relayKey, requestId, op, expiresAt, payloadB64, signature)) return
        if (expiresAt <= System.currentTimeMillis()) {
            sendResult(context, resultTopic, requestId, "expired", JSONObject().put("error", "request_expired"))
            return
        }
        if (!claimRemoteRequest(context, requestId)) {
            sendResult(context, resultTopic, requestId, "duplicate", JSONObject().put("error", "duplicate_request"))
            return
        }

        if (READ_ONLY_OPS.contains(op)) {
            val result = executeEnvelope(context, envelope)
            sendResult(context, resultTopic, requestId, if (result.optBoolean("ok", false)) "ok" else "error", result)
        } else {
            savePending(context, envelope, resultTopic)
            showApproval(context, requestId, op)
        }
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

    private fun savePending(context: Context, envelope: JSONObject, resultTopic: String) {
        val id = envelope.optString("request_id")
        context.getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE).edit()
            .putString("$id.envelope", envelope.toString())
            .putString("$id.result_topic", resultTopic)
            .apply()
    }

    private fun takePending(context: Context, requestId: String): Pair<JSONObject, String>? {
        val prefs = context.getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE)
        val raw = prefs.getString("$requestId.envelope", null) ?: return null
        val topic = prefs.getString("$requestId.result_topic", null) ?: return null
        prefs.edit().remove("$requestId.envelope").remove("$requestId.result_topic").apply()
        return runCatching { JSONObject(raw) to topic }.getOrNull()
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
                .setContentText("طلب تحكم على الهاتف: $op")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_input_add, "موافقة", approve)
                .addAction(android.R.drawable.ic_delete, "رفض", reject)
                .build()
        )
    }

    fun handleApproval(context: Context, requestId: String, approved: Boolean) {
        val pending = takePending(context, requestId) ?: return
        val (envelope, resultTopic) = pending
        if (!approved) {
            sendResult(context, resultTopic, requestId, "rejected", JSONObject().put("ok", false).put("error", "rejected_by_user"))
            return
        }
        if (envelope.optLong("expires_at_ms", 0L) <= System.currentTimeMillis()) {
            sendResult(context, resultTopic, requestId, "expired", JSONObject().put("ok", false).put("error", "request_expired"))
            return
        }
        executor.execute {
            val result = executeEnvelope(context.applicationContext, envelope)
            sendResult(context, resultTopic, requestId, if (result.optBoolean("ok", false)) "ok" else "error", result)
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
        val payload = decodePayload(envelope)
        return when (op) {
            "status" -> status(context)
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
    }

    private fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
            .put("secure_relay_state", p.getString("secure_relay_state", "unknown"))
            .put("secure_relay_running", isRunning())
            .put("secure_relay_connected", isConnected())
            .put("browser_service_running", HakimService.running)
            .put("legacy_channel_connected", HakimService.connected)
            .put("accessibility", HakimAccessibilityService.instance != null)
            .put("notification_listener", HakimNotificationListener.isConnected())
            .put("auto_update", AutoUpdater.diagnostics(context))
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

    private fun sendResult(context: Context, resultTopic: String, requestId: String, status: String, result: JSONObject): Boolean {
        return try {
            val relayKey = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RELAY_KEY, null) ?: return false
            val payload = JSONObject()
                .put("request_id", requestId)
                .put("status", status)
                .put("received_at_ms", System.currentTimeMillis())
                .put("result", result)
            val carrier = encryptResult(relayKey, payload)
            val conn = URL("https://ntfy.sh/$resultTopic").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.outputStream.use { it.write(carrier.toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            runCatching { (if (ok) conn.inputStream else conn.errorStream)?.close() }
            conn.disconnect()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("secure_relay_last_result_at", System.currentTimeMillis())
                .putString("secure_relay_last_result_state", if (ok) "sent" else "http_error")
                .apply()
            ok
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("secure_relay_last_result_state", "failed")
                .putString("secure_relay_last_result_error", e.javaClass.simpleName)
                .apply()
            false
        }
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
