package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Android-native OpenRouter OAuth/PKCE bridge.
 *
 * OpenRouter officially supports localhost callbacks for local-first apps.
 * Hakim therefore binds a short-lived server only on 127.0.0.1, launches the
 * system browser for consent, validates state + PKCE, exchanges the code over
 * HTTPS, stores the resulting API key in AndroidKeyStore, then closes the server.
 *
 * This deliberately avoids the old desktop callback being loaded inside Hakim's
 * WebView, which caused ERR_CLEARTEXT_NOT_PERMITTED.
 */
object OpenRouterPkceAuth {
    private const val PREFS = "hakim_openrouter_auth"
    private const val AUTH_URL = "https://openrouter.ai/auth"
    private const val EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
    private const val TIMEOUT_MS = 5 * 60 * 1000
    const val SECRET_OPENROUTER_KEY = "openrouter_api_key"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun start(activity: Activity): String {
        val verifier = randomUrlSafe(48)
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        )
        val state = randomUrlSafe(24)

        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = TIMEOUT_MS
        val callback = "http://127.0.0.1:" + server.localPort + "/callback"

        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString("status", "بانتظار موافقة OpenRouter في المتصفح")
            .putLong("started_at", System.currentTimeMillis())
            .apply()

        Thread {
            try {
                server.use { loopback ->
                    val socket = loopback.accept()
                    socket.use { peer ->
                        val reader = BufferedReader(InputStreamReader(peer.getInputStream(), Charsets.US_ASCII))
                        val requestLine = reader.readLine().orEmpty()
                        val target = requestLine.split(" ").getOrNull(1).orEmpty()
                        val params = parseQuery(target.substringAfter("?", ""))
                        val code = params["code"].orEmpty()
                        val returnedState = params["state"].orEmpty()

                        if (code.isBlank() || returnedState != state) {
                            sendBrowserResult(peer.getOutputStream(), false, "تعذر التحقق من جلسة الربط.")
                            setStatus(activity, "فشل الربط: رمز أو state غير صالح")
                            return@use
                        }

                        val key = exchangeCode(code, verifier)
                        if (key.isNullOrBlank()) {
                            sendBrowserResult(peer.getOutputStream(), false, "تعذر إنشاء مفتاح حكيم.")
                            setStatus(activity, "فشل تبادل رمز OpenRouter")
                            return@use
                        }

                        HakimSecretStore.put(activity, SECRET_OPENROUTER_KEY, key)
                        activity.getSharedPreferences(OpenRouterDirectEngine.PREFS, Activity.MODE_PRIVATE)
                            .edit()
                            .putBoolean("openrouter_field_verified", false)
                            .remove("openrouter_history")
                            .apply()
                        setStatus(activity, "تم الربط؛ ارجع إلى حكيم لاختبار المحرك المجاني")
                        sendBrowserResult(peer.getOutputStream(), true, "تم ربط OpenRouter بحكيم. يمكنك العودة إلى التطبيق.")
                    }
                }
            } catch (_: Exception) {
                setStatus(activity, "انتهت مهلة ربط OpenRouter أو انقطع الاتصال")
            }
        }.start()

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("callback_url", callback)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()

        activity.startActivity(Intent(Intent.ACTION_VIEW, authUri))
        return callback
    }

    fun status(activity: Activity): String =
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .getString("status", "غير مربوط").orEmpty()

    private fun exchangeCode(code: String, verifier: String): String? {
        val json = JSONObject()
            .put("code", code)
            .put("code_verifier", verifier)
            .put("code_challenge_method", "S256")
            .toString()

        val request = Request.Builder()
            .url(EXCHANGE_URL)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val raw = response.body?.string().orEmpty()
            JSONObject(raw).optString("key").takeIf { it.startsWith("sk-or-") }
        }
    }

    private fun sendBrowserResult(
        output: java.io.OutputStream,
        ok: Boolean,
        message: String
    ) {
        val title = if (ok) "تم الربط" else "فشل الربط"
        val html = """
            <!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title>
            <body style="font-family:sans-serif;padding:32px;text-align:center">
            <h2>$title</h2><p>$message</p><p>عد الآن إلى تطبيق حكيم.</p>
            </body></html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: " + bytes.size + "\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun setStatus(activity: Activity, value: String) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit().putString("status", value).putLong("updated_at", System.currentTimeMillis()).apply()
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split("&")
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val parts = pair.split("=", limit = 2)
                val k = URLDecoder.decode(parts[0], "UTF-8")
                val v = URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                k to v
            }
            .toMap()

    private fun randomUrlSafe(bytes: Int): String {
        val raw = ByteArray(bytes)
        SecureRandom().nextBytes(raw)
        return base64Url(raw)
    }

    private fun base64Url(raw: ByteArray): String =
        Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
