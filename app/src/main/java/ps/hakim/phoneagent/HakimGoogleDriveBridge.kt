package ps.hakim.phoneagent

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Thin Drive v3 bridge. It receives a short-lived user OAuth token and never owns credentials.
 * Multipart upload can preserve a binary file or import DOCX/PPTX/XLSX into Docs/Slides/Sheets.
 */
object HakimGoogleDriveBridge {
    const val VERSION = "GOOGLE-DRIVE-BRIDGE-2026-09-24-v1"
    private const val MAX_UPLOAD_BYTES = 25L * 1024L * 1024L

    data class Uploaded(
        val id: String,
        val name: String,
        val mimeType: String,
        val webViewLink: String?
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun ping(accessToken: String): Result<Unit> = runCatching {
        require(accessToken.isNotBlank())
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?pageSize=1&spaces=drive&fields=files(id)")
            .header("Authorization", "Bearer " + accessToken)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Google Drive HTTP " + response.code }
        }
    }

    fun upload(
        context: Context,
        accessToken: String,
        created: HakimLocalArtifactFactory.Created,
        target: HakimGoogleWorkspaceIntent.Target
    ): Result<Uploaded> = runCatching {
        require(accessToken.isNotBlank())
        val bytes = readCreated(context, created)
        require(bytes.isNotEmpty()) { "الملف المحلي فارغ." }
        require(bytes.size.toLong() <= MAX_UPLOAD_BYTES) { "الملف أكبر من حد الرفع المباشر الآمن." }

        val metadata = JSONObject()
        val nativeMime = target.googleMimeType
        if (nativeMime == null) {
            metadata.put("name", created.displayName)
        } else {
            metadata.put("name", created.displayName.substringBeforeLast('.'))
            metadata.put("mimeType", nativeMime)
        }

        val multipart = MultipartBody.Builder("hakim-drive-boundary-" + System.nanoTime())
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(bytes.toRequestBody(created.kind.toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,webViewLink")
            .header("Authorization", "Bearer " + accessToken)
            .post(multipart)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { "Google Drive HTTP " + response.code }
            val json = JSONObject(raw)
            val id = json.optString("id")
            require(id.isNotBlank()) { "لم تُرجع Google معرّف الملف." }
            Uploaded(
                id = id,
                name = json.optString("name").ifBlank { created.displayName },
                mimeType = json.optString("mimeType").ifBlank { nativeMime ?: created.kind },
                webViewLink = json.optString("webViewLink").takeIf { it.isNotBlank() }
            )
        }
    }

    private fun readCreated(
        context: Context,
        created: HakimLocalArtifactFactory.Created
    ): ByteArray {
        created.uri?.let { uri ->
            return context.contentResolver.openInputStream(uri)?.use { input ->
                readBounded(input)
            } ?: error("تعذر قراءة الملف المحلي.")
        }
        val file = File(created.savedAt)
        require(file.isFile) { "تعذر العثور على الملف المحلي." }
        require(file.length() <= MAX_UPLOAD_BYTES) { "الملف أكبر من الحد الآمن." }
        return file.readBytes()
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_UPLOAD_BYTES) { "الملف أكبر من الحد الآمن." }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
