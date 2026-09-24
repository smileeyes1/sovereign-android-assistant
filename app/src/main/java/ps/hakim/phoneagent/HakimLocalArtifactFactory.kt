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

    fun canHandle(prompt: String): Boolean = explicitAdditionWithinTen(prompt)

    fun canHandle(context: Context, prompt: String): Boolean {
        if (explicitAdditionWithinTen(prompt)) return true

        val directPdfAddition = isPdfAdditionWorksheet(prompt)
        val contextualFollowUp = isPdfWorksheetFollowUp(prompt)
        if (!directPdfAddition && !contextualFollowUp) return false

        val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
            .takeLast(8_000)

        if (explicitAdditionWithinTen(recent)) return true
        if (directPdfAddition && mentionsAdditionWithinTen(recent)) return true

        val lastKind = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_KIND, "")
            .orEmpty()
        return lastKind == KIND_ADD_WITHIN_10
    }

    fun create(context: Context, prompt: String): Result<Created> = runCatching {
        require(canHandle(context, prompt)) { "المخرج المحلي المطلوب غير مدعوم بعد." }
        val created = createAdditionWithinTenPdf(context)
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
        val pdf = listOf("pdf", "بي دي اف", "بى دى اف", "للتحميل", "للطباعة", "الطباعة").any { q.contains(it) }
        return worksheet && addition && pdf
    }

    private fun isPdfWorksheetFollowUp(text: String): Boolean {
        val q = normalize(text)
        val pdf = listOf("pdf", "بي دي اف", "بى دى اف", "ملف", "للتحميل", "تحميل", "للطباعة", "الطباعة").any { q.contains(it) }
        val referent = listOf("ورقة العمل", "ورقه العمل", "الورقة", "الورقه", "هذه", "هذي", "نفسها", "حولها", "حوّلها", "اريدها", "أريدها").any { q.contains(it) }
        return pdf && referent
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")


    private fun createAdditionWithinTenPdf(context: Context): Created {
        val displayName = "ورقة_عمل_الجمع_ضمن_١٠.pdf"
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            val math = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }

            val spec = HakimTeacherArtifactSpec.additionWithinTen()
            canvas.drawText(spec.title, 545f, 58f, title)
            canvas.drawText("الاسم: ____________________    الصف: ______    التاريخ: ______", 545f, 96f, body)
            canvas.drawLine(50f, 112f, 545f, 112f, line)
            canvas.drawText(spec.instruction, 545f, 145f, body)

            var y = 205f
            spec.problems.forEach { problem ->
                drawQuestion(canvas, problem.number, problem.a, problem.b, y, math, body, line)
                y += 60f
            }

            canvas.drawLine(50f, 792f, 545f, 792f, line)
            canvas.drawText("أحسنت المحاولة.", 545f, 820f, body)

            document.finishPage(page)

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
        } finally {
            document.close()
        }
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
        // مهم: نرسم الرموز واحدًا واحدًا من اليمين إلى اليسار كي يرى الطالب:
        // ٤ + ٣ = □
        // ولا نترك BiDi يعكس المعنى الرياضي.
        canvas.drawText(toEastern(number) + ")", 555f, y, body)

        val tokens = listOf(toEastern(a), "+", toEastern(b), "=")
        var x = 485f
        tokens.forEach { token ->
            canvas.drawText(token, x, y, math)
            x -= 65f
        }

        val box = RectF(x - 25f, y - 32f, x + 25f, y + 14f)
        canvas.drawRect(box, line)
        canvas.drawLine(65f, y + 25f, 525f, y + 25f, line)
    }

    fun toEastern(value: Int): String = value.toString().map { ch ->
        if (ch in '0'..'9') easternDigits[ch - '0'] else ch
    }.joinToString("")
}
