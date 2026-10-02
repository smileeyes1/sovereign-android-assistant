package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * نسخة حالة محمولة محدودة وغير سرية.
 *
 * الهدف هو نقل إعدادات تشغيل منخفضة الحساسية دون نقل هوية الجهاز أو أسرار
 * AndroidKeyStore أو بيانات الاقتران أو المحادثات أو محتوى المهام.
 */
object HakimPortableState {
    const val VERSION = "HAKIM-PORTABLE-NONSECRET-STATE-2026-10-02-v1"
    private const val SCHEMA_VERSION = 1
    private const val MAX_BYTES = 128 * 1024

    data class ExportReport(
        val fingerprint: String,
        val preferenceCount: Int
    )

    data class ImportReport(
        val applied: Int,
        val ignored: Int,
        val fingerprint: String
    )

    private enum class ValueType { BOOL, INT, LONG, STRING }

    private val SAFE_PREF_KEYS: Map<String, Map<String, ValueType>> = mapOf(
        "hakim_cost_policy" to mapOf(
            "free_only" to ValueType.BOOL,
            "gemini_free_tier_confirmed" to ValueType.BOOL
        ),
        "hakim_router" to mapOf(
            "provider_chatgpt_score" to ValueType.INT,
            "provider_gemini_score" to ValueType.INT,
            "provider_claude_score" to ValueType.INT,
            "provider_deepseek_score" to ValueType.INT,
            "last_provider" to ValueType.STRING,
            "last_provider_at" to ValueType.LONG
        )
    )

    private val PROVIDERS = setOf("chatgpt", "gemini", "claude", "deepseek")

