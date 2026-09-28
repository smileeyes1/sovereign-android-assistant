package ps.hakim.phoneagent

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * بوابة الاعتماد السيادية.
 *
 * لا تستبدل محركات التنفيذ؛ بل تمنعها من ترقية المهمة إلى "مكتملة"
 * قبل وجود عقد، ودليل أثر مناسب، وعدم وجود فجوة مادية معلومة،
 * وربط نفس القطعة المختبرة بالتسليم عند وجود ملف، واجتياز الانحدار
 * عندما تكون المهمة تغييرًا برمجيًا/نظاميًا يحمي خط أساس مثبت.
 */
object HakimAcceptanceGate {
    const val VERSION = "SOVEREIGN-ACCEPTANCE-GATE-2026-09-28-v1"
    private const val PREFS = "hakim_acceptance_gate"

    enum class State {
        IDLE,
        CONTRACTED,
        VERIFYING,
        READY_TO_DELIVER,
        DELIVERED,
        BLOCKED
    }

    enum class Kind {
        ANSWER,
        ARTIFACT,
        EXECUTION,
        SYSTEM_CHANGE
    }

    fun begin(
        context: Context,
        goalId: String,
        goal: String,
        acceptance: String,
        baseline: String
    ) {
        val kind = classify(goal, acceptance)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().clear()
            .putString("version", VERSION)
            .putString("goal_id", goalId.take(120))
            .putString("goal_sha256", sha256(goal.trim()))
            .putString("acceptance_sha256", sha256(acceptance.trim()))
            .putString("baseline", baseline.take(500))
            .putString("kind", kind.name)
            .putString("state", State.CONTRACTED.name)
            .putBoolean("acceptance_present", acceptance.isNotBlank())
            .putBoolean("effect_verified", false)
            .putBoolean("known_material_gap", false)
            .putBoolean("artifact_required", kind == Kind.ARTIFACT)
            .putBoolean("artifact_verified", false)
            .putBoolean("artifact_same_as_tested", false)
            .putBoolean("regression_required", kind == Kind.SYSTEM_CHANGE)
            .putBoolean("regression_passed", false)
            .putLong("started_at", System.currentTimeMillis())
            .apply()
    }

    fun recordEffectEvidence(
        context: Context,
        stage: String,
        evidence: String,
        effectVerified: Boolean
    ) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = p.edit()
            .putString("evidence_stage", stage.take(120))
            .putString("evidence_sha256", sha256(evidence.trim()))
            .putBoolean("effect_verified", effectVerified)
            .putString("state", State.VERIFYING.name)
            .putLong("evidence_at", System.currentTimeMillis())

