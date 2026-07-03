package com.goalio.scores

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object LiveMatchClockStore {
    private data class ClockState(
        var elapsedSeconds: Long,
        var anchoredAt: Instant,
        var running: Boolean
    )

    private val clocks = ConcurrentHashMap<String, ClockState>()
    private val minutePattern = Regex("\\b(\\d{1,3})(?:\\+(\\d+))?['’]?")

    @Synchronized
    fun label(
        key: String,
        state: String?,
        status: String?,
        description: String?,
        now: Instant
    ): String? {
        val source = "${status.orEmpty()} ${description.orEmpty()}".trim()
        val normalized = source.lowercase()
        terminalLabel(normalized)?.let {
            clocks.remove(key)
            return it
        }
        if (isHalfTime(normalized)) {
            val clock = clocks.getOrPut(key) { ClockState(45 * 60L, now, false) }
            clock.elapsedSeconds = maxOf(clock.elapsedSeconds, 45 * 60L)
            clock.anchoredAt = now
            clock.running = false
            return "HT"
        }
        if (state != "in") return null

        val fetchedSeconds = minutePattern.findAll(source)
            .mapNotNull { match ->
                val minute = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val added = match.groupValues[2].toLongOrNull() ?: 0L
                (minute + added).takeIf { it in 0L..130L }
            }
            .maxOrNull()
            ?.times(60L)
        val clock = clocks.getOrPut(key) { ClockState(fetchedSeconds ?: 0L, now, true) }
        val locallyElapsed = clock.elapsedSeconds +
            if (clock.running) Duration.between(clock.anchoredAt, now).seconds.coerceAtLeast(0L) else 0L
        if (fetchedSeconds != null && fetchedSeconds > locallyElapsed) {
            clock.elapsedSeconds = fetchedSeconds
            clock.anchoredAt = now
        } else if (!clock.running) {
            clock.elapsedSeconds = locallyElapsed
            clock.anchoredAt = now
        }
        clock.running = true
        val elapsed = clock.elapsedSeconds + Duration.between(clock.anchoredAt, now).seconds.coerceAtLeast(0L)
        if (fetchedSeconds == null && elapsed == 0L) return "LIVE"
        return "LIVE ${elapsed / 60}'%02d\"".format(elapsed % 60)
    }

    private fun isHalfTime(value: String): Boolean =
        value == "ht" || value.contains("half time") || value.contains("halftime")

    private fun terminalLabel(value: String): String? = when {
        value.contains("pens") || value.contains("penalties") -> "PENS"
        value.contains("aet") || value.contains("after extra time") -> "AET"
        value == "ft" || value.contains("full time") || value.contains("final") -> "FT"
        else -> null
    }
}
