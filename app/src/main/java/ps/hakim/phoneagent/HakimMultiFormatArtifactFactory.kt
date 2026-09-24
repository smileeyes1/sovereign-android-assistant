package ps.hakim.phoneagent

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Deterministic multi-format renderer.
 * Every renderer consumes the same HakimTeacherArtifactSpec.
 */
object HakimMultiFormatArtifactFactory {
    const val VERSION = "MULTIFORMAT-TEACHER-FACTORY-2026-09-24-v1"

    private val localFormats = linkedSetOf(
        HakimArtifactFormatRegistry.Format.PDF,
        HakimArtifactFormatRegistry.Format.HTML,
        HakimArtifactFormatRegistry.Format.DOCX,
        HakimArtifactFormatRegistry.Format.PPTX,
        HakimArtifactFormatRegistry.Format.XLSX,
        HakimArtifactFormatRegistry.Format.PNG
    )

    fun canHandle(context: Context, prompt: String): Boolean {
        if (!HakimLocalArtifactFactory.canHandle(context, prompt)) return false
        val requested = requestedLocalFormats(prompt)
        return requested.size > 1 || requested.any { it != HakimArtifactFormatRegistry.Format.PDF }
    }

    fun createAll(
        context: Context,
        prompt: String
    ): Result<List<HakimLocalArtifactFactory.Created>> = runCatching {
        require(HakimLocalArtifactFactory.canHandle(context, prompt)) {
            "المخرج المحلي المطلوب غير مدعوم بعد."
        }
        val spec = HakimTeacherArtifactSpec.additionWithinTen()
        val requested = requestedLocalFormats(prompt)
        require(requested.isNotEmpty()) { "لم تُحدّد صيغة محلية مدعومة." }

        requested.map { format ->
            when (format) {
                HakimArtifactFormatRegistry.Format.PDF ->
                    HakimLocalArtifactFactory.create(context, prompt).getOrThrow()
                HakimArtifactFormatRegistry.Format.HTML ->
                    saveBytes(context, "ورقة_عمل_الجمع_ضمن_١٠.html", format.mimeType, renderHtml(spec))
                HakimArtifactFormatRegistry.Format.DOCX ->
                    saveBytes(context, "ورقة_عمل_الجمع_ضمن_١٠.docx", format.mimeType, renderDocx(spec))
                HakimArtifactFormatRegistry.Format.PPTX ->
                    saveBytes(context, "ورقة_عمل_الجمع_ضمن_١٠.pptx", format.mimeType, renderPptx(spec))
                HakimArtifactFormatRegistry.Format.XLSX ->
                    saveBytes(context, "ورقة_عمل_الجمع_ضمن_١٠.xlsx", format.mimeType, renderXlsx(spec))
                HakimArtifactFormatRegistry.Format.PNG ->
                    saveBytes(context, "ورقة_عمل_الجمع_ضمن_١٠.png", format.mimeType, renderPng(spec))
                else -> error("الصيغة ليست ضمن المصنع المحلي الحالي: " + format.name)
            }
        }
    }

    fun requestedLocalFormats(prompt: String): LinkedHashSet<HakimArtifactFormatRegistry.Format> {
        val q = prompt.lowercase()
        if (listOf("كل الصيغ", "جميع الصيغ", "كل الملفات", "بكل الصيغ").any { q.contains(it) }) {
            return LinkedHashSet(localFormats)
        }
        val requested = HakimArtifactFormatRegistry.requested(prompt)
            .filterTo(linkedSetOf()) { it in localFormats }
        if (requested.isEmpty()) requested += HakimArtifactFormatRegistry.Format.PDF
        return LinkedHashSet(requested)
    }

    private fun renderHtml(spec: HakimTeacherArtifactSpec): ByteArray {
        val rows = spec.problems.joinToString("\n") { p ->
            "<div class=\"q\"><span class=\"n\">" + toEastern(p.number) +
                ")</span><span class=\"math\">" + toEastern(p.a) + " + " +
                toEastern(p.b) + " = <span class=\"box\"></span></span></div>"
        }
        val html = "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head>" +
            "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>" + xml(spec.title) + "</title><style>" +
            "@page{size:A4;margin:20mm}*{box-sizing:border-box}body{font-family:Arial,sans-serif;direction:rtl;color:#111;margin:0}" +
            "h1{font-size:26pt;margin:0 0 12mm;text-align:right}.meta{font-size:16pt;border-bottom:1px solid #444;padding-bottom:6mm;margin-bottom:7mm}" +
            ".inst{font-size:18pt;margin-bottom:5mm}.q{display:flex;flex-direction:row;justify-content:flex-start;align-items:center;gap:12mm;height:18mm;border-bottom:1px solid #bbb;font-size:25pt;page-break-inside:avoid}" +
            ".n{width:16mm;text-align:right}.math{direction:ltr;unicode-bidi:isolate;display:inline-flex;align-items:center;gap:5mm}" +
            ".box{display:inline-block;width:15mm;height:13mm;border:2px solid #111}.footer{margin-top:8mm;font-size:16pt}" +
            "</style></head><body><h1>" + xml(spec.title) + "</h1>" +
            "<div class=\"meta\">الاسم: ____________________ &nbsp;&nbsp; الصف: ______ &nbsp;&nbsp; التاريخ: ______</div>" +
            "<div class=\"inst\">" + xml(spec.instruction) + "</div>" + rows +
            "<div class=\"footer\">أحسنت المحاولة.</div></body></html>"
        return html.toByteArray(Charsets.UTF_8)
    }

