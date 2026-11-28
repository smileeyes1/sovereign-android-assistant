package ps.hakim.phoneagent

fun main() {
    data class Entry(val id: Int, val state: String, val time: Long)
    fun select(items: List<Entry>, limit: Int = 48) = HakimTaskRetention.select(items, limit, { it.state in setOf("COMPLETE", "CANCELLED") }, { it.time })
    val unfinished = listOf("QUEUED", "RUNNING", "VERIFYING", "WAITING", "BLOCKED", "PAUSED", "FAILED").mapIndexed { i, s -> Entry(i, s, i.toLong()) }
    val history = (100..199).map { Entry(it, if (it % 2 == 0) "COMPLETE" else "CANCELLED", it.toLong()) }
    val result = select(unfinished + history)
    check(result.containsAll(unfinished))
    check(result.count { it in history } == 48)
    check(result.filter { it in history }.map { it.id } == (199 downTo 152).toList())
    check(select(unfinished + history, 0).toSet() == unfinished.toSet())
    val many = (0..99).map { Entry(it, "QUEUED", it.toLong()) }
    check(select(many).size == 100)
    check(select(emptyList()).isEmpty())
    check(runCatching { select(history, -1) }.isFailure)
    check(select(result) == result)
    println("TASK_RETENTION_TEST=PASS 8/8")
}
