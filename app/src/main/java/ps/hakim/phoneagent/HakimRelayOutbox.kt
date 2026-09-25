package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable store-and-forward queue for encrypted relay result carriers.
 *
 * Only already-encrypted HR1 carriers are persisted. The queue is private to
 * the app, bounded, TTL-limited and flushed oldest-first when validated
 * connectivity returns.
 */
object HakimRelayOutbox {
    private const val DIR_NAME = "hakim_relay_outbox"
    private const val MAX_ITEMS = 64
    private const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L
    private const val TTL_MS = 24L * 60L * 60L * 1000L
    private val flushing = AtomicBoolean(false)

    data class Item(
        val file: File,
        val topic: String,
        val carrier: String,
        val createdAt: Long
    )

    fun enqueue(context: Context, topic: String, carrier: String): Boolean {
        if (topic.isBlank() || carrier.isBlank() || !carrier.startsWith("HR1.")) return false
        val dir = dir(context)
        cleanup(dir)

        val now = System.currentTimeMillis()
        val id = sha256("$topic\n$carrier").take(32)
        val file = File(dir, "$now-$id.json")
        if (file.exists()) return true

        val obj = JSONObject()
            .put("topic", topic)
            .put("carrier", carrier)
            .put("created_at", now)

        return runCatching {
            file.writeText(obj.toString(), Charsets.UTF_8)
            cleanup(dir)
            true
        }.getOrDefault(false)
    }

    fun pendingCount(context: Context): Int {
        val d = dir(context)
        cleanup(d)
        return d.listFiles()?.count { it.isFile && it.extension == "json" } ?: 0
    }

    fun flush(
        context: Context,
        sender: (topic: String, carrier: String) -> Boolean
    ): Int {
        if (!flushing.compareAndSet(false, true)) return 0
        try {
            val d = dir(context)
            cleanup(d)
            var sent = 0
            for (file in d.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }) {
                val item = read(file) ?: run {
                    file.delete()
                    continue
                }
                if (!sender(item.topic, item.carrier)) break
                if (file.delete()) sent++
            }
            return sent
        } finally {
            flushing.set(false)
        }
    }

    fun status(context: Context): JSONObject {
        val d = dir(context)
        cleanup(d)
        val files = d.listFiles().orEmpty().filter { it.isFile }
        return JSONObject()
            .put("store_and_forward", true)
            .put("encrypted_carriers_only", true)
            .put("pending", files.size)
            .put("bytes", files.sumOf { it.length() })
            .put("max_items", MAX_ITEMS)
            .put("max_bytes", MAX_TOTAL_BYTES)
            .put("ttl_ms", TTL_MS)
    }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun read(file: File): Item? = runCatching {
        val obj = JSONObject(file.readText(Charsets.UTF_8))
        val topic = obj.optString("topic")
        val carrier = obj.optString("carrier")
        val created = obj.optLong("created_at", 0L)
        if (topic.isBlank() || !carrier.startsWith("HR1.") || created <= 0L) null
        else Item(file, topic, carrier, created)
    }.getOrNull()

    private fun cleanup(dir: File) {
        val now = System.currentTimeMillis()
        var files = dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.toMutableList()

        for (file in files.toList()) {
            val item = read(file)
            if (item == null || now - item.createdAt > TTL_MS) {
                file.delete()
                files.remove(file)
            }
        }

        while (files.size > MAX_ITEMS) {
            files.removeAt(0).delete()
        }
        while (files.sumOf { it.length() } > MAX_TOTAL_BYTES && files.isNotEmpty()) {
            files.removeAt(0).delete()
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
