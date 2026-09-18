package ps.hakim.phoneagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * قناة النتائج السيادية:
 * 1) تحافظ على المسار الآمن الحالي أولًا حتى لا تكسر التوافق.
 * 2) عند فشله تستخدم قناة النتائج المباشرة الموجودة أصلًا، بلا اعتماد على Make.
 * 3) عند فشل الشبكة كلها تحفظ النتيجة محليًا مشفرة داخل AndroidKeyStore.
 * 4) تعيد تفريغ الصندوق تلقائيًا عند عودة الاتصال.
 *
 * لا تُخزَّن مفاتيح أو أسرار داخل الصندوق؛ تُقرأ مفاتيح الاقتران من مخزن التطبيق عند الإرسال.
 */
object HakimSovereignResultChannel {
    private const val PREFS = "hakim"
    private const val KEY_ALIAS = "hakim_sovereign_result_outbox_v1"
    private const val FILE_NAME = "hakim-sovereign-result-outbox.enc"
    private const val MAX_OUTBOX_BYTES = 64L * 1024L * 1024L
    private const val MAX_ENTRY_CHARS = 8_000_000
    private const val MAX_FLUSH_PER_RUN = 12
    private const val RESULT_TOPIC = "result_topic"
    private const val AUTH_KEY = "auth_key"
    private const val DIRECT_PREFIX = "HR1."
    private const val DIRECT_AAD = "HAKIM-RESULT-v1"
    private const val DIRECT_NONCE_BYTES = 12
    private const val DIRECT_TAG_BITS = 128

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val flushExecutor = Executors.newSingleThreadExecutor()
    private val flushing = AtomicBoolean(false)

    fun sendResult(
        context: Context,
        resultUrl: String,
        requestId: String,
        status: String,
        result: JSONObject
    ): Boolean {
        val app = context.applicationContext
        val payload = JSONObject()
            .put("request_id", requestId)
            .put("status", status)
            .put("received_at_ms", System.currentTimeMillis())
            .put("result", result)
            .toString()

        val transport = deliverResultNoQueue(app, resultUrl, requestId, payload)
        if (transport != null) {
            recordResultState(app, "sent", transport)
            return true
        }

        val queued = enqueue(
            app,
            JSONObject()
                .put("kind", "relay_result")
                .put("queued_at_ms", System.currentTimeMillis())
                .put("request_id", requestId)
                .put("result_url", resultUrl)
                .put("payload", payload)
        )
        recordResultState(app, if (queued) "queued_local_encrypted" else "outbox_full", "encrypted_outbox")
        return false
    }

    fun sendHealth(
        context: Context,
        resultUrl: String,
        relayKey: String,
        healthPayload: String
    ): Boolean {
        val app = context.applicationContext
        val health = runCatching { JSONObject(healthPayload) }.getOrNull() ?: return false
        val requestId = health.optString("request_id").ifBlank { "health-${System.currentTimeMillis()}" }
        val now = System.currentTimeMillis()

        val secureBody = if (resultUrl.startsWith("https://") && relayKey.isNotBlank()) {
            val signature = hmacHex(relayKey, "$requestId\nhealth\n$healthPayload")
            JSONObject()
                .put("request_id", requestId)
                .put("status", "health")
                .put("received_at_ms", now)
                .put("result", health)
                .put("sig", signature)
                .toString()
        } else ""

        val transport = deliverHealthNoQueue(app, resultUrl, requestId, secureBody, healthPayload)
        if (transport != null) {
            recordHealthState(app, "sent", transport, now)
            return true
        }

        val queued = enqueue(
            app,
            JSONObject()
                .put("kind", "health")
                .put("queued_at_ms", now)
                .put("request_id", requestId)
                .put("result_url", resultUrl)
                .put("secure_body", secureBody)
                .put("legacy_data", healthPayload)
        )
        recordHealthState(
            app,
            if (queued) "queued_local_encrypted" else "outbox_full",
            "encrypted_outbox",
            now
        )
        return false
    }