    fun exportToUri(context: Context, uri: Uri): ExportReport {
        val snapshot = buildSnapshot(context)
        val raw = snapshot.toString(2)
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "portable_state_too_large" }
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("portable_state_output_unavailable")
        out.bufferedWriter(Charsets.UTF_8).use { it.write(raw) }
        return ExportReport(
            fingerprint = snapshot.getString("fingerprint_sha256"),
            preferenceCount = countValues(snapshot.getJSONObject("preferences"))
        )
    }

    fun importFromUri(context: Context, uri: Uri): ImportReport {
        val raw = readBounded(context, uri)
        val root = JSONObject(raw)
        require(root.optInt("schema_version", -1) == SCHEMA_VERSION) { "portable_state_schema_unsupported" }
        require(root.optString("format") == VERSION) { "portable_state_format_unsupported" }
        require(root.optString("package") == context.packageName) { "portable_state_package_mismatch" }

        val prefs = root.optJSONObject("preferences")
            ?: error("portable_state_preferences_missing")
        validateShape(prefs)

        val expected = root.optString("fingerprint_sha256")
        require(expected.matches(Regex("^[0-9a-f]{64}$"))) { "portable_state_fingerprint_missing" }
        val actual = fingerprint(prefs)
        require(expected == actual) { "portable_state_fingerprint_mismatch" }

        var applied = 0
        var ignored = 0

        for ((prefName, keyTypes) in SAFE_PREF_KEYS) {
            val source = prefs.optJSONObject(prefName) ?: continue
            val target = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val edit = target.edit()

            for ((key, type) in keyTypes) {
                if (!source.has(key)) continue
                when (type) {
                    ValueType.BOOL -> {
                        val value = source.opt(key)
                        if (value !is Boolean) {
                            ignored += 1
                            continue
                        }
                        when (key) {
                            "free_only" -> {
                                // الاستعادة لا يجوز أن توسع الإنفاق أو تحول السياسة من مجاني إلى مدفوع.
                                edit.putBoolean(key, true)
                                applied += 1
                            }
                            "gemini_free_tier_confirmed" -> {
                                // التأكيد لا يُنقل إلى جهاز لا يملك اعتماد Gemini محليًا.
                                val localCredential = HakimSecretStore.has(
                                    context,
                                    GeminiDirectEngine.SECRET_GEMINI_KEY
                                )
                                edit.putBoolean(key, value && localCredential)
                                applied += 1
                            }
                            else -> {
                                edit.putBoolean(key, value)
                                applied += 1
                            }
                        }
                    }
                    ValueType.INT -> {
                        val value = source.opt(key)
                        val n = (value as? Number)?.toInt()
                        if (n == null || n !in -20..40) {
                            ignored += 1
                            continue
                        }
                        edit.putInt(key, n)
                        applied += 1
                    }
                    ValueType.LONG -> {
                        val value = source.opt(key)
                        val n = (value as? Number)?.toLong()
                        val maxReasonable = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
                        if (n == null || n < 0L || n > maxReasonable) {
                            ignored += 1
                            continue
                        }
                        edit.putLong(key, n)
                        applied += 1
                    }
                    ValueType.STRING -> {
                        val value = source.optString(key, "")
                        if (key == "last_provider" && value !in PROVIDERS) {
                            ignored += 1
                            continue
                        }
                        edit.putString(key, value.take(64))
                        applied += 1
                    }
                }
            }
            edit.apply()
        }

        // السياسة المالية الآمنة هي آخر كتابة حتى لو كانت النسخة قديمة أو معدلة.
        HakimFreePolicy.setFreeOnly(context, true)

        return ImportReport(applied = applied, ignored = ignored, fingerprint = actual)
    }

    fun status(context: Context): JSONObject {
        val snapshot = buildSnapshot(context)
        return JSONObject()
            .put("portable_state", true)
            .put("version", VERSION)
            .put("schema_version", SCHEMA_VERSION)
            .put("nonsecret_only", true)
            .put("device_bound_secrets_exported", false)
            .put("pairing_exported", false)
            .put("conversation_exported", false)
            .put("task_content_exported", false)
            .put("free_only_enforced_on_restore", true)
            .put("fingerprint_sha256", snapshot.getString("fingerprint_sha256"))
    }

    private fun buildSnapshot(context: Context): JSONObject {
        val prefsRoot = JSONObject()
        for ((prefName, keyTypes) in SAFE_PREF_KEYS.toSortedMap()) {
            val source = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).all
            val out = JSONObject()
            for ((key, type) in keyTypes.toSortedMap()) {
                val value = source[key] ?: continue
                if (matchesType(value, type)) out.put(key, value)
            }
            prefsRoot.put(prefName, out)
        }

        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("format", VERSION)
            .put("package", context.packageName)
            .put("exported_at_ms", System.currentTimeMillis())
            .put("scope", JSONObject()
                .put("nonsecret_only", true)
                .put("device_bound_secrets", false)
                .put("pairing", false)
                .put("conversation_content", false)
                .put("task_content", false)
            )
            .put("preferences", prefsRoot)
            .put("fingerprint_sha256", fingerprint(prefsRoot))
    }

    private fun validateShape(prefs: JSONObject) {
        val prefNames = prefs.keys().asSequence().toSet()
        require(prefNames.all { it in SAFE_PREF_KEYS }) { "portable_state_unknown_preference" }
        for (prefName in prefNames) {
            val source = prefs.optJSONObject(prefName)
                ?: error("portable_state_preference_not_object")
            val allowed = SAFE_PREF_KEYS.getValue(prefName)
            val keys = source.keys().asSequence().toSet()
            require(keys.all { it in allowed }) { "portable_state_unknown_key" }
        }
    }

    private fun fingerprint(prefs: JSONObject): String {
        val canonical = buildString {
            for ((prefName, keyTypes) in SAFE_PREF_KEYS.toSortedMap()) {
                val source = prefs.optJSONObject(prefName) ?: JSONObject()
                for ((key, type) in keyTypes.toSortedMap()) {
                    if (!source.has(key)) continue
                    val value = source.opt(key)
                    append(prefName).append('|')
                    append(key).append('|')
                    append(type.name).append('|')
                    append(value?.toString().orEmpty()).append('\n')
                }
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun matchesType(value: Any, type: ValueType): Boolean = when (type) {
        ValueType.BOOL -> value is Boolean
        ValueType.INT -> value is Int
        ValueType.LONG -> value is Long
        ValueType.STRING -> value is String
    }

    private fun countValues(prefs: JSONObject): Int {
        var count = 0
        val names = prefs.keys()
        while (names.hasNext()) {
            val obj = prefs.optJSONObject(names.next()) ?: continue
            count += obj.length()
        }
        return count
    }

    private fun readBounded(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("portable_state_input_unavailable")
        val output = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_BYTES) { "portable_state_too_large" }
                output.write(buffer, 0, n)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
