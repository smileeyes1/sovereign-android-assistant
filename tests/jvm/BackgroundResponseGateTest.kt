package ps.hakim.phoneagent

fun main() {
    fun accept(prompt: String, reply: String) = HakimArabicOutputGate.validateText(
        reply, HakimArabicOutputGate.audienceForPrompt(prompt),
        HakimPalestinianArabicProfile.explicitNonArabicRequested(prompt)
    ).passed
    check(accept("اشرح الجمع", "الجمع ضمّ مجموعتين."))
    check(!accept("اشرح الجمع", ""))
    check(!accept("اشرح الجمع", "   "))
    check(!accept("اشرح الجمع", "An English answer."))
    check(accept("اكتب بالإنجليزية فقط", "An English answer."))
    check(!accept("اشرح الجمع", "حدث error"))
    check(!accept("مسألة للصف الثاني", "2 + 3 = 5"))
    check(accept("مسألة للصف الثاني", "٥ = ٢ + ٣، أحسنت."))
    check(!accept("مسألة للصف الثاني", "= ٥"))
    check(!accept("مسألة للصف الثاني", "الجواب 5"))
    check(accept("اشرح الجمع", "الجواب 5"))
    println("AR_PS_BACKGROUND_GATE_TEST=PASS 11/11")
}
