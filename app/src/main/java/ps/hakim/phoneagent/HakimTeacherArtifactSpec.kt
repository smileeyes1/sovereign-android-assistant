package ps.hakim.phoneagent

/**
 * المصدر الدلالي الوحيد لأوراق الرياضيات الحتمية.
 *
 * لا يُسمح لأي Renderer أن يخترع محتوى مستقلًا عن هذه المواصفة.
 */
data class HakimTeacherArtifactSpec(
    val id: String,
    val title: String,
    val subject: String,
    val grade: String,
    val instruction: String,
    val problems: List<MathProblem>
) {
    enum class Operation(val symbol: String) {
        ADDITION("+"),
        SUBTRACTION("−")
    }

    data class MathProblem(
        val number: Int,
        val a: Int,
        val b: Int,
        val operation: Operation
    ) {
        init {
            require(number > 0)
            require(a in 0..10 && b in 0..10)
            when (operation) {
                Operation.ADDITION -> require(a + b <= 10)
                Operation.SUBTRACTION -> require(a >= b)
            }
        }

        fun result(): Int = when (operation) {
            Operation.ADDITION -> a + b
            Operation.SUBTRACTION -> a - b
        }
    }

    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(subject == "الرياضيات")
        require(grade.isNotBlank())
        require(instruction.isNotBlank())
        require(problems.size in 6..10)
        require(problems.map { it.number } == (1..problems.size).toList())
        require(problems.all { it.result() in 0..10 })
        require(listOf(title, subject, grade, instruction).none { it.contains(Regex("[A-Za-z]")) })
    }

    val fileStem: String
        get() = when (id) {
            ADDITION_ID -> "ورقة_عمل_الجمع_ضمن_١٠"
            SUBTRACTION_ID -> "ورقة_عمل_الطرح_ضمن_١٠"
            MIXED_ID -> "ورقة_عمل_الجمع_والطرح_ضمن_١٠"
            else -> "ورقة_عمل_رياضيات"
        }

    companion object {
        const val VERSION = "TEACHER-ARTIFACT-SPEC-2026-09-25-v2"
        const val ADDITION_ID = "worksheet_addition_within_10"
        const val SUBTRACTION_ID = "worksheet_subtraction_within_10"
        const val MIXED_ID = "worksheet_addition_subtraction_within_10"

        fun additionWithinTen(): HakimTeacherArtifactSpec =
            HakimTeacherArtifactSpec(
                id = ADDITION_ID,
                title = "ورقة عمل: الجمع ضمن ١٠",
                subject = "الرياضيات",
                grade = "الصف الأول",
                instruction = "أوجد ناتج الجمع، ثم اكتب الإجابة في المربع.",
                problems = listOf(
                    MathProblem(1, 1, 2, Operation.ADDITION),
                    MathProblem(2, 3, 4, Operation.ADDITION),
                    MathProblem(3, 5, 2, Operation.ADDITION),
                    MathProblem(4, 6, 3, Operation.ADDITION),
                    MathProblem(5, 4, 4, Operation.ADDITION),
                    MathProblem(6, 7, 2, Operation.ADDITION),
                    MathProblem(7, 1, 8, Operation.ADDITION),
                    MathProblem(8, 5, 5, Operation.ADDITION)
                )
            )

        fun subtractionWithinTen(): HakimTeacherArtifactSpec =
            HakimTeacherArtifactSpec(
                id = SUBTRACTION_ID,
                title = "ورقة عمل: الطرح ضمن ١٠",
                subject = "الرياضيات",
                grade = "الصف الأول",
                instruction = "أوجد ناتج الطرح، ثم اكتب الإجابة في المربع.",
                problems = listOf(
                    MathProblem(1, 5, 2, Operation.SUBTRACTION),
                    MathProblem(2, 7, 3, Operation.SUBTRACTION),
                    MathProblem(3, 8, 4, Operation.SUBTRACTION),
                    MathProblem(4, 9, 5, Operation.SUBTRACTION),
                    MathProblem(5, 6, 1, Operation.SUBTRACTION),
                    MathProblem(6, 10, 4, Operation.SUBTRACTION),
                    MathProblem(7, 4, 2, Operation.SUBTRACTION),
                    MathProblem(8, 10, 7, Operation.SUBTRACTION)
                )
            )

        fun mixedWithinTen(): HakimTeacherArtifactSpec =
            HakimTeacherArtifactSpec(
                id = MIXED_ID,
                title = "ورقة عمل: الجمع والطرح ضمن ١٠",
                subject = "الرياضيات",
                grade = "الصف الأول",
                instruction = "أوجد الناتج، ثم اكتب الإجابة في المربع.",
                problems = listOf(
                    MathProblem(1, 2, 3, Operation.ADDITION),
                    MathProblem(2, 6, 2, Operation.SUBTRACTION),
                    MathProblem(3, 4, 5, Operation.ADDITION),
                    MathProblem(4, 9, 3, Operation.SUBTRACTION),
                    MathProblem(5, 1, 7, Operation.ADDITION),
                    MathProblem(6, 8, 5, Operation.SUBTRACTION),
                    MathProblem(7, 3, 6, Operation.ADDITION),
                    MathProblem(8, 10, 6, Operation.SUBTRACTION)
                )
            )

        fun byId(id: String?): HakimTeacherArtifactSpec? = when (id) {
            ADDITION_ID -> additionWithinTen()
            SUBTRACTION_ID -> subtractionWithinTen()
            MIXED_ID -> mixedWithinTen()
            else -> null
        }

        fun resolveExplicit(text: String): HakimTeacherArtifactSpec? {
            val q = normalize(text)
            if (!withinTen(q)) return null
            val add = listOf("الجمع", "جمع", "addition", "joining").any { q.contains(it) }
            val sub = listOf("الطرح", "طرح", "subtraction").any { q.contains(it) }
            return when {
                add && sub -> mixedWithinTen()
                sub -> subtractionWithinTen()
                add -> additionWithinTen()
                else -> null
            }
        }

        fun looksLikeWorksheetArtifactRequest(text: String): Boolean {
            val q = normalize(text)
            val artifact = listOf(
                "ورقة عمل", "ورقه عمل", "worksheet",
                "pdf", "بي دي اف", "بى دى اف",
                "للتحميل", "تحميل", "للطباعة", "طباعة",
                "ملف", "docx", "word", "وورد", "html", "png", "صورة"
            ).any { q.contains(it) }
            val math = listOf("الجمع", "جمع", "الطرح", "طرح", "addition", "subtraction", "joining").any { q.contains(it) }
            return artifact && math
        }

        fun looksLikeArtifactFollowUp(text: String): Boolean {
            val q = normalize(text)
            val output = listOf(
                "pdf", "بي دي اف", "بى دى اف", "ملف",
                "للتحميل", "تحميل", "للطباعة", "طباعة",
                "docx", "word", "وورد", "html", "png", "صورة"
            ).any { q.contains(it) }
            val referent = listOf(
                "ورقة العمل", "ورقه العمل", "الورقة", "الورقه",
                "هذه", "هذي", "نفسها", "حولها", "حوّلها",
                "اريدها", "أريدها", "نفس الملف", "الملف"
            ).any { q.contains(it) }
            return output && referent
        }

        private fun withinTen(q: String): Boolean =
            listOf(
                "ضمن ١٠", "ضمن 10", "حتى ١٠", "حتى 10",
                "إلى ١٠", "الى ١٠", "العدد ١٠", "العدد 10",
                "within 10", "within ten"
            ).any { q.contains(it) }

        private fun normalize(text: String): String =
            text.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
