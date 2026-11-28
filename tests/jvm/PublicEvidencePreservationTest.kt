package ps.hakim.phoneagent

fun main() {
    val evidence = "النتيجة: اكتمل البناء.\nالدليل والقيود\nلم يُختبر التطبيق على الهاتف."
    check(HakimProductOutput.clean(evidence) == evidence)
    val technical = "القيود التقنية: لا يوجد اتصال مباشر.\nلا يجوز ادعاء التنفيذ."
    check(HakimProductOutput.clean(technical) == technical)
    val english = "Technical limitations: device not tested."
    check(HakimProductOutput.clean(english) == english)
    val verification = "التحقق: لم يثبت الأثر."
    check(HakimProductOutput.clean(verification) == verification)
    val measure = "القياس: لم يُقَس الوقت."
    check(HakimProductOutput.clean(measure) == measure)
    val note = "ملاحظة أخيرة: يلزم إذن المالك."
    check(HakimProductOutput.clean(note) == note)
    check(HakimProductOutput.clean("الجواب\nالتحقق الداخلي\nمحتوى تشخيصي") == "الجواب")
    check(HakimProductOutput.clean("الجواب\nسلسلة التفكير\nمحتوى تشخيصي") == "الجواب")
    check(HakimProductOutput.clean("Answer\nInternal reasoning\nprivate diagnostics") == "Answer")
    check(!HakimProductOutput.containsRawMarkup(evidence))
    check(HakimProductOutput.containsRawMarkup("التحقق الداخلي"))
    check(HakimProductOutput.clean("**القيود التقنية:** لم يُختبر.") == "القيود التقنية: لم يُختبر.")
    println("PUBLIC_EVIDENCE_PRESERVATION=PASS 12/12")
}
