package ps.hakim.phoneagent

import android.content.Context

/**
 * خزنة محلية للبيانات الشخصية المتكررة غير الحساسة.
 * كلمات المرور ورموز التحقق والبطاقات والمفاتيح السرية ممنوعة هنا عمدًا.
 */
object HakimPersonalVault {
    data class Field(val id: String, val title: String, val aliases: List<String>)

    private const val PREFS = "hakim_personal_vault_secure"
    private const val META = "hakim_personal_vault_meta"
    private const val KEY_INDEX = "field_index"
    private const val SHARE_WITH_REASONING = "share_profile_with_reasoning"

    val fields = listOf(
        Field("full_name", "الاسم الكامل", listOf("الاسم الكامل", "الاسم الثلاثي", "full name")),
        Field("first_name", "الاسم الأول", listOf("الاسم الأول", "الاسم الاول", "first name", "given name")),
        Field("last_name", "اسم العائلة", listOf("اسم العائلة", "اسم العائله", "اللقب", "last name", "surname", "family name")),
        Field("email", "البريد الإلكتروني", listOf("البريد", "البريد الإلكتروني", "الايميل", "الإيميل", "email", "e mail")),
        Field("phone", "رقم الهاتف", listOf("الهاتف", "رقم الهاتف", "الجوال", "الموبايل", "phone", "mobile")),
        Field("address", "العنوان", listOf("العنوان", "عنوان السكن", "address", "street address")),
        Field("city", "المدينة/البلدة", listOf("المدينة", "البلدة", "city", "town")),
        Field("country", "الدولة", listOf("الدولة", "البلد", "country")),
        Field("job_title", "المسمى الوظيفي", listOf("المسمى الوظيفي", "الوظيفة", "المهنة", "job title", "occupation")),
        Field("organization", "جهة العمل", listOf("جهة العمل", "المؤسسة", "المدرسة", "الشركة", "organization", "company", "school", "employer"))
    )

    private val allowedFieldIds: Set<String> by lazy { fields.map { it.id }.toSet() }

    fun save(context: Context, id: String, value: String): Boolean {
        val key = id.trim().lowercase()
        val clean = value.trim()
        if (key !in allowedFieldIds || forbidden(key) || forbidden(clean)) return false
        if (clean.isBlank()) {
            remove(context, key)
            return true
        }
        val ok = HakimSecureStore.put(context, PREFS, key, clean.take(4000))
        if (ok) {
            val meta = context.getSharedPreferences(META, Context.MODE_PRIVATE)
            val index = LinkedHashSet(meta.getStringSet(KEY_INDEX, emptySet()) ?: emptySet())
            index += key
            meta.edit().putStringSet(KEY_INDEX, index).apply()
        }
        return ok
    }

    fun get(context: Context, id: String): String? {
        val key = id.trim().lowercase()
        if (key !in allowedFieldIds) return null
        return HakimSecureStore.get(context, PREFS, key)
    }

    fun remove(context: Context, id: String) {
        val key = id.trim().lowercase()
        if (key !in allowedFieldIds) return
        HakimSecureStore.remove(context, PREFS, key)
        val meta = context.getSharedPreferences(META, Context.MODE_PRIVATE)
        val index = LinkedHashSet(meta.getStringSet(KEY_INDEX, emptySet()) ?: emptySet())
        if (index.remove(key)) meta.edit().putStringSet(KEY_INDEX, index).apply()
    }

    fun all(context: Context): Map<String, String> {
        val meta = context.getSharedPreferences(META, Context.MODE_PRIVATE)
        val index = meta.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()
        val out = linkedMapOf<String, String>()
        index.filter { it in allowedFieldIds }.sorted().forEach { id ->
            get(context, id)?.takeIf { it.isNotBlank() }?.let { out[id] = it }
        }
        return out
    }

    /** يحاول مطابقة تسمية الحقل الظاهر مع قيمة مخزنة محليًا دون تمريرها للنموذج. */
    fun valueForLabel(context: Context, rawLabel: String): Pair<Field, String>? {
        val label = normalize(rawLabel)
        if (label.isBlank() || forbidden(label)) return null
        for (field in fields) {
            val fieldId = normalize(field.id)
            val match = phraseMatch(label, fieldId) || field.aliases.any { phraseMatch(label, normalize(it)) }
            if (!match) continue
            val value = get(context, field.id)?.trim().orEmpty()
            if (value.isNotBlank()) return field to value
        }
        return null
    }

    fun setReasoningSharing(context: Context, enabled: Boolean) {
        context.getSharedPreferences(META, Context.MODE_PRIVATE).edit()
            .putBoolean(SHARE_WITH_REASONING, enabled)
            .apply()
    }

    fun reasoningSharingEnabled(context: Context): Boolean =
        context.getSharedPreferences(META, Context.MODE_PRIVATE)
            .getBoolean(SHARE_WITH_REASONING, false)

    /**
     * النموذج يرى أسماء الحقول المتاحة دائمًا فقط؛ القيم لا تظهر إلا مع تفعيل المشاركة
     * وذكر الحقل نفسه صراحة في المهمة. التعبئة العامة تعتمد fill_profile محليًا.
     */
    fun promptContext(context: Context, task: String = ""): String {
        val available = fields.filter { !get(context, it.id).isNullOrBlank() }
        if (available.isEmpty()) return ""
        val taskNorm = normalize(task)
        val explicitlyRelevant = if (reasoningSharingEnabled(context) && taskNorm.isNotBlank()) {
            available.filter { field ->
                phraseMatch(taskNorm, normalize(field.id)) ||
                    phraseMatch(taskNorm, normalize(field.title)) ||
                    field.aliases.any { phraseMatch(taskNorm, normalize(it)) }
            }
        } else emptyList()

        return buildString {
            appendLine("[خزنة بيانات حكيم المحلية]")
            appendLine("الحقول المتاحة محليًا: ${available.joinToString("، ") { "${it.id}=${it.title}" }}")
            appendLine("للتعبئة استخدم fill_profile{target,field_id} حتى تبقى القيمة على الجهاز.")
            if (explicitlyRelevant.isNotEmpty()) {
                appendLine("[قيم غير حساسة سمح المستخدم بمشاركتها وذكرها صراحة في هذه المهمة]")
                explicitlyRelevant.forEach { field ->
                    val value = get(context, field.id).orEmpty()
                    if (value.isNotBlank()) appendLine("• ${field.title}: ${value.take(500)}")
                }
            }
        }.take(3500)
    }

    private fun phraseMatch(label: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        if (label == phrase) return true
        return " $label ".contains(" $phrase ")
    }

    private fun normalize(v: String): String = v
        .replace(Regex("([a-z])([A-Z])")) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }
        .lowercase()
        .replace(Regex("[_\\-.:/]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun forbidden(v: String): Boolean = Regex(
        "(?i)(password|passcode|\\botp\\b|\\bpin\\b|cvv|cvc|card.?number|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|رقم.?البطاقة|مفتاح.?سري)"
    ).containsMatchIn(v)
}
