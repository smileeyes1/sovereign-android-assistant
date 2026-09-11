package ps.hakim.phoneagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object HakimRuleLedger {
    private const val KEY_ALIAS = "hakim_rule_ledger_v1"
    private const val FILE_NAME = "hakim-rule-ledger.enc"
    private const val PREFS = "hakim_rule_ledger_meta"
    private const val MAX_CONTEXT_RULES = 12
    private const val MAX_CONTEXT_CHARS = 3600

    fun capture(context: Context, raw: String, source: String = "command_center") {
        val text = raw.trim()
        if (text.isBlank()) return

        val hash = sha256(text)
        val meta = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (meta.getString("last_hash", "") == hash && now - meta.getLong("last_at", 0L) < 300_000L) return

        val category = classify(text)
        val event = JSONObject()
            .put("time", now)
            .put("source", source.take(40))
            .put("category", category)
            .put("sha256", hash)
            .put("text", text.take(12_000))

        appendEncrypted(context, event.toString())
        meta.edit()
            .putString("last_hash", hash)
            .putLong("last_at", now)
            .putString("last_category", category)
            .apply()
    }

    fun recentRuleContext(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return ""
        val lines = try { file.readLines(Charsets.UTF_8) } catch (_: Exception) { return "" }
        val rules = mutableListOf<String>()
        for (line in lines.asReversed()) {
            if (rules.size >= MAX_CONTEXT_RULES) break
            val plain = decryptLine(line) ?: continue
            val obj = try { JSONObject(plain) } catch (_: Exception) { continue }
            val category = obj.optString("category")
            if (category != "rule" && category != "correction" && category != "preference") continue
            val text = obj.optString("text").trim()
            if (text.isBlank()) continue
            rules += "• $text"
        }
        if (rules.isEmpty()) return ""
        val ordered = rules.asReversed().joinToString("\n")
        return ordered.takeLast(MAX_CONTEXT_CHARS)
    }

    fun status(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        val meta = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("encrypted_local_ledger", true)
            .put("entries_file_exists", file.exists())
            .put("bytes", if (file.exists()) file.length() else 0L)
            .put("last_at", meta.getLong("last_at", 0L))
            .put("last_category", meta.getString("last_category", ""))
            .put("precedence", "الأحدث الصريح يعلو عند التعارض، والتصحيح يعلو على السابق، والمهمة المؤقتة لا تصبح قاعدة عامة")
    }

    private fun classify(text: String): String {
        val s = text.lowercase()
        val correction = listOf("صحح", "صحّح", "استبدل", "بدل", "بدّل", "الغ", "ألغ", "إلغاء", "لا تعتمد", "عدّل القاعدة", "غيّر القاعدة")
        if (correction.any { s.contains(it) }) return "correction"

        val rule = listOf("ثبت", "ثبّت", "قاعدة", "دستور", "افتراضيا", "افتراضيًا", "دائما", "دائمًا", "من الآن", "كقاعدة", "اجعلها افتراضية", "اجعله افتراضي", "كل ما اقوله", "كل ما أقوله")
        if (rule.any { s.contains(it) }) return "rule"

        val preference = listOf("أفضل", "افضل", "أفضّل", "افضل دائما", "اريد عادة", "أريد عادة")
        if (preference.any { s.contains(it) }) return "preference"

        return "task"
    }

    private fun appendEncrypted(context: Context, plain: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(encrypted, 0, packed, iv.size, encrypted.size)
            File(context.filesDir, FILE_NAME).appendText(Base64.encodeToString(packed, Base64.NO_WRAP) + "\n", Charsets.UTF_8)
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("last_write_failed", true).apply()
        }
    }

    private fun decryptLine(line: String): String? {
        return try {
            val packed = Base64.decode(line.trim(), Base64.NO_WRAP)
            if (packed.size <= 12) {
                null
            } else {
                val iv = packed.copyOfRange(0, 12)
                val encrypted = packed.copyOfRange(12, packed.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
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

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
