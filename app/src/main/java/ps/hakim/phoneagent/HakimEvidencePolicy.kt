package ps.hakim.phoneagent

/**
 * Maps a goal to the minimum observation needed before Hakim may close it.
 * This prevents UI text such as "done" from proving high-impact real-world actions.
 */
object HakimEvidencePolicy {
    enum class Requirement { UI_OBSERVED, OS_OBSERVED, USER_CONFIRMED }

    private val osObservedMarkers = listOf(
        "ثبت", "ثبّت", "تثبيت", "حدّث التطبيق", "حدث التطبيق", "احذف", "إزالة التطبيق",
        "غيّر الإعداد", "غير الإعداد", "فعّل", "عطّل", "امنح صلاحية", "اسحب صلاحية"
    )

    private val userConfirmedMarkers = listOf(
        "ادفع", "دفع", "اشتر", "شراء", "احجز", "حجز", "انشر", "نشر",
        "أرسل الرسالة", "ارسل الرسالة", "وقّع", "وقع", "وافق نيابة", "اقبل نيابة"
    )

    fun requirementFor(goal: String): Requirement {
        val q = goal.lowercase()
        return when {
            userConfirmedMarkers.any { q.contains(it) } -> Requirement.USER_CONFIRMED
            osObservedMarkers.any { q.contains(it) } -> Requirement.OS_OBSERVED
            else -> Requirement.UI_OBSERVED
        }
    }

    fun accepts(goal: String, stage: HakimGoalSupervisor.EvidenceStage): Boolean {
        return when (requirementFor(goal)) {
            Requirement.UI_OBSERVED ->
                stage in setOf(
                    HakimGoalSupervisor.EvidenceStage.UI_OBSERVED,
                    HakimGoalSupervisor.EvidenceStage.OS_INSTALLED,
                    HakimGoalSupervisor.EvidenceStage.USER_CONFIRMED
                )
            Requirement.OS_OBSERVED ->
                stage in setOf(
                    HakimGoalSupervisor.EvidenceStage.OS_INSTALLED,
                    HakimGoalSupervisor.EvidenceStage.USER_CONFIRMED
                )
            Requirement.USER_CONFIRMED ->
                stage == HakimGoalSupervisor.EvidenceStage.USER_CONFIRMED
        }
    }
}
