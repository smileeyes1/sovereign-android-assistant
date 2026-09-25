package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import org.json.JSONArray

/**
 * Persists only URI references for user-selected attachments.
 * No attachment bytes or extracted content are written here.
 * Invalid/revoked grants are silently dropped on restore.
 */
object HakimAttachmentSessionStore {
    private const val PREFS = "hakim_attachment_session"
    private const val KEY_URIS = "uris"

    fun save(context: Context, attachments: List<HakimAttachmentGateway.Attachment>) {
        val array = JSONArray()
        attachments
            .take(HakimAttachmentGateway.MAX_ATTACHMENTS_PER_TASK)
            .map { it.uri }
            .distinct()
            .filter { it.scheme == "content" }
            .forEach { array.put(it.toString()) }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URIS, array.toString())
            .apply()
    }

    fun restore(context: Context): List<HakimAttachmentGateway.Attachment> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URIS, "[]")
            .orEmpty()
        val uris = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(array.length(), HakimAttachmentGateway.MAX_ATTACHMENTS_PER_TASK)) {
                    val value = array.optString(i).trim()
                    if (value.startsWith("content://")) add(Uri.parse(value))
                }
            }
        }.getOrDefault(emptyList())

        val restored = HakimAttachmentGateway.fromUris(
            context,
            uris,
            persistReadAccess = false
        )

        // Rewrite the store so revoked/transient grants do not linger.
        save(context, restored)
        return restored
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
