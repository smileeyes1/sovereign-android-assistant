package ps.hakim.phoneagent

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** سجل محلي محدود للتنزيلات؛ لا يحفظ الكوكيز أو ترويسات الاعتماد. */
object HakimBrowserDownloadLedger {
    const val VERSION = "BROWSER-DOWNLOAD-LEDGER-2026-09-16-v1"
    private const val PREFS = "hakim_browser_downloads"
    private const val KEY = "entries"
    private const val MAX = 30

    fun recordStart(context: Context, id: Long, url: String, filename: String) {
        val arr = read(context)
        arr.put(JSONObject()
            .put("id", id)
            .put("host", runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault(""))
            .put("filename", filename.take(180))
            .put("started_at", System.currentTimeMillis())
            .put("status", "PENDING"))
        write(context, arr)
    }

    fun refresh(context: Context): JSONArray {
        val dm = context.getSystemService(DownloadManager::class.java) ?: return read(context)
        val old = read(context); val updated = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            val id = item.optLong("id", -1L)
            if (id <= 0) continue
            var cursor: Cursor? = null
            runCatching {
                cursor = dm.query(DownloadManager.Query().setFilterById(id))
                if (cursor?.moveToFirst() == true) {
                    val status = cursor!!.getInt(cursor!!.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor!!.getLong(cursor!!.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = cursor!!.getLong(cursor!!.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val localUri = cursor!!.getString(cursor!!.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)).orEmpty()
                    item.put("status", when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> "SUCCESSFUL"
                        DownloadManager.STATUS_FAILED -> "FAILED"
                        DownloadManager.STATUS_PAUSED -> "PAUSED"
                        DownloadManager.STATUS_RUNNING -> "RUNNING"
                        else -> "PENDING"
                    }).put("bytes", done).put("total_bytes", total)
                        .put("local_uri", if (status == DownloadManager.STATUS_SUCCESSFUL) localUri.take(500) else "")
                        .put("checked_at", System.currentTimeMillis())
                }
            }
            cursor?.close()
            updated.put(item)
        }
        write(context, updated)
        return updated
    }

    fun latest(context: Context): JSONObject? {
        val arr = refresh(context)
        return if (arr.length() == 0) null else arr.optJSONObject(arr.length() - 1)
    }

    private fun read(context: Context): JSONArray = runCatching {
        JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
    }.getOrDefault(JSONArray())

    private fun write(context: Context, arr: JSONArray) {
        val compact = JSONArray(); val start = (arr.length() - MAX).coerceAtLeast(0)
        for (i in start until arr.length()) compact.put(arr.optJSONObject(i))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, compact.toString()).apply()
    }

    fun status(context: Context): JSONObject {
        val arr = refresh(context)
        return JSONObject().put("version", VERSION).put("tracked", arr.length())
            .put("cookies_persisted", false).put("authorization_headers_persisted", false)
            .put("latest", if (arr.length() > 0) arr.optJSONObject(arr.length()-1) else JSONObject.NULL)
    }
}