    fun flushAsync(context: Context) {
        val app = context.applicationContext
        if (!flushing.compareAndSet(false, true)) return
        flushExecutor.execute {
            try {
                flush(app)
            } finally {
                flushing.set(false)
            }
        }
    }

    fun status(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("provider_independent_fallback", true)
            .put("encrypted_local_outbox", true)
            .put("outbox_bytes", if (file.exists()) file.length() else 0L)
            .put("outbox_state", p.getString("sovereign_outbox_state", "empty"))
            .put("last_result_transport", p.getString("secure_relay_last_result_transport", ""))
            .put("last_health_transport", p.getString("last_health_beacon_transport", ""))
            .put("last_flush_at", p.getLong("sovereign_outbox_last_flush_at", 0L))
            .put("last_flush_delivered", p.getInt("sovereign_outbox_last_flush_delivered", 0))
    }

    private fun deliverResultNoQueue(
        context: Context,
        resultUrl: String,
        requestId: String,
        payload: String
    ): String? {
        if (postJson(resultUrl, payload)) return "secure_webhook"
        if (postEncryptedNtfy(context, payload)) return "encrypted_ntfy"
        return null
    }

    private fun deliverHealthNoQueue(
        context: Context,
        resultUrl: String,
        requestId: String,
        secureBody: String,
        legacyData: String
    ): String? {
        if (secureBody.isNotBlank() && postJson(resultUrl, secureBody)) return "secure_webhook"
        if (postEncryptedNtfy(context, legacyData)) return "encrypted_ntfy"
        if (postLegacyNtfy(context, requestId, legacyData)) return "legacy_ntfy"
        return null
    }

