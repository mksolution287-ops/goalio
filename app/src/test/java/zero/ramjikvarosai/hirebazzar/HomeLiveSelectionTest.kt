package zero.ramjikvarosai.hirebazzar

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLiveSelectionTest {
    private val kickoff = Instant.parse("2026-07-04T12:00:00Z")

    @Test
    fun scheduledMatchBecomesLiveAtKickoffBeforeStateFlips() {
        val match = match(state = "pre", status = "Scheduled")

        assertFalse(match.isHomeLiveAt(kickoff.minusSeconds(1)))
        assertTrue(match.isHomeLiveAt(kickoff))
    }

    @Test
    fun halftimeIsDetectedAsLive() {
        assertTrue(match(state = "pre", status = "HT").isHomeLiveAt(kickoff.plusSeconds(60)))
    }

    @Test
    fun fullTimeAlwaysReleasesLiveMatch() {
        val match = match(state = "in", status = "FT", description = "Full Time")

        assertTrue(match.isHomeTerminal())
        assertFalse(match.isHomeLiveAt(kickoff.plusSeconds(90 * 60)))
        assertFalse(match.canRemainHomeLiveAt(kickoff.plusSeconds(90 * 60)))
    }

    @Test
    fun staleLivePinExpiresAfterSixHours() {
        val match = match(state = "in", status = "LIVE")

        assertTrue(match.canRemainHomeLiveAt(kickoff.plusSeconds(5 * 60 * 60)))
        assertFalse(match.canRemainHomeLiveAt(kickoff.plusSeconds(7 * 60 * 60)))
    }

    private fun match(state: String, status: String, description: String? = null) = ScheduleMatch(
        matchId = "match-1",
        league = "fifa.world",
        name = "Home vs Away",
        shortName = null,
        status = status,
        statusDescription = description,
        state = state,
        kickoff = kickoff.toString(),
        homeTeam = null,
        awayTeam = null,
        venue = null,
        detailApi = "/match-1"
    )
}
