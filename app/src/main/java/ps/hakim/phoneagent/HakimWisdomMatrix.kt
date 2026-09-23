package ps.hakim.phoneagent

/**
 * مصفوفة قرار متعددة المعايير.
 *
 * القيود الحاكمة تُطبق أولًا؛ الدرجة لا تستطيع تعويض خرق الأمان/التفويض.
 */
object HakimWisdomMatrix {
    data class Candidate(
        val id: String,
        val supported: Boolean,
        val authorized: Boolean,
        val directReturn: Boolean,
        val officialChannel: Boolean,
        val fieldVerified: Boolean,
        val quality: Int,
        val reliability: Int,
        val privacy: Int,
        val costEfficiency: Int,
        val latency: Int,
        val reversibility: Int
    )

    data class Ranked(
        val candidate: Candidate,
        val score: Int,
        val rejectedReason: String? = null
    )

    fun rank(candidates: List<Candidate>): List<Ranked> =
        candidates.map { c ->
            val rejection = when {
                !c.supported -> "لا يدعم المهمة"
                !c.authorized -> "غير مأذون"
                !c.directReturn -> "لا يعيد النتيجة إلى حكيم"
                !c.officialChannel -> "قناة غير رسمية"
                else -> null
            }
            if (rejection != null) {
                Ranked(c, Int.MIN_VALUE, rejection)
            } else {
                Ranked(c, weightedScore(c), null)
            }
        }.sortedWith(
            compareByDescending<Ranked> { it.rejectedReason == null }
                .thenByDescending { it.score }
                .thenBy { it.candidate.id }
        )

    fun choose(candidates: List<Candidate>): Candidate? =
        rank(candidates).firstOrNull { it.rejectedReason == null }?.candidate

    private fun weightedScore(c: Candidate): Int {
        fun bounded(v: Int) = v.coerceIn(0, 100)
        return (
            bounded(c.quality) * 30 +
            bounded(c.reliability) * 20 +
            bounded(c.privacy) * 15 +
            bounded(c.costEfficiency) * 15 +
            bounded(c.latency) * 8 +
            bounded(c.reversibility) * 7 +
            (if (c.fieldVerified) 100 else 0) * 5
        ) / 100
    }
}
