package ps.hakim.phoneagent

/** Keep every unfinished task; bound only completed/cancelled history. */
internal object HakimTaskRetention {
    fun <T> select(items: List<T>, historyLimit: Int, isTerminal: (T) -> Boolean, updatedAt: (T) -> Long): List<T> {
        require(historyLimit >= 0)
        val ordered = items.sortedByDescending(updatedAt)
        var history = 0
        return ordered.filter { item ->
            if (!isTerminal(item)) true else if (history < historyLimit) { history++; true } else false
        }
    }
}
