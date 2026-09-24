package ps.hakim.phoneagent

/**
 * Canonical output vocabulary for the Palestinian-teacher factory.
 *
 * A format being listed here means it is a governed target, not that field delivery is already
 * proven. Local deterministic support and Google Workspace export support are tracked separately.
 */
object HakimArtifactFormatRegistry {
    const val VERSION = "TEACHER-ARTIFACT-FORMATS-2026-09-24-v1"

    enum class Format(val extension: String, val mimeType: String) {
        PDF("pdf", "application/pdf"),
        HTML("html", "text/html"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PNG("png", "image/png"),
        JPG("jpg", "image/jpeg"),
        ODT("odt", "application/vnd.oasis.opendocument.text"),
        RTF("rtf", "application/rtf"),
        TXT("txt", "text/plain"),
        CSV("csv", "text/csv"),
        EPUB("epub", "application/epub+zip"),
        MARKDOWN("md", "text/markdown"),
        GOOGLE_DOC("", "application/vnd.google-apps.document"),
        GOOGLE_SLIDES("", "application/vnd.google-apps.presentation"),
        GOOGLE_SHEET("", "application/vnd.google-apps.spreadsheet")
    }

    val teacherCoreTargets: Set<Format> = linkedSetOf(
        Format.PDF,
        Format.HTML,
        Format.DOCX,
        Format.PPTX,
        Format.XLSX,
        Format.PNG,
        Format.GOOGLE_DOC,
        Format.GOOGLE_SLIDES,
        Format.GOOGLE_SHEET
    )

    /** What the current Android app can deterministically create without an external account. */
    val localDeterministicNow: Set<Format> = linkedSetOf(
        Format.PDF,
        Format.HTML,
        Format.DOCX,
        Format.PPTX,
        Format.XLSX,
        Format.PNG
    )

    fun requested(prompt: String): Set<Format> {
        val q = prompt.lowercase()
        val out = linkedSetOf<Format>()
        if (listOf("pdf", "بي دي اف", "بى دى اف").any { q.contains(it) }) out += Format.PDF
        if (listOf("html", "اتش تي ام ال", "صفحة ويب").any { q.contains(it) }) out += Format.HTML
        if (listOf("word", "وورد", "docx").any { q.contains(it) }) out += Format.DOCX
        if (listOf("powerpoint", "بوربوينت", "pptx", "عرض تقديمي").any { q.contains(it) }) out += Format.PPTX
        if (listOf("excel", "اكسل", "إكسل", "xlsx").any { q.contains(it) }) out += Format.XLSX
        if (listOf("png", "صورة", "صور").any { q.contains(it) }) out += Format.PNG
        return out
    }

    fun googleDocumentExports(): Set<Format> = linkedSetOf(
        Format.DOCX, Format.ODT, Format.RTF, Format.PDF, Format.TXT,
        Format.HTML, Format.EPUB, Format.MARKDOWN
    )

    fun googlePresentationExports(): Set<Format> =
        linkedSetOf(Format.PPTX, Format.PDF, Format.TXT)

    fun googleSpreadsheetExports(): Set<Format> =
        linkedSetOf(Format.XLSX, Format.PDF, Format.CSV)

    fun defaultTeacherOutput(): Format = Format.PDF
}
