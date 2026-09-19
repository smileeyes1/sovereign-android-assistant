package ps.hakim.phoneagent

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * اختبار ميداني غير هدّام يعمل على الهاتف نفسه.
 * لا يحول الفحص الذاتي وحده إلى FIELD_VERIFIED؛ بل يميز PASS/PARTIAL/BLOCKED ويعرض الأدلة والفجوات.
 */
object HakimFieldValidation {
    enum class Level { PASS, PARTIAL, BLOCKED }

    data class Check(val name: String, val level: Level, val detail: String) {
        fun toJson(): JSONObject = JSONObject().put("name", name).put("level", level.name).put("detail", detail.take(320))
    }

    private const val EXPECTED_F4 = "F42D71B0308A543E253099C02301BFDBFEFA45F8755B12AB22A3C903305E442E"
    private const val ALTERNATE_D1 = "D13E7AA8271CB6D32AEC2157CC5BA4FAFD226957EB0C731E9CEBA827BF78B0D3"

    fun run(context: Context): JSONObject {
        val app = context.applicationContext
        HakimQuranicInvariantKernel.requireInherited("field_validation")
        val checks = ArrayList<Check>()
        fun add(name: String, level: Level, detail: String) { checks += Check(name, level, detail) }

        val version = packageVersion(app)
        add("هوية التطبيق", if (app.packageName == "ps.hakim.stable" && version > 0) Level.PASS else Level.BLOCKED,
            "${app.packageName} / versionCode=$version")

        val signer = signerSha256(app)
        val signerLevel = when (signer) {
            EXPECTED_F4 -> Level.PASS
            ALTERNATE_D1 -> Level.PARTIAL
            else -> Level.BLOCKED
        }
        add("هوية التوقيع الميداني", signerLevel, when (signer) {
            EXPECTED_F4 -> "سلالة F4 الحاكمة الحالية"
            ALTERNATE_D1 -> "سلالة D1 البديلة مثبتة؛ لا يجوز وصفها كتحديث موضعي لـF4"
            else -> "توقيع غير معروف: ${signer.take(16)}…"
        })

        val self = runCatching { HakimSelfCheck.run(app) }.getOrElse {
            HakimFaultLedger.record(app, "field_validation_selfcheck", it, severity = HakimFaultLedger.Severity.CRITICAL)
            JSONObject().put("status", "FAIL_CLOSED")
        }
        add("الفحص الذاتي الحاكم", when (self.optString("status")) {
            "PASS" -> Level.PASS
            "PASS_WITH_WARNINGS" -> Level.PARTIAL
            else -> Level.BLOCKED
        }, self.optString("status", "UNKNOWN"))

        val integration = runCatching { HakimIntegrationFabric.structuralStatus(app, "field_validation") }.getOrElse { JSONObject() }
        add("نسيج التكامل", if (integration.optBoolean("structural_integrity")) Level.PASS else Level.BLOCKED,
            if (integration.optBoolean("structural_integrity")) "سليم" else "فشل بنيوي")

        val quranPolicy = HakimQuranicCorpusPolicy.status()
        add("سياسة القرآن كله", if (quranPolicy.optBoolean("all_114_surahs_covered")) Level.PASS else Level.BLOCKED,
            "تغطية حاكمة للسور=${quranPolicy.optInt("surah_count", 0)}")
        val verifiedCorpus = HakimVerifiedQuranCorpus.status(app)
        add("نص القرآن المحلي المتحقق", if (verifiedCorpus.optBoolean("ready")) Level.PASS else Level.PARTIAL,
            if (verifiedCorpus.optBoolean("ready")) "١١٤ سورة / ${verifiedCorpus.optInt("ayah_count")} آية من مصدر رسمي متحقق" else "غير مثبت محليًا بعد؛ النص الدقيق يجب أن يبقى على مسار المصدر الموثوق")

        val secureToken = "field_${System.nanoTime()}"
        val secureOk = HakimSecureStore.put(app, "hakim_field_validation", "roundtrip", secureToken) &&
            HakimSecureStore.get(app, "hakim_field_validation", "roundtrip") == secureToken
        HakimSecureStore.remove(app, "hakim_field_validation", "roundtrip")
        add("التخزين المشفر", if (secureOk) Level.PASS else Level.BLOCKED, if (secureOk) "AndroidKeyStore round-trip نجح" else "فشل round-trip")

        val systems = runCatching { HakimSystemOfSystems.compose(app, "اختبار ميداني آمن") }.getOrNull()
        add("نظام الأنظمة", if (systems != null && systems.units.isNotEmpty()) Level.PASS else Level.BLOCKED,
            "الأنظمة الفعالة=${systems?.units?.size ?: 0} / وضع=${systems?.executionMode.orEmpty()}")

        val resource = HakimResourceGovernor.snapshot(app)
        add("حاكم موارد الهاتف", Level.PASS, "الوضع=${resource.mode}; ذاكرة متاحة=${"%.2f".format(resource.availMemoryRatio)}; بطارية=${resource.batteryPercent}%")

        val adaptive = HakimAdaptiveLearning.status(app)
        add("التعلم التكيفي", if (adaptive.optBoolean("adaptive_learning") && !adaptive.optBoolean("can_expand_authority")) Level.PASS else Level.BLOCKED,
            "القرار=${adaptive.optString("last_adaptation_decision", "COLLECTING_EVIDENCE")}; epoch=${adaptive.optLong("epoch", 0)}")

        val faults = HakimFaultLedger.status(app)
        add("منع الفشل الصامت", if (faults.optBoolean("repeated_material_fault")) Level.PARTIAL else Level.PASS,
            "أحداث حديثة=${faults.optInt("recent_event_count")}; عطل مادي متكرر=${faults.optBoolean("repeated_material_fault")}")

        val mesh = HakimCapabilityMesh.discover(app).associateBy { it.id }
        fun capability(id: String, title: String, core: Boolean = false) {
            val n = mesh[id]
            val level = when {
                n == null || !n.available -> if (core) Level.BLOCKED else Level.PARTIAL
                n.readyNow -> Level.PASS
                else -> Level.PARTIAL
            }
            add(title, level, n?.reason ?: "غير موجود")
        }
        capability("hakim_browser", "متصفح حكيم")
        capability("in_app_reasoning", "الاستدلال المتقدم")
        capability("speech_input", "الإدخال الصوتي")
        capability("tts_output", "الرد الصوتي")
        capability("local_adb", "الاتصال المحلي ADB")
        capability("validated_network", "الشبكة الموثقة")
        capability("trusted_updater", "منظومة التحديث")
        capability("secure_store", "قدرة التخزين الآمن", core = true)

        val blocked = checks.count { it.level == Level.BLOCKED }
        val partial = checks.count { it.level == Level.PARTIAL }
        val overall = when {
            blocked > 0 -> Level.BLOCKED
            partial > 0 -> Level.PARTIAL
            else -> Level.PASS
        }
        val report = JSONObject()
            .put("field_validation", true)
            .put("overall", overall.name)
            .put("time", System.currentTimeMillis())
            .put("blocked", blocked)
            .put("partial", partial)
            .put("checks", JSONArray(checks.map { it.toJson() }))
            .put("field_verified", false)
            .put("field_verified_requires_real_scenarios", true)
            .put("human_equivalence_not_claimed", true)
            .put("note", "هذا الفحص يثبت جاهزية مكونات الهاتف فقط؛ FIELD_VERIFIED يتطلب نجاح سيناريوهات فعلية من البداية للنهاية على الجهاز.")

        app.getSharedPreferences("hakim_field_validation", Context.MODE_PRIVATE).edit()
            .putString("last_report", report.toString().take(60000))
            .putString("last_overall", overall.name)
            .putLong("last_run_at", System.currentTimeMillis())
            .apply()
        return report
    }

    fun last(context: Context): JSONObject? = runCatching {
        val raw = context.applicationContext.getSharedPreferences("hakim_field_validation", Context.MODE_PRIVATE)
            .getString("last_report", "").orEmpty()
        if (raw.isBlank()) null else JSONObject(raw)
    }.getOrNull()

    private fun packageVersion(context: Context): Long = runCatching {
        val i = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong()
    }.getOrDefault(0L)

    private fun signerSha256(context: Context): String = runCatching {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return@runCatching ""
            val cert = if (signing.hasMultipleSigners()) signing.apkContentsSigners.firstOrNull() else signing.signingCertificateHistory.firstOrNull()
            cert?.toByteArray() ?: return@runCatching ""
        } else {
            @Suppress("DEPRECATION") val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION") info.signatures?.firstOrNull()?.toByteArray() ?: return@runCatching ""
        }
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
    }.getOrDefault("")
}
