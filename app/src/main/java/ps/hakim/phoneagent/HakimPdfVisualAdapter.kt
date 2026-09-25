package ps.hakim.phoneagent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Local PDF -> ordered page-image adapter.
 *
 * This is a derived representation for model vision, never a replacement for
 * the authoritative PDF. It is intentionally bounded: large documents fail
 * closed rather than silently dropping pages.
 */
object HakimPdfVisualAdapter {
    private const val AUTHORITY_SUFFIX = ".hakim.files"
    private const val MAX_PDFS_PER_TASK = 3
    private const val MAX_PAGES_PER_PDF = 8
    private const val MAX_TOTAL_PAGES = 12
    private const val MAX_RENDER_DIMENSION = 1800
    private const val JPEG_QUALITY = 92
    private const val MAX_TOTAL_DERIVED_BYTES = 16L * 1024L * 1024L

    data class Adapted(
        val attachments: List<HakimAttachmentGateway.Attachment>,
        val sourcePdfCount: Int,
        val renderedPageCount: Int,
        val note: String
    )

    fun canAdapt(source: List<HakimAttachmentGateway.Attachment>): Boolean {
        val pdfs = source.filter { isPdf(it) }
        return pdfs.isNotEmpty() && pdfs.size <= MAX_PDFS_PER_TASK
    }

    fun adapt(
        context: Context,
        source: List<HakimAttachmentGateway.Attachment>
    ): Result<Adapted> = runCatching {
        val pdfs = source.filter { isPdf(it) }
        require(pdfs.isNotEmpty()) { "لا توجد ملفات PDF قابلة للتحويل البصري." }
        require(pdfs.size <= MAX_PDFS_PER_TASK) { "عدد ملفات PDF أكبر من الحد الآمن للمهمة الواحدة." }
        require(source.all { isPdf(it) || it.mimeType.startsWith("image/") }) {
            "المهمة تجمع أنواع ملفات لا يمكن تحويلها جميعًا إلى تمثيل بصري موحد دون فقد."
        }

        val out = mutableListOf<HakimAttachmentGateway.Attachment>()
        out += source.filter { it.mimeType.startsWith("image/") }

        val dir = File(context.filesDir, "hakim_intake/pdf_pages").apply { mkdirs() }
        cleanup(dir)

        var totalPages = 0
        var totalDerivedBytes = 0L

        pdfs.forEach { pdf ->
            val pfd = context.contentResolver.openFileDescriptor(pdf.uri, "r")
                ?: error("تعذر فتح PDF: ${pdf.displayName}")
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount in 1..MAX_PAGES_PER_PDF) {
                        "PDF «${pdf.displayName}» يحتوي ${renderer.pageCount} صفحة؛ يتجاوز حد التحويل الكامل الآمن ($MAX_PAGES_PER_PDF)."
                    }
                    require(totalPages + renderer.pageCount <= MAX_TOTAL_PAGES) {
                        "إجمالي صفحات PDF يتجاوز حد التحويل الكامل الآمن ($MAX_TOTAL_PAGES)."
                    }

                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = minOf(
                                2.0f,
                                MAX_RENDER_DIMENSION.toFloat() /
                                    maxOf(page.width, page.height).coerceAtLeast(1).toFloat()
                            )
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val file = File(
                                dir,
                                "p${totalPages + 1}-${UUID.randomUUID().toString().take(8)}.jpg"
                            )
                            FileOutputStream(file).use { output ->
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                                    "تعذر حفظ الصفحة المشتقة."
                                }
                                output.fd.sync()
                            }
                            bitmap.recycle()

                            totalDerivedBytes += file.length()
                            require(totalDerivedBytes <= MAX_TOTAL_DERIVED_BYTES) {
                                "التمثيل البصري الكامل للـPDF يتجاوز حد النقل الآمن."
                            }

                            val uri = FileProvider.getUriForFile(
                                context,
                                context.packageName + AUTHORITY_SUFFIX,
                                file
                            )
                            out += HakimAttachmentGateway.Attachment(
                                uri = uri,
                                mimeType = "image/jpeg",
                                displayName = "${pdf.displayName} — صفحة ${index + 1} من ${renderer.pageCount}",
                                sizeBytes = file.length()
                            )
                            totalPages++
                        }
                    }
                }
            }
        }

        Adapted(
            attachments = out,
            sourcePdfCount = pdfs.size,
            renderedPageCount = totalPages,
            note = "حُوّلت صفحات PDF كاملة محليًا إلى صور مرتبة للتحليل البصري؛ الأصل بقي محفوظًا ومرجعيًا."
        )
    }

    private fun isPdf(a: HakimAttachmentGateway.Attachment): Boolean =
        a.mimeType.equals("application/pdf", ignoreCase = true) ||
            a.displayName.endsWith(".pdf", ignoreCase = true)

    private fun cleanup(dir: File) {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        dir.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }
}
