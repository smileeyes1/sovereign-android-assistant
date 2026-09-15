package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * قابلية نقل محلية صريحة. لا تصدّر كلمات المرور/OTP/PIN/CVV/المفاتيح أو هوية التوقيع الخاصة.
 * قد تتضمن النسخة بيانات شخصية غير سرية خزّنها المستخدم (مثل الاسم/الهاتف/العنوان)، والتصدير لا يحدث إلا بفعل المستخدم إلى وجهة يختارها نظام Android.
 * الإصدار ٢ يضيف القواعد الفعالة المنقحة ودليل التعلم المحلي المجهول، دون مهمة جارية أو أسرار.
 */
object HakimSovereignPortability {
    const val SCHEMA_VERSION = 2
    private const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    private const val MAX_BACKUP_BYTES = 512 * 1024
    private const val MAX_SITES = 80
    private const val MAX_TRUSTED_HOSTS = 120

    data class ImportResult(val success: Boolean, val changed: Int, val message: String)

    private data class LocalSnapshot(
        val global: String,
        val sites: Map<String, String>,
        val profile: Map<String, String>,
        val trustedHosts: Set<String>,
        val proactiveEnabled: Boolean,
        val reasoningSharing: Boolean,
        val effectiveRules: JSONArray,
        val adaptiveEvidence: JSONObject
    )

    fun exportJson(context: Context): String {
        HakimQuranicInvariantKernel.requireInherited("sovereign_export")
        val sites = JSONObject()
        HakimGovernanceStore.allSites(context).forEach { (host, instructions) ->
            sites.put(host, HakimGovernanceStore.exportSafeText(instructions).take(12000))
        }
        val profile = JSONObject()
        HakimPersonalVault.all(context).forEach { (id, value) ->
            profile.put(id, value.take(4000))
        }
        val trusted = JSONArray()
        HakimSiteTrust.trustedHosts(context).sorted().forEach { trusted.put(it) }
        val safeGlobal = HakimGovernanceStore.exportSafeText(HakimGovernanceStore.global(context))
        val effectiveRules = HakimRuleLedger.portableRules(context)
        val adaptiveEvidence = HakimAdaptiveLearningPortability.exportEvidence(context)

        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("package", "ps.hakim.stable")
            .put("exported_at", System.currentTimeMillis())
            .put("contains_secrets", false)
            .put("contains_signing_private_key", false)
            .put("contains_pending_mission", false)
            .put("contains_personal_data", profile.length() > 0)
            .put("governance_global", safeGlobal.take(24000))
            .put("site_instructions", sites)
            .put("profile_non_sensitive", profile)
            .put("trusted_profile_hosts", trusted)
            .put("effective_rules", effectiveRules)
            .put("adaptive_learning_evidence", adaptiveEvidence)
            .put("proactive_enabled", HakimProactiveEngine.isEnabled(context))
            .put("share_profile_with_reasoning", HakimPersonalVault.reasoningSharingEnabled(context))
            .put("note", "النسخة تستبعد كلمات المرور ورموز التحقق والبطاقات والمفاتيح الخاصة والمهمة الجارية. تنقل القواعد الفعالة بعد تنقيح الأسرار ودليل التعلم المجهول، وقد تحتوي بيانات شخصية خزّنها المستخدم؛ احفظها في وجهة موثوقة يختارها بنفسه")
            .toString(2)
    }

