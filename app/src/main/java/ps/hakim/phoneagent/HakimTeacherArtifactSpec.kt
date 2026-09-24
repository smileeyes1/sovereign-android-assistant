package ps.hakim.phoneagent

/**
 * Canonical semantic source for deterministic Palestinian-teacher artifacts.
 * Renderers must consume this spec instead of inventing independent content per format.
 */
data class HakimTeacherArtifactSpec(
    val id: String,
    val title: String,
    val subject: String,
    val grade: String,
    val instruction: String,
    val problems: List<MathProblem>
) {
    data class MathProblem(val number: Int, val a: Int, val b: Int)

    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(problems.isNotEmpty())
        require(problems.all { it.a >= 0 && it.b >= 0 && it.a + it.b <= 10 })
    }

    companion object {
        const val VERSION = "TEACHER-ARTIFACT-SPEC-2026-09-24-v1"

        fun additionWithinTen(): HakimTeacherArtifactSpec =
            HakimTeacherArtifactSpec(
                id = "worksheet_addition_within_10",
                title = "ورقة عمل: الجمع ضمن ١٠",
                subject = "الرياضيات",
                grade = "الصف الأول",
                instruction = "أوجد ناتج الجمع، ثم اكتب الإجابة في المربع.",
                problems = listOf(
                    MathProblem(1, 1, 2),
                    MathProblem(2, 3, 4),
                    MathProblem(3, 5, 2),
                    MathProblem(4, 6, 3),
                    MathProblem(5, 4, 4),
                    MathProblem(6, 7, 2),
                    MathProblem(7, 1, 8),
                    MathProblem(8, 5, 5),
                    MathProblem(9, 2, 6),
                    MathProblem(10, 3, 6)
                )
            )
    }
}