    private fun renderDocx(spec: HakimTeacherArtifactSpec): ByteArray {
        val body = buildString {
            append(wordParagraph(spec.title, true, 36))
            append(wordParagraph("الاسم: ____________________    الصف: ______    التاريخ: ______", false, 28))
            append(wordParagraph(spec.instruction, false, 30))
            spec.problems.forEach { p ->
                append(wordParagraph(
                    toEastern(p.number) + ")    " + toEastern(p.a) + " + " + toEastern(p.b) + " = □",
                    true,
                    34
                ))
            }
            append(wordParagraph("أحسنت المحاولة.", false, 28))
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
            body +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>" +
            "</w:body></w:document>"
        return zip(linkedMapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
            "_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
            "word/document.xml" to document
        ))
    }

    private fun wordParagraph(text: String, bold: Boolean, sizeHalfPoints: Int): String {
        val b = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"right\"/><w:spacing w:after=\"160\"/></w:pPr>" +
            "<w:r><w:rPr><w:rtl/>" + b + "<w:sz w:val=\"" + sizeHalfPoints +
            "\"/><w:szCs w:val=\"" + sizeHalfPoints + "\"/></w:rPr><w:t xml:space=\"preserve\">" +
            xml(text) + "</w:t></w:r></w:p>"
    }

