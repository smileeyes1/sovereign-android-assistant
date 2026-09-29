package ps.hakim.phoneagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * فهرس محلي مشفر لعناوين محادثات ChatGPT التي ظهرت فعليًا فقط.
 * لا يخزن نصوص المحادثات أو كلمات المرور أو رموز التحقق.
 */
object HakimChatGptIndex {
    private const val PREFS = "hakim_chatgpt_index"
    private const val KEY_ALIAS = "hakim_chatgpt_index_v1"
    private const val FIELD = "titles_ciphertext"

    fun storeVisibleTitles(context: Context, titles: JSONArray): Int {
        val cleaned = JSONArray()
        val seen = LinkedHashSet<String>()
        for (i in 0 until titles.length()) {
            val value = titles.optString(i).trim().take(160)
            if (value.isNotBlank() && seen.add(value)) cleaned.put(value)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(cleaned.toString().toByteArray(Charsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(FIELD, encoded)
            .apply()
        return cleaned.length()
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
