package ps.hakim.phoneagent

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * مصنع محلي صغير للمخرجات التعليمية التي يمكن إنجازها حتميًا بلا نموذج خارجي.
 *
 * القاعدة: إذا كان حكيم يعرف كيف يُنتج الأثر نفسه محليًا، فلا يفتح OAuth ولا يرسل
 * نص المستخدم إلى مزود خارجي لمجرد أن محركًا عامًا غير مهيأ.
 */
object HakimLocalArtifactFactory {

    data class Created(
        val uri: Uri?,
        val displayName: String,
        val savedAt: String,
        val kind: String = "application/pdf"
    )

    private val easternDigits = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')

    private const val PREFS = "hakim_local_artifacts"
    private const val LAST_KIND = "last_kind"
    private const val KIND_ADD_WITHIN_10 = "worksheet_addition_within_10"

    fun canHandle(prompt: String): Boolean =
        explicitAdditionWithinTen(prompt) || isPdfAdditionWorksheet(prompt)

    fun canHandle(context: Context, prompt: String): Boolean {
        if (explicitAdditionWithinTen(prompt) || isPdfAdditionWorksheet(prompt)) return true
        if (!isPdfWorksheetFollowUp(prompt)) return false

        val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
            .takeLast(8_000)

        if (explicitAdditionWithinTen(recent) || isPdfAdditionWorksheet(recent)) return true

        val lastKind = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_KIND, "")
            .orEmpty()
        return lastKind == KIND_ADD_WITHIN_10
    }

    fun create(context: Context, prompt: String): Result<Created> = runCatching {
        require(canHandle(context, prompt)) { "المخرج المحلي المطلوب غير مدعوم بعد." }
        val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
            .takeLast(8_000)
        val maxSum = resolveMaxSum(prompt, recent)
        val created = createAdditionWorksheetPdf(context, maxSum)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_KIND, KIND_ADD_WITHIN_10)
            .putLong("last_created_at", System.currentTimeMillis())
            .apply()
        created
    }

    private fun explicitAdditionWithinTen(text: String): Boolean {
        val q = normalize(text)
        val worksheet = q.contains("ورقة عمل") || q.contains("ورقه عمل") || q.contains("worksheet")
        val addition = q.contains("الجمع") || q.contains("جمع") || q.contains("addition") || q.contains("joining")
        return worksheet && addition && mentionsAdditionWithinTen(q)
    }

    private fun mentionsAdditionWithinTen(text: String): Boolean {
        val q = normalize(text)
        return listOf(
            "ضمن ١٠", "ضمن 10", "حتى ١٠", "حتى 10", "إلى ١٠", "الى ١٠",
            "within 10", "joining within 10", "addition within 10"
        ).any { q.contains(it) }
    }

    private fun isPdfAdditionWorksheet(text: String): Boolean {
        val q = normalize(text)
        val worksheet = q.contains("ورقة عمل") || q.contains("ورقه عمل") || q.contains("worksheet")
        val addition = q.contains("الجمع") || q.contains("جمع") || q.contains("addition") || q.contains("joining")
        val pdf = listOf("pdf", "بي دي اف", "بى دى اف", "للتحميل", "للطباعة", "الطباعة", "ملف").any { q.contains(it) }
        return worksheet && addition && pdf
    }

    private fun isPdfWorksheetFollowUp(text: String): Boolean {
        val q = normalize(text)
        val pdf = listOf("pdf", "بي دي اف", "بى دى اف", "ملف", "للتحميل", "تحميل", "للطباعة", "الطباعة").any { q.contains(it) }
        val referent = listOf("ورقة العمل", "ورقه العمل", "الورقة", "الورقه", "هذه", "هذي", "نفسها", "حولها", "حوّلها", "اريدها", "أريدها").any { q.contains(it) }
        return pdf && referent
    }

    private fun resolveMaxSum(prompt: String, recent: String): Int {
        val q = normalize(prompt + " " + recent)
        return when {
            listOf("ضمن ١٨", "ضمن 18", "حتى ١٨", "حتى 18").any { q.contains(it) } -> 18
            listOf("ضمن ٢٠", "ضمن 20", "حتى ٢٠", "حتى 20").any { q.contains(it) } -> 20
            else -> 10
        }
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")


    private fun createAdditionWorksheetPdf(context: Context, maxSum: Int): Created {
        val easternLimit = toEastern(maxSum)
        val displayName = "ورقة_عمل_الجمع_ضمن_${easternLimit}.pdf"
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 27f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val instruction = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val math = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 31f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
            }

            canvas.drawText("ورقة عمل: الجمع ضمن $easternLimit", 545f, 58f, title)
            canvas.drawText("الاسم: __________________________", 545f, 96f, label)
            canvas.drawText("الصف: __________      التاريخ: __________", 545f, 126f, label)
            canvas.drawLine(50f, 146f, 545f, 146f, stroke)
            canvas.drawText("أوجد ناتج الجمع، ثم اكتب الإجابة في المربع.", 545f, 182f, instruction)

            val problems = problemsFor(maxSum)
            var y = 240f
            problems.forEachIndexed { index, pair ->
                drawQuestion(canvas, index + 1, pair.first, pair.second, y, math, label, stroke)
                y += 68f
            }

            canvas.drawLine(50f, 790f, 545f, 790f, stroke)
            canvas.drawText("أحسنت المحاولة.", 545f, 820f, label)

            document.finishPage(page)
            return savePdfDocument(context, document, displayName)
        } finally {
            document.close()
        }
    }

    private fun problemsFor(maxSum: Int): List<Pair<Int, Int>> {
        val base = if (maxSum <= 10) {
            listOf(1 to 2, 3 to 4, 5 to 2, 6 to 3, 4 to 4, 7 to 2, 1 to 8, 5 to 5)
        } else {
            listOf(4 to 5, 7 to 6, 8 to 5, 9 to 7, 6 to 8, 10 to 4, 11 to 5, 9 to 9)
        }
        return base.filter { it.first + it.second <= maxSum }.take(8)
    }



    fun createTextPdf(context: Context, title: String, rawContent: String): Result<Created> = runCatching {
        val content = HakimProductOutput.clean(rawContent).trim()
        require(content.isNotBlank()) { "لا يوجد محتوى صالح لإنشاء PDF." }
        require(!HakimProductOutput.containsRawMarkup(content)) { "بقيت وسوم خام بعد التنظيف." }

        val displayName = "حكيم_مستند.pdf"
        val document = PdfDocument()
        try {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            var canvas = page.canvas
            var y = 58f
            canvas.drawText(title.ifBlank { "مستند حكيم" }.take(70), 545f, y, titlePaint)
            y += 42f

            fun newPage() {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                canvas = page.canvas
                y = 58f
            }

            for (paragraph in content.lines()) {
                if (paragraph.isBlank()) {
                    y += 16f
                    if (y > 790f) newPage()
                    continue
                }

                val words = paragraph.trim().split(Regex("\\s+"))
                var line = ""
                for (word in words) {
                    val candidate = if (line.isBlank()) word else "$line $word"
                    if (bodyPaint.measureText(candidate) <= 490f) {
                        line = candidate
                    } else {
                        if (line.isNotBlank()) {
                            if (y > 790f) newPage()
                            canvas.drawText(line, 545f, y, bodyPaint)
                            y += 27f
                        }
                        line = word
                    }
                }
                if (line.isNotBlank()) {
                    if (y > 790f) newPage()
                    canvas.drawText(line, 545f, y, bodyPaint)
                    y += 27f
                }
                y += 6f
            }

            document.finishPage(page)
            savePdfDocument(context, document, displayName)
        } finally {
            document.close()
        }
    }

    private fun savePdfDocument(context: Context, document: PdfDocument, displayName: String): Created {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/حكيم")
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
            return Created(uri, displayName, "التنزيلات/حكيم/$displayName")
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "حكيم").apply { mkdirs() }
        val file = File(dir, displayName)
        FileOutputStream(file).use { document.writeTo(it) }
        return Created(null, displayName, file.absolutePath)
    }

    private fun drawQuestion(
        canvas: android.graphics.Canvas,
        number: Int,
        a: Int,
        b: Int,
        y: Float,
        math: Paint,
        body: Paint,
        line: Paint
    ) {
        // عين الطالب هي الحكم: يظهر بصريًا «٤ + ٣ = □».
        canvas.drawText(toEastern(number) + ")", 548f, y, body)

        val tokens = listOf(toEastern(a), "+", toEastern(b), "=")
        var x = 455f
        tokens.forEach { token ->
            canvas.drawText(token, x, y, math)
            x -= 62f
        }

        val box = RectF(x - 30f, y - 36f, x + 30f, y + 18f)
        canvas.drawRect(box, line)
    }

    fun toEastern(value: Int): String = value.toString().map { ch ->
        if (ch in '0'..'9') easternDigits[ch - '0'] else ch
    }.joinToString("")
}
