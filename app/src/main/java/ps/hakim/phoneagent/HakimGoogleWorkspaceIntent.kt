package ps.hakim.phoneagent

import android.content.Context

/** Maps explicit user requests to a Google Workspace target without hijacking local file requests. */
object HakimGoogleWorkspaceIntent {
    const val VERSION = "GOOGLE-WORKSPACE-INTENT-2026-09-24-v1"

    enum class Target(
        val googleMimeType: String?,
        val localSourceHint: String
    ) {
        DOCS("application/vnd.google-apps.document", " وورد docx"),
        SLIDES("application/vnd.google-apps.presentation", " بوربوينت pptx"),
        SHEETS("application/vnd.google-apps.spreadsheet", " إكسل xlsx"),
        DRIVE(null, " بي دي اف pdf")
    }

    fun target(prompt: String): Target? {
        val q = prompt.lowercase()
        return when {
            listOf("google docs", "مستندات جوجل", "مستند جوجل", "جوجل دوكس").any { q.contains(it) } -> Target.DOCS
            listOf("google slides", "عروض جوجل", "شرائح جوجل", "جوجل سلايد").any { q.contains(it) } -> Target.SLIDES
            listOf("google sheets", "جداول جوجل", "جدول جوجل", "جوجل شيت").any { q.contains(it) } -> Target.SHEETS
            listOf("google drive", "جوجل درايف", "درايف").any { q.contains(it) } -> Target.DRIVE
            else -> null
        }
    }

    fun canHandle(context: Context, prompt: String): Boolean =
        target(prompt) != null && HakimLocalArtifactFactory.canHandle(context, prompt)
}
