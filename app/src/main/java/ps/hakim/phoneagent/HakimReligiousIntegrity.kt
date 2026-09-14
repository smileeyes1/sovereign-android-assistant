package ps.hakim.phoneagent

/**
 * طبقة نزاهة للمهام الشرعية: تفصل النص الشرعي عن التفسير والاجتهاد،
 * وتمنع نسبة قول إلى القرآن أو السنة بلا تثبت مناسب.
 * لا تستخدم القرآن كآلية تقنية أو تعويذة؛ البركة معنى شرعي وليست بديلاً عن السبب التقني.
 */
object HakimReligiousIntegrity {
    data class Assessment(
        val religious: Boolean,
        val exactSourceRequired: Boolean,
        val recognizedDisagreementPossible: Boolean,
        val reason: String
    )

    fun assess(raw: String): Assessment {
        val s = raw.trim().lowercase()
        val religious = religiousRegex.containsMatchIn(s)
        if (!religious) return Assessment(false, false, false, "المهمة ليست شرعية")
        val exact = exactSourceRegex.containsMatchIn(s)
        val dispute = disagreementRegex.containsMatchIn(s)
        return Assessment(
            religious = true,
            exactSourceRequired = exact,
            recognizedDisagreementPossible = dispute,
            reason = when {
                exact -> "المهمة تتضمن نصًا أو نسبة شرعية تحتاج تثبتًا دقيقًا"
                dispute -> "المهمة قد تتضمن خلافًا فقهيًا معتبرًا"
                else -> "المهمة ذات صلة شرعية"
            }
        )
    }

    fun promptContext(raw: String): String {
        val a = assess(raw)
        if (!a.religious) return ""
        return buildString {
            appendLine("[حاكم النزاهة الشرعية]")
            appendLine("في الحكم الشرعي: القرآن الكريم والسنة الصحيحة هما المرجع الأعلى، مع التثبت من صحة النقل ودلالته وسياقه واحترام الخلاف المعتبر.")
            appendLine("ميّز صراحة بين: نص القرآن، الحديث ونسبته ودرجته عند الحاجة، التفسير المنقول، كلام أهل العلم، والاجتهاد/الترجيح البشري. لا تخلط بينها ولا تنسب الاجتهاد إلى الله أو رسوله.")
            appendLine("لا تنقل آية أو حديثًا بلفظ جازم من ذاكرة غير متحققة إذا كانت دقة اللفظ أو المرجع مؤثرة؛ تحقق من مصدر موثوق أولًا أو صرّح بعدم ثبوت اللفظ.")
            appendLine("إذا وُجد خلاف معتبر فلا تقدّم أحد الأقوال كإجماع، واذكر موضع الخلاف بقدر الحاجة دون تشويش.")
            appendLine("إذا تعارض المطلوب مع حكم شرعي ثابت فلا تُعِن على المحرم، وقدّم البديل المشروع الأقرب للغاية.")
            appendLine("الحروف المقطعة إن وردت لا تُفسر ولا تُنسب لها أسرار أو خوارزميات أو قوى تقنية؛ وأي رموز برمجية تشبهها تبقى اصطلاحًا بشريًا فقط.")
            appendLine("الاستعانة بالبسملة والدعاء والبركة تكون باحترام معناها الشرعي، ولا تُستخدم بدل التحقق والهندسة والأسباب المادية اللازمة.")
            if (a.exactSourceRequired) appendLine("هذه المهمة تتطلب تحققًا نصيًا/مصدرًا دقيقًا قبل نسبة النص.")
            if (a.recognizedDisagreementPossible) appendLine("افحص وجود خلاف معتبر قبل الترجيح أو الجزم.")
        }.take(4200)
    }

    fun requiresSourceVerification(raw: String): Boolean = assess(raw).exactSourceRequired

    private val religiousRegex = Regex(
        "(?i)(القرآن|القرءان|قرآن|قرءان|آية|اية|سورة|حديث|السنة|سنة النبي|رسول الله|النبي|حلال|حرام|فتوى|فتوى|شرعي|الشريعة|فقه|عبادة|صلاة|زكاة|صيام|حج|دعاء|ذكر|بركة|بسملة|استخارة)"
    )

    private val exactSourceRegex = Regex(
        "(?i)(نص الآية|نص الاية|اكتب الآية|اكتب الاية|قال الله|ورد في القرآن|رقم الآية|رقم الاية|اسم السورة|نص الحديث|قال الرسول|قال النبي|حديث صحيح|تخريج|درجة الحديث|رواه|أخرجه|اقتباس)"
    )

    private val disagreementRegex = Regex(
        "(?i)(حكم|فتوى|يجوز|لا يجوز|مكروه|واجب|فرض|سنة|مستحب|خلاف|اختلاف|مذهب|الفقهاء|العلماء)"
    )
}
