package ps.hakim.phoneagent

fun main() {
    val eligible = listOf("QUEUED", "RUNNING", "VERIFYING", "WAITING", "BLOCKED", "PAUSED", "FAILED")
    eligible.forEach { check(HakimResumeEligibility.canResume(it, true)) }
    eligible.forEach { check(!HakimResumeEligibility.canResume(it, false)) }
    listOf("COMPLETE", "CANCELLED", "", "UNKNOWN").forEach { check(!HakimResumeEligibility.canResume(it, true)) }
    println("RESUME_ELIGIBILITY_TEST=PASS 18/18")
}
