package ps.hakim.phoneagent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * قابلية نقل محلية صريحة. لا تصدّر كلمات المرور/OTP/PIN/CVV/المفاتيح أو هوية التوقيع الخاصة.
 * التصدير لا يحدث إلا بفعل المستخدم إلى وجهة يختارها نظام Android.
 */
object HakimSovereignPortability {
    const val SCHEMA_VERSION = 1
    private const val MAX_BACKUP_BYTES = 512 * 1024
    private const val MAX_SITES = 80
    private const val MAX_TRUSTED_HOSTS = 120

    data class ImportResult(val success: Boolean, val changed: Int, val message: String)

    fun exportJson(context: Context): String {
        HakimQuranicInvariantKernel.requireInherited("sovereign_export")
        val sites = JSONObject()
        HakimGovernanceStore.allSites(context).forEach { (host, instructions) ->
            sites.put(host, instructions.take(12000))
        }
        val profile = JSONObject()
        HakimPersonalVault.all(context).forEach { (id, value) ->
            profile.put(id, value.take(4000))
        }
        val trusted = JSONArray()
        HakimSiteTrust.trustedHosts(context).sorted().forEach { trusted.put(it) }

        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("package", "ps.hakim.stable")
            .put("exported_at", System.currentTimeMillis())
            .put("contains_secrets", false)
            .put("contains_signing_private_key", false)
            .put("governance_global", HakimGovernanceStore.global(context).take(24000))
            .put("site_instructions", sites)
            .put("profile_non_sensitive", profile)
            .put("trusted_profile_hosts", trusted)
            .put("proactive_enabled", HakimProactiveEngine.isEnabled(context))
            .put("share_profile_with_reasoning", HakimPersonalVault.reasoningSharingEnabled(context))
            .put("note", "نسخة سيادية غير سرية؛ كلمات المرور ورموز التحقق والبطاقات والمفاتيح الخاصة مستبعدة عمدًا")
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
        if (root.optInt("schema_version", -1) != SCHEMA_VERSION) {
            return ImportResult(false, 0, "إصدار مخطط النسخة غير مدعوم")
        }
        if (root.optString("package") != "ps.hakim.stable") {
            return ImportResult(false, 0, "النسخة ليست لتطبيق حكيم المعتمد")
        }
        if (root.optBoolean("contains_secrets", false) || root.optBoolean("contains_signing_private_key", false)) {
            return ImportResult(false, 0, "رفضت النسخة لأنها تعلن احتواء أسرار أو مفتاح توقيع")
        }

        val global = root.optString("governance_global", "").take(24000)
        val sitesObj = root.optJSONObject("site_instructions") ?: JSONObject()
        val profileObj = root.optJSONObject("profile_non_sensitive") ?: JSONObject()
        val trustedArr = root.optJSONArray("trusted_profile_hosts") ?: JSONArray()
        if (sitesObj.length() > MAX_SITES || trustedArr.length() > MAX_TRUSTED_HOSTS) {
            return ImportResult(false, 0, "النسخة تحتوي عددًا غير معقول من المواقع")
        }

        val sites = linkedMapOf<String, String>()
        sitesObj.keys().forEach { host -> sites[host] = sitesObj.optString(host, "").take(12000) }
        val trusted = linkedSetOf<String>()
        for (i in 0 until trustedArr.length()) {
            trustedArr.optString(i).trim().takeIf { it.isNotBlank() }?.let { trusted += it }
        }

        val profile = linkedMapOf<String, String>()
        HakimPersonalVault.fields.forEach { field ->
            if (profileObj.has(field.id)) profile[field.id] = profileObj.optString(field.id, "").take(4000)
        }

        var changed = 0
        if (!HakimGovernanceStore.setGlobal(context, global)) return ImportResult(false, changed, "تعذر استعادة النظام الحاكم")
        changed++
        if (!HakimGovernanceStore.replaceSites(context, sites)) return ImportResult(false, changed, "تعذر استعادة تعليمات المواقع")
        changed += sites.size
        if (!HakimSiteTrust.replaceTrustedHosts(context, trusted)) return ImportResult(false, changed, "تعذر استعادة ثقة المواقع")
        changed += trusted.size
        profile.forEach { (id, value) ->
            if (HakimPersonalVault.save(context, id, value)) changed++
        }
        HakimPersonalVault.setReasoningSharing(context, root.optBoolean("share_profile_with_reasoning", false))
        HakimProactiveEngine.setEnabled(context, root.optBoolean("proactive_enabled", true))
        changed += 2
        return ImportResult(true, changed, "تمت الاستعادة محليًا. لم تُستورد أسرار أو مفاتيح توقيع.")
    }

    fun status(context: Context): JSONObject = JSONObject()
        .put("sovereign_portability", true)
        .put("explicit_user_export", true)
        .put("explicit_user_import_confirmation", true)
        .put("portable_governance", true)
        .put("portable_non_sensitive_profile", true)
        .put("portable_site_trust", true)
        .put("secrets_excluded", true)
        .put("signing_private_key_excluded", true)
        .put("current_sites", HakimGovernanceStore.allSites(context).size)
        .put("current_trusted_hosts", HakimSiteTrust.trustedHosts(context).size)
}