    fun importJson(context: Context, raw: String, confirmed: Boolean): ImportResult {
        if (!confirmed) return ImportResult(false, 0, "الاستعادة تحتاج تأكيدًا صريحًا")
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            return ImportResult(false, 0, "ملف النسخة أكبر من الحد الآمن")
        }
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return ImportResult(false, 0, "ملف النسخة ليس JSON صالحًا")
        }
        val schema = root.optInt("schema_version", -1)
        if (schema !in MIN_SUPPORTED_SCHEMA_VERSION..SCHEMA_VERSION) {
            return ImportResult(false, 0, "إصدار مخطط النسخة غير مدعوم")
        }
        if (root.optString("package") != "ps.hakim.stable") {
            return ImportResult(false, 0, "النسخة ليست لتطبيق حكيم المعتمد")
        }
        if (root.optBoolean("contains_secrets", false) || root.optBoolean("contains_signing_private_key", false)) {
            return ImportResult(false, 0, "رفضت النسخة لأنها تعلن احتواء أسرار أو مفتاح توقيع")
        }
        if (root.optBoolean("contains_pending_mission", false)) {
            return ImportResult(false, 0, "رفضت النسخة لأنها تعلن احتواء مهمة جارية")
        }

        // لا نثق بإعلان الملف وحده: ننقّي نصوص الحاكمية مرة أخرى عند الاستيراد.
        val global = HakimGovernanceStore.exportSafeText(
            root.optString("governance_global", "")
        ).take(24000)
        val sitesObj = root.optJSONObject("site_instructions") ?: JSONObject()
        val profileObj = root.optJSONObject("profile_non_sensitive") ?: JSONObject()
        val trustedArr = root.optJSONArray("trusted_profile_hosts") ?: JSONArray()
        if (sitesObj.length() > MAX_SITES || trustedArr.length() > MAX_TRUSTED_HOSTS) {
            return ImportResult(false, 0, "النسخة تحتوي عددًا غير معقول من المواقع")
        }

        val sites = linkedMapOf<String, String>()
        sitesObj.keys().forEach { host ->
            sites[host] = HakimGovernanceStore.exportSafeText(
                sitesObj.optString(host, "")
            ).take(12000)
        }
        val trusted = linkedSetOf<String>()
        for (i in 0 until trustedArr.length()) {
            trustedArr.optString(i).trim().takeIf { it.isNotBlank() }?.let { trusted += it }
        }

        val profile = linkedMapOf<String, String>()
        HakimPersonalVault.fields.forEach { field ->
            if (profileObj.has(field.id)) profile[field.id] = profileObj.optString(field.id, "").take(4000)
        }

        // v1 يبقى مدعومًا بلا مسح الحالة الجديدة التي لم يكن يعرفها.
        val portableRules: JSONArray? = if (schema >= 2) {
            root.optJSONArray("effective_rules")
                ?: return ImportResult(false, 0, "نسخة الإصدار ٢ تفتقد القواعد الفعالة")
        } else null
        val adaptiveEvidence: JSONObject? = if (schema >= 2) {
            root.optJSONObject("adaptive_learning_evidence")
                ?: return ImportResult(false, 0, "نسخة الإصدار ٢ تفتقد دليل التعلم")
        } else null

        // PREVENT: التقط الحالة قبل أي كتابة حتى لا تبقى استعادة نصف مكتملة.
        val before = snapshot(context)
        fun fail(message: String): ImportResult {
            val rolledBack = restoreSnapshot(context, before)
            val suffix = if (rolledBack) {
                "أُعيدت الحالة السابقة كاملة."
            } else {
                "تعذر إثبات استعادة الحالة السابقة كاملة؛ أوقف الاستخدام الحساس وافتح فحص حكيم الذاتي."
            }
            return ImportResult(false, 0, "$message $suffix")
        }

        if (!HakimGovernanceStore.setGlobal(context, global)) {
            return fail("تعذر استعادة النظام الحاكم.")
        }
        if (!replaceSitesSafely(context, sites)) {
            return fail("تعذر استعادة تعليمات المواقع.")
        }
        if (!HakimSiteTrust.replaceTrustedHosts(context, trusted)) {
            return fail("تعذر استعادة ثقة المواقع.")
        }
        if (!replaceProfileSafely(context, profile)) {
            return fail("تعذر استعادة خزنة البيانات الشخصية القابلة للنقل.")
        }
        if (portableRules != null && !HakimRuleLedger.replacePortableRules(context, portableRules)) {
            return fail("تعذر استعادة القواعد الفعالة بصورة ذرية.")
        }
        if (adaptiveEvidence != null && !HakimAdaptiveLearningPortability.replaceEvidence(context, adaptiveEvidence)) {
            return fail("تعذر استعادة دليل التعلم المحلي.")
        }

        HakimPersonalVault.setReasoningSharing(
            context,
            root.optBoolean("share_profile_with_reasoning", false)
        )
        HakimProactiveEngine.setEnabled(
            context,
            root.optBoolean("proactive_enabled", true)
        )

        val changed = 1 + sites.size + trusted.size + profile.size + 2 +
            (portableRules?.length() ?: 0) + if (adaptiveEvidence != null) 1 else 0
        return ImportResult(
            true,
            changed,
            if (schema >= 2) {
                "تمت الاستعادة السيادية محليًا كوحدة واحدة قابلة للتراجع، بما فيها القواعد الفعالة ودليل التعلم. لم تُستورد أسرار أو مهمة جارية أو مفاتيح توقيع."
            } else {
                "تمت استعادة النسخة القديمة محليًا مع حفظ القواعد ودليل التعلم الحاليين لأن هذا المخطط القديم لم يكن ينقلهما."
            }
        )
    }

    /** يكتب الهدف أولًا ثم يحذف القديم؛ وعند فشل أي كتابة يعيد لقطة المواقع السابقة. */
    private fun replaceSitesSafely(context: Context, target: Map<String, String>): Boolean {
        val before = HakimGovernanceStore.allSites(context)
        for ((host, instructions) in target) {
            if (instructions.isBlank()) continue
            if (!HakimGovernanceStore.setSite(context, host, instructions)) {
                restoreSitesBestEffort(context, before)
                return false
            }
        }
        val desired = target.filterValues { it.isNotBlank() }.keys
        before.keys.filter { it !in desired }.forEach { HakimGovernanceStore.setSite(context, it, "") }
        return true
    }

    /** يجعل الخزنة مساوية للنسخة، لا مجرد دمجها؛ ويعيد القيم القديمة عند فشل كتابة مشفرة. */
    private fun replaceProfileSafely(context: Context, target: Map<String, String>): Boolean {
        val before = HakimPersonalVault.all(context)
        for ((id, value) in target) {
            if (value.isBlank()) continue
            if (!HakimPersonalVault.save(context, id, value)) {
                restoreProfileBestEffort(context, before)
                return false
            }
        }
        val desired = target.filterValues { it.isNotBlank() }.keys
        HakimPersonalVault.fields.map { it.id }.filter { it !in desired }.forEach {
            HakimPersonalVault.remove(context, it)
        }
        return true
    }

    private fun snapshot(context: Context): LocalSnapshot = LocalSnapshot(
        global = HakimGovernanceStore.global(context),
        sites = HakimGovernanceStore.allSites(context),
        profile = HakimPersonalVault.all(context),
        trustedHosts = HakimSiteTrust.trustedHosts(context),
        proactiveEnabled = HakimProactiveEngine.isEnabled(context),
        reasoningSharing = HakimPersonalVault.reasoningSharingEnabled(context),
        effectiveRules = HakimRuleLedger.portableRules(context),
        adaptiveEvidence = HakimAdaptiveLearningPortability.exportEvidence(context)
    )

    private fun restoreSnapshot(context: Context, before: LocalSnapshot): Boolean {
        var ok = true
        if (!HakimGovernanceStore.setGlobal(context, before.global)) ok = false
        if (!restoreSitesBestEffort(context, before.sites)) ok = false
        if (!HakimSiteTrust.replaceTrustedHosts(context, before.trustedHosts)) ok = false
        if (!restoreProfileBestEffort(context, before.profile)) ok = false
        if (!HakimRuleLedger.replacePortableRules(context, before.effectiveRules)) ok = false
        if (!HakimAdaptiveLearningPortability.replaceEvidence(context, before.adaptiveEvidence)) ok = false
        HakimPersonalVault.setReasoningSharing(context, before.reasoningSharing)
        HakimProactiveEngine.setEnabled(context, before.proactiveEnabled)
        return ok
    }

    private fun restoreSitesBestEffort(context: Context, target: Map<String, String>): Boolean {
        var ok = true
        val current = HakimGovernanceStore.allSites(context)
        target.forEach { (host, instructions) ->
            if (!HakimGovernanceStore.setSite(context, host, instructions)) ok = false
        }
        current.keys.filter { it !in target.keys }.forEach {
            if (!HakimGovernanceStore.setSite(context, it, "")) ok = false
        }
        return ok
    }

    private fun restoreProfileBestEffort(context: Context, target: Map<String, String>): Boolean {
        var ok = true
        HakimPersonalVault.fields.forEach { field ->
            val value = target[field.id]
            if (value.isNullOrBlank()) {
                HakimPersonalVault.remove(context, field.id)
            } else if (!HakimPersonalVault.save(context, field.id, value)) {
                ok = false
            }
        }
        return ok
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("sovereign_portability", true)
        .put("schema_version", SCHEMA_VERSION)
        .put("explicit_user_export", true)
        .put("explicit_user_import_confirmation", true)
        .put("portable_governance", true)
        .put("portable_non_sensitive_profile", true)
        .put("portable_site_trust", true)
        .put("portable_effective_rules", true)
        .put("portable_adaptive_evidence", true)
        .put("pending_mission_excluded", true)
        .put("restore_rollback_guard", true)
        .put("profile_restore_replaces_not_merges", true)
        .put("secrets_excluded", true)
        .put("signing_private_key_excluded", true)
        .put("export_may_contain_personal_data", HakimPersonalVault.all(context).isNotEmpty())
        .put("current_sites", HakimGovernanceStore.allSites(context).size)
        .put("current_trusted_hosts", HakimSiteTrust.trustedHosts(context).size)
        .put("current_portable_rules", HakimRuleLedger.portableRules(context).length())
}