        // نجاح أثر جديد مناسب بعد إصلاح يثبت أن الفجوة السابقة لم تعد قائمة.
        if (effectVerified && evidence.isNotBlank()) {
            edit.putBoolean("known_material_gap", false)
                .remove("material_gap_sha256")
        }
        edit.apply()
    }

    fun markMaterialGap(context: Context, evidence: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("known_material_gap", true)
            .putString("material_gap_sha256", sha256(evidence.trim()))
            .putString("state", State.BLOCKED.name)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun requireRegression(context: Context, reason: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("regression_required", true)
            .putBoolean("regression_passed", false)
            .putString("regression_reason_sha256", sha256(reason.trim()))
            .putString("state", State.VERIFYING.name)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun recordRegression(context: Context, passed: Boolean, evidence: String) {
        require(evidence.isNotBlank()) { "regression_requires_evidence" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("regression_passed", passed)
            .putString("regression_evidence_sha256", sha256(evidence.trim()))
            .putString("state", if (passed) State.VERIFYING.name else State.BLOCKED.name)
            .putLong("regression_at", System.currentTimeMillis())
            .apply()
    }

    /**
     * يعيد فتح نفس الهدف الفعلي، يتحقق من بنيته عند PDF، ثم يقرأه مرة ثانية
     * كبصمة التسليم. اختلاف البصمتين يمنع الاعتماد.
     */
    fun verifyAndBindArtifact(
        context: Context,
        uri: Uri?,
        path: String,
        mimeType: String
    ): JSONObject {
        val tested = verifyAndFingerprint(context, uri, path, mimeType)
        val delivered = fingerprint(context, uri, path)
        require(tested.first == delivered.first) { "tested_delivered_artifact_mismatch" }
        require(tested.second == delivered.second) { "tested_delivered_size_mismatch" }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("artifact_required", true)
            .putBoolean("artifact_verified", true)
            .putBoolean("artifact_same_as_tested", true)
            .putString("tested_artifact_sha256", tested.first)
            .putString("delivered_artifact_sha256", delivered.first)
            .putLong("artifact_bytes", tested.second)
            .putString("state", State.VERIFYING.name)
            .putLong("artifact_verified_at", System.currentTimeMillis())
            .apply()

        return JSONObject()
            .put("verified", true)
            .put("same_artifact", true)
            .put("sha256", tested.first)
            .put("bytes", tested.second)
    }

    fun canClose(context: Context): Boolean =
        evaluate(context).optBoolean("ready", false)

    fun markDelivered(context: Context) {
        require(canClose(context)) { "delivery_before_acceptance_gate" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", State.DELIVERED.name)
            .putLong("delivered_at", System.currentTimeMillis())
            .apply()
    }

    fun evaluate(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val goalId = p.getString("goal_id", "").orEmpty()
        if (goalId.isBlank()) {
            return JSONObject()
                .put("ready", false)
                .put("state", State.IDLE.name)
                .put("reason", "no_active_contract")
        }

        val acceptancePresent = p.getBoolean("acceptance_present", false)
        val effectVerified = p.getBoolean("effect_verified", false)
        val materialGap = p.getBoolean("known_material_gap", false)
        val artifactRequired = p.getBoolean("artifact_required", false)
        val artifactReady =
            !artifactRequired ||
                (p.getBoolean("artifact_verified", false) &&
                    p.getBoolean("artifact_same_as_tested", false))
        val regressionRequired = p.getBoolean("regression_required", false)
        val regressionReady =
            !regressionRequired || p.getBoolean("regression_passed", false)

        val reason = when {
            !acceptancePresent -> "acceptance_missing"
            materialGap -> "material_gap_open"
            !effectVerified -> "effect_not_verified"
            !artifactReady -> "artifact_not_verified_or_changed"
            !regressionReady -> "regression_not_proven"
            else -> "acceptance_proven"
        }
        val ready = reason == "acceptance_proven"
        val current = p.getString("state", State.CONTRACTED.name).orEmpty()
        val state = when {
            current == State.DELIVERED.name && ready -> State.DELIVERED
            ready -> State.READY_TO_DELIVER
            materialGap -> State.BLOCKED
            else -> State.VERIFYING
        }

        p.edit()
            .putString("state", state.name)
            .putString("last_reason", reason)
            .putLong("evaluated_at", System.currentTimeMillis())
            .apply()

        return JSONObject()
            .put("ready", ready)
            .put("state", state.name)
            .put("reason", reason)
            .put("kind", p.getString("kind", Kind.ANSWER.name))
            .put("effect_verified", effectVerified)
            .put("known_material_gap", materialGap)
            .put("artifact_required", artifactRequired)
            .put("artifact_verified", p.getBoolean("artifact_verified", false))
            .put("artifact_same_as_tested", p.getBoolean("artifact_same_as_tested", false))
            .put("regression_required", regressionRequired)
            .put("regression_passed", p.getBoolean("regression_passed", false))
    }

    fun status(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val decision = evaluate(context)
        return JSONObject()
            .put("acceptance_gate", true)
            .put("version", VERSION)
            .put("single_goal_contract", true)
            .put("evidence_required", true)
            .put("material_gap_blocks_close", true)
            .put("same_tested_delivered_artifact_required", true)
            .put("regression_gate_supported", true)
            .put("goal_id", p.getString("goal_id", ""))
            .put("state", decision.optString("state"))
            .put("reason", decision.optString("reason"))
            .put("ready", decision.optBoolean("ready"))
            .put("kind", decision.optString("kind"))
            .put("artifact_required", decision.optBoolean("artifact_required"))
            .put("regression_required", decision.optBoolean("regression_required"))
    }

    private fun classify(goal: String, acceptance: String): Kind {
        val q = goal.lowercase()
        if (acceptance.contains("جواب مباشر")) return Kind.ANSWER

        val artifact = listOf(
            "pdf", "بي دي اف", "ملف", "ورقة عمل", "ورقه عمل",
            "docx", "word", "وورد", "html", "png", "pptx", "عرض تقديمي"
        ).any { q.contains(it) }
        if (artifact) return Kind.ARTIFACT

        val systemChange = listOf(
            "apk", "github", "مستودع", "تطبيق حكيم",
            "تحديث حكيم", "حدّث حكيم", "اصلح حكيم", "أصلح حكيم",
            "ابن تطبيق", "أنشئ تطبيق", "انشئ تطبيق"
        ).any { q.contains(it) } && acceptance.contains("أثر تنفيذي")
        if (systemChange) return Kind.SYSTEM_CHANGE

        val execution = acceptance.contains("أثر تنفيذي") || listOf(
            "نفذ", "نفّذ", "قم", "ثبت", "ثبّت", "اصلح", "أصلح",
            "افتح", "أرسل", "ارسل", "احذف", "حدّث", "حدث"
        ).any { q.contains(it) }
        return if (execution) Kind.EXECUTION else Kind.ANSWER
    }

    private fun verifyAndFingerprint(
        context: Context,
        uri: Uri?,
        path: String,
        mimeType: String
    ): Pair<String, Long> {
        val fp = fingerprint(context, uri, path)
        require(fp.second > 0L) { "artifact_empty" }

        if (mimeType.equals("application/pdf", ignoreCase = true)) {
            val magic = input(context, uri, path).use { stream ->
                val bytes = ByteArray(5)
                val count = stream.read(bytes)
                if (count == 5) String(bytes, Charsets.US_ASCII) else ""
            }
            require(magic == "%PDF-") { "artifact_not_pdf" }
            require(fp.second >= 700L) { "pdf_below_safe_size" }

            val pages = if (uri != null) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                } ?: error("artifact_open_failed")
            } else {
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                }
            }
            require(pages >= 1) { "pdf_without_pages" }
        }
        return fp
    }

    private fun fingerprint(
        context: Context,
        uri: Uri?,
        path: String
    ): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        input(context, uri, path).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
                total += count
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash to total
    }

    private fun input(context: Context, uri: Uri?, path: String): InputStream {
        if (uri != null) {
            return context.contentResolver.openInputStream(uri)
                ?: error("artifact_input_unavailable")
        }
        val file = File(path)
        require(file.exists() && file.isFile) { "artifact_path_missing" }
        return file.inputStream()
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