    private fun postJson(url: String, body: String): Boolean {
        if (!url.startsWith("https://") || body.isBlank()) return false
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "HAKIM-Sovereign-Result/1")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun postEncryptedNtfy(context: Context, data: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val preferredResultTopic = p.getString(RESULT_TOPIC, "").orEmpty().trim()
        val relayTopic = p.getString(HakimUnifiedRelay.KEY_TOPIC, "").orEmpty().trim()
        val topic = preferredResultTopic.ifBlank { relayTopic }
        val relayKey = p.getString(HakimUnifiedRelay.KEY_RELAY_KEY, "").orEmpty().trim()
        if (topic.isBlank() || relayKey.isBlank()) return false
        if (!topic.matches(Regex("^[A-Za-z0-9_-]{8,160}$"))) return false

        val carrier = runCatching {
            val keyMaterial = "$DIRECT_AAD\u0000$relayKey".toByteArray(Charsets.UTF_8)
            val aesKey = MessageDigest.getInstance("SHA-256").digest(keyMaterial)
            val nonce = ByteArray(DIRECT_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(DIRECT_TAG_BITS, nonce)
            )
            cipher.updateAAD(DIRECT_AAD.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(nonce.size + encrypted.size)
            System.arraycopy(nonce, 0, packed, 0, nonce.size)
            System.arraycopy(encrypted, 0, packed, nonce.size, encrypted.size)
            DIRECT_PREFIX + Base64.encodeToString(
                packed,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }.getOrNull() ?: return false

        val req = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .header("User-Agent", "HAKIM-Sovereign-Result/1")
            .post(carrier.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun postLegacyNtfy(context: Context, requestId: String, data: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val topic = p.getString(RESULT_TOPIC, "").orEmpty().trim()
        val authKey = p.getString(AUTH_KEY, "").orEmpty().trim()
        if (topic.isBlank() || authKey.isBlank()) return false
        if (!topic.matches(Regex("^[A-Za-z0-9_-]{8,160}$"))) return false

        val wrapper = JSONObject()
            .put("request_id", requestId)
            .put("chunk", 1)
            .put("total", 1)
            .put("data", data)
            .put("sig", hmacHex(authKey, "$requestId\n1\n1\n$data"))
            .toString()

        val req = Request.Builder()
            .url("https://ntfy.sh/$topic")
            .header("User-Agent", "HAKIM-Sovereign-Result/1")
            .post(wrapper.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    private fun flush(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrElse { return }
        if (lines.isEmpty()) return

        val decoded = mutableListOf<JSONObject>()
        for (line in lines) {
            val plain = decryptLine(line)
            val item = plain?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (item == null) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("sovereign_outbox_state", "decrypt_failed")
                    .apply()
                return
            }
            decoded += item
        }

        val remaining = mutableListOf<JSONObject>()
        var attempted = 0
        var delivered = 0

        for (item in decoded) {
            if (attempted >= MAX_FLUSH_PER_RUN) {
                remaining += item
                continue
            }
            attempted++

            val ok = when (item.optString("kind")) {
                "relay_result" -> deliverResultNoQueue(
                    context,
                    item.optString("result_url"),
                    item.optString("request_id"),
                    item.optString("payload")
                ) != null

                "health" -> deliverHealthNoQueue(
                    context,
                    item.optString("result_url"),
                    item.optString("request_id"),
                    item.optString("secure_body"),
                    item.optString("legacy_data")
                ) != null

                else -> false
            }

            if (ok) delivered++ else remaining += item
        }

        if (rewrite(context, remaining)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sovereign_outbox_state", if (remaining.isEmpty()) "empty" else "pending")
                .putLong("sovereign_outbox_last_flush_at", System.currentTimeMillis())
                .putInt("sovereign_outbox_last_flush_delivered", delivered)
                .apply()
        }
    }

    @Synchronized
    private fun enqueue(context: Context, item: JSONObject): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        if (item.toString().length > MAX_ENTRY_CHARS) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sovereign_outbox_state", "entry_too_large")
                .apply()
            return false
        }
        if (file.exists() && file.length() >= MAX_OUTBOX_BYTES) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sovereign_outbox_state", "full")
                .apply()
            return false
        }
        return try {
            file.appendText(encryptLine(item.toString()) + "\n", Charsets.UTF_8)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sovereign_outbox_state", "pending")
                .apply()
            true
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sovereign_outbox_state", "write_failed")
                .apply()
            false
        }
    }

    private fun rewrite(context: Context, items: List<JSONObject>): Boolean {
        val atomic = AtomicFile(File(context.filesDir, FILE_NAME))
        var stream: FileOutputStream? = null
        return try {
            stream = atomic.startWrite()
            for (item in items) {
                stream.write((encryptLine(item.toString()) + "\n").toByteArray(Charsets.UTF_8))
            }
            atomic.finishWrite(stream)
            stream = null
            true
        } catch (_: Exception) {
            stream?.let { runCatching { atomic.failWrite(it) } }
            false
        }
    }

    private fun recordResultState(context: Context, state: String, transport: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("secure_relay_last_result_at", System.currentTimeMillis())
            .putString("secure_relay_last_result_state", state)
            .putString("secure_relay_last_result_transport", transport)
            .apply()
    }

    private fun recordHealthState(context: Context, state: String, transport: String, now: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_health_beacon_state", state)
            .putString("last_health_beacon_transport", transport)
            .putLong("last_health_beacon_at", now)
            .apply()
    }

    private fun encryptLine(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(encrypted, 0, packed, iv.size, encrypted.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decryptLine(line: String): String? = try {
        val packed = Base64.decode(line.trim(), Base64.NO_WRAP)
        if (packed.size <= 12) null else {
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun hmacHex(keyText: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes(keyText), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun keyBytes(keyText: String): ByteArray {
        val hex = keyText.trim()
        return if (hex.length % 2 == 0 && hex.matches(Regex("^[0-9a-fA-F]+$"))) {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } else {
            hex.toByteArray(StandardCharsets.UTF_8)
        }
    }
}