    private fun renderXlsx(spec: HakimTeacherArtifactSpec): ByteArray {
        val rows = buildString {
            append(xlsxRow(1, listOf(spec.title)))
            append(xlsxRow(2, listOf("الاسم", "", "الصف", "", "التاريخ", "")))
            append(xlsxRow(3, listOf(spec.instruction)))
            spec.problems.forEachIndexed { index, p ->
                append(xlsxRow(index + 4, listOf(
                    toEastern(p.number), toEastern(p.a), "+", toEastern(p.b), "=", "□"
                )))
            }
        }
        val sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>" +
            "<cols><col min=\"1\" max=\"6\" width=\"18\" customWidth=\"1\"/></cols><sheetData>" +
            rows + "</sheetData></worksheet>"
        return zip(linkedMapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>",
            "_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookViews><workbookView/></workbookViews><sheets><sheet name=\"ورقة العمل\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
            "xl/worksheets/sheet1.xml" to sheet
        ))
    }

    private fun xlsxRow(row: Int, values: List<String>): String = buildString {
        append("<row r=\"" + row + "\">")
        values.forEachIndexed { index, value ->
            val ref = columnName(index + 1) + row
            append("<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
                xml(value) + "</t></is></c>")
        }
        append("</row>")
    }

    private fun renderPptx(spec: HakimTeacherArtifactSpec): ByteArray {
        val paragraphs = buildString {
            append(pptParagraph(spec.title, 2800, true))
            append(pptParagraph(spec.instruction, 1800, false))
            spec.problems.forEach { p ->
                append(pptParagraph(
                    toEastern(p.number) + ")  " + toEastern(p.a) + " + " + toEastern(p.b) + " = □",
                    1900,
                    true
                ))
            }
        }
        val slide = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">" +
            "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
            "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
            "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"ورقة العمل\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
            "<p:spPr><a:xfrm><a:off x=\"457200\" y=\"304800\"/><a:ext cx=\"11277600\" cy=\"6248400\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>" +
            "<p:txBody><a:bodyPr rtlCol=\"1\" wrap=\"square\"/><a:lstStyle/>" + paragraphs +
            "</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
        return zip(pptParts(slide))
    }

    private fun pptParagraph(text: String, size: Int, bold: Boolean): String =
        "<a:p><a:pPr algn=\"r\" rtl=\"1\"/><a:r><a:rPr lang=\"ar-SA\" sz=\"" + size +
            "\" b=\"" + (if (bold) "1" else "0") + "\" rtl=\"1\"/><a:t>" + xml(text) +
            "</a:t></a:r><a:endParaRPr lang=\"ar-SA\" sz=\"" + size + "\"/></a:p>"

    private fun pptParts(slide: String): LinkedHashMap<String, String> = linkedMapOf(
        "[Content_Types].xml" to PPT_CONTENT_TYPES,
        "_rels/.rels" to PPT_ROOT_RELS,
        "docProps/core.xml" to PPT_CORE,
        "docProps/app.xml" to PPT_APP,
        "ppt/presentation.xml" to PPT_PRESENTATION,
        "ppt/_rels/presentation.xml.rels" to PPT_PRESENTATION_RELS,
        "ppt/slides/slide1.xml" to slide,
        "ppt/slides/_rels/slide1.xml.rels" to PPT_SLIDE_RELS,
        "ppt/slideLayouts/slideLayout1.xml" to PPT_LAYOUT,
        "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to PPT_LAYOUT_RELS,
        "ppt/slideMasters/slideMaster1.xml" to PPT_MASTER,
        "ppt/slideMasters/_rels/slideMaster1.xml.rels" to PPT_MASTER_RELS,
        "ppt/theme/theme1.xml" to PPT_THEME
    )

    private fun renderPng(spec: HakimTeacherArtifactSpec): ByteArray {
        val bitmap = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 36f; textAlign = Paint.Align.RIGHT
        }
        val math = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        canvas.drawText(spec.title, 1140f, 110f, title)
        canvas.drawText("الاسم: ____________________    الصف: ______    التاريخ: ______", 1140f, 180f, body)
        canvas.drawLine(100f, 205f, 1140f, 205f, line)
        canvas.drawText(spec.instruction, 1140f, 270f, body)
        var y = 380f
        spec.problems.forEach { p ->
            canvas.drawText(toEastern(p.number) + ")", 1150f, y, body)
            val tokens = listOf(toEastern(p.a), "+", toEastern(p.b), "=")
            var x = 1000f
            tokens.forEach { token ->
                canvas.drawText(token, x, y, math)
                x -= 135f
            }
            canvas.drawRect(RectF(x - 50f, y - 65f, x + 50f, y + 25f), line)
            canvas.drawLine(130f, y + 50f, 1080f, y + 50f, line)
            y += 120f
        }
        val out = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "تعذر ضغط صورة PNG." }
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun saveBytes(
        context: Context,
        displayName: String,
        mimeType: String,
        bytes: ByteArray
    ): HakimLocalArtifactFactory.Created {
        require(bytes.isNotEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/حكيم")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء الملف في التنزيلات.")
            try {
                resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("تعذر فتح الملف للكتابة.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return HakimLocalArtifactFactory.Created(uri, displayName, "التنزيلات/حكيم/" + displayName, mimeType)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "حكيم").apply { mkdirs() }
        val file = File(dir, displayName)
        FileOutputStream(file).use { it.write(bytes) }
        return HakimLocalArtifactFactory.Created(null, displayName, file.absolutePath, mimeType)
    }

    private fun zip(parts: LinkedHashMap<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.key))
                zip.write(entry.value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun xml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    private fun columnName(index: Int): String {
        var n = index
        val out = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            out.append(('A'.code + r).toChar())
            n = (n - 1) / 26
        }
        return out.reverse().toString()
    }

    private fun toEastern(value: Int): String = HakimLocalArtifactFactory.toEastern(value)

    private const val PPT_CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/><Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/><Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/><Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/><Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/><Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/><Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/></Types>"
    private const val PPT_ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/></Relationships>"
    private const val PPT_CORE = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>ورقة عمل الجمع ضمن ١٠</dc:title><dc:creator>حكيم</dc:creator></cp:coreProperties>"
    private const val PPT_APP = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>حكيم</Application><PresentationFormat>Widescreen</PresentationFormat><Slides>1</Slides></Properties>"
    private const val PPT_PRESENTATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"screen16x9\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"
    private const val PPT_PRESENTATION_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide1.xml\"/></Relationships>"
    private const val PPT_SLIDE_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/></Relationships>"
    private const val PPT_LAYOUT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:sldLayout xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" type=\"blank\" preserve=\"1\"><p:cSld name=\"فارغ\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
    private const val PPT_LAYOUT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>"
    private const val PPT_MASTER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:sldMaster xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMap accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" bg1=\"lt1\" bg2=\"lt2\" folHlink=\"folHlink\" hlink=\"hlink\" tx1=\"dk1\" tx2=\"dk2\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"1\" r:id=\"rId1\"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>"
    private const val PPT_MASTER_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/></Relationships>"
    private const val PPT_THEME = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Hakim\"><a:themeElements><a:clrScheme name=\"Hakim\"><a:dk1><a:srgbClr val=\"000000\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"1F1F1F\"/></a:dk2><a:lt2><a:srgbClr val=\"F2F2F2\"/></a:lt2><a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1><a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3><a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4><a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5><a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme><a:fontScheme name=\"Hakim\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"Arial\"/></a:majorFont><a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"Arial\"/></a:minorFont></a:fontScheme><a:fmtScheme name=\"Hakim\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w=\"9525\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"
}
