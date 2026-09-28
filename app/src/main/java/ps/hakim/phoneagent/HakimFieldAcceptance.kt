package ps.hakim.phoneagent

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Field acceptance runs only synthetic, bounded probes.
 * It never reads user conversation text, user files, browser pages, or credentials.
 */
object HakimFieldAcceptance {
    const val VERSION = "FIELD-ACCEPTANCE-20313-v1"
    private const val PREFS = "hakim_field_acceptance"
    private const val REPORT = "report"
    private const val LAST_VERSION = "last_version"
    private const val LAST_RUN_AT = "last_run_at"
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hakim-field-acceptance").apply { isDaemon = true }
    }

    fun install(context: Context) {
        val app = context.applicationContext
        val version = currentVersion(app)
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getLong(LAST_VERSION, -1L) == version && p.getString(REPORT, "").orEmpty().isNotBlank()) return
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                val report = run(app)
                p.edit()
                    .putLong(LAST_VERSION, version)
                    .putLong(LAST_RUN_AT, System.currentTimeMillis())
                    .putString(REPORT, report.toString())
                    .apply()
                HakimHealthBeacon.sendAsync(app, "field_acceptance")
            } finally {
                running.set(false)
            }
        }
    }

    fun status(context: Context): JSONObject {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val parsed = runCatching { JSONObject(p.getString(REPORT, "").orEmpty()) }.getOrNull()
        return JSONObject()
            .put("version", VERSION)
            .put("running", running.get())
            .put("last_version", p.getLong(LAST_VERSION, -1L))
            .put("last_run_at", p.getLong(LAST_RUN_AT, 0L))
            .put("report", parsed ?: JSONObject.NULL)
    }

    private fun run(context: Context): JSONObject {
        val pdf = localPdfAndContextProbe(context)
        val attachment = attachmentProbe(context)
        val browser = browserProbe()
        val modelPrefs = context.getSharedPreferences(GeminiDirectEngine.PREFS, Context.MODE_PRIVATE)
        val modelSnapshot = snapshot(modelPrefs)
        val chat: ProbeState
        val multimodal: ProbeState
        try {
            chat = ordinaryChatProbe(context)
            multimodal = multimodalProbe(context)
        } finally {
            restore(modelPrefs, modelSnapshot)
        }

        val localPass =
            pdf.pdfPass &&
                pdf.contextPass &&
                attachment.state == "PASS" &&
                browser.state == "PASS"
        val externalPass = chat.state == "PASS" && multimodal.state == "PASS"
        val externalBlocked = chat.state.startsWith("BLOCKED") || multimodal.state.startsWith("BLOCKED")
        val overall = when {
            !localPass -> "FAIL_LOCAL"
            externalPass -> "PASS"
            externalBlocked -> "PASS_LOCAL_EXTERNAL_BLOCKED"
            else -> "PASS_LOCAL_EXTERNAL_FAILED"
        }

        return JSONObject()
            .put("status", overall)
            .put("app_version", currentVersion(context))
            .put("synthetic_only", true)
            .put("personal_content_used", false)
            .put("free_only_policy", HakimFreePolicy.freeOnly(context))
            .put("local_pdf", if (pdf.pdfPass) "PASS" else "FAIL")
            .put("contextual_followup", if (pdf.contextPass) "PASS" else "FAIL")
            .put("attachment_intake", attachment.state)
            .put("browser_privacy", browser.state)
            .put("ordinary_chat", chat.state)
            .put("ordinary_chat_engine", chat.engine)
            .put("multimodal", multimodal.state)
            .put("multimodal_engine", multimodal.engine)
            .put("local_pdf_detail", pdf.detail)
            .put("attachment_detail", attachment.detail)
            .put("browser_detail", browser.detail)
    }

    private data class PdfProbe(val pdfPass: Boolean, val contextPass: Boolean, val detail: String)
    private data class ProbeState(val state: String, val engine: String = "", val detail: String = "")

    private fun localPdfAndContextProbe(context: Context): PdfProbe {
        val prefs = context.getSharedPreferences("hakim_local_artifacts", Context.MODE_PRIVATE)
        val before = snapshot(prefs)
        var created: HakimLocalArtifactFactory.Created? = null
        return try {
            val prompt = "أنشئ ورقة عمل PDF عن الجمع ضمن ١٠"
            val expected = HakimLocalArtifactFactory.resolveSpec(context, prompt)
                ?: return PdfProbe(false, false, "spec_unavailable")
            created = HakimLocalArtifactFactory.create(context, prompt).getOrThrow()
            val pdfPass = verifyPdf(context, created!!)
            val follow = HakimLocalArtifactFactory.resolveSpec(context, "أريدها PDF للطباعة")
            val contextPass = follow?.id == expected.id
            PdfProbe(pdfPass, contextPass, if (pdfPass && contextPass) "verified_and_context_preserved" else "verification_failed")
        } catch (e: Exception) {
            PdfProbe(false, false, e.javaClass.simpleName.take(80))
        } finally {
            created?.let { cleanupCreated(context, it) }
            restore(prefs, before)
        }
    }

    private fun verifyPdf(context: Context, created: HakimLocalArtifactFactory.Created): Boolean {
        val uri = created.uri ?: return runCatching {
            val file = File(created.savedAt)
            file.exists() && file.length() >= 700L &&
                file.inputStream().use { input ->
                    val bytes = ByteArray(5)
                    input.read(bytes) == 5 && String(bytes, Charsets.US_ASCII) == "%PDF-"
                }
        }.getOrDefault(false)

        return runCatching {
            val magic = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(5)
                if (input.read(bytes) == 5) String(bytes, Charsets.US_ASCII) else ""
            }.orEmpty()
            if (magic != "%PDF-") return@runCatching false
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                PdfRenderer(pfd).use { renderer ->
                    size >= 700L && renderer.pageCount >= 1
                }
            } ?: false
        }.getOrDefault(false)
    }

    private fun cleanupCreated(context: Context, created: HakimLocalArtifactFactory.Created) {
        if (created.uri != null) {
            runCatching { context.contentResolver.delete(created.uri, null, null) }
        } else {
            runCatching { File(created.savedAt).delete() }
        }
    }

    private fun attachmentProbe(context: Context): ProbeState {
        val root = File(context.filesDir, "hakim_intake/field_acceptance").apply { mkdirs() }
        val source = File(root, "hakim-field-attachment.txt")
        val marker = "HAKIM_FIELD_ATTACHMENT_20313"
        var shaPrefix = ""
        return try {
            source.writeText(marker, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".hakim.files",
                source
            )
            val attachment = HakimAttachmentGateway.Attachment(
                uri = uri,
                mimeType = "text/plain",
                displayName = "hakim-field-attachment.txt",
                sizeBytes = source.length()
            )
            val plan = HakimVerifiedIntake.prepare(
                context,
                "اقرأ النص الاصطناعي فقط",
                listOf(attachment)
            ).getOrThrow()
            val provenance = plan.provenance.singleOrNull()
                ?: return ProbeState("FAIL", detail = "provenance_missing")
            shaPrefix = provenance.sha256.take(24)
            val pass =
                provenance.sha256.matches(Regex("^[0-9a-f]{64}$")) &&
                    provenance.verifiedBytes == source.length() &&
                    provenance.exactTextAvailable &&
                    plan.canUseExactTextFallback &&
                    plan.exactTextEnvelope.orEmpty().contains(marker)
            if (pass) ProbeState("PASS", detail = "byte_verified_exact_text")
            else ProbeState("FAIL", detail = "intake_verification_failed")
        } catch (e: Exception) {
            ProbeState("FAIL", detail = e.javaClass.simpleName.take(80))
        } finally {
            runCatching { source.delete() }
            if (shaPrefix.isNotBlank()) {
                File(context.filesDir, "hakim_intake").listFiles().orEmpty()
                    .filter { it.name.startsWith(shaPrefix) }
                    .forEach { runCatching { it.delete() } }
            }
        }
    }

    private fun browserProbe(): ProbeState {
        var result = JSONObject().put("ok", false).put("error", "browser_service_unavailable")
        repeat(4) {
            result = HakimService.fieldBrowserSelfTest()
            if (result.optString("error") != "browser_service_unavailable") return@repeat
            try { Thread.sleep(900L) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        val pass =
            result.optBoolean("ok") &&
                result.optBoolean("safe_readable") &&
                result.optBoolean("privacy_gate")
        return if (pass) ProbeState("PASS", detail = "isolated_safe_and_sensitive_pages")
        else ProbeState("FAIL", detail = result.optString("error", "browser_self_test_failed").take(100))
    }

    private fun ordinaryChatProbe(context: Context): ProbeState {
        val engines = eligibleEngines(context, requireImages = false)
        if (engines.isEmpty()) return ProbeState("BLOCKED_NOT_CONFIGURED", detail = "no_free_direct_engine")
        var sawAuthorizationBlock = false
        var sawFailure = false
        for (engine in engines) {
            when (val result = engine.complete(
                "اختبار آلي اصطناعي غير شخصي لحكيم. أجب بكلمة واحدة: جاهز",
                emptyList()
            )) {
                is HakimInferenceEngine.Result.Success -> {
                    if (result.text.isNotBlank()) return ProbeState("PASS", engine.id, "returned_in_app")
                    sawFailure = true
                }
                is HakimInferenceEngine.Result.NeedsAuthorization -> sawAuthorizationBlock = true
                is HakimInferenceEngine.Result.Unavailable -> sawFailure = true
                is HakimInferenceEngine.Result.Failure -> sawFailure = true
            }
        }
        return when {
            sawAuthorizationBlock && !sawFailure -> ProbeState("BLOCKED_AUTHORIZATION", detail = "free_engine_requires_authorization")
            else -> ProbeState("FAIL", detail = "configured_free_engines_failed")
        }
    }

    private fun multimodalProbe(context: Context): ProbeState {
        val engines = eligibleEngines(context, requireImages = true)
        if (engines.isEmpty()) return ProbeState("BLOCKED_NOT_CONFIGURED", detail = "no_free_image_engine")
        val dir = File(context.filesDir, "hakim_intake/field_acceptance").apply { mkdirs() }
        val image = File(dir, "hakim-field-red.png")
        return try {
            val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            FileOutputStream(image).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                out.fd.sync()
            }
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".hakim.files",
                image
            )
            val attachment = HakimAttachmentGateway.Attachment(
                uri = uri,
                mimeType = "image/png",
                displayName = "hakim-field-red.png",
                sizeBytes = image.length()
            )

            var sawAuthorizationBlock = false
            var sawFailure = false
            for (engine in engines) {
                when (val result = engine.complete(
                    "هذه صورة اصطناعية للاختبار فقط. ما اللون الغالب؟ أجب بكلمة واحدة.",
                    listOf(attachment)
                )) {
                    is HakimInferenceEngine.Result.Success -> {
                        val answer = result.text.trim().lowercase()
                        val recognized = answer.contains("أحمر") || answer.contains("احمر") || answer.contains("red")
                        if (recognized) return ProbeState("PASS", engine.id, "synthetic_image_understood")
                        sawFailure = true
                    }
                    is HakimInferenceEngine.Result.NeedsAuthorization -> sawAuthorizationBlock = true
                    is HakimInferenceEngine.Result.Unavailable -> sawFailure = true
                    is HakimInferenceEngine.Result.Failure -> sawFailure = true
                }
            }
            if (sawAuthorizationBlock && !sawFailure) {
                ProbeState("BLOCKED_AUTHORIZATION", detail = "free_image_engine_requires_authorization")
            } else {
                ProbeState("FAIL", detail = "configured_image_engines_failed")
            }
        } catch (e: Exception) {
            ProbeState("FAIL", detail = e.javaClass.simpleName.take(80))
        } finally {
            runCatching { image.delete() }
        }
    }

    private fun eligibleEngines(context: Context, requireImages: Boolean): List<HakimInferenceEngine> =
        HakimEngineRegistry.directEngines(context)
            .filter { HakimFreePolicy.allows(it.id, context) }
            .filter { HakimInferenceEngine.Capability.GENERAL_CHAT in it.capabilities }
            .filter { !requireImages || HakimInferenceEngine.Capability.IMAGES in it.capabilities }

    private fun currentVersion(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    private fun snapshot(prefs: SharedPreferences): Map<String, Any?> = HashMap(prefs.all)

    private fun restore(prefs: SharedPreferences, values: Map<String, Any?>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        edit.commit()
    }
}
