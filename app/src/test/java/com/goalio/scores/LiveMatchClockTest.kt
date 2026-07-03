package com.goalio.scores

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LiveMatchClockTest {
    @Test
    fun clockNeverRegressesAndStopsAtMatchBreaks() {
        val key = "eng.1:test-${System.nanoTime()}"
        val start = Instant.parse("2026-07-03T18:36:00Z")

        assertEquals("LIVE 36'00\"", LiveMatchClockStore.label(key, "in", "36'", "Live", start))
        assertEquals("LIVE 36'50\"", LiveMatchClockStore.label(key, "in", "36'", "Live", start.plusSeconds(50)))
        assertEquals("LIVE 37'00\"", LiveMatchClockStore.label(key, "in", "35'", "Live", start.plusSeconds(60)))
        assertEquals("LIVE 38'00\"", LiveMatchClockStore.label(key, "in", "38'", "Live", start.plusSeconds(65)))
        assertEquals("LIVE 77'00\"", LiveMatchClockStore.label(key, "in", "56'", "77' - Second Half", start.plusSeconds(70)))
        assertEquals("HT", LiveMatchClockStore.label(key, "in", "HT", "Half Time", start.plusSeconds(70)))
        assertEquals("HT", LiveMatchClockStore.label(key, "in", "HT", "Half Time", start.plusSeconds(300)))
        assertEquals("FT", LiveMatchClockStore.label(key, "post", "FT", "Full Time", start.plusSeconds(3600)))
    }
}
