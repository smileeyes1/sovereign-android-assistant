package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.util.Base64
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * One-click OpenRouter PKCE onboarding for the free in-app engine.
 *
 * The only unavoidable user action is logging in / approving at OpenRouter once.
 * The verifier, authorization code and returned key are never logged.
 */
object OpenRouterOAuthManager {
    private const val AUTH_URL = "https://openrouter.ai/auth"
    private const val EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
    private const val PREFS = "hakim_openrouter_oauth"
    private const val KEY_PENDING_PROMPT = "pending_prompt"
    private const val TIMEOUT_MS = 180_000

    @Volatile private var activeServer: ServerSocket? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun start(activity: Activity, pendingPrompt: String? = null) {
        if (HakimSecretStore.has(activity, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)) {
            HakimUserResourcePolicy.markUserOwned(activity, OpenRouterFreeEngine.ID)
            activity.runOnUiThread {
                Toast.makeText(activity, "الذكاء المجاني مرتبط بالفعل.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        runCatching { activeServer?.close() }
        activeServer = null

        val verifier = randomUrlSafe(48)
        val challenge = sha256Base64Url(verifier)
        val state = randomUrlSafe(24)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = TIMEOUT_MS
        }
        activeServer = server
        val callback = "http://127.0.0.1:" + server.localPort + "/callback"

        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().apply {
            if (pendingPrompt.isNullOrBlank()) remove(KEY_PENDING_PROMPT)
            else putString(KEY_PENDING_PROMPT, pendingPrompt.take(4000))
            putLong("started_at", System.currentTimeMillis())
            apply()
        }

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("callback_url", callback)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()

        Thread {
            var status = "failed"
            try {
                server.use { listener ->
                    val socket = listener.accept()
                    socket.use { clientSocket ->
                        clientSocket.soTimeout = 15_000
                        val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
                        val requestLine = reader.readLine().orEmpty()
                        val target = requestLine.substringAfter("GET ", "").substringBefore(" HTTP/", "")
                        val callbackUri = Uri.parse("http://127.0.0.1" + target)
                        val returnedState = callbackUri.getQueryParameter("state").orEmpty()
                        val code = callbackUri.getQueryParameter("code").orEmpty()

                        status = if (returnedState != state || code.isBlank()) {
                            "invalid"
                        } else {
                            val key = exchange(code, verifier)
                            if (key.isNullOrBlank()) {
                                "exchange_failed"
                            } else {
                                HakimSecretStore.put(activity, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY, key)
                                HakimUserResourcePolicy.markUserOwned(activity, OpenRouterFreeEngine.ID)
                                HakimUserResourcePolicy.enforce(activity)
                                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("connected", true)
                                    .putLong("connected_at", System.currentTimeMillis())
                                    .apply()
                                "success"
                            }
                        }

                        val html = """<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>حكيم</title><body style="font-family:sans-serif;text-align:center;padding:3rem"><h2>حكيم</h2><p>${if (status == "success") "تم ربط الذكاء المجاني. جارٍ الرجوع إلى حكيم…" else "تعذر إكمال الربط. ارجع إلى حكيم وأعد المحاولة."}</p><p><a href="hakim://openrouter-connected?status=$status">الرجوع إلى حكيم</a></p><script>setTimeout(function(){location.href="hakim://openrouter-connected?status=$status"},300);</script></body></html>"""
                        val bytes = html.toByteArray(Charsets.UTF_8)
                        val out = clientSocket.getOutputStream()
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.size + "\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                        out.write(bytes)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                status = "failed"
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                    .edit().putString("last_error", "oauth_callback_failed").apply()
            } finally {
                activeServer = null
                if (status != "success") {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "تعذر ربط الذكاء المجاني. أعد المحاولة من إدارة حكيم.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()

        if (!launchExternalBrowser(activity, authUri)) {
            runCatching { server.close() }
            activeServer = null
            Toast.makeText(
                activity,
                "تعذر فتح متصفح خارجي آمن لإكمال الربط.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun launchExternalBrowser(activity: Activity, authUri: Uri): Boolean {
        val base = Intent(Intent.ACTION_VIEW, authUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val candidates = activity.packageManager.queryIntentActivities(
            base,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val external = candidates.firstOrNull {
            it.activityInfo.packageName != activity.packageName
        }

        if (external != null) {
            return runCatching {
                activity.startActivity(
                    Intent(base).apply {
                        setPackage(external.activityInfo.packageName)
                    }
                )
                true
            }.getOrDefault(false)
        }

        val browserSelector = Intent.makeMainSelectorActivity(
            Intent.ACTION_MAIN,
            Intent.CATEGORY_APP_BROWSER
        ).apply {
            data = authUri
        }

        return runCatching {
            activity.startActivity(browserSelector)
            true
        }.getOrDefault(false)
    }

    fun takePendingPrompt(context: android.content.Context): String? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_PENDING_PROMPT, null)
        prefs.edit().remove(KEY_PENDING_PROMPT).apply()
        return value
    }

    fun hasPendingPrompt(context: android.content.Context): Boolean =
        !context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_PENDING_PROMPT, null).isNullOrBlank()

    private fun exchange(code: String, verifier: String): String? {
        val body = JSONObject()
            .put("code", code)
            .put("code_verifier", verifier)
            .put("code_challenge_method", "S256")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(EXCHANGE_URL)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                JSONObject(raw).optString("key").takeIf { it.startsWith("sk-or-") }
            }
        }.getOrNull()
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
