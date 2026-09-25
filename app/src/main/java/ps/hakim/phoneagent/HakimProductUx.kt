package ps.hakim.phoneagent

/**
 * طبقة العرض النهائية للمستخدم. التفاصيل التقنية تبقى في القياس/السجل الداخلي
 * ولا تُعرض في الواجهة العادية إلا بطلب صريح من المستخدم.
 */
object HakimProductUx {
    const val VERSION = "PRODUCT-UX-2026-09-24-v1"

    fun publicStatus(internal: String): String {
        val s = internal.lowercase()
        return when {
            s.contains("ينشئ") || s.contains("pdf") || s.contains("ملف") -> "يجهّز الملف…"
            s.contains("يبحث") || s.contains("متصفح") || s.contains("ويب") -> "يبحث ويجهّز النتيجة…"
            s.contains("محرك") || s.contains("نموذج") || s.contains("يجيب") -> "يعمل على طلبك…"
            s.contains("مرفق") -> "يعالج المرفقات…"
            s.contains("فشل") || s.contains("تعذر") || s.contains("خطأ") -> "تعذر إكمال الطلب"
            s.contains("اكتمل") || s.contains("جاهز") -> "جاهز"
            else -> "يعمل على طلبك…"
        }
    }

    fun publicError(raw: String): String {
        val r = raw.lowercase()
        return when {
            r.contains("شبك") || r.contains("timeout") || r.contains("اتصال") ->
                "تعذر الاتصال مؤقتًا. سيحاول حكيم استخدام مسار آخر تلقائيًا."
            r.contains("صلاح") || r.contains("permission") ->
                "تحتاج هذه المهمة صلاحية إضافية منك."
            r.contains("تفويض") || r.contains("oauth") || r.contains("authorization") ->
                "تحتاج هذه الميزة ربطًا لمرة واحدة قبل استخدامها."
            else ->
                "تعذر إكمال الطلب بهذه الوسيلة. سيحاول حكيم مسارًا آخر إن كان متاحًا."
        }
    }

    fun completionMessage(kind: String, location: String? = null): String = when (kind) {
        "pdf" -> if (location.isNullOrBlank()) "الملف جاهز." else "الملف جاهز ومحفوظ في $location."
        "browser" -> "اكتملت المهمة وأصبحت النتيجة جاهزة."
        else -> "اكتملت المهمة."
    }

    fun exposeDiagnosticsInPrimaryUi(): Boolean = false
}
