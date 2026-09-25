package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Verified multimodal intake boundary.
 *
 * Rules:
 * 1) The original source remains authoritative.
 * 2) A private byte-for-byte mirror is created when bounded and readable.
 * 3) Every accepted source is fully streamed and SHA-256 fingerprinted.
 * 4) Text substitution is allowed only when the complete source is intrinsically textual,
 *    strictly decoded without replacement characters, round-trips byte-for-byte after BOM removal,
 *    and the user's intent does not require visual/layout evidence.
 * 5) Images/PDF/video/audio and layout-sensitive tasks never become "text only" by assumption.
 */
object HakimVerifiedIntake {
    private const val AUTHORITY_SUFFIX = ".hakim.files"
    private const val MAX_PRIVATE_MIRROR_BYTES = 64L * 1024L * 1024L
    private const val MAX_EXACT_TEXT_BYTES = 64L * 1024L
    private const val MAX_TOTAL_TEXT_BYTES = 96L * 1024L

    data class Provenance(
        val displayName: String,
        val mimeType: String,
        val sha256: String,
        val verifiedBytes: Long,
        val mirroredPrivately: Boolean,
        val exactTextAvailable: Boolean
    )

    data class Plan(
        val attachments: List<HakimAttachmentGateway.Attachment>,
        val provenance: List<Provenance>,
        val exactTextEnvelope: String?,
        val originalRequired: Boolean,
        val reason: String
    ) {
        val canUseExactTextFallback: Boolean
            get() = exactTextEnvelope != null && !originalRequired

        fun modelEnvelope(): String = buildString {
            appendLine()
            appendLine("بيان تحقق المرفقات من حكيم:")
            provenance.forEachIndexed { index, p ->
                append(index + 1)
                append(". ")
                append(p.displayName)
                append(" | ")
                append(p.mimeType)
                append(" | bytes=")
                append(p.verifiedBytes)
                append(" | sha256=")
                appendLine(p.sha256)
            }
            if (exactTextEnvelope != null) {
                appendLine()
                append(exactTextEnvelope)
            }
        }
    }

    fun prepare(
        context: Context,
        prompt: String,
        source: List<HakimAttachmentGateway.Attachment>
    ): Result<Plan> = runCatching {
        require(source.isNotEmpty()) { "لا توجد مرفقات للتحقق." }
        cleanupOldMirrors(context)

        val prepared = source.map { prepareOne(context, it) }
        val exactTextAllowedByIntent = !requiresOriginalVisual(prompt)
        val allExactText = prepared.all { it.exactText != null }
        val totalTextBytes = prepared.sumOf { it.exactTextBytes }

        val exactEnvelope = if (
            exactTextAllowedByIntent &&
            allExactText &&
            totalTextBytes <= MAX_TOTAL_TEXT_BYTES
        ) {
            buildString {
                appendLine("المحتوى النصي الآتي تم استخراجه كاملاً من مصادر نصية فقط بعد تحقق صارم؛ لا يمثل صورًا أو تنسيقًا بصريًا:")
                prepared.forEachIndexed { index, item ->
                    appendLine()
                    appendLine("<<< بداية المرفق ${index + 1}: ${item.attachment.displayName} | sha256=${item.sha256} >>>")
                    appendLine(item.exactText.orEmpty())
                    appendLine("<<< نهاية المرفق ${index + 1} >>>")
                }
            }
        } else {
            null
        }

        val originalRequired = exactEnvelope == null
        Plan(
            attachments = prepared.map { it.attachment },
            provenance = prepared.map {
                Provenance(
                    displayName = it.attachment.displayName,
                    mimeType = it.attachment.mimeType,
                    sha256 = it.sha256,
                    verifiedBytes = it.verifiedBytes,
                    mirroredPrivately = it.mirroredPrivately,
                    exactTextAvailable = it.exactText != null
                )
            },
            exactTextEnvelope = exactEnvelope,
            originalRequired = originalRequired,
            reason = when {
                exactEnvelope != null ->
                    "المصدر نصي كامل ومتحقق؛ يمكن استخدام تمثيل نصي حرفي كمسار بديل دون الادعاء بأنه بديل للمعلومات البصرية."
                requiresOriginalVisual(prompt) ->
                    "المقصد يحتاج معلومات بصرية/تنسيقية؛ الأصل إلزامي ولا يُستبدل بنص."
                else ->
                    "نوع المرفق أو حجمه لا يسمح بتمثيل نصي كامل مثبت؛ الأصل إلزامي."
            }
        )
    }

    private data class Prepared(
        val attachment: HakimAttachmentGateway.Attachment,
        val sha256: String,
        val verifiedBytes: Long,
        val mirroredPrivately: Boolean,
        val exactText: String?,
        val exactTextBytes: Long
    )

