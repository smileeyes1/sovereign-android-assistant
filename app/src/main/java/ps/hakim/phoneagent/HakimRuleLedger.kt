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

/**
 * Minimal encrypted rule ledger.
 *
 * Temporary task text is never persisted. Only explicit rules, corrections and
 * preferences are stored, after secret redaction and size bounding.
 */
object HakimRuleLedger {
    private const val KEY_ALIAS = "hakim_rule_ledger_v1"
    private const val FILE_NAME = "hakim-rule-ledger.enc"
    private const val PREFS = "hakim_rule_ledger_meta"
    private const val MAX_CONTEXT_RULES = 12
    private const val MAX_CONTEXT_CHARS = 3600
    private const val MAX_STORED_RULE_CHARS = 2400
    private const val MAX_LEDGER_LINES = 200
    private const val MAX_LEDGER_BYTES = 512L * 1024L

    fun capture(context: Context, raw: String, source: String = "command_center") {
        val text = raw.trim()
        if (text.isBlank()) return

        val hash = sha256(text)
        val meta = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (meta.getString("last_hash", "") == hash && now - meta.getLong("last_at", 0L) < 300_000L) return

        val category = classify(text)
        meta.edit()
            .putString("last_hash", hash)
            .putLong("last_at", now)
            .putString("last_category", category)
            .apply()

        // A temporary task is intentionally not a persistent memory/rule.
        if (category == "task") {
            meta.edit().putLong("last_task_seen_at", now).apply()
            return
        }

        val minimized = minimizeRuleText(text)
        if (minimized.isBlank()) {
            meta.edit().putBoolean("last_rule_suppressed_sensitive", true).apply()
            return
        }

        val event = JSONObject()
            .put("time", now)
            .put("source", source.take(40))
            .put("category", category)
            .put("sha256", hash)
            .put("text", minimized.take(MAX_STORED_RULE_CHARS))

        appendEncrypted(context, event.toString())
        compact(context)
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
            if (category !in setOf("rule", "correction", "preference")) continue
            val text = obj.optString("text").trim()
            if (text.isBlank()) continue
            rules += "• $text"
        }
        if (rules.isEmpty()) return ""
        return rules.asReversed().joinToString("\n").takeLast(MAX_CONTEXT_CHARS)
    }

    fun status(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        val meta = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("encrypted_local_ledger", true)
            .put("temporary_tasks_persisted", false)
            .put("secret_redaction_enabled", true)
            .put("entries_file_exists", file.exists())
            .put("bytes", if (file.exists()) file.length() else 0L)
            .put("max_bytes", MAX_LEDGER_BYTES)
            .put("max_lines", MAX_LEDGER_LINES)
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

    private fun minimizeRuleText(raw: String): String {
        var s = raw.trim().take(MAX_STORED_RULE_CHARS)

        // Secret-like assignments in Arabic/English.
        s = s.replace(
            Regex(
                "(?i)(password|passcode|secret|token|api[_ -]?key|access[_ -]?key|كلمة\\s*المرور|رمز\\s*الدخول|رمز\\s*التحقق|مفتاح\\s*(?:api|واجهة|الوصول))\\s*[:=]\\s*[^\\s,;]+"
            ),
            "\$1=[محجوب]"
        )

        // Common API/token shapes and long opaque credentials.
        s = s.replace(Regex("(?i)\\b(?:sk|pk|rk)-[A-Za-z0-9_-]{16,}\\b"), "[سر محجوب]")
        s = s.replace(Regex("\\bAIza[0-9A-Za-z_-]{20,}\\b"), "[سر محجوب]")
        s = s.replace(Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{16,}=*"), "Bearer [محجوب]")
        s = s.replace(Regex("\\b[0-9a-fA-F]{40,}\\b"), "[قيمة حساسة محجوبة]")
        s = s.replace(Regex("\\b[A-Za-z0-9+/]{64,}={0,2}\\b"), "[قيمة حساسة محجوبة]")

        // Remove sensitive query values while retaining the rule context.
        s = s.replace(
            Regex("(?i)([?&](?:token|key|secret|password|code)=)[^&#\\s]+"),
            "\$1[محجوب]"
        )

        return s.replace(Regex("\\s+"), " ").trim()
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
            File(context.filesDir, FILE_NAME).appendText(
                Base64.encodeToString(packed, Base64.NO_WRAP) + "\n",
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("last_write_failed", true).apply()
        }
    }

    private fun compact(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: return
        if (lines.size <= MAX_LEDGER_LINES && file.length() <= MAX_LEDGER_BYTES) return

        val kept = lines.takeLast(MAX_LEDGER_LINES).toMutableList()
        while (kept.isNotEmpty() && kept.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1L } > MAX_LEDGER_BYTES) {
            kept.removeAt(0)
        }
        runCatching {
            file.writeText(
                if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"),
                Charsets.UTF_8
            )
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
