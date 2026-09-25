package ps.hakim.phoneagent

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * مُصيّر PDF عام. لا يعرف موضوع الدرس ولا يعتمد على نموذج بعينه.
 * مسؤوليته: نص نهائي صالح -> PDF حقيقي -> تحقق من نفس الملف -> تسليم.
 */
object HakimUniversalPdfRenderer {
    const val VERSION = "UNIVERSAL-PDF-RENDERER-2026-09-25-v1"

    data class Rendered(
        val uri: Uri?,
        val displayName: String,
        val savedAt: String,
        val pageCount: Int,
        val byteSize: Long,
        val verified: Boolean
    )

    fun render(
        context: Context,
        request: HakimArtifactRequest,
        rawContent: String
    ): Result<Rendered> = runCatching {
        val content = HakimArtifactPipeline.validateContent(request, rawContent).getOrThrow()
        val displayName = request.fileStem + ".pdf"
        val document = PdfDocument()

        try {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 1f
            }

            var pageNo = 0
            var page: PdfDocument.Page? = null
            var y = 0f

            fun newPage() {
                page?.let { document.finishPage(it) }
                pageNo += 1
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, pageNo).create()
                )
                val canvas = page!!.canvas
                y = 55f
                if (pageNo == 1) {
                    canvas.drawText(
                        HakimArtifactRequest.toEasternDigits(request.title),
                        545f,
                        y,
                        titlePaint
                    )
                    y += 34f
                    canvas.drawLine(50f, y, 545f, y, rulePaint)
                    y += 30f
                } else {
                    canvas.drawText(
                        HakimArtifactRequest.toEasternDigits(request.title).take(70),
                        545f,
                        y,
                        headingPaint
                    )
                    y += 32f
                }
            }

            fun finishPageFooter() {
                val current = page ?: return
                val canvas = current.canvas
                canvas.drawLine(50f, 804f, 545f, 804f, rulePaint)
                canvas.drawText(
                    "صفحة " + HakimArtifactRequest.toEasternDigits(pageNo.toString()),
                    297.5f,
                    823f,
                    footerPaint
                )
            }

            fun ensureSpace(height: Float) {
                if (page == null) newPage()
                if (y + height > 790f) {
                    finishPageFooter()
                    newPage()
                }
            }

            fun drawWrapped(text: String, paint: Paint, indent: Float = 0f, extraAfter: Float = 6f) {
                val normalized = text.replace(Regex("\\s+"), " ").trim()
                if (normalized.isBlank()) {
                    y += 14f
                    return
                }
                val maxWidth = 485f - indent
                val words = normalized.split(" ")
                var line = ""

                fun flush() {
                    if (line.isBlank()) return
                    ensureSpace(26f)
                    page!!.canvas.drawText(line, 545f - indent, y, paint)
                    y += if (paint === headingPaint) 29f else 26f
                    line = ""
                }

                for (word in words) {
                    val candidate = if (line.isBlank()) word else "$line $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        line = candidate
                    } else {
                        flush()
                        if (paint.measureText(word) <= maxWidth) {
                            line = word
                        } else {
                            // نص طويل جدًا بلا مسافات؛ يجزأ بأمان بدل القص.
                            var chunk = ""
                            for (ch in word) {
                                val next = chunk + ch
                                if (paint.measureText(next) <= maxWidth) chunk = next
                                else {
                                    line = chunk
                                    flush()
                                    chunk = ch.toString()
                                }
                            }
                            line = chunk
                        }
                    }
                }
                flush()
                y += extraAfter
            }

            newPage()

            val lines = content.lines()
                .map { it.trim() }

            for (raw in lines) {
                if (raw.isBlank()) {
                    y += 10f
                    continue
                }

                val line = HakimArtifactRequest.toEasternDigits(raw)
                    .replace(Regex("^[-*•]\\s*"), "• ")

                val looksHeading =
                    line.length <= 55 &&
                    !line.endsWith("؟") &&
                    !line.endsWith(".") &&
                    (
                        line.endsWith(":") ||
                        line.startsWith("الهدف") ||
                        line.startsWith("النشاط") ||
                        line.startsWith("التقويم") ||
                        line.startsWith("التعليمات") ||
                        line.startsWith("السؤال") ||
                        line.startsWith("الجزء")
                    )

                if (looksHeading) {
                    ensureSpace(34f)
                    drawWrapped(line.removeSuffix(":"), headingPaint, extraAfter = 8f)
                } else {
                    val bullet = line.startsWith("• ")
                    drawWrapped(
                        line,
                        bodyPaint,
                        indent = if (bullet) 12f else 0f,
                        extraAfter = if (request.kind == HakimArtifactRequest.Kind.WORKSHEET &&
                            (line.endsWith("؟") || line.matches(Regex("^[٠-٩]+[.)].*")))) 16f else 6f
                    )

                    // مساحة كتابة إضافية للأسئلة/التدريبات.
                    if (
                        request.kind == HakimArtifactRequest.Kind.WORKSHEET &&
                        (line.endsWith("؟") || line.matches(Regex("^[٠-٩]+[.)].*")))
                    ) {
                        ensureSpace(32f)
                        page!!.canvas.drawLine(85f, y + 8f, 505f, y + 8f, rulePaint)
                        y += 25f
                    }
                }
            }

            finishPageFooter()
            page?.let { document.finishPage(it) }

            val stored = store(context, document, displayName)
            verify(context, stored.first, stored.second, displayName)
        } finally {
            document.close()
        }
    }

    private fun store(
        context: Context,
        document: PdfDocument,
        displayName: String
    ): Pair<Uri?, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/حكيم"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء ملف PDF في التنزيلات.")
            try {
                resolver.openOutputStream(uri, "w")?.use { out ->
                    document.writeTo(out)
                } ?: error("تعذر فتح ملف PDF للكتابة.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri to "التنزيلات/حكيم/$displayName"
        }

        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "حكيم"
        ).apply { mkdirs() }
        val file = File(dir, displayName)
        FileOutputStream(file).use { document.writeTo(it) }
        return null to file.absolutePath
    }

    private fun verify(
        context: Context,
        uri: Uri?,
        path: String,
        displayName: String
    ): Rendered {
        if (uri != null) {
            val resolver = context.contentResolver
            val magic = resolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(5)
                val count = input.read(bytes)
                if (count == 5) String(bytes, Charsets.US_ASCII) else ""
            }.orEmpty()
            require(magic == "%PDF-") { "الملف الناتج ليس PDF صالحًا." }

            var bytes = -1L
            var pages = 0
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                bytes = pfd.statSize
                PdfRenderer(pfd).use { renderer ->
                    pages = renderer.pageCount
                }
            } ?: error("تعذر إعادة فتح PDF للتحقق.")

            require(bytes >= 700L) { "PDF الناتج أصغر من الحد الآمن." }
            require(pages >= 1) { "PDF الناتج بلا صفحات." }

            return Rendered(
                uri = uri,
                displayName = displayName,
                savedAt = path,
                pageCount = pages,
                byteSize = bytes,
                verified = true
            )
        }

        val file = File(path)
        require(file.exists() && file.length() >= 700L) {
            "ملف PDF المحلي غير موجود أو ناقص."
        }
        val magic = file.inputStream().use { input ->
            val bytes = ByteArray(5)
            val count = input.read(bytes)
            if (count == 5) String(bytes, Charsets.US_ASCII) else ""
        }
        require(magic == "%PDF-") { "الملف الناتج ليس PDF صالحًا." }

        val pfd = android.os.ParcelFileDescriptor.open(
            file,
            android.os.ParcelFileDescriptor.MODE_READ_ONLY
        )
        val pages = pfd.use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
        require(pages >= 1)

        return Rendered(
            uri = null,
            displayName = displayName,
            savedAt = path,
            pageCount = pages,
            byteSize = file.length(),
            verified = true
        )
    }
}
