package ps.hakim.phoneagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * فهرس محلي مشفر لعناوين وروابط محادثات ChatGPT التي ظهرت فعليًا في واجهة الحساب.
 * لا يخزن نصوص المحادثات ولا كلمات المرور ولا رموز التحقق.
 */
object HakimChatGptIndex {
    private const val PREFS = "hakim_chatgpt_index"
    private const val FIELD = "entries_ciphertext"
    private const val KEY_ALIAS = "hakim_chatgpt_index_v1"

    fun merge(context: Context, visible: JSONArray): JSONObject {
        val map = LinkedHashMap<String, JSONObject>()
        val current = load(context)
        for (i in 0 until current.length()) {
            val e = current.optJSONObject(i) ?: continue
            val href = e.optString("href")
            if (validHref(href)) map[href] = e
        }
        val now = System.currentTimeMillis()
        for (i in 0 until visible.length()) {
            val e = visible.optJSONObject(i) ?: continue
            val href = e.optString("href").trim()
            val title = e.optString("title").trim().replace(Regex("\\s+"), " ").take(180)
            if (!validHref(href) || title.isBlank()) continue
            map[href] = JSONObject()
                .put("title", title)
                .put("href", href)
                .put("last_seen_ms", now)
        }
        val out = JSONArray()
        map.values.toList().takeLast(1500).forEach { out.put(it) }
        save(context, out)
        return JSONObject().put("ok", true).put("count", out.length())
    }

    fun load(context: Context): JSONArray {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(FIELD, null) ?: return JSONArray()
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= 12) return@runCatching JSONArray()
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            JSONArray(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        }.getOrElse { JSONArray() }
    }

    fun search(context: Context, query: String): JSONArray {
        val q = query.trim()
        val out = JSONArray()
        if (q.isBlank()) return out
        val all = load(context)
        for (i in 0 until all.length()) {
            val e = all.optJSONObject(i) ?: continue
            if (e.optString("title").contains(q, ignoreCase = true)) out.put(e)
        }
        return out
    }

    fun resolveHref(context: Context, titleOrQuery: String): String? {
        val q = titleOrQuery.trim()
        if (validHref(q)) return q
        if (q.isBlank()) return null
        val all = load(context)
        var contains: String? = null
        for (i in 0 until all.length()) {
            val e = all.optJSONObject(i) ?: continue
            val title = e.optString("title")
            val href = e.optString("href").takeIf(::validHref)
            if (title.equals(q, ignoreCase = true)) return href
            if (contains == null && title.contains(q, ignoreCase = true)) contains = href
        }
        return contains
    }

    private fun save(context: Context, array: JSONArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(FIELD, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    private fun validHref(raw: String): Boolean = runCatching {
        val u = android.net.Uri.parse(raw)
        u.scheme.equals("https", true) &&
            (u.host.equals("chatgpt.com", true) || u.host.equals("www.chatgpt.com", true)) &&
            Regex("(^|/)c/[^/?#]+").containsMatchIn(u.path.orEmpty())
    }.getOrDefault(false)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
}
