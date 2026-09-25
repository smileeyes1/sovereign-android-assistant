package ps.hakim.phoneagent

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Narrow boundary between Android URI grants and Hakim's verified intake.
 *
 * It never asks for broad storage access. User-selected content is accepted only
 * through content:// URIs and is deduplicated before it reaches the verifier.
 */
object HakimAttachmentGateway {
    const val MAX_ATTACHMENTS_PER_TASK = 20

    data class Attachment(
        val uri: Uri,
        val mimeType: String,
        val displayName: String,
        val sizeBytes: Long?
    )

    /** Legacy/document fallback kept for callers outside CommandCenterActivity. */
    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun fromUris(
        context: Context,
        sourceUris: Collection<Uri>,
        persistReadAccess: Boolean
    ): List<Attachment> {
        val uris = LinkedHashSet<Uri>()
        sourceUris.forEach { uri ->
            if (uris.size < MAX_ATTACHMENTS_PER_TASK && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                uris += uri
            }
        }

        if (persistReadAccess) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers grant transient access only. VerifiedIntake mirrors
                    // the bytes immediately when possible, so no silent persistence claim is made.
                }
            }
        }

        return uris.mapNotNull { inspect(context, it) }
    }

    fun fromResult(context: Context, data: Intent?): List<Attachment> {
        if (data == null) return emptyList()
        val uris = linkedSetOf<Uri>()
        data.data?.let { uris += it }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris += it }
            }
        }
        return fromUris(context, uris, persistReadAccess = true)
    }

    fun fromInboundShare(context: Context, intent: Intent?): List<Attachment> {
        val i = intent ?: return emptyList()
        val uris = linkedSetOf<Uri>()
        if (i.action == Intent.ACTION_SEND) {
            streamUri(i)?.let { uris += it }
        }
        if (i.action == Intent.ACTION_SEND_MULTIPLE) {
            streamUris(i).forEach { uris += it }
        }
        i.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { uris += it }
            }
        }
        return fromUris(context, uris, persistReadAccess = false)
    }

    fun buildShareIntent(
        context: Context,
        text: String,
        attachments: List<Attachment>
    ): Intent {
        if (attachments.isEmpty()) {
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }

        val bounded = attachments.take(MAX_ATTACHMENTS_PER_TASK)
        val uris = ArrayList(bounded.map { it.uri })
        val commonType = commonMimeType(bounded)
        val action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        return Intent(action).apply {
            type = commonType
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Hakim attachment", uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
    }

    fun summary(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return "لا توجد مرفقات"
        val names = attachments.take(3).joinToString("، ") { it.displayName }
        val suffix = if (attachments.size > 3) " +" + (attachments.size - 3) else ""
        return attachments.size.toString() + " مرفق: " + names + suffix
    }

    private fun inspect(context: Context, uri: Uri): Attachment? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.trim()?.lowercase().orEmpty().ifBlank { "*/*" }
            var name = uri.lastPathSegment ?: "مرفق"
            var size: Long? = null
            val cursor: Cursor? = resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) name = c.getString(nameIndex) ?: name
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !c.isNull(sizeIndex)) {
                        c.getLong(sizeIndex).takeIf { it >= 0L }?.let { size = it }
                    }
                }
            }
            Attachment(uri = uri, mimeType = mime, displayName = name.take(240), sizeBytes = size)
        } catch (_: Exception) {
            null
        }
    }

    private fun commonMimeType(attachments: List<Attachment>): String {
        val types = attachments.map { it.mimeType }.filter { it.isNotBlank() && it != "*/*" }
        if (types.isEmpty()) return "*/*"
        if (types.distinct().size == 1) return types.first()
        val families = types.map { it.substringBefore("/", "") }.filter { it.isNotBlank() }
        if (families.isNotEmpty() && families.distinct().size == 1) return families.first() + "/*"
        return "*/*"
    }

    @Suppress("DEPRECATION")
    private fun streamUri(intent: Intent): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    @Suppress("DEPRECATION")
    private fun streamUris(intent: Intent): List<Uri> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }
}
