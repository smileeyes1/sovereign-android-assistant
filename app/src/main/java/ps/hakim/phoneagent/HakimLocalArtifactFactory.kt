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
    private const val LAST_SPEC_ID = "last_spec_id"

    /**
     * يعيد مواصفة تعليمية فقط عندما يكون مقصد المستخدم «مخرجًا/ملفًا»،
     * لا عندما يسأل سؤالًا تعليميًا عاديًا.
     */
    fun resolveSpec(context: Context, prompt: String): HakimTeacherArtifactSpec? {
        val explicit = HakimTeacherArtifactSpec.resolveExplicit(prompt)
        if (explicit != null && isArtifactIntent(prompt)) return explicit

        if (!HakimTeacherArtifactSpec.looksLikeArtifactFollowUp(prompt)) return null

        val lastId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_SPEC_ID, "")
            .orEmpty()
        HakimTeacherArtifactSpec.byId(lastId)?.let { return it }

        val recent = context.getSharedPreferences("hakim_conversation", Context.MODE_PRIVATE)
            .getString("recent", "")
            .orEmpty()
            .takeLast(8_000)
        return HakimTeacherArtifactSpec.resolveExplicit(recent)
    }

    fun canHandle(prompt: String): Boolean =
        HakimTeacherArtifactSpec.resolveExplicit(prompt) != null && isArtifactIntent(prompt)

    fun canHandle(context: Context, prompt: String): Boolean =
        resolveSpec(context, prompt) != null

    fun create(context: Context, prompt: String): Result<Created> = runCatching {
        val spec = resolveSpec(context, prompt)
            ?: error("لا توجد مواصفة تعليمية محلية مطابقة لهذا الطلب.")
        val created = createWorksheetPdf(context, spec)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_SPEC_ID, spec.id)
            .putLong("last_created_at", System.currentTimeMillis())
            .apply()
        created
    }

    private fun isArtifactIntent(text: String): Boolean {
        val q = normalize(text)
        val artifact = listOf(
            "ورقة عمل", "ورقه عمل", "worksheet",
            "pdf", "بي دي اف", "بى دى اف", "ملف",
            "للتحميل", "تحميل", "للطباعة", "طباعة",
            "word", "وورد", "docx", "html", "png", "صورة"
        ).any { q.contains(it) }
        val creation = listOf("أنشئ", "انشئ", "اصنع", "صمم", "صمّم", "جهز", "جهّز").any { q.contains(it) }
        return artifact || creation
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun createWorksheetPdf(
        context: Context,
        spec: HakimTeacherArtifactSpec
    ): Created {
        verifyStudentSpec(spec)
        val displayName = spec.fileStem + ".pdf"
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 17f
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

            canvas.drawText(spec.title, 545f, 56f, title)
            canvas.drawText(
                "الاسم: ____________________    الصف: ______    التاريخ: ______",
                545f,
                94f,
                body
            )
            canvas.drawLine(50f, 110f, 545f, 110f, line)
            canvas.drawText(spec.instruction, 545f, 142f, body)

            var y = 205f
            spec.problems.forEach { problem ->
                drawQuestion(canvas, problem, y, math, body, line)
                y += 66f
            }

            canvas.drawLine(50f, 760f, 545f, 760f, line)
            canvas.drawText("أحسنت المحاولة.", 545f, 795f, body)
            document.finishPage(page)

            return savePdfDocument(context, document, displayName)
        } finally {
            document.close()
        }
    }

    private fun verifyStudentSpec(spec: HakimTeacherArtifactSpec) {
        val visible = buildString {
            append(spec.title)
            append(spec.subject)
            append(spec.grade)
            append(spec.instruction)
        }
        require(!visible.contains(Regex("[A-Za-z]"))) {
            "تسربت لغة أجنبية إلى ورقة الطالب."
        }
        require(spec.problems.all { it.result() in 0..10 }) {
            "وجدت مسألة خارج نطاق ١٠."
        }
        require(spec.problems.size <= 8) {
            "عدد الأسئلة يتجاوز سعة الصفحة الآمنة."
        }
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
        problem: HakimTeacherArtifactSpec.MathProblem,
        y: Float,
        math: Paint,
        body: Paint,
        line: Paint
    ) {
        // الحكم لما يراه الطالب: ٤ + ٣ = □ أو ٧ − ٣ = □.
        // نرسم الرموز واحدًا واحدًا من اليمين إلى اليسار، ولا نترك BiDi يغيّر المعنى.
        canvas.drawText(toEastern(problem.number) + ")", 555f, y, body)

        val tokens = listOf(
            toEastern(problem.a),
            problem.operation.symbol,
            toEastern(problem.b),
            "="
        )
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