    private fun prepareOne(
        context: Context,
        source: HakimAttachmentGateway.Attachment
    ): Prepared {
        val resolver = context.contentResolver
        val digest = MessageDigest.getInstance("SHA-256")
        val dir = File(context.filesDir, "hakim_intake").apply { mkdirs() }
        val temp = File(dir, ".${UUID.randomUUID()}.part")

        var total = 0L
        var mirror = true
        resolver.openInputStream(source.uri).use { input ->
            requireNotNull(input) { "تعذر فتح المرفق: ${source.displayName}" }
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    digest.update(buffer, 0, read)
                    total += read.toLong()
                    if (mirror && total <= MAX_PRIVATE_MIRROR_BYTES) {
                        output.write(buffer, 0, read)
                    } else {
                        mirror = false
                    }
                }
                output.fd.sync()
            }
        }

        source.sizeBytes?.let { announced ->
            require(announced == total) {
                "تغير حجم المرفق أثناء القراءة: ${source.displayName}"
            }
        }

        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val safeName = sanitizeName(source.displayName)
        val canonicalUri: Uri
        var mirrored = false

        if (mirror && total <= MAX_PRIVATE_MIRROR_BYTES) {
            val target = File(dir, "${sha.take(24)}-$safeName")
            if (target.exists()) {
                temp.delete()
            } else {
                require(temp.renameTo(target)) { "تعذر تثبيت النسخة المحلية الموثقة." }
            }
            canonicalUri = FileProvider.getUriForFile(
                context,
                context.packageName + AUTHORITY_SUFFIX,
                target
            )
            mirrored = true
        } else {
            temp.delete()
            canonicalUri = source.uri
        }

        val verifiedAttachment = source.copy(
            uri = canonicalUri,
            sizeBytes = total
        )

        val exact = if (
            isIntrinsicText(source.mimeType, source.displayName) &&
            total <= MAX_EXACT_TEXT_BYTES
        ) {
            readExactText(context, verifiedAttachment)
        } else {
            null
        }

        return Prepared(
            attachment = verifiedAttachment,
            sha256 = sha,
            verifiedBytes = total,
            mirroredPrivately = mirrored,
            exactText = exact,
            exactTextBytes = if (exact != null) total else 0L
        )
    }

    private fun readExactText(
        context: Context,
        attachment: HakimAttachmentGateway.Attachment
    ): String? {
        val bytes = readAllBounded(context, attachment.uri, MAX_EXACT_TEXT_BYTES) ?: return null
        if (bytes.isEmpty()) return ""

        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() ->
                decodeStrict(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)

            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() ->
                decodeStrict(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16LE)

            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() ->
                decodeStrict(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)

            else -> decodeStrict(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun decodeStrict(bytes: ByteArray, charset: java.nio.charset.Charset): String? {
        return runCatching {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            val roundTrip = text.toByteArray(charset)
            if (!roundTrip.contentEquals(bytes)) return null
            text
        }.getOrNull()
    }

    private fun readAllBounded(context: Context, uri: Uri, max: Long): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read.toLong()
                if (total > max) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun isIntrinsicText(mime: String, name: String): Boolean {
        val normalized = mime.lowercase()
        if (normalized.startsWith("text/")) return true
        if (normalized in setOf(
                "application/json",
                "application/ld+json",
                "application/xml",
                "application/yaml",
                "application/x-yaml",
                "application/javascript",
                "application/sql",
                "application/csv"
            )
        ) return true

        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "txt", "md", "csv", "json", "xml", "html", "htm", "yaml", "yml",
            "log", "ini", "cfg", "properties", "kt", "java", "py", "js", "ts", "css", "sql"
        )
    }

    private fun requiresOriginalVisual(prompt: String): Boolean {
        val q = prompt.lowercase()
        val markers = listOf(
            "صورة", "الصورة", "مرئي", "بصري", "شكل", "موضع", "مكان", "لون", "ألوان",
            "خط", "تنسيق", "تخطيط", "جدول كما يظهر", "توقيع", "ختم", "رسم", "مخطط",
            "واجهة", "تصميم", "بكسل", "pdf", "بي دي اف", "video", "فيديو", "audio", "صوت",
            "layout", "visual", "formatting", "font", "position", "pixel"
        )
        return markers.any { q.contains(it) }
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name
            .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
            .trim('_')
            .takeLast(80)
        return cleaned.ifBlank { "attachment.bin" }
    }

    private fun cleanupOldMirrors(context: Context) {
        val dir = File(context.filesDir, "hakim_intake")
        if (!dir.exists()) return
        val cutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        dir.listFiles().orEmpty().forEach { file ->
            if (file.isFile && (file.name.endsWith(".part") || file.lastModified() < cutoff)) {
                runCatching { file.delete() }
            }
        }
    }
}
